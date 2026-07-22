package com.focusgate.launcher.agent

import android.content.Context
import android.util.Log
import com.focusgate.launcher.model.AgentDecision
import com.focusgate.launcher.model.DecisionParser
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** Lifecycle of the on-device model, surfaced to the UI. */
sealed interface AgentState {
    data object Checking : AgentState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : AgentState
    data object Ready : AgentState
    data class Unavailable(val reason: String) : AgentState
}

/**
 * On-device LLM gatekeeper backed by Google AI Edge's MediaPipe LLM Inference
 * (a Gemma model we ship/side-load ourselves). This deliberately does NOT use
 * Gemini Nano / AICore: that path depends on Google provisioning a feature to
 * the device, which isn't available on every phone. Here the model file lives
 * on the device and runs on its GPU/CPU — fully offline, no cloud, no keys.
 *
 * The public surface (initialize / decide / close) is identical to the previous
 * AICore-based agent, so the ViewModel, chat UI, and timer are untouched.
 */
class FocusAgent(private val context: Context) {

    // Heavyweight: holds the loaded weights + KV cache. Created once in
    // initialize(), reused for every decision, released in close().
    @Volatile
    private var engine: LlmInference? = null

    // MediaPipe's LLM engine is thread-affine — its GPU/OpenCL context binds to
    // the thread that first drives it. EVERY engine call (load + all inference)
    // must run on this one dedicated thread; otherwise the 2nd inference lands on
    // a different pool thread and deadlocks.
    private val llmDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "focusgate-llm") }
            .asCoroutineDispatcher()

    /** Where the user side-loads the model. Readable via `adb push`, no runtime permission. */
    private val modelDir: File get() = context.getExternalFilesDir(null) ?: context.filesDir

    /**
     * Locate a side-loaded model file. Accepts whatever the user downloaded from
     * the LiteRT/Gemma release (.task or .litertlm), so they don't have to rename it.
     */
    private fun findModelFile(): File? =
        modelDir.listFiles()
            ?.firstOrNull { it.isFile && (it.extension == "task" || it.extension == "litertlm") }

    /**
     * Unpack the model bundled inside the APK (assets/gemma.task) to internal
     * storage once. MediaPipe needs a real file path (it mmaps the file and can't
     * read straight from the APK zip), so the first launch copies it out. Returns
     * null only if this build has no bundled model.
     */
    private fun ensureBundledModel(): File? {
        val dest = File(context.filesDir, "gemma.task")
        if (dest.exists() && dest.length() > 100_000_000L) return dest // already unpacked
        return try {
            context.assets.open(BUNDLED_MODEL).use { input ->
                dest.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
            }
            dest
        } catch (e: Exception) {
            Log.w(TAG, "no bundled model in this build", e)
            runCatching { dest.delete() }
            null
        }
    }

    /**
     * Load the model. Emits states through [onState]; returns when Ready or
     * Unavailable is known. Loading a ~500 MB model takes a few seconds, so this
     * runs entirely off the main thread.
     */
    suspend fun initialize(onState: (AgentState) -> Unit) {
        onState(AgentState.Checking)

        // Prefer a side-loaded model (lets power users swap it) else unpack the one
        // bundled in the APK. Both the copy and the load are heavy, so run off-main.
        val modelFile = withContext(llmDispatcher) { findModelFile() ?: ensureBundledModel() }
        if (modelFile == null) {
            onState(
                AgentState.Unavailable(
                    "no model in this build. Side-load a Gemma .task into:\n" +
                        modelDir.absolutePath
                )
            )
            return
        }

        val loaded = withContext(llmDispatcher) {
            // GPU is much faster on the S25's Adreno; if GPU init fails for this
            // model build we transparently retry on CPU rather than give up.
            loadEngine(modelFile, LlmInference.Backend.GPU)
                ?: loadEngine(modelFile, LlmInference.Backend.CPU)
        }

        if (loaded != null) {
            engine = loaded
            onState(AgentState.Ready)
        } else {
            onState(
                AgentState.Unavailable(
                    "couldn't load ${modelFile.name} — it may be the wrong format " +
                        "(need a MediaPipe/LiteRT .task) or too large for memory"
                )
            )
        }
    }

    private fun loadEngine(modelFile: File, backend: LlmInference.Backend): LlmInference? = try {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            // Total token budget (prompt + reply) for the KV cache. Our prompt is
            // an app list + short instructions; 2048 leaves ample room and keeps
            // memory/latency modest on a 1B model.
            .setMaxTokens(2048)
            // Must be >= the session's topK below.
            .setMaxTopK(64)
            .setPreferredBackend(backend)
            .build()
        LlmInference.createFromOptions(context, options)
    } catch (e: Throwable) {
        Log.w(TAG, "engine load failed on $backend", e)
        null
    }

    /**
     * One gatekeeping round. Same contract as before: user message (+ optional
     * prior push-back) in, parsed [AgentDecision] out, null on failure.
     */
    suspend fun decide(
        userMessage: String,
        appLabels: List<String>,
        priorExchange: Pair<String, String>?,
    ): AgentDecision? {
        val llm = engine ?: return null
        val prompt = gemmaWrap(buildPrompt(userMessage, appLabels, priorExchange))
        val raw = withContext(llmDispatcher) {
            // Fresh session per decision so decisions don't accumulate context.
            // Low temperature + tight topK keep the JSON well-formed and stable.
            var session: LlmInferenceSession? = null
            try {
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.1f)
                    .setTopK(40)
                    .setTopP(0.9f)
                    .setRandomSeed(1)
                    .build()
                session = LlmInferenceSession.createFromOptions(llm, sessionOptions)
                session.addQueryChunk(prompt)
                // Synchronous, on the pinned llm thread. `adb logcat -s FocusAgent`
                // shows the raw output when tuning prompts. maxTokens on the engine
                // bounds how long a rambling generation can run.
                session.generateResponse().also { Log.d(TAG, "raw model output: $it") }
            } catch (e: Exception) {
                Log.w(TAG, "inference failed", e)
                null
            } finally {
                runCatching { session?.close() }
            }
        } ?: return null
        return DecisionParser.parse(raw)
    }

    /**
     * Yes/no judgment for a conditionally-allowed category: does [userText] give a
     * reason that qualifies as [requirement] (e.g. "educational")? Greedy decoding
     * for a stable one-word answer; fails closed (false) on any error so the
     * restriction holds rather than leaking access.
     */
    suspend fun meetsRequirement(userText: String, requirement: String): Boolean {
        val llm = engine ?: return false
        val prompt = gemmaWrap(
            "An app may be opened only if the user's reason is \"$requirement\". " +
                "Reason given: \"$userText\". " +
                "Answer with ONLY one word: YES if it clearly qualifies as $requirement, else NO."
        )
        val raw = withContext(llmDispatcher) {
            var session: LlmInferenceSession? = null
            try {
                val opts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.0f)
                    .setTopK(1)
                    .setTopP(1.0f)
                    .setRandomSeed(1)
                    .build()
                session = LlmInferenceSession.createFromOptions(llm, opts)
                session.addQueryChunk(prompt)
                session.generateResponse().also { Log.d(TAG, "requirement judge: $it") }
            } catch (e: Exception) {
                Log.w(TAG, "requirement judge failed", e)
                null
            } finally {
                runCatching { session?.close() }
            }
        } ?: return false
        val a = raw.uppercase()
        val yes = a.indexOf("YES")
        val no = a.indexOf("NO")
        return yes >= 0 && (no < 0 || yes < no)
    }

    /** Gemma instruction models expect this turn framing; MediaPipe won't add it for us. */
    private fun gemmaWrap(body: String): String =
        "<start_of_turn>user\n$body<end_of_turn>\n<start_of_turn>model\n"

    // NOTE on prompt design for a 1B model: we deliberately do NOT list installed
    // apps here. Feeding a 1B the app catalogue makes it echo random entries (it
    // once "approved" Adidas for a WhatsApp request). The model just names the app
    // the user asked for; InstalledAppsRepository fuzzy-matches it to a package.
    // A one-shot example per verdict keeps the tiny model on-format.
    private fun buildPrompt(
        userMessage: String,
        @Suppress("UNUSED_PARAMETER") appLabels: List<String>,
        priorExchange: Pair<String, String>?,
    ): String = buildString {
        appendLine("You are FocusGate, a strict but fair gatekeeper for phone apps.")
        appendLine("Decide whether to let the user open the app they ask for.")
        appendLine("Rules:")
        appendLine("- Every request must state how many minutes. If minutes is missing or 0, verdict is \"question\" asking how long — even for tools.")
        appendLine("- Tools (work, study, calls, maps, banking, utilities): once a duration is given, approve readily.")
        appendLine("- Distractions (social media, video, games): require a real reason AND at most 30 minutes.")
        appendLine("- If the app or reason is vague, verdict \"question\" and ask for what's missing.")
        appendLine("Reply with ONE line of minified JSON and nothing else, using this schema:")
        appendLine(
            """{"app":"<app the user named>","purpose":"<short>","category":"tool|distraction",""" +
                """"minutes":<int>,"verdict":"approve|question","reply":"<one short sentence to the user>"}"""
        )
        appendLine("Example — User: \"WhatsApp to reply to my mum, 10 minutes\"")
        appendLine(
            """{"app":"WhatsApp","purpose":"reply to mum","category":"tool","minutes":10,""" +
                """"verdict":"approve","reply":"Sure — 10 minutes on WhatsApp."}"""
        )
        appendLine("Example — User: \"instagram\"")
        appendLine(
            """{"app":"Instagram","purpose":"","category":"distraction","minutes":0,""" +
                """"verdict":"question","reply":"What do you need Instagram for, and for how long?"}"""
        )
        if (priorExchange != null) {
            appendLine("Earlier the user asked: \"${priorExchange.first}\"")
            appendLine("You replied: \"${priorExchange.second}\"")
            appendLine("This is their final justification — approve it, or ask once more only if it is still too vague.")
        }
        append("Now respond to — User: \"$userMessage\"")
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
        runCatching { llmDispatcher.close() }
    }

    private companion object {
        const val TAG = "FocusAgent"
        const val BUNDLED_MODEL = "gemma.task" // in app/src/main/assets, unpacked on first run
    }
}

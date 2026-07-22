package com.focusgate.launcher.ui

import android.app.Application
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focusgate.launcher.agent.AgentState
import com.focusgate.launcher.agent.FocusAgent
import com.focusgate.launcher.apps.InstalledApp
import com.focusgate.launcher.apps.InstalledAppsRepository
import com.focusgate.launcher.model.AgentDecision
import com.focusgate.launcher.model.ChatMessage
import com.focusgate.launcher.schedule.AppCategorizer
import com.focusgate.launcher.schedule.Category
import com.focusgate.launcher.schedule.ScheduleRepository
import com.focusgate.launcher.service.FocusAccessibilityService
import com.focusgate.launcher.timer.FocusTimerService
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val agent = FocusAgent(application)
    private val appsRepo = InstalledAppsRepository(application)
    private val scheduleRepo = ScheduleRepository.getInstance(application)

    /** First-run flow: the intro/FAQ, then tag apps for cost tiers, then the chat. */
    enum class SetupStep { NONE, INTRO, CATEGORIZE }

    private val _setupStep = MutableStateFlow(currentSetupStep())
    val setupStep: StateFlow<SetupStep> = _setupStep.asStateFlow()

    /** The live coin balance, shown in the header. */
    val coins: StateFlow<Int> = scheduleRepo.coins

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _agentState = MutableStateFlow<AgentState>(AgentState.Checking)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private val _isDefaultHome = MutableStateFlow(true)
    val isDefaultHome: StateFlow<Boolean> = _isDefaultHome.asStateFlow()

    /** Whether the hard-eject Accessibility Service is currently enabled by the user. */
    private val _blockingEnabled = MutableStateFlow(checkBlockingEnabled())
    val blockingEnabled: StateFlow<Boolean> = _blockingEnabled.asStateFlow()

    /**
     * The in-progress negotiation for one app request. We accumulate the whole
     * exchange here (not just a single round) so context is never lost, and we
     * track how many times the agent has pushed back so it must decide instead
     * of interrogating forever.
     */
    private var pending: Pending? = null

    private data class Pending(val transcript: String, val questionsAsked: Int)

    private val nextId = AtomicLong(0)
    private var welcomed = false

    init {
        refreshHomeStatus()
        viewModelScope.launch {
            // initialize() emits its states from a background thread; StateFlow
            // updates and the message list (via update{}) are thread-safe.
            agent.initialize { state ->
                _agentState.value = state
                if (!welcomed && (state is AgentState.Ready || state is AgentState.Unavailable)) {
                    welcomed = true
                    greetFor(state)
                }
            }
        }
    }

    private fun greetFor(state: AgentState) {
        when (state) {
            is AgentState.Ready ->
                addAgent("FoKused here. Which app do you need, what for, and for how many minutes?")
            is AgentState.Unavailable ->
                addAgent(
                    "On-device AI is unavailable (${state.reason}). Gatekeeping is off — " +
                        "type e.g. \"open YouTube 10\" and I'll launch it with a timer."
                )
            else -> Unit
        }
    }

    /**
     * Wipe the conversation back to a fresh greeting. Called when the screen
     * locks so returning to FocusGate always starts a clean session — matching
     * the launcher's ephemeral, distraction-free intent.
     */
    fun reset() {
        if (_messages.value.isEmpty()) return // already fresh; avoid a needless redraw
        pending = null
        _thinking.value = false
        _messages.value = emptyList()
        greetFor(_agentState.value)
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _thinking.value) return
        addUser(text)
        when (_agentState.value) {
            is AgentState.Ready -> evaluate(text)
            is AgentState.Unavailable -> manualOpen(text)
            else -> addAgent("Hold on — the gatekeeper model is still getting ready.")
        }
    }

    private fun evaluate(text: String) {
        viewModelScope.launch {
            _thinking.value = true
            try {
                val apps = withContext(Dispatchers.IO) { appsRepo.launchableApps() }
                val prior = pending
                // Accumulate the whole negotiation so the thread is never lost.
                val transcript = prior?.let { "${it.transcript}. $text" } ?: text
                val asked = prior?.questionsAsked ?: 0

                val decision = agent.decide(
                    userMessage = transcript,
                    appLabels = apps.map { it.label },
                    priorExchange = null, // full context already lives in the transcript
                )

                // Resolve the two things a launch actually needs IN CODE — never
                // trust a 1B model to hold context or reliably extract fields.
                val target = appsRepo.match(decision?.app.orEmpty(), apps)
                    ?: appsRepo.match(transcript, apps)
                val minutes = (decision?.minutes ?: 0).takeIf { it in 1..600 }
                    ?: extractMinutes(transcript)
                val wantsApprove = decision?.verdict.equals("approve", ignoreCase = true)

                when {
                    // We have an app and a duration. Approve if the model approves,
                    // OR if it already pushed back once — after one justification we
                    // stop stalling and honour a complete request. This is what
                    // prevents the endless-question / off-topic spiral.
                    target != null && minutes != null && (wantsApprove || asked >= 1) -> {
                        pending = null
                        openWithCoins(target, minutes.coerceIn(1, 120))
                    }
                    // Don't know which app yet.
                    target == null -> {
                        pending = Pending(transcript, asked + 1)
                        addAgent(
                            if (asked >= 1) "I still can't tell which app — type its exact name and how many minutes."
                            else decision?.reply?.ifBlank { null }
                                ?: "Which app do you want, what for, and for how many minutes?"
                        )
                    }
                    // Have the app but no duration — FocusGate always needs a length.
                    minutes == null -> {
                        pending = Pending(transcript, asked + 1)
                        addAgent("How many minutes do you want on ${target.label}?")
                    }
                    // Have app + duration, but the model wants to push back. Allow
                    // exactly one push-back (the branch above resolves the next turn).
                    else -> {
                        pending = Pending(transcript, asked + 1)
                        addAgent(
                            decision?.reply?.ifBlank { null }
                                ?: "Why do you need ${target.label} right now?"
                        )
                    }
                }
            } finally {
                _thinking.value = false
            }
        }
    }

    /** Pull a duration out of free text: "2 hours" -> 120, otherwise the first number = minutes. */
    private fun extractMinutes(text: String): Int? {
        Regex("""(\d{1,3})\s*(hours?|hrs?|h)\b""", RegexOption.IGNORE_CASE).find(text)?.let {
            return (it.groupValues[1].toInt() * 60).coerceIn(1, 600)
        }
        return Regex("""\b(\d{1,3})\b""").find(text)?.groupValues?.get(1)?.toInt()
            ?.takeIf { it in 1..600 }
    }

    /**
     * Charge coins, then launch. Entertainment costs 10×; if the balance can't
     * cover it we refuse and nudge the user to earn more by staying off the phone.
     */
    private fun openWithCoins(target: InstalledApp, minutes: Int) {
        val cost = scheduleRepo.costFor(target.packageName, minutes)
        if (!scheduleRepo.canAfford(cost)) {
            addAgent(
                "${target.label} for $minutes min costs $cost 🪙 — you've got " +
                    "${scheduleRepo.coins.value}. Keep your screen off to earn more (1 🪙/min)."
            )
            return
        }
        scheduleRepo.spend(cost)
        val tier = if (scheduleRepo.isEntertainment(target.packageName)) " (entertainment)" else ""
        addAgent(
            "−$cost 🪙$tier · opening ${target.label} for $minutes min · " +
                "balance ${scheduleRepo.coins.value} 🪙"
        )
        startSession(target, minutes)
    }

    private fun startSession(app: InstalledApp, minutes: Int) {
        val context = getApplication<Application>()
        // Register the approved session so the eject service leaves this app alone
        // until the timer runs out, even if its category is otherwise blocked now.
        scheduleRepo.startSession(app.packageName, minutes)
        FocusTimerService.start(context, app.packageName, app.label, minutes)
        val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch == null) {
            addAgent("${app.label} has no launchable screen — pick another app.", isError = true)
            return
        }
        // Launching from an app context requires NEW_TASK.
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }

    // ---- one-time setup: tag apps so each has a coin cost tier ----

    private fun currentSetupStep(): SetupStep = when {
        !scheduleRepo.hasSeenIntro() -> SetupStep.INTRO
        !scheduleRepo.hasCategories() -> SetupStep.CATEGORIZE
        else -> SetupStep.NONE
    }

    fun dismissIntro() {
        scheduleRepo.markIntroSeen()
        refreshSetup()
    }

    /** Re-evaluate whether a setup screen is due (called on resume / first unlock). */
    fun refreshSetup() {
        _setupStep.value = currentSetupStep()
    }

    /** Apps to tag, paired with the best-guess category for pre-selection. */
    fun appsForCategorize(): List<Pair<InstalledApp, Category>> {
        val current = scheduleRepo.categories.value
        val ctx = getApplication<Application>()
        return appsRepo.launchableApps().map { app ->
            app to (current[app.packageName] ?: AppCategorizer.suggest(ctx, app.packageName))
        }
    }

    fun saveCategories(map: Map<String, Category>) {
        scheduleRepo.setCategories(map)
        refreshSetup()
    }

    // ---- fallback path when Gemini Nano is genuinely unavailable ----

    private val manualCommand =
        Regex("""^open\s+(.+?)(?:\s+for)?\s+(\d{1,3})\s*(?:m|min|mins|minutes)?$""", RegexOption.IGNORE_CASE)

    private fun manualOpen(text: String) {
        val match = manualCommand.find(text)
        if (match == null) {
            addAgent("AI is unavailable on this device, so use: open <app name> <minutes>")
            return
        }
        val (name, mins) = match.destructured
        val target = appsRepo.match(name)
        if (target == null) {
            addAgent("I can't find \"$name\" on this phone.")
            return
        }
        val minutes = mins.toInt().coerceIn(1, 120)
        openWithCoins(target, minutes)
    }

    // ---- callbacks from MainActivity ----

    fun onTimerExpired(label: String) {
        scheduleRepo.clearSession()
        addAgent("Time's up for $label. What's next?")
    }

    /** Bank any screen-off earnings and celebrate them in the chat (called on resume). */
    fun settleEarnings() {
        scheduleRepo.settleScreenOff()
        val earned = scheduleRepo.takePendingAnnounce()
        if (earned > 0) addAgent("+$earned 🪙 for staying off your phone!")
    }

    fun refreshBlockingStatus() {
        _blockingEnabled.value = checkBlockingEnabled()
    }

    private fun checkBlockingEnabled(): Boolean {
        val ctx = getApplication<Application>()
        val expected = ComponentName(ctx, FocusAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun refreshHomeStatus() {
        val context = getApplication<Application>()
        _isDefaultHome.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager
                .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == context.packageName
        }
    }

    // ---- helpers ----

    private fun addUser(text: String) =
        append(ChatMessage(nextId.getAndIncrement(), text, fromUser = true))

    private fun addAgent(text: String, isError: Boolean = false) =
        append(ChatMessage(nextId.getAndIncrement(), text, fromUser = false, isError = isError))

    private fun append(message: ChatMessage) {
        _messages.update { it + message }
    }

    override fun onCleared() {
        agent.close()
        super.onCleared()
    }
}

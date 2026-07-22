# FocusGate

A minimal Android **launcher** that replaces your home screen with a chat.
To open any app you must tell an **on-device** AI agent *which app, why, and for
how long*. The agent approves or pushes back; on approval it launches the app
with a foreground-service countdown, and when time runs out a full-screen
notification pulls you back to the gate. No cloud, no API keys, no persistence.

The agent runs a **Gemma** model locally via Google AI Edge's MediaPipe LLM
Inference (`com.google.mediapipe:tasks-genai`). This was chosen over Gemini
Nano / ML Kit AICore because Nano depends on Google provisioning a specific
feature to the device — on the S25 Ultra that feature (`606-FEATURE_NOT_FOUND:
Feature 636`) is not yet available to third-party apps, even though the phone
runs Nano v2 for first-party features. MediaPipe instead runs a model file we
side-load ourselves, so it works on any capable device regardless of AICore
provisioning — still fully offline. See **Model setup** below.

Target device: Samsung Galaxy S25 Ultra · Android 15/16 · One UI 7.

## Project layout

```
app/src/main/java/com/focusgate/launcher/
├── MainActivity.kt                  # HOME activity, role request, back-swallow, expiry re-entry
├── agent/FocusAgent.kt              # MediaPipe LLM wrapper: load model → session → inference
├── apps/InstalledAppsRepository.kt  # PackageManager query + fuzzy app matching
├── model/AgentDecision.kt           # JSON contract + defensive parser
├── model/ChatMessage.kt
├── timer/FocusTimerService.kt       # FGS countdown + chronometer notification + full-screen expiry
└── ui/
    ├── ChatViewModel.kt             # MVVM state: messages, agent state, approve/question loop
    ├── ChatScreen.kt                # Compose chat UI (bubbles, banners, input bar)
    └── theme/Theme.kt               # always-dark, calm Material 3 scheme
```

## Build & install

1. Open the `FocusGate` folder in Android Studio (Ladybug or newer, SDK 35 installed).
2. First sync: if Studio complains about a missing Gradle wrapper jar, either let
   Studio provision it, or run `gradle wrapper --gradle-version 8.9` once from a
   machine with Gradle installed. Everything else downloads on sync.
3. `Run` on the S25 Ultra over USB. Then tap **Set as home app** in the banner
   (or Settings → Apps → Choose default apps → Home app → FocusGate).

To escape FocusGate during development: Settings → Apps → Choose default apps →
Home app → One UI Home.

## Model setup (required once)

The app looks for a MediaPipe/LiteRT model file (`*.task` or `*.litertlm`) in its
own external files dir and loads the first one it finds. Nothing is bundled in
the APK — you side-load it once:

1. **Get the model** (license-gated, one-time): open
   [`litert-community/Gemma3-1B-IT`](https://huggingface.co/litert-community/Gemma3-1B-IT)
   on Hugging Face, sign in, accept the Gemma license, and download
   `gemma3-1b-it-int4.task` (~529 MB, 4-bit, GPU-capable, tokenizer bundled).
2. **Push it to the phone** (the folder already exists once the app has run once):
   ```
   adb push gemma3-1b-it-int4.task /sdcard/Android/data/com.focusgate.launcher/files/
   ```
3. **Reopen FocusGate.** The header flips from *AI offline* → *on-device AI ready*
   once the engine loads (a few seconds on first launch).

The filename doesn't matter — any `.task`/`.litertlm` in that folder is picked up.

## The core loop

1. You type e.g. *"YouTube, one video my friend sent, 10 minutes"*.
2. `FocusAgent` builds one tight prompt (your message + installed-app labels),
   wraps it in Gemma's turn template, runs it in a low-temperature
   `LlmInferenceSession`, and asks for **only** compact JSON:
   `{"app":…,"purpose":…,"category":"tool|distraction","minutes":…,"verdict":"approve|question","reply":…}`
3. `approve` → fuzzy-match the app, start `FocusTimerService`, launch the app.
   `question` → the agent's reply is shown; you get **one** justification round,
   then it decides for good.
4. The persistent notification counts down (a system chronometer — zero
   per-second updates). On expiry a full-screen notification returns you to chat.

## On-device AI notes & sharp edges

- **No model = graceful offline.** With no model file present the chat says so
  (showing the exact folder path) and accepts a manual command instead:
  `open <app name> <minutes>` (launch + timer, no gatekeeping).
- **GPU with CPU fallback.** The engine loads on the GPU backend for speed; if
  GPU init fails for a given model build, `FocusAgent` transparently retries on
  CPU before reporting unavailable.
- **One session per decision.** Each decision runs in a fresh
  `LlmInferenceSession` (temperature 0.1) so decisions don't accumulate context;
  `maxTokens=2048` bounds the KV cache.
- **Model swaps.** Everything is confined to `agent/FocusAgent.kt`. Drop in a
  different `.task` (e.g. a larger Gemma) and it's used automatically. The
  MediaPipe LLM Inference API is in maintenance mode; the going-forward Kotlin
  API is LiteRT-LM — a future migration would again be contained to that file.
- **Full-screen intents.** On Android 14+ only alarm/call apps get true
  full-screen launches by default; others may be downgraded to a heads-up
  notification. Tapping it still returns to FocusGate. You can grant the
  full-screen permission manually under App info → Additional permissions.
- **`QUERY_ALL_PACKAGES`.** Required for a launcher, but needs a policy
  declaration if this ever ships on Google Play.

## What it deliberately doesn't do

No Room/persistence (chat resets on process death — `stateNotNeeded="true"`),
no summary screen, no app grid, no way to bypass the chat from inside the app.
Enforcement is soft: the timer nudges you back, it does not kill the app.

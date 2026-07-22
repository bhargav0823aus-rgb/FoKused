# FoKused

Turn your home screen into a game. Earn coins by keeping your phone down, spend
them to open apps, and let an on-device AI dragon keep you focused.

FoKused is an Android **launcher** that replaces your home screen with a chat. To
open any app you ask **Dragon King Fo**, and it costs coins. You earn coins by
keeping the screen off. Fun apps cost more than useful ones. Everything runs
on-device: a local Gemma model, no cloud, no account.

Target device: Samsung Galaxy S25 Ultra (Android 15/16, One UI 7). minSdk 26.

## Download

From the [**v1.0 release**](https://github.com/bhargav0823aus-rgb/FoKused/releases/tag/v1.0):

- `FoKused-v1.0.apk` (~590 MB) - **the complete app with the AI model bundled in.**
  Just sideload it and open (allow "Install unknown apps"). On first launch it
  unpacks the model to internal storage, then Dragon Fo is ready. No separate
  download or `adb push` needed.

Prefer a smaller install? The model is also attached separately as
`gemma3-1b-it-int4.task` - build the app from source (a slim APK), then side-load
the model:
```
adb push gemma3-1b-it-int4.task /sdcard/Android/data/com.focusgate.launcher/files/
```
The app uses a side-loaded model if present, otherwise the bundled one.

## How it works

- **Earn coins** by staying off your phone: every full minute the screen is off is
  1 coin.
- **Spend coins** to open apps through the chat. Cost = minutes x rate. Normal apps
  (messaging, work, calls, music) are 1 coin/min. Entertainment (social, video,
  games) is 10 coins/min. So WhatsApp for 5 minutes is 5 coins; Instagram for 10 is
  100.
- **Dragon Fo** is the gate: you say the app, why, and how long. A local Gemma model
  parses your request; the app resolves the exact app, minutes, and cost in code and
  charges you. Not enough coins means no entry.
- **Strict mode** (optional Accessibility service): the chat is then the only door
  into any app. Open something another way (recents, a notification, another
  launcher) and Fo sends you back to the gate. Dialer, Settings, and FoKused are
  always exempt.
- A **countdown timer** runs for the session you paid for; when it ends you are
  returned to the gate.

## Tech stack

- **Kotlin**, **Jetpack Compose**, **Material 3**, single-Activity **MVVM**
  (ViewModel + StateFlow), coroutines.
- On-device LLM: **Google AI Edge / MediaPipe LLM Inference** (`tasks-genai`)
  running **Gemma 3 1B** (4-bit `.task`), GPU-accelerated, pinned to a dedicated
  thread.
- Persistence: **SharedPreferences** + **kotlinx.serialization** (no database).
- Enforcement: **AccessibilityService** (foreground-app watcher).
- Timer: **foreground Service** with a countdown notification.
- Coin earning: **BroadcastReceiver** for screen on/off.
- Pixel-art UI: custom mascot animation, `Pixelify Sans` font, black/yellow/green
  theme.

## Project layout

```
app/src/main/java/com/focusgate/launcher/
  MainActivity.kt                 HOME activity, lifecycle, role + settings
  FoKusedApplication.kt           screen on/off receiver -> coin earning
  agent/FocusAgent.kt             MediaPipe LLM wrapper (load, infer, parse)
  model/AgentDecision.kt          JSON contract + defensive parser
  apps/InstalledAppsRepository.kt PackageManager query + fuzzy app match
  schedule/ScheduleRepository.kt  shared state: coins, category tags, session
  schedule/Category.kt, AppCategorizer.kt   cost tiers
  service/FocusAccessibilityService.kt      total-lockdown eject
  timer/FocusTimerService.kt      foreground countdown
  ui/  ChatViewModel, ChatScreen, IntroScreen, CategorizeScreen, Mascot, theme
```

## Build from source

1. Open the project in Android Studio (SDK 35 installed) and let it sync, or run
   `./gradlew :app:assembleRelease` (JDK 17).
2. Install the APK on a device and set FoKused as the home app when prompted.
3. Side-load the Gemma model as shown under **Download**.

The release build here is signed with the local debug key for easy sideloading;
swap in your own keystore for Play Store distribution.

## License / credits

- App code: your project.
- The mascot, coin, and logo art are pixel assets in `Pixel Images/` and
  `app/src/main/res`.
- The AI model is Google's **Gemma 3 1B**, used under the
  [Gemma license](https://ai.google.dev/gemma/terms). It is distributed via the
  GitHub release for convenience; by downloading it you accept those terms.
- Font: **Pixelify Sans** (SIL Open Font License).

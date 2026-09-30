# SAM — Smart Autonomous Machine

<p align="center">
  <strong>A voice-first, on-device AI assistant that controls your Android phone.</strong>
</p>

<p align="center">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-green">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-36-blue">
  <img alt="Kotlin" src="https://img.shields.io/badge/lang-Kotlin-7F52FF">
  <img alt="Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-lightgrey">
</p>

---

**SAM** is an Android assistant that listens, understands natural-language commands,
and then *acts* on the device for you — opening apps, placing calls, sending messages,
setting alarms, navigating UI, summarizing notifications, answering questions from your
own documents, and more. It blends local on-device capabilities (voice, OCR, accessibility)
with pluggable cloud LLM providers (Gemini, OpenAI-compatible, Anthropic, or a local model).

## ✨ Key Features

- 🎙️ **Voice-first interaction** — wake/record speech, ASR, and TTS with a live status **orb** overlay.
- 🧠 **Command understanding** — parses natural-language intents into typed actions with slot filling and multi-language support.
- 🤖 **Pluggable AI providers** — Google Gemini, OpenAI / Groq (OpenAI-compatible), Anthropic Claude, or a local endpoint.
- 📱 **Device control** — open apps, web/app search, calls, WhatsApp messaging, alarms, timers, calendar, maps, volume & media.
- ♿ **Accessibility automation** — tap, scroll, and type on-screen through an AccessibilityService (never bypasses lock screens or payments).
- 🔤 **Visual Lens (OCR)** — capture or pick an image and read text via ML Kit, then ask the model about it.
- 📚 **Personal Knowledge Base** — index PDFs and images and ask questions over your own documents.
- 🔔 **Notification intelligence** — reads and summarizes notifications via a NotificationListenerService.
- 🧩 **Task Engine** — builds and executes multi-step plans with confirmation and verification gates.
- 🎛️ **System modes** — Normal, Voice Assistant, Driving, Focus, Silent, Developer.
- 🔐 **Secrets stored securely** — API keys kept in EncryptedSharedPreferences (never committed).

## 🏗️ Architecture

Modern Android stack with a clean, layered structure:

| Layer | Responsibility |
|-------|----------------|
| `ui/` | Jetpack Compose screens, animated orb, overlay host, theme |
| `engine/` | voice, command parser, AI providers, accessibility, vision, knowledge, notifications, task runtime |
| `domain/` | Models, intents, parsed commands, action plans |
| `data/` | Room database + seeding |
| `core/` | DI (Hilt), identity, network monitor, permissions, secure secret store |

**Tech stack:** Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore ·
OkHttp · Kotlinx Serialization · Coroutines · CameraX · ML Kit · PDFBox-Android ·
AndroidX Security Crypto.

## 📂 Project Structure

```
app/src/main/java/com/shlok/sam/
├── core/          # DI, identity, network, permissions, secret store
├── data/          # Room DB + seed data
├── domain/        # Models & intent types
├── engine/        # ai, android, command, knowledge, notifications,
│                  # overlay, runtime, task, vision, voice
└── ui/            # orb, overlay, screens, theme, root + viewmodel
```

## 🚀 Getting Started

### Prerequisites
- **Android Studio** (Ladybug or newer)
- **JDK 17**
- **Android SDK** with Platform 36 and Build-Tools 36.x
- A physical device or emulator running **Android 8.0 (API 26)+**

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/theshlok18/cV-app3.git
   cd cV-app3
   ```

2. **Open in Android Studio** and let Gradle sync.

3. **Configure the SDK path** — ensure `local.properties` points to your Android SDK:
   ```properties
   sdk.dir=/path/to/your/Android/sdk
   ```

4. **Provide AI provider keys** (optional — local/None works too). SAM reads secrets
   from a secure store; add your Gemini / OpenAI / Anthropic / Groq key from within the app.

5. **Run** the `app` configuration on a device or emulator:
   ```bash
   ./gradlew assembleDebug
   ```

### Post-install permissions
To unlock its full capabilities, grant SAM:
- **Microphone** (voice) & **Camera** (Visual Lens)
- **Display over other apps** (the floating orb overlay)
- **Accessibility** service (on-screen automation)
- **Notification access** (notification summaries)

## ⚙️ Build Commands

```bash
./gradlew compileDebugKotlin   # compile only
./gradlew assembleDebug        # build debug APK
./gradlew assembleRelease      # build release APK
./gradlew test                 # run unit tests
```

## 🔒 Security & Privacy

- API keys are stored in **EncryptedSharedPreferences** and are never committed to the repo.
- The AccessibilityService performs only user-requested actions and does **not** bypass
  lock screens, payments, or system security.
- SAM can run fully with a **local** model endpoint if you prefer to keep data on-device.

## 🤝 Contributing

Contributions are welcome! Feel free to open an issue or submit a pull request.
1. Fork the repo
2. Create a feature branch (`git checkout -b feature/amazing-idea`)
3. Commit your changes (`git commit -m "Add amazing idea"`)
4. Push to the branch (`git push origin feature/amazing-idea`)
5. Open a Pull Request

## 📄 License

Released under the **MIT License**. See `LICENSE` for details.

---

<p align="center">Built by <a href="https://github.com/theshlok18">Shlok</a>.</p>

Read in other languages: [Русский](./README.md)

# FromChat

FromChat is a 100% free and open-source messenger. This repository contains the cross-platform client for Android and iOS.

[📥 Download](https://github.com/fromchat-messenger/app/releases/latest) • [💬 Telegram Channel](https://t.me/fromchat_ch) • [🖥️ Server](https://github.com/fromchat-messenger/backend)

## ✨ Features

- **Voice and video calls** — high-quality communication via LiveKit
- **Screen sharing** — share your screen during calls
- **Group chat** — community of all server users
- **Direct messages** — uses legal encryption scheme. Server-side E2EE mode planned.
- **Device management** — control active sessions
- **Dark mode by default** — stylish and easy on the eyes
- **Open source** — full transparency

## 📊 Client Comparison

⚠️ **iOS is temporarily not supported.** iOS development requires a lot of time and due to Apple's restrictions it is much more complex than Android. Therefore it will be released later.

This table shows which features are implemented in the official clients.


| Feature | Android | Web | iOS |
| --- | --- | --- | --- |
| **Messaging and profiles** | ✅ | ✅ | ✅ |
| **Voice/video calls** | ✅ | ❌ | ❌ |
| **Screen sharing** | ✅ | ❌ | ❌ |
| **Message reactions** | ❌ | ✅ | ❌ |
| **Rich attachment support** | ✅ | ❌ | ❌ |


---

## 🏗️ Tech Stack

- **Kotlin** — a programming language focused on safety and performance
- **Compose Multiplatform** — declarative UI framework for cross-platform development
- **Material Design 3** — modern user interface components
- **Ktor Client** — asynchronous HTTP client for network requests
- **LiveKit** — infrastructure for video calls and conferences
- **SQLDelight** — type-safe SQL queries for local data storage
- **Firebase Messaging** — push notifications
- **Coil** — image loading and caching


## 📥 Build and Development (Android Studio)

### Requirements

- Latest version of Android Studio
- Components updated to the latest versions

### Quick Start

1. **Clone the repository:**
  ```bash
   git clone https://github.com/fromchat-messenger/app.git
   cd app
  ```
2. **Generate keys (Debug & Release):**
  1. Set variables (replace `CHANGEME` with your secure passwords):
    ```bash
    DEBUG_STORE_PASS=CHANGEME
    DEBUG_KEY_PASS=CHANGEME
    RELEASE_STORE_PASS=CHANGEME
    RELEASE_KEY_PASS=CHANGEME
    ```
  2. Run commands:
    ```bash
    mkdir -p app/android/keys

    keytool -genkey -v -keystore app/android/keys/debug.jks \
      -keyalg RSA -keysize 2048 -validity 10000 \
      -alias key0 -storepass $DEBUG_STORE_PASS -keypass $DEBUG_KEY_PASS \
      -dname "CN=Debug, O=FromChat, C=RU"

    keytool -genkey -v -keystore app/android/keys/release.jks \
      -keyalg RSA -keysize 2048 -validity 10000 \
      -alias key0 -storepass $RELEASE_STORE_PASS -keypass $RELEASE_KEY_PASS \
      -dname "CN=Release, O=FromChat, C=RU"

    cat > app/android/keystore.properties << EOF
    releaseStorePassword=$RELEASE_STORE_PASS
    releaseKeyPassword=$RELEASE_KEY_PASS
    debugStorePassword=$DEBUG_STORE_PASS
    debugKeyPassword=$DEBUG_KEY_PASS
    EOF
    ```
3. **Open in Android Studio:**
  - Select: `File → Open → app/`
  - Android Studio automatically loads all dependencies and syncs Gradle
4. **Run:**
  - Click `Run → Run 'Android'` (Shift+F10)
  - Select an emulator or connected device

### Project Structure

```
android/
├── app/ # Client code
│   ├── android/         # Android app module
│   └── shared/          # Shared Compose code (Android + iOS)
│       ├── commonMain/  # Cross-platform code
│       ├── androidMain/ # Android-specific code
│       └── iosMain/     # iOS-specific code (not ready yet)
├── utils/ # Module with useful stuff that can be used in other projects
│   ├── android/ # Android library module
│   └── shared/           
└── gradle/libs.versions.toml # Dependency management
```

## 🤝 Contribute

If you want to support the project development, just send a Pull Request. I'm sure you already know that, I'm too lazy to write it all out.

## 📄 License

This project is licensed under the GNU Affero General Public License v3.0. For details, see the [LICENSE](./LICENSE) file.

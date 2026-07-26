Read in other languages: [Русский](./README.md)

# FromChat

FromChat is a 100% free and open-source messenger. This repository is the cross-platform client (Android, Desktop via Compose Multiplatform, and iOS).

[📥 Download](https://github.com/fromchat-messenger/android/releases/latest) • [💬 Telegram Channel](https://t.me/fromchat_ch) • [🖥️ Server](https://github.com/fromchat-messenger/backend)

## ✨ Features

- **Voice and video calls** — LiveKit
- **Screen sharing** during calls (Android)
- **Public chat** — server-wide community
- **Direct messages** — legal encryption scheme; server-side E2EE planned
- **Device management** — active sessions
- **Dark mode** by default
- **Open source**

## 📊 Client Comparison

| Feature | Android | Desktop | Web | iOS |
| --- | --- | --- | --- | --- |
| **Messaging and profiles** | ✅ | ✅ | ✅ | ✅ |
| **Voice/video calls** | ✅ | ✅ | ✅ | ✅ |
| **Screen sharing** | ✅ | ❌ | ✅ | ❌ |
| **Push when backgrounded** | ✅ (FCM) | ✅ (persistent WS + tray) | ✅ | ❌ (WS while open) |
| **Message reactions** | ❌ | ❌ | ✅ | ❌ |
| **Rich attachment support** | ✅ | ✅ | ❌ | ✅ |

Desktop replaces the former Electron client in the Web repo. Run with `./gradlew :app:desktop:run`.

---

## 🏗️ Tech Stack

- **Kotlin**
- **Compose Multiplatform**
- **Material Design 3**
- **Ktor Client**
- **LiveKit** — calls
- **SQLDelight** — local storage
- **Firebase Messaging** — push
- **Coil** — images

---

## 📥 Build and Development (Android Studio)

### Requirements

- Latest Android Studio
- JDK from Android Studio (JetBrains Runtime)

### Quick start

1. **Clone the repository:**

   ```bash
   git clone https://github.com/fromchat-messenger/android.git
   cd android
   ```

2. **Generate keys (Debug & Release):**

   ```bash
   DEBUG_STORE_PASS=CHANGEME
   DEBUG_KEY_PASS=CHANGEME
   RELEASE_STORE_PASS=CHANGEME
   RELEASE_KEY_PASS=CHANGEME

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

3. **Open in Android Studio:** `File → Open` → repo root. Gradle syncs dependencies automatically.

4. **Run:** `Run → Run 'Android'` (or the app debug configuration).  
   Debug application id: `ru.fromchat.beta`.

### CLI build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:shared:compileAndroidMain :app:android:assembleDebug
```

APK: `app/android/build/outputs/apk/debug/android-debug.apk`.

### Project structure

```
android/
├── app/
│   ├── android/          # Android app module
│   └── shared/           # Compose Multiplatform
│       ├── commonMain/
│       ├── androidMain/
│       └── iosMain/      # not ready yet
├── utils/                # reusable utilities
│   ├── android/
│   └── shared/
└── gradle/libs.versions.toml
```

Code style: [CODE_STYLE.md](./CODE_STYLE.md).

---

## 🤝 Contribute

Pull requests are welcome. For large changes, follow `CODE_STYLE.md`.

## 📄 License

GNU Affero General Public License v3.0 — see [LICENSE](./LICENSE).

## 🔗 Related Repositories

- [Backend](https://github.com/fromchat-messenger/backend)
- [Web](https://github.com/fromchat-messenger/web)
- [Website](https://github.com/fromchat-messenger/site)
- [Deployment](https://github.com/fromchat-messenger/deployment)

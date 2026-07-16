Читать на других языках: [English](./README.en.md)

# Наш телеграм канал: [Telegram канал](https://telegram.me/exchatclient) [FromChat канал (скоро)](https://fromchat.ru/)

# FromChat

FromChat — 100% бесплатный и открытый мессенджер. В этом репозитории — кроссплатформенный клиент (Android + iOS; iOS пока не готов).

[📥 Скачать](https://github.com/fromchat-messenger/android/releases/latest) • [💬 Telegram-канал](https://t.me/fromchat_ch) • [🖥️ Сервер](https://github.com/fromchat-messenger/backend)

## ✨ Возможности

- **Голосовые и видеозвонки** — LiveKit
- **Демонстрация экрана** во время звонков
- **Общий чат** — сообщество пользователей сервера
- **Личные сообщения** — легальная схема шифрования; E2EE на сервере планируется
- **Управление устройствами** — активные сеансы
- **Тёмный режим** по умолчанию
- **Открытый исходный код**

## 📊 Сравнение клиентов

⚠️ **iOS временно не поддерживается** (ограничения Apple и объём работы). Клиент выйдет позже.

| Возможность | Android | Web | iOS |
| --- | --- | --- | --- |
| **Обмен сообщениями и профили** | ✅ | ✅ | ❌ |
| **Голосовые/видеозвонки** | ✅ | ✅ | ❌ |
| **Демонстрация экрана** | ✅ | ✅ | ❌ |
| **Реакции на сообщения** | ❌ | ✅ | ❌ |
| **Расширенная поддержка вложений** | ✅ | ❌ | ❌ |

---

## 🏗️ Технологический стек

- **Kotlin**
- **Compose Multiplatform**
- **Material Design 3**
- **Ktor Client**
- **LiveKit** — звонки
- **SQLDelight** — локальное хранилище
- **Firebase Messaging** — push
- **Coil** — изображения

---

## 📥 Сборка и разработка (Android Studio)

### Требования

- Актуальная Android Studio
- JDK из Android Studio (JetBrains Runtime)

### Быстрый старт

1. **Клонируйте репозиторий:**

   ```bash
   git clone https://github.com/fromchat-messenger/android.git
   cd android
   ```

2. **Сгенерируйте ключи (Debug & Release):**

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

3. **Откройте в Android Studio:** `File → Open` → корень репозитория. Gradle подтянет зависимости сам.

4. **Запустите:** `Run → Run 'Android'` (или debug-конфигурацию приложения).  
   Debug application id: `ru.fromchat.beta`.

### Сборка из CLI

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:shared:compileAndroidMain :app:android:assembleDebug
```

APK: `app/android/build/outputs/apk/debug/android-debug.apk`.

### Структура проекта

```
android/
├── app/
│   ├── android/          # Android app module
│   └── shared/           # Compose Multiplatform
│       ├── commonMain/
│       ├── androidMain/
│       └── iosMain/      # ещё не готово
├── utils/                # переиспользуемые утилиты
│   ├── android/
│   └── shared/
└── gradle/libs.versions.toml
```

Стиль кода: [CODE_STYLE.md](./CODE_STYLE.md).

---

## 🤝 Внести вклад

Pull Request приветствуются. Перед крупными изменениями загляните в `CODE_STYLE.md`.

## 📄 Лицензия

GNU Affero General Public License v3.0 — см. [LICENSE](./LICENSE).

## 🔗 Связанные репозитории

- [Backend](https://github.com/fromchat-messenger/backend)
- [Web](https://github.com/fromchat-messenger/web)
- [Website](https://github.com/fromchat-messenger/site)
- [Deployment](https://github.com/fromchat-messenger/deployment)

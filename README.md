Читать на других языках: [English](./README.en.md)

# FromChat

FromChat — 100% бесплатный и открытый мессенджер. В этом репозитории содержится кроссплатформенный клиент для Android и iOS.

[📥 Скачать](https://github.com/fromchat-messenger/app/releases/latest) • [💬 Telegram-канал](https://t.me/fromchat_ch) • [🖥️ Сервер](https://github.com/fromchat-messenger/backend)

## ✨ Возможности

- **Голосовые и видеозвонки** — высокое качество связи через LiveKit
- **Совместное использование экрана** — демонстрация экрана при вызовах
- **Общий чат** — сообщество всех пользователей на сервере
- **Личные сообщения** — используется легальная схема шифрования. Планируется режим E2EE на сервере.
- **Управление устройствами** — контроль активных сеансов
- **Тёмный режим по умолчанию** — стильный и приятный для глаз вид
- **Открытый исходный код** — полная прозрачность

## 📊 Сравнение клиентов

⚠️ **iOS временно не поддерживается.** Разработка iOS-части требует много времени и из-за ограничений Apple это гораздо сложнее, чем на Android. Поэтому она выйдет позже.

В этой таблице показано, какие возможности реализованы в официальных клиентах.


| Возможность                         | Android | Web | iOS |
| ----------------------------------- | ------- | --- | --- |
| **Обмен сообщениями и профили**     | ✅       | ✅   | ✅   |
| **Голосовые/видеозвонки**           | ✅       | ❌   | ❌   |
| **Совместное использование экрана** | ✅       | ❌   | ❌   |
| **Реакции на сообщения**            | ❌       | ✅   | ❌   |
| **Расширенная поддержка вложений**  | ✅       | ❌   | ❌   |


---

## 🏗️ Технологический стек

- **Kotlin** — язык программирования, ориентированный на безопасность и производительность
- **Compose Multiplatform** — объявленный UI фреймворк для кроссплатформенной разработки
- **Material Design 3** — современные компоненты пользовательского интерфейса
- **Ktor Client** — асинхронный HTTP клиент для сетевых запросов
- **LiveKit** — инфраструктура для видеозвонков и конференций
- **SQLDelight** — тип-безопасные SQL запросы для локального хранилища данных
- **Firebase Messaging** — push-уведомления
- **Coil** — загрузка и кэширование изображений


## 📥 Сборка и разработка (Android Studio)

### Требования

- Последняя версия Android Studio
- Компоненты, обновленные до последних версий

### Быстрый старт

1. **Клонируйте репозиторий:**
  ```bash
   git clone https://github.com/fromchat-messenger/app.git
   cd app
  ```
2. **Сгенерируйте ключи (Debug & Release):**
  1. Установите переменные (замените `CHANGEME` на ваши надежные пароли):
    ```bash
    DEBUG_STORE_PASS=CHANGEME
    DEBUG_KEY_PASS=CHANGEME
    RELEASE_STORE_PASS=CHANGEME
    RELEASE_KEY_PASS=CHANGEME
    ```
  2. Выполните команды:
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
3. **Откройте в Android Studio:**
  - Выберите: `File → Open → app/`
  - Android Studio автоматически загружает все зависимости и синхронизирует Gradle
4. **Запустите:**
  - Нажмите `Run → Run 'Android'` (Shift+F10)
  - Выберите эмулятор или подключённое устройство

### Структура проекта

```
android/
├── app/ # Код клиента
│   ├── android/         # Модуль Android-приложения
│   └── shared/          # Общий код Compose (Android + iOS)
│       ├── commonMain/  # Кроссплатформенный код
│       ├── androidMain/ # Android-специфичный код
│       └── iosMain/     # iOS-специфичный код (еще не готово)
├── utils/ # Модуль всяких полезных штук, которые можно использовать в другом проекте
│   ├── android/ # Модуль Android-библиотеки
│   └── shared/           
└── gradle/libs.versions.toml # Управление зависимостями
```

## 🤝 Внести вклад

Если вам хочется поддержать разработку проекта, просто кидайте Pull Request, я уверен, что вы это уже знаете, мне лень это расписывать.

## 📄 Лицензия

Этот проект лицензирован в соответствии с лицензией GNU Affero General Public License v3.0. Подробности см. в файле [LICENSE](./LICENSE).
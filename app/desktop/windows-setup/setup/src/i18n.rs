//! Russian installer copy (primary locale for Windows setup).

pub const WINDOW_TITLE: &str = "Установка FromChat";
pub const WINDOW_TITLE_UNINSTALL: &str = "Удаление FromChat";
pub const WINDOW_TITLE_UPGRADE: &str = "Обновление FromChat";
pub const UPGRADE_ERROR_TITLE: &str = "Ошибка обновления";
pub const TAGLINE: &str = "100% бесплатный и открытый мессенджер.";
pub const INSTALL: &str = "Установка";
pub const INSTALL_DESC: &str =
    "Полная установка с ярлыками в \"Пуске\" и на рабочем столе (рекомендуется).";
pub const DELETE: &str = "Удалить";
pub const DELETE_DESC: &str =
    "Удалить установленную копию FromChat с этого компьютера.";
pub const PRESERVE_USER_DATA: &str = "Сохранить пользовательские данные";
pub const UPGRADE: &str = "Обновить";
pub const UPGRADE_DESC: &str =
    "Обновить существующую копию FromChat на этом компьютере без потери данных.";
pub const UPGRADE_SELECT_TITLE: &str = "Обновление";
pub const UPGRADE_CONFIRM_TITLE: &str = "Подтверждение обновления";
pub const UPGRADE_CONFIRM_BODY: &str =
    "FromChat будет обновлён до последней версии. Пользовательские данные и настройки сохранятся.";
pub const UPGRADE_ACTION: &str = "Обновить";
pub const UPGRADING: &str = "Обновление…";
pub const UPGRADE_REPLACING_FILES: &str = "Замена файлов…";
pub const NEXT: &str = "Далее";
pub const UNINSTALL_TITLE: &str = "Удаление";
pub const UNINSTALL_CONFIRM_BODY: &str =
    "FromChat будет удалён с этого компьютера вместе со всеми файлами программы. Это действие нельзя отменить.";
pub const UNINSTALL_ACTION: &str = "Удалить";
pub const UNINSTALLING: &str = "Удаление…";
pub const REMOVING_FILES: &str = "Удаление файлов…";
pub const UNINSTALL_DONE_TITLE: &str = "FromChat удален";
pub const UNINSTALL_DONE_SUBTITLE: &str = "Нам очень жаль, что вы уходите.";
pub const UNINSTALL_SUPPORT_PREFIX: &str = "Если возникли проблемы, напишите в ";
pub const UNINSTALL_SUPPORT_LABEL: &str = "поддержку";
pub const NOT_INSTALLED: &str = "FromChat не установлен на этом компьютере.";
pub const PORTABLE: &str = "Портативная версия";
pub const PORTABLE_DESC: &str =
    "Для опытных пользователей. Распаковка FromChat без установки, идеально для флешек или внешних SSD.";
pub const INSTALL_LOCATION: &str = "Папка установки";
pub const DESTINATION: &str = "Папка назначения";
pub const BROWSE: &str = "Обзор";
pub const ALL_USERS: &str = "Установить для всех пользователей (администратор)";
pub const START_MENU: &str = "Ярлык в меню «Пуск»";
pub const DESKTOP: &str = "Ярлык на рабочем столе";
pub const INSTALL_ACTION: &str = "Установить";
pub const EXTRACT_ACTION: &str = "Извлечь";
pub const LAUNCH_ACTION: &str = "Запустить";
pub const CLOSE: &str = "Закрыть";
pub const CLOSE_CONFIRM_TITLE: &str = "Закрыть мастер установки?";
pub const CLOSE_CONFIRM_BODY: &str =
    "Установка или удаление ещё не завершены. Вы уверены, что хотите выйти?";
pub const CLOSE_CONFIRM_PROMPT: &str =
    "Установка или удаление ещё не завершены.\n\nНажмите «Да», чтобы закрыть мастер установки, или «Нет», чтобы остаться.";
pub const CLOSE_CONFIRM_STAY: &str = "Остаться";
pub const CLOSE_CONFIRM_EXIT: &str = "Закрыть";
pub const DIALOG_OK: &str = "OK";
pub const INSTALL_ERROR_TITLE: &str = "Ошибка установки";
pub const INSTALL_ERROR_BODY: &str =
    "Произошла ошибка при установке. Продолжить установку невозможно.";
pub const UNINSTALL_ERROR_TITLE: &str = "Ошибка удаления";
pub const UNINSTALL_ERROR_BODY: &str =
    "Произошла ошибка при удалении. Продолжить удаление невозможно.";
pub const INSTALLING: &str = "Установка…";

pub const DONE_TITLE: &str = "Установка завершена";
pub const DONE_OPEN_HINT_DESKTOP: &str =
    "Чтобы открыть FromChat, нажмите на ярлык на рабочем столе или найдите его в меню «Пуск».";
pub const DONE_OPEN_HINT: &str = "Чтобы открыть FromChat, найдите его в меню «Пуск».";
pub const DONE_OPEN_HINT_PORTABLE: &str =
    "Чтобы открыть FromChat, запустите «FromChat Portable.exe» в выбранной папке.";
pub const DONE_TELEGRAM_PREFIX: &str = "Подпишитесь на ";
pub const DONE_TELEGRAM_CHANNEL_LABEL: &str = "@fromchat_ch";
pub const DONE_TELEGRAM_MIDDLE: &str =
    " в Telegram, чтобы получать важные новости о мессенджере. Остались вопросы? Пишите в ";
pub const DONE_TELEGRAM_SUPPORT_LABEL: &str = "поддержку (сообщения канала)";
pub const DONE_TELEGRAM_SUPPORT_URL: &str = "https://t.me/fromchat_ch?direct";
pub const DONE_TELEGRAM_CHANNEL_URL: &str = "https://t.me/fromchat_ch";

pub const PREPARING: &str = "Подготовка…";
pub const READING_PACKAGE: &str = "Чтение пакета…";
pub const EXTRACTING: &str = "Распаковка…";
pub const HIDING_FILES: &str = "Скрытие служебных файлов…";
pub const REQUESTING_ADMIN: &str = "Запрос прав администратора…";
pub const ELEVATION_DENIED: &str =
    "Не удалось получить права администратора. Операция отменена.";
pub const COPYING: &str = "Копирование…";
pub const REGISTERING: &str = "Регистрация…";

pub fn version_label(version: &str) -> String {
    format!("Версия {version}")
}

pub fn installed_copy_title(edition: &str, version: Option<&str>) -> String {
    match version {
        Some(version) => format!("{edition} {version}"),
        None => edition.to_owned(),
    }
}

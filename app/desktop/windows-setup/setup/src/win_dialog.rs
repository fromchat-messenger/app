//! Native Windows message boxes (separate modal window, system chrome).

#[cfg(windows)]
mod imp {
    use windows::core::PCWSTR;
    use windows::Win32::Foundation::HWND;
    use windows::Win32::UI::WindowsAndMessaging::{
        MessageBoxW, MB_DEFBUTTON2, MB_ICONERROR, MB_ICONWARNING, MB_OK, MB_YESNO, IDNO, IDYES,
    };

    fn wide(s: &str) -> Vec<u16> {
        s.encode_utf16().chain(std::iter::once(0)).collect()
    }

    pub fn confirm_close(hwnd: HWND) -> bool {
        let title = wide(crate::i18n::CLOSE_CONFIRM_TITLE);
        let text = wide(crate::i18n::CLOSE_CONFIRM_PROMPT);
        let result = unsafe {
            MessageBoxW(
                hwnd,
                PCWSTR(text.as_ptr()),
                PCWSTR(title.as_ptr()),
                MB_YESNO | MB_ICONWARNING | MB_DEFBUTTON2,
            )
        };
        result == IDYES
    }

    pub fn show_operation_error(hwnd: HWND, title: &str, message: &str) {
        let title = wide(title);
        let text = wide(message);
        unsafe {
            let _ = MessageBoxW(
                hwnd,
                PCWSTR(text.as_ptr()),
                PCWSTR(title.as_ptr()),
                MB_OK | MB_ICONERROR,
            );
        }
    }

    pub fn show_fatal_operation_error(hwnd: HWND, title: &str, message: &str) {
        show_operation_error(hwnd, title, message);
    }

    pub fn show_message(title: &str, text: &str) {
        let title = wide(title);
        let text = wide(text);
        unsafe {
            let _ = MessageBoxW(
                HWND::default(),
                PCWSTR(text.as_ptr()),
                PCWSTR(title.as_ptr()),
                MB_OK | MB_ICONERROR,
            );
        }
    }
}

#[cfg(windows)]
pub use imp::{confirm_close, show_fatal_operation_error, show_message};

#[cfg(not(windows))]
pub fn confirm_close() -> bool {
    true
}

#[cfg(not(windows))]
pub fn show_fatal_operation_error(_title: &str, _message: &str) {}

#[cfg(not(windows))]
pub fn show_message(_title: &str, _text: &str) {}

#[cfg(windows)]
pub fn hwnd_from_frame(frame: &eframe::Frame) -> windows::Win32::Foundation::HWND {
    use raw_window_handle::{HasWindowHandle, RawWindowHandle};
    use windows::Win32::Foundation::HWND;
    if let Ok(handle) = frame.window_handle() {
        if let RawWindowHandle::Win32(win32) = handle.as_raw() {
            return HWND(win32.hwnd.get() as _);
        }
    }
    HWND::default()
}

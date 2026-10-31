//! Native Windows 11 window chrome (rounded corners via DWM).

/// Stable identity for taskbar / jump lists. Must be set before any HWND is created.
#[cfg(windows)]
pub fn set_process_app_user_model_id() {
    use windows::core::w;
    use windows::Win32::UI::Shell::SetCurrentProcessExplicitAppUserModelID;

    let result = unsafe { SetCurrentProcessExplicitAppUserModelID(w!("denis0001-dev.FromChat.Installer")) };
    if let Err(error) = result {
        crate::win_log::warn(&format!(
            "win_chrome: SetCurrentProcessExplicitAppUserModelID failed: {error:?}"
        ));
    } else {
        crate::win_log::info("win_chrome: AppUserModelID set");
    }
}

#[cfg(not(windows))]
pub fn set_process_app_user_model_id() {}

#[cfg(windows)]
pub fn apply(frame: &eframe::Frame, title: &str) {
    apply_rounded_corners(frame);
    set_window_title(frame, title);
}

#[cfg(windows)]
pub fn set_window_title(frame: &eframe::Frame, title: &str) {
    use windows::core::PCWSTR;
    use windows::Win32::UI::WindowsAndMessaging::SetWindowTextW;

    let hwnd = crate::win_dialog::hwnd_from_frame(frame);
    if hwnd.0.is_null() {
        crate::win_log::warn("win_chrome: set_window_title skipped (no hwnd)");
        return;
    }
    let wide: Vec<u16> = title.encode_utf16().chain(std::iter::once(0)).collect();
    let result = unsafe { SetWindowTextW(hwnd, PCWSTR(wide.as_ptr())) };
    if let Err(error) = result {
        crate::win_log::warn(&format!("win_chrome: SetWindowTextW failed: {error:?}"));
    }
}

#[cfg(windows)]
fn apply_rounded_corners(frame: &eframe::Frame) {
    use raw_window_handle::{HasWindowHandle, RawWindowHandle};
    use windows::Win32::Foundation::HWND;
    use windows::Win32::Graphics::Dwm::{
        DwmSetWindowAttribute, DWMWA_WINDOW_CORNER_PREFERENCE, DWM_WINDOW_CORNER_PREFERENCE,
    };

    let Ok(handle) = frame.window_handle() else {
        crate::win_log::warn("win_chrome: no window handle");
        return;
    };
    let RawWindowHandle::Win32(win32) = handle.as_raw() else {
        return;
    };
    let hwnd = HWND(win32.hwnd.get() as _);
    // DWMWCP_ROUND — opt in to Win11 rounded corners for borderless custom frames.
    let preference = DWM_WINDOW_CORNER_PREFERENCE(2);
    let result = unsafe {
        DwmSetWindowAttribute(
            hwnd,
            DWMWA_WINDOW_CORNER_PREFERENCE,
            &preference as *const _ as *const _,
            std::mem::size_of::<DWM_WINDOW_CORNER_PREFERENCE>() as u32,
        )
    };
    if let Err(error) = result {
        crate::win_log::warn(&format!("win_chrome: DwmSetWindowAttribute failed: {error:?}"));
    } else {
        crate::win_log::info("win_chrome: DWMWCP_ROUND applied");
    }
}

#[cfg(not(windows))]
pub fn apply(_frame: &eframe::Frame, _title: &str) {}

#[cfg(not(windows))]
pub fn set_window_title(_frame: &eframe::Frame, _title: &str) {}

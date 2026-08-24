//! Native Windows 11 window chrome (rounded corners via DWM).

#[cfg(windows)]
pub fn apply(frame: &eframe::Frame) {
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
pub fn apply(_frame: &eframe::Frame) {}

//! Console attachment for CLI-driven setup (`--upgrade`, `--uninstall`).

#[cfg(windows)]
pub fn configure() {
    use windows::Win32::System::Console::{AttachConsole, ATTACH_PARENT_PROCESS};

    let cli_mode = std::env::args().any(|arg| {
        matches!(
            arg.as_str(),
            fromchat_installer_common::UPGRADE_ARG
                | fromchat_installer_common::UNINSTALL_ARG
                | fromchat_installer_common::LAUNCH_ARG
        )
    });

    if cli_mode {
        unsafe {
            let _ = AttachConsole(ATTACH_PARENT_PROCESS);
        }
    }
}

#[cfg(not(windows))]
pub fn configure() {}

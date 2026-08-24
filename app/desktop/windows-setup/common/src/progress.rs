use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ProgressEvent {
    Status { message: String },
    Progress { fraction: f32 },
    Done { launch_path: String },
    Uninstalled,
    Error { message: String },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "cmd", rename_all = "snake_case")]
pub enum HelperCommand {
    Install {
        dest: String,
        version: String,
        all_users: bool,
        edition: String,
        start_menu: bool,
        desktop: bool,
        payload_path: String,
        launcher_path: String,
        uninstaller_path: String,
        setup_exe_path: String,
    },
    Uninstall {
        install_dir: String,
        all_users: bool,
        edition: String,
        preserve_setup_exe: Option<String>,
        preserve_user_data: bool,
    },
    Upgrade {
        dest: String,
        version: String,
        all_users: bool,
        edition: String,
        payload_path: String,
        launcher_path: String,
        setup_exe_path: String,
        desktop_shortcut: bool,
    },
    Ping,
}

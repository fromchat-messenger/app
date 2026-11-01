//! Installed beta visible launcher: spawns jpackage `FromChat.exe` with beta data-dir flags.
//! Portable folders use jpackage `FromChat.exe` directly (portable options in `FromChat.cfg`).

#![windows_subsystem = "windows"]

use anyhow::{Context, Result};
use fromchat_installer_common::{find_jpackage_app_exe, VISIBLE_BETA_EXE};
use std::env;
use std::process::{Command, Stdio};

fn main() {
    if let Err(e) = run() {
        eprintln!("FromChat: {e:#}");
        std::process::exit(1);
    }
}

fn run() -> Result<()> {
    let exe = env::current_exe()?;
    let dir = exe
        .parent()
        .map(|p| p.to_path_buf())
        .context("launcher exe has no parent directory")?;

    let app = find_jpackage_app_exe(&dir)?;
    let mut cmd = Command::new(&app);
    cmd.current_dir(
        app.parent()
            .unwrap_or(&dir)
            .to_path_buf(),
    )
    .args(env::args().skip(1))
    .stdin(Stdio::inherit())
    .stdout(Stdio::inherit())
    .stderr(Stdio::inherit());

    if is_installed_beta_launcher(&exe) {
        cmd.env("FROMCHAT_APP_DATA_NAME", "FromChatBeta");
        push_jvm_arg(&mut cmd, "-Dfromchat.app.data.name=FromChatBeta");
    }

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const DETACHED_PROCESS: u32 = 0x00000008;
        const CREATE_BREAKAWAY_FROM_JOB: u32 = 0x01000000;
        cmd.creation_flags(DETACHED_PROCESS | CREATE_BREAKAWAY_FROM_JOB);
    }

    let _child = cmd.spawn().with_context(|| format!("spawn {}", app.display()))?;
    std::process::exit(0);
}

fn push_jvm_arg(cmd: &mut Command, flag: &str) {
    cmd.arg(format!("-J{flag}"));
}

fn is_installed_beta_launcher(exe: &std::path::Path) -> bool {
    exe.file_name()
        .and_then(|n| n.to_str())
        .is_some_and(|n| n.eq_ignore_ascii_case(VISIBLE_BETA_EXE))
}

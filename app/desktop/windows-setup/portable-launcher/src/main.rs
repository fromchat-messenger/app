//! Visible portable / beta entrypoint:
//! - SFX portable: extract once beside itself, then relaunch.
//! - Installed beta: launch hidden jpackage exe with beta data-dir JVM flags.
//! - Never uses `JAVA_TOOL_OPTIONS` (breaks when the install path contains spaces).

#![windows_subsystem = "windows"]

use anyhow::{bail, Context, Result};
use fromchat_installer_common::{
    extract_zstd_tar, read_bundle_from_exe, HIDDEN_APP_EXE, PORTABLE_MAGIC, VISIBLE_BETA_EXE,
    VISIBLE_PORTABLE_EXE,
};
#[cfg(windows)]
use fromchat_installer_common::{hide_all_except, set_hidden};
use std::env;
use std::path::{Path, PathBuf};
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

    let mut portable_extract = false;

    // Standalone portable download: extract into a sibling folder on first run.
    if let Ok(bundle) = read_bundle_from_exe(&exe, PORTABLE_MAGIC) {
        let target = dir.join("FromChat");
        let marker = target.join(".fromchat-extracted");
        if !marker.is_file() {
            std::fs::create_dir_all(&target)?;
            extract_zstd_tar(bundle.payload_zstd()?, &target)?;
            let launcher_dest = target.join(VISIBLE_PORTABLE_EXE);
            if !bundle.launcher.is_empty() {
                std::fs::write(&launcher_dest, &bundle.launcher)?;
            } else {
                std::fs::copy(&exe, &launcher_dest)?;
            }
            #[cfg(windows)]
            {
                hide_all_except(&target, VISIBLE_PORTABLE_EXE)?;
                let data = target.join("fromchat-data");
                std::fs::create_dir_all(&data)?;
                set_hidden(&data, true)?;
            }
            std::fs::write(&marker, b"1\n")?;
            #[cfg(windows)]
            set_hidden(&marker, true)?;
        }
        let launcher = target.join(VISIBLE_PORTABLE_EXE);
        if launcher.is_file() && launcher.canonicalize().ok() != exe.canonicalize().ok() {
            let status = Command::new(&launcher)
                .args(env::args().skip(1))
                .status()?;
            std::process::exit(status.code().unwrap_or(1));
        }
        portable_extract = true;
    }

    let app = find_app_exe(&dir)?;
    let mut cmd = Command::new(&app);
    cmd.current_dir(&dir)
        .args(env::args().skip(1))
        .stdin(Stdio::inherit())
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());

    if portable_extract || is_true_portable_layout(&dir) {
        push_jvm_arg(&mut cmd, "-Dfromchat.portable=true");
        push_jvm_arg(&mut cmd, &format!("-Dfromchat.exe.dir={}", dir.display()));
    } else if is_installed_beta_launcher(&exe) {
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

fn is_installed_beta_launcher(exe: &Path) -> bool {
    exe.file_name()
        .and_then(|n| n.to_str())
        .is_some_and(|n| n.eq_ignore_ascii_case(VISIBLE_BETA_EXE))
}

fn is_true_portable_layout(dir: &Path) -> bool {
    dir.join("fromchat-data").is_dir() && !dir.join("runtime").is_dir()
}

fn find_app_exe(dir: &PathBuf) -> Result<PathBuf> {
    for name in [HIDDEN_APP_EXE, "bin/FromChat.exe", "FromChat.exe"] {
        let p = dir.join(name);
        if p.is_file() {
            return Ok(p);
        }
    }
    for entry in walk(dir)? {
        let file_name = entry.file_name().and_then(|n| n.to_str()).unwrap_or("");
        if file_name.eq_ignore_ascii_case(HIDDEN_APP_EXE)
            || file_name.eq_ignore_ascii_case("FromChat.exe")
        {
            if file_name.eq_ignore_ascii_case(VISIBLE_BETA_EXE)
                || file_name.eq_ignore_ascii_case(VISIBLE_PORTABLE_EXE)
            {
                continue;
            }
            return Ok(entry);
        }
    }
    bail!("{HIDDEN_APP_EXE} not found next to the launcher");
}

fn walk(root: &PathBuf) -> Result<Vec<PathBuf>> {
    let mut out = Vec::new();
    fn rec(dir: &PathBuf, out: &mut Vec<PathBuf>) -> Result<()> {
        for e in std::fs::read_dir(dir)? {
            let e = e?;
            let p = e.path();
            if e.file_type()?.is_dir() {
                rec(&p, out)?;
            } else {
                out.push(p);
            }
        }
        Ok(())
    }
    rec(root, &mut out)?;
    Ok(out)
}

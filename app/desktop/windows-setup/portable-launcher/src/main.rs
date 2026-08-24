//! Visible portable entrypoint / SFX:
//! - If this EXE embeds a portable payload, extract once beside itself then relaunch.
//! - Otherwise launch the hidden FromChat.exe with portable JVM flags.

#![windows_subsystem = "windows"]

use anyhow::{bail, Context, Result};
use fromchat_setup_common::{extract_zstd_tar, read_bundle_from_exe, PORTABLE_MAGIC, VISIBLE_PORTABLE_EXE};
#[cfg(windows)]
use fromchat_setup_common::{hide_all_except, set_hidden};
use std::env;
use std::path::PathBuf;
use std::process::{Command, Stdio};

fn main() {
    if let Err(e) = run() {
        eprintln!("FromChat Portable: {e:#}");
        std::process::exit(1);
    }
}

fn run() -> Result<()> {
    let exe = env::current_exe()?;
    let dir = exe
        .parent()
        .map(|p| p.to_path_buf())
        .context("portable exe has no parent directory")?;

    // Standalone portable download: extract into a sibling folder on first run.
    if let Ok(bundle) = read_bundle_from_exe(&exe, PORTABLE_MAGIC) {
        let target = dir.join("FromChat");
        let marker = target.join(".fromchat-extracted");
        if !marker.is_file() {
            std::fs::create_dir_all(&target)?;
            extract_zstd_tar(&bundle.payload_zstd, &target)?;
            let launcher_dest = target.join(VISIBLE_PORTABLE_EXE);
            // Prefer embedded launcher bytes; fall back to copying ourselves without payload.
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
    }

    let app = find_app_exe(&dir)?;
    let mut cmd = Command::new(&app);
    cmd.current_dir(&dir)
        .args(env::args().skip(1))
        .stdin(Stdio::inherit())
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());
    cmd.env(
        "JAVA_TOOL_OPTIONS",
        format!(
            "-Dfromchat.portable=true -Dfromchat.exe.dir={}",
            dir.display()
        ),
    );
    let status = cmd.status().with_context(|| format!("spawn {}", app.display()))?;
    std::process::exit(status.code().unwrap_or(1));
}

fn find_app_exe(dir: &PathBuf) -> Result<PathBuf> {
    for name in ["FromChat.exe", "bin/FromChat.exe"] {
        let p = dir.join(name);
        if p.is_file() {
            return Ok(p);
        }
    }
    for entry in walk(dir)? {
        if entry
            .file_name()
            .is_some_and(|n| n.eq_ignore_ascii_case("FromChat.exe"))
        {
            return Ok(entry);
        }
    }
    bail!("Hidden FromChat.exe not found next to the portable launcher");
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

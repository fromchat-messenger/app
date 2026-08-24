use anyhow::{bail, Context, Result};
use fromchat_installer_common::patch_jpackage_exe_icon;
use std::env;
use std::path::PathBuf;

fn main() {
    if let Err(error) = run() {
        eprintln!("fromchat-icon-patch: {error:#}");
        std::process::exit(1);
    }
}

fn run() -> Result<()> {
    let exe = PathBuf::from(
        env::args()
            .nth(1)
            .context("usage: fromchat-icon-patch <FromChat.exe>")?,
    );
    if !exe.is_file() {
        bail!("not a file: {}", exe.display());
    }
    patch_jpackage_exe_icon(&exe)?;
    println!("Patched icon: {}", exe.display());
    Ok(())
}

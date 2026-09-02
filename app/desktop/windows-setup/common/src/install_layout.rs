use crate::payload::HIDDEN_APP_EXE;
use crate::FromChatEdition;
use anyhow::{bail, Context, Result};
use std::fs;
use std::path::{Path, PathBuf};

/// User-visible launcher in the install root (has the embedded app icon).
pub const VISIBLE_BETA_EXE: &str = "FromChat Beta.exe";

/// Legacy beta launcher name removed on upgrade.
const LEGACY_VISIBLE_BETA_EXE: &str = "FromChat (Beta).exe";

/// Visible launcher name for an edition, if different from [HIDDEN_APP_EXE].
pub fn visible_launcher_name(edition: FromChatEdition) -> Option<&'static str> {
    match edition {
        FromChatEdition::Release => None,
        FromChatEdition::Beta => Some(VISIBLE_BETA_EXE),
    }
}

/// Returns the path users should run (visible beta launcher or jpackage exe).
#[cfg(windows)]
pub fn finalize_install_launcher(
    dest: &Path,
    edition: FromChatEdition,
    launcher_bytes: &[u8],
) -> Result<PathBuf> {
    let hidden = find_jpackage_app_exe(dest)?;
    if let Some(visible_name) = visible_launcher_name(edition) {
        let visible = dest.join(visible_name);
        fs::write(&visible, launcher_bytes)
            .with_context(|| format!("write visible launcher {}", visible.display()))?;
        let legacy = dest.join(LEGACY_VISIBLE_BETA_EXE);
        if legacy.is_file() {
            let _ = fs::remove_file(&legacy);
        }
        crate::hide_all_except(dest, visible_name)?;
        Ok(visible)
    } else {
        Ok(hidden)
    }
}

#[cfg(not(windows))]
pub fn finalize_install_launcher(
    dest: &Path,
    _edition: FromChatEdition,
    _launcher_bytes: &[u8],
) -> Result<PathBuf> {
    find_jpackage_app_exe(dest)
}

pub fn find_jpackage_app_exe(dest: &Path) -> Result<PathBuf> {
    let candidates = [
        dest.join(HIDDEN_APP_EXE),
        dest.join("bin").join(HIDDEN_APP_EXE),
    ];
    for candidate in candidates {
        if candidate.is_file() {
            return Ok(candidate);
        }
    }
    for entry in walk_files(dest)? {
        let name = entry.file_name().and_then(|n| n.to_str()).unwrap_or("");
        if name.eq_ignore_ascii_case(HIDDEN_APP_EXE) {
            return Ok(entry);
        }
        if name.eq_ignore_ascii_case(VISIBLE_BETA_EXE) {
            continue;
        }
    }
    bail!("{HIDDEN_APP_EXE} not found under {}", dest.display());
}

fn walk_files(root: &Path) -> Result<Vec<PathBuf>> {
    let mut out = Vec::new();
    fn rec(dir: &Path, out: &mut Vec<PathBuf>) -> Result<()> {
        for entry in fs::read_dir(dir)? {
            let entry = entry?;
            let path = entry.path();
            if entry.file_type()?.is_dir() {
                rec(&path, out)?;
            } else {
                out.push(path);
            }
        }
        Ok(())
    }
    rec(root, &mut out)?;
    Ok(out)
}

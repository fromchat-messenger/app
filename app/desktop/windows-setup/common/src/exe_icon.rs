use crate::BRANDING_PNG;
use anyhow::{Context, Result};
use editpe::{Image, ResourceEntryName};
use std::path::Path;

/// `RT_ICON` / `RT_GROUP_ICON` — Windows uses the first numeric group as the Task Manager icon.
const RT_ICON: u32 = 3;
const RT_GROUP_ICON: u32 = 14;

/// Embeds the branded app icon into a jpackage `FromChat.exe` (Task Manager / Alt+Tab).
///
/// jpackage leaves the default Java coffee-cup as group icon ID 1. `set_main_icon` only
/// adds a named `MAINICON` group and keeps that Java icon, so Task Manager still shows it.
/// Strip every icon resource first, then write ours as the only group.
pub fn patch_jpackage_exe_icon(exe_path: &Path) -> Result<()> {
    let mut image = Image::parse_file(exe_path)
        .with_context(|| format!("parse {}", exe_path.display()))?;
    let mut resources = image.resource_directory().cloned().unwrap_or_default();
    resources.root_mut().remove(ResourceEntryName::ID(RT_ICON));
    resources.root_mut().remove(ResourceEntryName::ID(RT_GROUP_ICON));
    let tmp = std::env::temp_dir().join("fromchat-branding.png");
    std::fs::write(&tmp, BRANDING_PNG).context("write temp branding png")?;
    resources
        .set_main_icon_file(
            tmp.to_str()
                .context("temp icon path is not valid UTF-8")?,
        )
        .context("set main icon on jpackage exe")?;
    image
        .set_resource_directory(resources)
        .context("apply icon resources to jpackage exe")?;
    let temp = exe_path.with_extension("icon-patch.tmp.exe");
    if temp.is_file() {
        std::fs::remove_file(&temp).ok();
    }
    image
        .write_file(&temp)
        .with_context(|| format!("write temp {}", temp.display()))?;
    replace_file(&temp, exe_path)
        .with_context(|| format!("replace {}", exe_path.display()))?;
    let _ = std::fs::remove_file(&tmp);
    Ok(())
}

fn replace_file(src: &Path, dest: &Path) -> Result<()> {
    if dest.is_file() {
        let mut permissions = std::fs::metadata(dest)
            .with_context(|| format!("stat {}", dest.display()))?
            .permissions();
        permissions.set_readonly(false);
        std::fs::set_permissions(dest, permissions)
            .with_context(|| format!("clear read-only on {}", dest.display()))?;
        std::fs::remove_file(dest)
            .with_context(|| format!("remove {}", dest.display()))?;
    }
    std::fs::rename(src, dest).with_context(|| format!("rename to {}", dest.display()))?;
    Ok(())
}

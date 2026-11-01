use crate::payload::{
    HIDDEN_APP_EXE, INTERNAL_PORTABLE_APP_EXE, PORTABLE_BETA_DATA_DIR, PORTABLE_DATA_DIR,
    VISIBLE_PORTABLE_EXE,
};
use crate::FromChatEdition;
use anyhow::{bail, Context, Result};
use std::fs;
use std::path::{Path, PathBuf};

/// User-visible launcher in the install root (has the embedded app icon).
pub const VISIBLE_BETA_EXE: &str = "FromChat Beta.exe";

/// Legacy beta launcher name removed on upgrade.
const LEGACY_VISIBLE_BETA_EXE: &str = "FromChat (Beta).exe";

/// Stable per-folder id (survives moving the portable directory); used for Windows AppUserModelID.
pub const PORTABLE_INSTANCE_FILE: &str = ".fromchat-portable-instance";

pub fn portable_user_data_dir_name(registration_id: &str) -> &'static str {
    if registration_id.eq_ignore_ascii_case("FromChat Beta") {
        PORTABLE_BETA_DATA_DIR
    } else {
        PORTABLE_DATA_DIR
    }
}

pub fn read_portable_registration_id(root: &Path) -> Option<String> {
    fs::read_to_string(root.join(".fromchat-portable-id"))
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
}

pub fn is_portable_layout_root(root: &Path) -> bool {
    read_portable_registration_id(root).is_some()
        || root.join(PORTABLE_BETA_DATA_DIR).is_dir()
        || root.join(PORTABLE_DATA_DIR).is_dir()
}

/// After portable extract: one jpackage `FromChat.exe`, portable flags in `FromChat.cfg`.
pub fn finalize_portable_directory(root: &Path, registration_id: &str) -> Result<()> {
    migrate_legacy_portable_layout(root)?;
    write_portable_metadata(root, registration_id)?;
    let instance_id = ensure_portable_instance_id(root)?;
    patch_portable_jpackage_cfg(root, registration_id, &instance_id)
}

fn write_portable_metadata(root: &Path, registration_id: &str) -> Result<()> {
    fs::write(
        root.join(".fromchat-portable-id"),
        registration_id.trim(),
    )
    .with_context(|| format!("write portable id marker under {}", root.display()))?;
    fs::create_dir_all(root.join(portable_user_data_dir_name(registration_id)))?;
    Ok(())
}

pub fn ensure_portable_instance_id(root: &Path) -> Result<String> {
    let path = root.join(PORTABLE_INSTANCE_FILE);
    if path.is_file() {
        if let Some(id) = read_portable_instance_id(root) {
            return Ok(id);
        }
    }
    let id = uuid::Uuid::new_v4().simple().to_string();
    fs::write(&path, format!("{id}\n"))
        .with_context(|| format!("write {}", path.display()))?;
    Ok(id)
}

pub fn read_portable_instance_id(root: &Path) -> Option<String> {
    let raw = fs::read_to_string(root.join(PORTABLE_INSTANCE_FILE)).ok()?;
    let id = raw.trim();
    if id.len() == 32 && id.chars().all(|c| c.is_ascii_hexdigit()) {
        Some(id.to_ascii_lowercase())
    } else {
        None
    }
}

fn migrate_legacy_portable_layout(root: &Path) -> Result<()> {
    let legacy = root.join(VISIBLE_PORTABLE_EXE);
    if legacy.is_file() {
        let _ = fs::remove_file(&legacy);
    }
    let internal = root.join(INTERNAL_PORTABLE_APP_EXE);
    if !internal.is_file() {
        return Ok(());
    }
    let main = root.join(HIDDEN_APP_EXE);
    if main.is_file() {
        let _ = fs::remove_file(&main);
    }
    fs::rename(&internal, &main)
        .with_context(|| format!("restore jpackage {}", main.display()))?;
    Ok(())
}

fn find_fromchat_cfg(root: &Path) -> Result<PathBuf> {
    for entry in walk_files(root)? {
        if entry
            .file_name()
            .and_then(|n| n.to_str())
            .is_some_and(|n| n.eq_ignore_ascii_case("FromChat.cfg"))
        {
            return Ok(entry);
        }
    }
    bail!("FromChat.cfg not found under {}", root.display())
}

pub fn portable_app_user_model_id(registration_id: &str, instance_id: &str) -> String {
    let base = if registration_id.eq_ignore_ascii_case("FromChat Beta") {
        "denis0001-dev.FromChat.Beta.Portable"
    } else {
        "denis0001-dev.FromChat.Portable"
    };
    format!("{base}.{instance_id}")
}

pub fn portable_shell_display_name(registration_id: &str) -> &'static str {
    if registration_id.eq_ignore_ascii_case("FromChat Beta") {
        "FromChat Portable Beta"
    } else {
        "FromChat Portable"
    }
}

fn patch_portable_jpackage_cfg(root: &Path, registration_id: &str, instance_id: &str) -> Result<()> {
    let cfg_path = find_fromchat_cfg(root)?;
    let mut text = fs::read_to_string(&cfg_path)
        .with_context(|| format!("read {}", cfg_path.display()))?;
    let aumid = portable_app_user_model_id(registration_id, instance_id);
    let display_name = portable_shell_display_name(registration_id);
    if text.contains("java-options=-Dfromchat.portable") {
        let mut changed = false;
        if !cfg_contains_java_option(&text, "java.awt.application.name", display_name) {
            text = strip_cfg_java_option(&text, "java.awt.application.name");
            text = format!(
                "{text}\r\njava-options=-Djava.awt.application.name={display_name}\r\n"
            );
            changed = true;
        }
        if !cfg_contains_aumid(&text, &aumid) {
            text = strip_cfg_java_option(&text, "fromchat.windows.app.user.model.id");
            text = format!(
                "{text}\r\njava-options=-Dfromchat.windows.app.user.model.id={aumid}\r\n"
            );
            changed = true;
        }
        if changed {
            fs::write(&cfg_path, text).with_context(|| format!("write {}", cfg_path.display()))?;
        }
        return Ok(());
    }
    // Fix mistaken legacy patches (bare -D lines are ignored by jpackage).
    if text.contains("-Dfromchat.portable") {
        text = text
            .replace("\r\n-Dfromchat.portable=true", "")
            .replace("\n-Dfromchat.portable=true", "")
            .replace("\r\n-Dfromchat.app.data.name=FromChatBeta", "")
            .replace("\n-Dfromchat.app.data.name=FromChatBeta", "");
    }
    let mut opts = vec![
        "java-options=-Dfromchat.portable=true".to_string(),
        "java-options=-Dfromchat.exe.dir=$APPDIR\\..".to_string(),
        format!("java-options=-Djava.awt.application.name={display_name}"),
        format!("java-options=-Dfromchat.windows.app.user.model.id={aumid}"),
    ];
    if registration_id.eq_ignore_ascii_case("FromChat Beta") {
        opts.push("java-options=-Dfromchat.app.data.name=FromChatBeta".to_string());
    }
    let block = opts.join("\r\n");
    text = if text.contains("[JavaOptions]") {
        format!("{text}\r\n{block}\r\n")
    } else {
        format!("{text}\r\n[JavaOptions]\r\n{block}\r\n")
    };
    fs::write(&cfg_path, text).with_context(|| format!("write {}", cfg_path.display()))?;
    Ok(())
}

fn cfg_contains_aumid(text: &str, aumid: &str) -> bool {
    cfg_contains_java_option(text, "fromchat.windows.app.user.model.id", aumid)
}

fn cfg_contains_java_option(text: &str, property_name: &str, value: &str) -> bool {
    let prefix = format!("-D{property_name}=");
    text.lines().any(|line| {
        line.trim()
            .strip_prefix("java-options=")
            .and_then(|opt| opt.strip_prefix(&prefix))
            .is_some_and(|v| v.trim() == value)
    })
}

fn strip_cfg_java_option(text: &str, property_name: &str) -> String {
    let prop = format!("-D{property_name}=");
    text.lines()
        .filter(|line| {
            let t = line.trim();
            !(t.starts_with("java-options") && t.contains(&prop))
        })
        .collect::<Vec<_>>()
        .join(if text.contains("\r\n") { "\r\n" } else { "\n" })
}

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
        crate::patch_jpackage_exe_icon(&visible)
            .with_context(|| format!("patch icon on {}", visible.display()))?;
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
        if name.eq_ignore_ascii_case(VISIBLE_BETA_EXE)
            || name.eq_ignore_ascii_case(VISIBLE_PORTABLE_EXE)
        {
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

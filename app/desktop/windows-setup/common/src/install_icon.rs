use std::io::Cursor;
use std::path::{Path, PathBuf};

/// RGBA8 icon pixels (width × height × 4).
#[derive(Debug, Clone)]
pub struct IconRgba {
    pub width: u32,
    pub height: u32,
    pub pixels: Vec<u8>,
}

/// Parses a Windows `DisplayIcon` registry value (`path` or `path,index`).
pub fn parse_display_icon(raw: &str) -> (PathBuf, i32) {
    let raw = raw.trim().trim_matches('"');
    if let Some((path, index_str)) = raw.rsplit_once(',') {
        if index_str.chars().all(|c| c.is_ascii_digit()) {
            if let Ok(index) = index_str.parse::<i32>() {
                let path = path.trim().trim_matches('"');
                if !path.is_empty() {
                    return (PathBuf::from(path), index);
                }
            }
        }
    }
    (PathBuf::from(raw), 0)
}

/// Loads the install icon referenced by registry, with optional install-dir fallbacks.
pub fn load_install_display_icon(
    display_icon: Option<&str>,
    install_dir: &Path,
) -> Option<IconRgba> {
    if let Some(raw) = display_icon.filter(|s| !s.is_empty()) {
        let (path, _index) = parse_display_icon(raw);
        if let Some(icon) = try_load_icon_path(&path) {
            return Some(icon);
        }
    }
    let ico = install_dir.join("FromChat.ico");
    if ico.is_file() {
        return load_ico_file(&ico);
    }
    None
}

fn try_load_icon_path(path: &Path) -> Option<IconRgba> {
    if !path.is_file() {
        return None;
    }
    let ext = path
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or_default();
    if ext.eq_ignore_ascii_case("ico") {
        return load_ico_file(path);
    }
    if ext.eq_ignore_ascii_case("exe") {
        let ico = path.with_extension("ico");
        if ico.is_file() {
            return load_ico_file(&ico);
        }
    }
    None
}

fn load_ico_file(path: &Path) -> Option<IconRgba> {
    let data = std::fs::read(path).ok()?;
    let dir = ico::IconDir::read(&mut Cursor::new(data)).ok()?;
    let target = 40u32;
    let entry = dir
        .entries()
        .iter()
        .min_by_key(|entry| {
            let w = u32::from(entry.width());
            let h = u32::from(entry.height());
            w.abs_diff(target) + h.abs_diff(target)
        })?;
    let image = entry.decode().ok()?;
    let width = u32::from(image.width());
    let height = u32::from(image.height());
    let rgba = image.rgba_data().to_vec();
    Some(IconRgba {
        width,
        height,
        pixels: rgba,
    })
}

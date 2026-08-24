use anyhow::{Context, Result};
use std::fs;
use std::path::{Path, PathBuf};

pub const BRANDING_PNG: &[u8] = include_bytes!("../../assets/app_window_icon.png");

const ICO_SIZES: [u32; 4] = [16, 32, 48, 256];

pub fn write_install_icon(install_dir: &Path) -> Result<PathBuf> {
    fs::create_dir_all(install_dir)?;
    let ico_path = install_dir.join("FromChat.ico");
    if !ico_path.is_file() {
        let bytes = png_bytes_to_ico(BRANDING_PNG)?;
        fs::write(&ico_path, bytes).with_context(|| format!("write {}", ico_path.display()))?;
    }
    Ok(ico_path)
}

pub fn png_bytes_to_ico(png: &[u8]) -> Result<Vec<u8>> {
    let image = image::load_from_memory(png).context("decode branding png")?;
    let mut icon_dir = ico::IconDir::new(ico::ResourceType::Icon);
    for size in ICO_SIZES {
        let resized = image.resize_exact(size, size, image::imageops::FilterType::Lanczos3);
        let rgba = resized.to_rgba8();
        let entry = ico::IconDirEntry::encode(&ico::IconImage::from_rgba_data(
            size,
            size,
            rgba.into_raw(),
        ))
        .context("encode icon entry")?;
        icon_dir.add_entry(entry);
    }
    let mut out = Vec::new();
    icon_dir.write(&mut out).context("encode ico")?;
    Ok(out)
}

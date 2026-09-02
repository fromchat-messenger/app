fn main() {
    embed_windows_resources("../assets/app_window_icon.png");
    // welcome_brand_bg.png is exported from welcome_brand_bg.html via
    // scripts/export-welcome-brand-bg.mjs (same Chromium CSS as the macOS DMG).
    println!("cargo:rerun-if-changed=../assets/welcome_brand_bg.png");
    println!("cargo:rerun-if-env-changed=FROMCHAT_SETUP_VERSION");
}

fn embed_windows_resources(png_relative: &str) {
    if std::env::var("CARGO_CFG_TARGET_OS").ok().as_deref() != Some("windows") {
        return;
    }
    let manifest_dir = std::path::PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap());
    let png_path = manifest_dir.join(png_relative);
    let out_dir = std::path::PathBuf::from(std::env::var("OUT_DIR").unwrap());
    let ico_path = out_dir.join("app.ico");
    png_to_ico(&png_path, &ico_path);

    let version = std::env::var("FROMCHAT_SETUP_VERSION")
        .unwrap_or_else(|_| env!("CARGO_PKG_VERSION").to_string());
    let version_string = four_part_version_string(&version);

    const DISPLAY_NAME: &str = "Установщик FromChat";

    let mut res = winres::WindowsResource::new();
    res.set_icon(ico_path.to_str().expect("ico path utf-8"));
    // Russian (0x0419) + Unicode code page so Task Manager reads FileDescription.
    res.set_language(0x0419);
    res.set("FileDescription", DISPLAY_NAME);
    res.set("ProductName", DISPLAY_NAME);
    res.set("CompanyName", "denis0001-dev");
    res.set("LegalCopyright", "© FromChat, 2026");
    res.set("InternalName", DISPLAY_NAME);
    res.set("OriginalFilename", "FromChat-Setup.exe");
    res.set("FileVersion", &version_string);
    res.set("ProductVersion", &version_string);
    res.set_version_info(
        winres::VersionInfo::FILEVERSION,
        version_string_to_u64(&version),
    );
    res.set_version_info(
        winres::VersionInfo::PRODUCTVERSION,
        version_string_to_u64(&version),
    );
    if let Err(error) = res.compile() {
        panic!("winres compile failed: {error}");
    }
}

fn four_part_version_string(version: &str) -> String {
    let mut parts: Vec<u16> = version
        .split('.')
        .map(|part| part.parse().unwrap_or(0))
        .collect();
    while parts.len() < 4 {
        parts.push(0);
    }
    parts.truncate(4);
    format!(
        "{}.{}.{}.{}",
        parts[0], parts[1], parts[2], parts[3]
    )
}

fn version_string_to_u64(version: &str) -> u64 {
    let mut parts: Vec<u16> = version
        .split('.')
        .map(|part| part.parse().unwrap_or(0))
        .collect();
    while parts.len() < 4 {
        parts.push(0);
    }
    parts.truncate(4);
    ((parts[0] as u64) << 48)
        | ((parts[1] as u64) << 32)
        | ((parts[2] as u64) << 16)
        | (parts[3] as u64)
}

fn png_to_ico(png_path: &std::path::Path, ico_path: &std::path::Path) {
    let image = image::open(png_path).expect("open icon png");
    let mut icon_dir = ico::IconDir::new(ico::ResourceType::Icon);
    for size in [16u32, 32, 48, 256] {
        let resized = image.resize_exact(size, size, image::imageops::FilterType::Lanczos3);
        let rgba = resized.to_rgba8();
        let entry = ico::IconDirEntry::encode(&ico::IconImage::from_rgba_data(
            size,
            size,
            rgba.into_raw(),
        ))
        .expect("encode icon entry");
        icon_dir.add_entry(entry);
    }
    let file = std::fs::File::create(ico_path).expect("create ico");
    icon_dir.write(file).expect("write ico");
}

fn main() {
    embed_windows_icon("../assets/app_window_icon.png");
}

fn embed_windows_icon(png_relative: &str) {
    if std::env::var("CARGO_CFG_TARGET_OS").ok().as_deref() != Some("windows") {
        return;
    }
    let manifest_dir = std::path::PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap());
    let png_path = manifest_dir.join(png_relative);
    let out_dir = std::path::PathBuf::from(std::env::var("OUT_DIR").unwrap());
    let ico_path = out_dir.join("app.ico");
    png_to_ico(&png_path, &ico_path);
    let mut res = winres::WindowsResource::new();
    res.set_icon(ico_path.to_str().expect("ico path utf-8"));
    res.set("FileDescription", "FromChat Beta");
    res.set("ProductName", "FromChat Beta");
    res.set("OriginalFilename", "FromChat Beta.exe");
    if let Err(error) = res.compile() {
        panic!("winres compile failed: {error}");
    }
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

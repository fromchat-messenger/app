use anyhow::{Context, Result};
use clap::Parser;
use fromchat_installer_common::{
    append_bundle, append_setup_bundle_v2, compress_directory_zstd, BundleMeta, BundleMode,
    SETUP_MAGIC,
};
use std::fs;
use std::path::PathBuf;

#[derive(Parser, Debug)]
struct Args {
    /// Legacy single-arch app image (host CPU only).
    #[arg(long)]
    app_image: Option<PathBuf>,
    #[arg(long)]
    app_image_x64: Option<PathBuf>,
    #[arg(long)]
    app_image_arm64: Option<PathBuf>,
    #[arg(long)]
    version: String,
    /// Uninstall registry id baked into the installer (`FromChat` or `FromChat Beta`).
    #[arg(long, default_value = "FromChat")]
    registration_id: String,
    #[arg(long)]
    setup_out: PathBuf,
    #[arg(long)]
    setup_bin: PathBuf,
    #[arg(long)]
    helper_bin: PathBuf,
    #[arg(long)]
    launcher_bin: PathBuf,
}

fn main() -> Result<()> {
    let args = Args::parse();
    let setup_stub = fs::read(&args.setup_bin)?;
    let helper = fs::read(&args.helper_bin)?;
    let launcher = fs::read(&args.launcher_bin)?;
    let meta = BundleMeta {
        version: args.version,
        mode: BundleMode::Setup,
        registration_id: args.registration_id,
    };

    if let Some(parent) = args.setup_out.parent() {
        fs::create_dir_all(parent)?;
    }

    let payload_x64 = match (&args.app_image_x64, &args.app_image) {
        (Some(dir), _) => Some(compress_directory_zstd(dir)?),
        (None, Some(dir)) if std::env::consts::ARCH == "x86_64" => {
            Some(compress_directory_zstd(dir)?)
        }
        _ => None,
    };
    let payload_arm64 = match (&args.app_image_arm64, &args.app_image) {
        (Some(dir), _) => Some(compress_directory_zstd(dir)?),
        (None, Some(dir)) if std::env::consts::ARCH == "aarch64" => {
            Some(compress_directory_zstd(dir)?)
        }
        _ => None,
    };

    if payload_x64.is_some() || payload_arm64.is_some() {
        append_setup_bundle_v2(
            &setup_stub,
            &meta,
            &helper,
            &launcher,
            payload_x64.as_deref(),
            payload_arm64.as_deref(),
            &args.setup_out,
        )?;
    } else if let Some(app_image) = &args.app_image {
        let payload = compress_directory_zstd(app_image)
            .with_context(|| format!("compress {}", app_image.display()))?;
        append_bundle(
            &setup_stub,
            SETUP_MAGIC,
            &meta,
            &helper,
            &launcher,
            &payload,
            &args.setup_out,
        )?;
    } else {
        anyhow::bail!("provide --app-image, --app-image-x64, and/or --app-image-arm64");
    }

    println!("Wrote {}", args.setup_out.display());
    Ok(())
}

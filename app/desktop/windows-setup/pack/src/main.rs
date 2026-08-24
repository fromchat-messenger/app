use anyhow::{Context, Result};
use clap::Parser;
use fromchat_setup_common::{
    append_bundle, compress_directory_zstd, BundleMeta, BundleMode, SETUP_MAGIC,
};
use std::fs;
use std::path::PathBuf;

#[derive(Parser, Debug)]
struct Args {
    #[arg(long)]
    app_image: PathBuf,
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
    let payload = compress_directory_zstd(&args.app_image)
        .with_context(|| format!("compress {}", args.app_image.display()))?;
    let setup_stub = fs::read(&args.setup_bin)?;
    let helper = fs::read(&args.helper_bin)?;
    let launcher = fs::read(&args.launcher_bin)?;

    if let Some(parent) = args.setup_out.parent() {
        fs::create_dir_all(parent)?;
    }

    append_bundle(
        &setup_stub,
        SETUP_MAGIC,
        &BundleMeta {
            version: args.version,
            mode: BundleMode::Setup,
            registration_id: args.registration_id,
        },
        &helper,
        &launcher,
        &payload,
        &args.setup_out,
    )?;

    println!("Wrote {}", args.setup_out.display());
    Ok(())
}

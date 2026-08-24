use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use std::fs::{self, File};
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};

pub const SETUP_MAGIC: &[u8; 8] = b"FCHSETUP";
pub const PORTABLE_MAGIC: &[u8; 8] = b"FCHPORT1";
pub const VISIBLE_PORTABLE_EXE: &str = "FromChat Portable.exe";
pub const HIDDEN_APP_EXE: &str = "FromChat.exe";
pub const PIPE_NAME: &str = r"\\.\pipe\fromchat-setup-helper";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BundleMeta {
    pub version: String,
    pub mode: BundleMode,
    /// Programs and Features registry id (`FromChat` or `FromChat Beta`).
    #[serde(default = "default_registration_id")]
    pub registration_id: String,
}

fn default_registration_id() -> String {
    "FromChat".to_owned()
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum BundleMode {
    Setup,
    Portable,
}

#[derive(Debug, Clone)]
pub struct EmbeddedBundle {
    pub meta: BundleMeta,
    pub helper: Vec<u8>,
    pub launcher: Vec<u8>,
    pub payload_zstd: Vec<u8>,
}

#[repr(C)]
#[derive(Clone, Copy)]
struct Footer {
    magic: [u8; 8],
    meta_off: u64,
    meta_len: u64,
    helper_off: u64,
    helper_len: u64,
    launcher_off: u64,
    launcher_len: u64,
    payload_off: u64,
    payload_len: u64,
}

impl Footer {
    const SIZE: usize = 8 + 8 * 8;

    fn to_bytes(self) -> [u8; Self::SIZE] {
        let mut out = [0u8; Self::SIZE];
        out[0..8].copy_from_slice(&self.magic);
        let mut i = 8;
        for v in [
            self.meta_off,
            self.meta_len,
            self.helper_off,
            self.helper_len,
            self.launcher_off,
            self.launcher_len,
            self.payload_off,
            self.payload_len,
        ] {
            out[i..i + 8].copy_from_slice(&v.to_le_bytes());
            i += 8;
        }
        out
    }

    fn from_bytes(bytes: &[u8]) -> Result<Self> {
        if bytes.len() < Self::SIZE {
            bail!("footer too short");
        }
        let mut magic = [0u8; 8];
        magic.copy_from_slice(&bytes[0..8]);
        let mut vals = [0u64; 8];
        for (idx, slot) in vals.iter_mut().enumerate() {
            let start = 8 + idx * 8;
            *slot = u64::from_le_bytes(bytes[start..start + 8].try_into().unwrap());
        }
        Ok(Self {
            magic,
            meta_off: vals[0],
            meta_len: vals[1],
            helper_off: vals[2],
            helper_len: vals[3],
            launcher_off: vals[4],
            launcher_len: vals[5],
            payload_off: vals[6],
            payload_len: vals[7],
        })
    }
}

pub fn append_bundle(
    stub: &[u8],
    magic: &[u8; 8],
    meta: &BundleMeta,
    helper: &[u8],
    launcher: &[u8],
    payload_zstd: &[u8],
    out: &Path,
) -> Result<()> {
    let meta_bytes = serde_json::to_vec(meta)?;
    let mut file = File::create(out).with_context(|| format!("create {}", out.display()))?;
    file.write_all(stub)?;
    let base = stub.len() as u64;
    let meta_off = base;
    file.write_all(&meta_bytes)?;
    let helper_off = meta_off + meta_bytes.len() as u64;
    file.write_all(helper)?;
    let launcher_off = helper_off + helper.len() as u64;
    file.write_all(launcher)?;
    let payload_off = launcher_off + launcher.len() as u64;
    file.write_all(payload_zstd)?;
    let footer = Footer {
        magic: *magic,
        meta_off,
        meta_len: meta_bytes.len() as u64,
        helper_off,
        helper_len: helper.len() as u64,
        launcher_off,
        launcher_len: launcher.len() as u64,
        payload_off,
        payload_len: payload_zstd.len() as u64,
    };
    file.write_all(&footer.to_bytes())?;
    Ok(())
}

pub fn read_bundle_from_exe(exe: &Path, expected_magic: &[u8; 8]) -> Result<EmbeddedBundle> {
    let mut file = File::open(exe).with_context(|| format!("open {}", exe.display()))?;
    let len = file.metadata()?.len();
    if len < Footer::SIZE as u64 {
        bail!("executable too small to contain a bundle");
    }
    file.seek(SeekFrom::End(-(Footer::SIZE as i64)))?;
    let mut footer_buf = [0u8; Footer::SIZE];
    file.read_exact(&mut footer_buf)?;
    let footer = Footer::from_bytes(&footer_buf)?;
    if &footer.magic != expected_magic {
        bail!("bundle magic mismatch");
    }
    let mut meta = vec![0u8; footer.meta_len as usize];
    file.seek(SeekFrom::Start(footer.meta_off))?;
    file.read_exact(&mut meta)?;
    let mut helper = vec![0u8; footer.helper_len as usize];
    file.seek(SeekFrom::Start(footer.helper_off))?;
    file.read_exact(&mut helper)?;
    let mut launcher = vec![0u8; footer.launcher_len as usize];
    file.seek(SeekFrom::Start(footer.launcher_off))?;
    file.read_exact(&mut launcher)?;
    let mut payload_zstd = vec![0u8; footer.payload_len as usize];
    file.seek(SeekFrom::Start(footer.payload_off))?;
    file.read_exact(&mut payload_zstd)?;
    Ok(EmbeddedBundle {
        meta: serde_json::from_slice(&meta)?,
        helper,
        launcher,
        payload_zstd,
    })
}

pub fn compress_directory_zstd(dir: &Path) -> Result<Vec<u8>> {
    let mut builder = tar::Builder::new(Vec::new());
    builder.append_dir_all(".", dir)?;
    let tar_bytes = builder.into_inner()?;
    Ok(zstd::encode_all(&tar_bytes[..], 10)?)
}

pub fn extract_zstd_tar(payload_zstd: &[u8], dest: &Path) -> Result<()> {
    fs::create_dir_all(dest)?;
    let tar_bytes = zstd::decode_all(payload_zstd)?;
    let mut archive = tar::Archive::new(&tar_bytes[..]);
    archive.unpack(dest)?;
    Ok(())
}

pub fn current_exe() -> Result<PathBuf> {
    Ok(std::env::current_exe()?)
}

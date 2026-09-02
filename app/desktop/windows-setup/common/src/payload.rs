use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use std::fs::{self, File};
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};

pub const SETUP_MAGIC: &[u8; 8] = b"FCHSETUP";
pub const SETUP_MAGIC_V2: &[u8; 8] = b"FCHSETU2";
pub const PORTABLE_MAGIC: &[u8; 8] = b"FCHPORT1";
pub const VISIBLE_PORTABLE_EXE: &str = "FromChat Portable.exe";
pub const HIDDEN_APP_EXE: &str = "FromChat.exe";
pub const SETUP_INSTALLER_EXE: &str = "FromChat-Installer.exe";
pub const SETUP_HELPER_EXE: &str = "FromChat-Installer-Helper.exe";
pub const PIPE_NAME: &str = r"\\.\pipe\FromChat-Installer-Helper";

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
    payload_x64: Option<Vec<u8>>,
    payload_arm64: Option<Vec<u8>>,
    legacy_payload: Option<Vec<u8>>,
}

impl EmbeddedBundle {
    pub fn payload_zstd(&self) -> Result<&[u8]> {
        if let Some(payload) = host_payload(self) {
            return Ok(payload);
        }
        bail!("installer has no payload for this CPU ({})", std::env::consts::ARCH)
    }
}

fn host_payload(bundle: &EmbeddedBundle) -> Option<&[u8]> {
    match std::env::consts::ARCH {
        "x86_64" => bundle
            .payload_x64
            .as_deref()
            .or(bundle.legacy_payload.as_deref()),
        "aarch64" => bundle
            .payload_arm64
            .as_deref()
            .or(bundle.legacy_payload.as_deref()),
        _ => bundle.legacy_payload.as_deref(),
    }
}

#[repr(C)]
#[derive(Clone, Copy)]
struct FooterV1 {
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

#[repr(C)]
#[derive(Clone, Copy)]
struct FooterV2 {
    magic: [u8; 8],
    meta_off: u64,
    meta_len: u64,
    helper_off: u64,
    helper_len: u64,
    launcher_off: u64,
    launcher_len: u64,
    payload_x64_off: u64,
    payload_x64_len: u64,
    payload_arm64_off: u64,
    payload_arm64_len: u64,
}

impl FooterV1 {
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

impl FooterV2 {
    const SIZE: usize = 8 + 10 * 8;

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
            self.payload_x64_off,
            self.payload_x64_len,
            self.payload_arm64_off,
            self.payload_arm64_len,
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
        let mut vals = [0u64; 10];
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
            payload_x64_off: vals[6],
            payload_x64_len: vals[7],
            payload_arm64_off: vals[8],
            payload_arm64_len: vals[9],
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
    let footer = FooterV1 {
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

pub fn append_setup_bundle_v2(
    stub: &[u8],
    meta: &BundleMeta,
    helper: &[u8],
    launcher: &[u8],
    payload_x64: Option<&[u8]>,
    payload_arm64: Option<&[u8]>,
    out: &Path,
) -> Result<()> {
    if payload_x64.is_none() && payload_arm64.is_none() {
        bail!("at least one app payload is required");
    }
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

    let (payload_x64_off, payload_x64_len) = if let Some(payload) = payload_x64 {
        let off = launcher_off + launcher.len() as u64;
        file.write_all(payload)?;
        (off, payload.len() as u64)
    } else {
        (0, 0)
    };

    let (payload_arm64_off, payload_arm64_len) = if let Some(payload) = payload_arm64 {
        let off = if payload_x64_len > 0 {
            payload_x64_off + payload_x64_len
        } else {
            launcher_off + launcher.len() as u64
        };
        file.write_all(payload)?;
        (off, payload.len() as u64)
    } else {
        (0, 0)
    };

    let footer = FooterV2 {
        magic: *SETUP_MAGIC_V2,
        meta_off,
        meta_len: meta_bytes.len() as u64,
        helper_off,
        helper_len: helper.len() as u64,
        launcher_off,
        launcher_len: launcher.len() as u64,
        payload_x64_off,
        payload_x64_len,
        payload_arm64_off,
        payload_arm64_len,
    };
    file.write_all(&footer.to_bytes())?;
    Ok(())
}

pub fn read_bundle_from_exe(exe: &Path, expected_magic: &[u8; 8]) -> Result<EmbeddedBundle> {
    let mut file = File::open(exe).with_context(|| format!("open {}", exe.display()))?;
    let len = file.metadata()?.len();
    if len < FooterV1::SIZE as u64 {
        bail!("executable too small to contain a bundle");
    }

    file.seek(SeekFrom::End(-(FooterV2::SIZE as i64)))?;
    let mut footer_v2_buf = [0u8; FooterV2::SIZE];
    file.read_exact(&mut footer_v2_buf)?;
    if &footer_v2_buf[0..8] == SETUP_MAGIC_V2 && expected_magic == SETUP_MAGIC {
        let footer = FooterV2::from_bytes(&footer_v2_buf)?;
        return read_bundle_v2(&mut file, footer);
    }

    file.seek(SeekFrom::End(-(FooterV1::SIZE as i64)))?;
    let mut footer_v1_buf = [0u8; FooterV1::SIZE];
    file.read_exact(&mut footer_v1_buf)?;
    let footer = FooterV1::from_bytes(&footer_v1_buf)?;
    if &footer.magic != expected_magic {
        bail!("bundle magic mismatch");
    }
    read_bundle_v1(&mut file, footer)
}

fn read_bundle_v1(file: &mut File, footer: FooterV1) -> Result<EmbeddedBundle> {
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
        payload_x64: None,
        payload_arm64: None,
        legacy_payload: Some(payload_zstd),
    })
}

fn read_bundle_v2(file: &mut File, footer: FooterV2) -> Result<EmbeddedBundle> {
    let mut meta = vec![0u8; footer.meta_len as usize];
    file.seek(SeekFrom::Start(footer.meta_off))?;
    file.read_exact(&mut meta)?;
    let mut helper = vec![0u8; footer.helper_len as usize];
    file.seek(SeekFrom::Start(footer.helper_off))?;
    file.read_exact(&mut helper)?;
    let mut launcher = vec![0u8; footer.launcher_len as usize];
    file.seek(SeekFrom::Start(footer.launcher_off))?;
    file.read_exact(&mut launcher)?;

    let payload_x64 = read_optional_payload(file, footer.payload_x64_off, footer.payload_x64_len)?;
    let payload_arm64 =
        read_optional_payload(file, footer.payload_arm64_off, footer.payload_arm64_len)?;

    Ok(EmbeddedBundle {
        meta: serde_json::from_slice(&meta)?,
        helper,
        launcher,
        payload_x64,
        payload_arm64,
        legacy_payload: None,
    })
}

fn read_optional_payload(file: &mut File, offset: u64, len: u64) -> Result<Option<Vec<u8>>> {
    if len == 0 {
        return Ok(None);
    }
    let mut payload = vec![0u8; len as usize];
    file.seek(SeekFrom::Start(offset))?;
    file.read_exact(&mut payload)?;
    Ok(Some(payload))
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

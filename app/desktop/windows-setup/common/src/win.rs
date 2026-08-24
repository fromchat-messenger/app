use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use std::fs;
use std::os::windows::process::CommandExt;
use std::path::{Path, PathBuf};
use windows::core::{Interface, PCWSTR};
use windows::Win32::Foundation::{CloseHandle, GENERIC_READ, GENERIC_WRITE, HANDLE, INVALID_HANDLE_VALUE};
use windows::Win32::Security::{
    GetTokenInformation, TokenElevation, TOKEN_ELEVATION, TOKEN_QUERY,
};
use windows::Win32::System::Threading::{
    GetCurrentProcess, GetCurrentProcessId, OpenProcess, OpenProcessToken, CREATE_NO_WINDOW,
    PROCESS_QUERY_LIMITED_INFORMATION, PROCESS_TERMINATE, TerminateProcess,
};
use windows::Win32::System::Diagnostics::ToolHelp::{
    CreateToolhelp32Snapshot, Process32FirstW, Process32NextW, PROCESSENTRY32W, TH32CS_SNAPPROCESS,
};
use windows::Win32::System::Threading::QueryFullProcessImageNameW;
use windows::Win32::Storage::FileSystem::{
    CreateFileW, FlushFileBuffers, ReadFile, SetFileAttributesW, WriteFile, FILE_ATTRIBUTE_HIDDEN,
    FILE_ATTRIBUTE_NORMAL, FILE_FLAGS_AND_ATTRIBUTES, FILE_SHARE_READ, FILE_SHARE_WRITE,
    OPEN_EXISTING,
};
use windows::Win32::System::Com::{
    CoCreateInstance, CoInitializeEx, CoTaskMemFree, CoUninitialize, CLSCTX_INPROC_SERVER,
    COINIT_APARTMENTTHREADED, IPersistFile,
};
use windows::Win32::System::Pipes::{
    ConnectNamedPipe, CreateNamedPipeW, DisconnectNamedPipe, PIPE_READMODE_BYTE, PIPE_TYPE_BYTE, PIPE_WAIT,
};
use windows::Win32::System::Registry::{
    RegCloseKey, RegCreateKeyExW, RegDeleteTreeW, RegOpenKeyExW, RegQueryValueExW, RegSetValueExW,
    HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE, KEY_READ, KEY_WRITE, REG_CREATE_KEY_DISPOSITION,
    REG_DWORD, REG_OPTION_NON_VOLATILE, REG_SZ, REG_VALUE_TYPE, HKEY,
};
use windows::Win32::UI::Shell::{
    IShellLinkW, ShellExecuteW, FOLDERID_CommonPrograms, FOLDERID_Desktop, FOLDERID_Programs,
    SHGetKnownFolderPath, KF_FLAG_DEFAULT,
};
use windows::Win32::UI::WindowsAndMessaging::SW_SHOWNORMAL;

/// Copied setup binary in the install folder (Control Panel uninstaller).
pub const UNINSTALL_SETUP_EXE: &str = "Uninstall FromChat.exe";

/// CLI flag: skip welcome and open the uninstall confirmation screen.
pub const UNINSTALL_ARG: &str = "--uninstall";

/// CLI flag: silently upgrade the installed copy for this installer's registration id.
pub const UPGRADE_ARG: &str = "--upgrade";

fn wide(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

/// Release vs beta uninstall registry entries (at most one of each per machine).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum FromChatEdition {
    Release,
    Beta,
}

impl FromChatEdition {
    pub fn registry_key(&self) -> &'static str {
        match self {
            Self::Release => "FromChat",
            Self::Beta => "FromChat Beta",
        }
    }

    pub fn display_name(&self) -> &'static str {
        self.registry_key()
    }

    pub fn shortcut_name(&self) -> &'static str {
        self.display_name()
    }

    pub fn from_registry_key(key: &str) -> Option<Self> {
        if key.eq_ignore_ascii_case("FromChat") {
            Some(Self::Release)
        } else if key.eq_ignore_ascii_case("FromChat Beta") {
            Some(Self::Beta)
        } else {
            None
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InstalledFromChat {
    pub install_dir: PathBuf,
    pub all_users: bool,
    pub version: Option<String>,
    pub edition: FromChatEdition,
    pub display_icon: Option<String>,
}

const KNOWN_REGISTRY_KEYS: &[&str] = &["FromChat", "FromChat Beta"];

pub fn uninstall_setup_path(install_dir: &Path) -> PathBuf {
    install_dir.join(UNINSTALL_SETUP_EXE)
}

pub fn uninstall_command_line(uninstaller: &Path) -> String {
    format!("\"{}\" {}", uninstaller.to_string_lossy(), UNINSTALL_ARG)
}

pub fn copy_uninstall_setup(setup_exe: &Path, install_dir: &Path) -> Result<PathBuf> {
    let dest = uninstall_setup_path(install_dir);
    fs::copy(setup_exe, &dest)
        .with_context(|| format!("copy setup to {}", dest.display()))?;
    Ok(dest)
}

pub fn directory_size_bytes(root: &Path) -> Result<u64> {
    if !root.is_dir() {
        return Ok(0);
    }
    let mut total = 0u64;
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        let path = entry.path();
        if entry.file_type()?.is_dir() {
            total = total.saturating_add(directory_size_bytes(&path)?);
        } else {
            total = total.saturating_add(entry.metadata()?.len());
        }
    }
    Ok(total)
}

pub fn detect_all_installed() -> Vec<InstalledFromChat> {
    let mut out = Vec::new();
    for &registry_key in KNOWN_REGISTRY_KEYS {
        if let Some(installed) = read_install_key(HKEY_LOCAL_MACHINE, true, registry_key) {
            push_unique_install(&mut out, installed);
        }
        if let Some(installed) = read_install_key(HKEY_CURRENT_USER, false, registry_key) {
            push_unique_install(&mut out, installed);
        }
    }
    out.truncate(2);
    out
}

pub fn detect_installed() -> Option<InstalledFromChat> {
    detect_all_installed().into_iter().next()
}

/// Looks up a single install matching the uninstall registry id for this installer build.
pub fn find_installed_by_registration_id(registration_id: &str) -> Option<InstalledFromChat> {
    if FromChatEdition::from_registry_key(registration_id).is_none() {
        return None;
    }
    read_install_key(HKEY_LOCAL_MACHINE, true, registration_id)
        .or_else(|| read_install_key(HKEY_CURRENT_USER, false, registration_id))
}

fn push_unique_install(out: &mut Vec<InstalledFromChat>, installed: InstalledFromChat) {
    if out
        .iter()
        .any(|entry| entry.install_dir == installed.install_dir && entry.edition == installed.edition)
    {
        return;
    }
    out.push(installed);
}

fn read_install_key(root: HKEY, all_users: bool, registry_key: &str) -> Option<InstalledFromChat> {
    let edition = FromChatEdition::from_registry_key(registry_key)?;
    unsafe {
        let sub = wide(&format!(
            r"Software\Microsoft\Windows\CurrentVersion\Uninstall\{registry_key}"
        ));
        let mut key = HKEY::default();
        RegOpenKeyExW(root, PCWSTR(sub.as_ptr()), 0, KEY_READ, &mut key)
            .ok()
            .ok()?;
        let install_dir = read_reg_string(key, "InstallLocation").ok()?;
        let version = read_reg_string(key, "DisplayVersion").ok();
        let display_icon = read_reg_string(key, "DisplayIcon").ok();
        let _ = RegCloseKey(key).ok();
        if install_dir.is_empty() {
            return None;
        }
        let install_dir = PathBuf::from(install_dir);
        if !install_dir.is_dir() {
            return None;
        }
        Some(InstalledFromChat {
            install_dir,
            all_users,
            version,
            edition,
            display_icon,
        })
    }
}

pub fn uninstall_fromchat(
    installed: &InstalledFromChat,
    preserve_exe: Option<&Path>,
    preserve_user_data: bool,
) -> Result<()> {
    init_com()?;
    terminate_processes_in_install_dir(&installed.install_dir)?;
    let shortcut = format!("{}.lnk", installed.edition.shortcut_name());
    let programs = programs_folder(installed.all_users)?;
    let _ = fs::remove_file(programs.join(&shortcut));
    let desk = desktop_folder()?;
    let _ = fs::remove_file(desk.join(&shortcut));
    delete_uninstall_registry(installed.all_users, installed.edition.registry_key())?;
    if installed.install_dir.is_dir() {
        let auto_preserve = std::env::current_exe()
            .ok()
            .filter(|exe| exe.starts_with(&installed.install_dir));
        let effective_preserve = preserve_exe.or(auto_preserve.as_deref());
        remove_install_dir_best_effort(&installed.install_dir, effective_preserve)?;
        if installed.install_dir.exists() {
            schedule_remove_dir_after_exit(&installed.install_dir)?;
        }
    }
    if !preserve_user_data {
        clear_user_data(&installed.install_dir)?;
    }
    Ok(())
}

/// Wipes session, settings, cache, and portable data for this installation.
pub fn clear_user_data(install_dir: &Path) -> Result<()> {
    let portable_data = install_dir.join("fromchat-data");
    if portable_data.is_dir() {
        let _ = fs::remove_dir_all(&portable_data);
    }
    if let Ok(local_app_data) = std::env::var("LOCALAPPDATA") {
        let app_data = PathBuf::from(local_app_data).join("FromChat");
        if app_data.is_dir() {
            let _ = fs::remove_dir_all(&app_data);
        }
    }
    clear_java_prefs_node(r"Software\JavaSoft\Prefs\ru\fromchat\settings")?;
    clear_java_prefs_node(r"Software\JavaSoft\Prefs\ru\fromchat\secure")?;
    Ok(())
}

fn clear_java_prefs_node(subkey: &str) -> Result<()> {
    let sub = wide(subkey);
    unsafe {
        let _ = RegDeleteTreeW(HKEY_CURRENT_USER, PCWSTR(sub.as_ptr()));
    }
    Ok(())
}

pub fn wipe_install_dir(install_dir: &Path, preserve_exe: Option<&Path>) -> Result<()> {
    terminate_processes_in_install_dir(install_dir)?;
    if install_dir.is_dir() {
        remove_install_dir_best_effort(install_dir, preserve_exe)?;
    }
    Ok(())
}

fn remove_install_dir_best_effort(install_dir: &Path, preserve_exe: Option<&Path>) -> Result<()> {
    for entry in fs::read_dir(install_dir)? {
        let entry = entry?;
        let path = entry.path();
        if preserve_exe.is_some_and(|exe| exe == path) {
            continue;
        }
        if entry.file_type()?.is_dir() {
            let _ = fs::remove_dir_all(&path);
        } else {
            let _ = fs::remove_file(&path);
        }
    }
    Ok(())
}

fn init_com() -> Result<()> {
    unsafe {
        CoInitializeEx(None, COINIT_APARTMENTTHREADED).ok()?;
    }
    Ok(())
}

pub fn terminate_processes_in_install_dir(install_dir: &Path) -> Result<()> {
    let install_dir = normalize_path(install_dir);
    let self_pid = unsafe { GetCurrentProcessId() };
    let snapshot = unsafe { CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0)? };
    let mut entry = PROCESSENTRY32W {
        dwSize: std::mem::size_of::<PROCESSENTRY32W>() as u32,
        ..Default::default()
    };
    let mut found = unsafe { Process32FirstW(snapshot, &mut entry).is_ok() };
    while found {
        let pid = entry.th32ProcessID;
        if pid != self_pid {
            if let Ok(image) = process_image_path(pid) {
                let image = normalize_path(&image);
                if image.starts_with(&install_dir) {
                    let _ = terminate_process(pid);
                }
            }
        }
        found = unsafe { Process32NextW(snapshot, &mut entry).is_ok() };
    }
    unsafe {
        let _ = CloseHandle(snapshot);
    }
    std::thread::sleep(std::time::Duration::from_millis(400));
    Ok(())
}

fn process_image_path(pid: u32) -> Result<PathBuf> {
    unsafe {
        let handle = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid)?;
        let mut buf = vec![0u16; 32_768];
        let mut size = buf.len() as u32;
        QueryFullProcessImageNameW(
            handle,
            windows::Win32::System::Threading::PROCESS_NAME_WIN32,
            windows::core::PWSTR(buf.as_mut_ptr()),
            &mut size,
        )?;
        let _ = CloseHandle(handle);
        let path = String::from_utf16_lossy(&buf[..size as usize]);
        Ok(PathBuf::from(path))
    }
}

fn terminate_process(pid: u32) -> Result<()> {
    unsafe {
        let handle = OpenProcess(PROCESS_TERMINATE, false, pid)?;
        TerminateProcess(handle, 1)?;
        let _ = CloseHandle(handle);
    }
    Ok(())
}

fn normalize_path(path: &Path) -> PathBuf {
    std::fs::canonicalize(path).unwrap_or_else(|_| path.to_path_buf())
}

fn schedule_remove_dir_after_exit(install_dir: &Path) -> Result<()> {
    let dir = install_dir.to_string_lossy().replace('\"', "");
    let script = format!("timeout /t 2 /nobreak > nul & rd /s /q \"{dir}\"");
    std::process::Command::new("cmd")
        .args(["/C", &script])
        .creation_flags(CREATE_NO_WINDOW.0)
        .spawn()
        .context("schedule install dir removal")?;
    Ok(())
}

pub fn set_hidden(path: &Path, hidden: bool) -> Result<()> {
    let w = wide(&path.to_string_lossy());
    let attr = if hidden {
        FILE_FLAGS_AND_ATTRIBUTES(FILE_ATTRIBUTE_HIDDEN.0)
    } else {
        FILE_FLAGS_AND_ATTRIBUTES(FILE_ATTRIBUTE_NORMAL.0)
    };
    unsafe {
        SetFileAttributesW(PCWSTR(w.as_ptr()), attr)
            .ok()
            .with_context(|| format!("SetFileAttributes {}", path.display()))?;
    }
    Ok(())
}

pub fn hide_all_except(root: &Path, visible_name: &str) -> Result<()> {
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        let name_str = entry.file_name().to_string_lossy().into_owned();
        if name_str.eq_ignore_ascii_case(visible_name) {
            set_hidden(&entry.path(), false)?;
            continue;
        }
        set_hidden(&entry.path(), true)?;
        if entry.file_type()?.is_dir() {
            hide_tree(&entry.path())?;
        }
    }
    Ok(())
}

fn hide_tree(dir: &Path) -> Result<()> {
    for entry in fs::read_dir(dir)? {
        let entry = entry?;
        set_hidden(&entry.path(), true)?;
        if entry.file_type()?.is_dir() {
            hide_tree(&entry.path())?;
        }
    }
    Ok(())
}

pub fn known_folder(id: windows::core::GUID) -> Result<PathBuf> {
    unsafe {
        let pwstr = SHGetKnownFolderPath(&id, KF_FLAG_DEFAULT, None)?;
        let path = pwstr.to_string()?;
        CoTaskMemFree(Some(pwstr.0 as *const _ as *mut _));
        Ok(PathBuf::from(path))
    }
}

pub fn create_shortcut(
    link_path: &Path,
    target: &Path,
    working_dir: &Path,
    description: &str,
    icon: &Path,
) -> Result<()> {
    unsafe {
        CoInitializeEx(None, COINIT_APARTMENTTHREADED).ok()?;
        // CLSID_ShellLink
        let clsid = windows::core::GUID::from_u128(0x0002_1401_0000_0000_C000_000000000046);
        let link: IShellLinkW = CoCreateInstance(&clsid, None, CLSCTX_INPROC_SERVER)?;
        let target_w = wide(&target.to_string_lossy());
        let work_w = wide(&working_dir.to_string_lossy());
        let desc_w = wide(description);
        let icon_w = wide(&icon.to_string_lossy());
        link.SetPath(PCWSTR(target_w.as_ptr()))?;
        link.SetWorkingDirectory(PCWSTR(work_w.as_ptr()))?;
        link.SetDescription(PCWSTR(desc_w.as_ptr()))?;
        link.SetIconLocation(PCWSTR(icon_w.as_ptr()), 0)?;
        let persist: IPersistFile = link.cast()?;
        let link_w = wide(&link_path.to_string_lossy());
        persist.Save(PCWSTR(link_w.as_ptr()), true)?;
        CoUninitialize();
    }
    Ok(())
}

pub fn write_uninstall_registry(
    all_users: bool,
    edition: FromChatEdition,
    version: &str,
    install_dir: &Path,
    uninstall_exe: &Path,
    display_icon: &Path,
) -> Result<()> {
    let root = if all_users {
        HKEY_LOCAL_MACHINE
    } else {
        HKEY_CURRENT_USER
    };
    let sub = wide(&format!(
        r"Software\Microsoft\Windows\CurrentVersion\Uninstall\{}",
        edition.registry_key()
    ));
    unsafe {
        let mut key = HKEY::default();
        let mut disposition = REG_CREATE_KEY_DISPOSITION::default();
        RegCreateKeyExW(
            root,
            PCWSTR(sub.as_ptr()),
            0,
            None,
            REG_OPTION_NON_VOLATILE,
            KEY_WRITE,
            None,
            &mut key,
            Some(&mut disposition),
        )
        .ok()?;
        set_reg_string(key, "DisplayName", edition.display_name())?;
        set_reg_string(key, "DisplayVersion", version)?;
        set_reg_string(key, "Publisher", "FromChat")?;
        set_reg_string(key, "InstallLocation", &install_dir.to_string_lossy())?;
        set_reg_string(key, "DisplayIcon", &display_icon.to_string_lossy())?;
        set_reg_string(
            key,
            "UninstallString",
            &uninstall_command_line(uninstall_exe),
        )?;
        let size_kb = (directory_size_bytes(install_dir)? / 1024).min(u32::MAX as u64) as u32;
        set_reg_dword(key, "EstimatedSize", size_kb)?;
        RegCloseKey(key).ok()?;
    }
    Ok(())
}

unsafe fn read_reg_string(key: HKEY, name: &str) -> Result<String> {
    let name_w = wide(name);
    let mut kind = REG_VALUE_TYPE::default();
    let mut len = 0u32;
    RegQueryValueExW(key, PCWSTR(name_w.as_ptr()), None, Some(&mut kind), None, Some(&mut len))
        .ok()?;
    if len < 2 {
        return Ok(String::new());
    }
    let mut buf = vec![0u8; len as usize];
    RegQueryValueExW(
        key,
        PCWSTR(name_w.as_ptr()),
        None,
        Some(&mut kind),
        Some(buf.as_mut_ptr()),
        Some(&mut len),
    )
    .ok()?;
    let wide_len = (len as usize / 2).saturating_sub(1);
    let chars: Vec<u16> = buf
        .chunks_exact(2)
        .take(wide_len)
        .map(|chunk| u16::from_le_bytes([chunk[0], chunk[1]]))
        .collect();
    Ok(String::from_utf16_lossy(&chars))
}

unsafe fn set_reg_dword(key: HKEY, name: &str, value: u32) -> Result<()> {
    let name_w = wide(name);
    let bytes = value.to_le_bytes();
    RegSetValueExW(key, PCWSTR(name_w.as_ptr()), 0, REG_DWORD, Some(&bytes)).ok()?;
    Ok(())
}

unsafe fn set_reg_string(key: HKEY, name: &str, value: &str) -> Result<()> {
    let name_w = wide(name);
    let value_w = wide(value);
    let bytes_len = value_w.len() * 2;
    let bytes = std::slice::from_raw_parts(value_w.as_ptr() as *const u8, bytes_len);
    RegSetValueExW(key, PCWSTR(name_w.as_ptr()), 0, REG_SZ, Some(bytes)).ok()?;
    Ok(())
}

pub fn delete_uninstall_registry(all_users: bool, registry_key: &str) -> Result<()> {
    let root = if all_users {
        HKEY_LOCAL_MACHINE
    } else {
        HKEY_CURRENT_USER
    };
    let sub = wide(&format!(
        r"Software\Microsoft\Windows\CurrentVersion\Uninstall\{registry_key}"
    ));
    unsafe {
        let _ = RegDeleteTreeW(root, PCWSTR(sub.as_ptr()));
    }
    Ok(())
}

pub fn is_elevated() -> bool {
    unsafe {
        let mut token = HANDLE::default();
        if OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &mut token).is_err() {
            return false;
        }
        let mut elevation = TOKEN_ELEVATION::default();
        let mut returned = 0u32;
        let ok = GetTokenInformation(
            token,
            TokenElevation,
            Some(&mut elevation as *mut _ as *mut _),
            std::mem::size_of::<TOKEN_ELEVATION>() as u32,
            &mut returned,
        )
        .is_ok();
        let _ = CloseHandle(token);
        ok && elevation.TokenIsElevated != 0
    }
}

pub fn elevate_helper(helper_path: &Path, args: &str) -> Result<()> {
    let file = wide(&helper_path.to_string_lossy());
    let params = wide(args);
    let op = wide("runas");
    unsafe {
        let ret = ShellExecuteW(
            None,
            PCWSTR(op.as_ptr()),
            PCWSTR(file.as_ptr()),
            PCWSTR(params.as_ptr()),
            None,
            SW_SHOWNORMAL,
        );
        if (ret.0 as usize) <= 32 {
            bail!("ShellExecute elevation failed ({})", ret.0 as usize);
        }
    }
    Ok(())
}

pub fn default_install_dir(all_users: bool) -> Result<PathBuf> {
    if all_users {
        let program_files =
            std::env::var("ProgramFiles").unwrap_or_else(|_| r"C:\Program Files".into());
        Ok(PathBuf::from(program_files).join("FromChat"))
    } else {
        let local = std::env::var("LOCALAPPDATA").context("LOCALAPPDATA")?;
        Ok(PathBuf::from(local).join("Programs").join("FromChat"))
    }
}

pub fn programs_folder(all_users: bool) -> Result<PathBuf> {
    if all_users {
        known_folder(FOLDERID_CommonPrograms)
    } else {
        known_folder(FOLDERID_Programs)
    }
}

pub fn desktop_folder() -> Result<PathBuf> {
    known_folder(FOLDERID_Desktop)
}

pub struct NamedPipeServer {
    handle: HANDLE,
}

#[cfg(windows)]
// HANDLE is thread-safe to use from the owning pipe server after `accept`.
unsafe impl Send for NamedPipeServer {}

impl NamedPipeServer {
    pub fn create(name: &str) -> Result<Self> {
        let w = wide(name);
        unsafe {
            let handle = CreateNamedPipeW(
                PCWSTR(w.as_ptr()),
                windows::Win32::Storage::FileSystem::FILE_FLAGS_AND_ATTRIBUTES(0x00000003),
                PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT,
                1,
                64 * 1024,
                64 * 1024,
                0,
                None,
            );
            if handle == INVALID_HANDLE_VALUE {
                bail!("CreateNamedPipe failed");
            }
            Ok(Self { handle })
        }
    }

    pub fn accept(&self) -> Result<()> {
        unsafe {
            ConnectNamedPipe(self.handle, None)
                .ok()
                .context("ConnectNamedPipe")?;
        }
        Ok(())
    }

    pub fn handle(&self) -> HANDLE {
        self.handle
    }
}

impl Drop for NamedPipeServer {
    fn drop(&mut self) {
        unsafe {
            let _ = DisconnectNamedPipe(self.handle);
            let _ = CloseHandle(self.handle);
        }
    }
}

pub fn pipe_read_line(handle: HANDLE) -> Result<String> {
    let mut buf = Vec::new();
    let mut byte = [0u8; 1];
    loop {
        let mut read = 0u32;
        unsafe {
            ReadFile(handle, Some(&mut byte), Some(&mut read), None)
                .ok()
                .context("ReadFile")?;
        }
        if read == 0 {
            break;
        }
        if byte[0] == b'\n' {
            break;
        }
        if byte[0] != b'\r' {
            buf.push(byte[0]);
        }
    }
    Ok(String::from_utf8_lossy(&buf).into_owned())
}

pub fn pipe_write_line(handle: HANDLE, line: &str) -> Result<()> {
    let mut data = line.as_bytes().to_vec();
    data.push(b'\n');
    let mut written = 0u32;
    unsafe {
        WriteFile(handle, Some(&data), Some(&mut written), None)
            .ok()
            .context("WriteFile")?;
        FlushFileBuffers(handle)
            .ok()
            .context("FlushFileBuffers")?;
    }
    Ok(())
}

pub fn open_pipe_client(name: &str) -> Result<HANDLE> {
    let w = wide(name);
    unsafe {
        let handle = CreateFileW(
            PCWSTR(w.as_ptr()),
            GENERIC_READ.0 | GENERIC_WRITE.0,
            FILE_SHARE_READ | FILE_SHARE_WRITE,
            None,
            OPEN_EXISTING,
            FILE_ATTRIBUTE_NORMAL,
            None,
        )?;
        Ok(handle)
    }
}

/// Named pipe client/server handle (re-exported for helper binary).
pub type PipeHandle = HANDLE;

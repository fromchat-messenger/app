#![windows_subsystem = "windows"]

use anyhow::{Context, Result};
use clap::Parser;
use fromchat_setup_common::{
    copy_uninstall_setup, create_shortcut, desktop_folder, extract_zstd_tar, open_pipe_client,
    pipe_write_line, programs_folder, uninstall_fromchat, wipe_install_dir, write_uninstall_registry,
    FromChatEdition, HelperCommand, InstalledFromChat, ProgressEvent,
};
use std::fs;
use std::path::{Path, PathBuf};
use windows::Win32::System::Com::{CoInitializeEx, CoUninitialize, COINIT_APARTMENTTHREADED};

#[derive(Parser)]
struct Args {
    #[arg(long, default_value = r"\\.\pipe\fromchat-setup-helper")]
    pipe: String,
}

fn main() {
    if let Err(e) = run() {
        eprintln!("{e:#}");
        std::process::exit(1);
    }
}

fn run() -> Result<()> {
    unsafe {
        let _ = CoInitializeEx(None, COINIT_APARTMENTTHREADED);
    }
    let result = run_inner();
    unsafe {
        CoUninitialize();
    }
    result
}

fn run_inner() -> Result<()> {
    let args = Args::parse();
    let handle = open_pipe_client(&args.pipe).context("connect to setup UI pipe")?;
    let line = fromchat_setup_common::pipe_read_line(handle)?;
    let cmd: HelperCommand = serde_json::from_str(&line)?;
    if let Err(error) = dispatch(handle, cmd) {
        let _ = pipe_write_line(
            handle,
            &serde_json::to_string(&ProgressEvent::Error {
                message: format!("{error:#}"),
            })?,
        );
    }
    Ok(())
}

fn dispatch(handle: fromchat_setup_common::PipeHandle, cmd: HelperCommand) -> Result<()> {
    match cmd {
        HelperCommand::Ping => {
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Status {
                    message: "pong".into(),
                })?,
            )?;
        }
        HelperCommand::Install {
            dest,
            version,
            all_users,
            edition,
            start_menu,
            desktop,
            payload_path,
            uninstaller_path,
            setup_exe_path,
        } => {
            let dest = PathBuf::from(dest);
            let uninstaller_path = PathBuf::from(uninstaller_path);
            let setup_exe_path = PathBuf::from(setup_exe_path);
            let edition = parse_edition(&edition)?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Status {
                    message: "Copying runtime…".into(),
                })?,
            )?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Progress { fraction: 0.2 })?,
            )?;
            let payload = fs::read(&payload_path)?;
            let _ = fs::remove_dir_all(&dest);
            fs::create_dir_all(&dest)?;
            extract_zstd_tar(&payload, &dest)?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Progress { fraction: 0.7 })?,
            )?;
            let app_exe = find_app_exe(&dest)?;
            let icon = fromchat_setup_common::write_install_icon(&dest)?;
            copy_uninstall_setup(&setup_exe_path, &dest)?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Status {
                    message: "Registering…".into(),
                })?,
            )?;
            write_uninstall_registry(
                all_users,
                edition,
                &version,
                &dest,
                &uninstaller_path,
                &icon,
            )?;
            let shortcut = format!("{}.lnk", edition.shortcut_name());
            if start_menu {
                let link = programs_folder(all_users)?.join(&shortcut);
                create_shortcut(
                    &link,
                    &app_exe,
                    &dest,
                    edition.display_name(),
                    &icon,
                )?;
            }
            if desktop {
                let link = desktop_folder()?.join(&shortcut);
                create_shortcut(
                    &link,
                    &app_exe,
                    &dest,
                    edition.display_name(),
                    &icon,
                )?;
            }
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Progress { fraction: 1.0 })?,
            )?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Done {
                    launch_path: app_exe.to_string_lossy().into_owned(),
                })?,
            )?;
        }
        HelperCommand::Uninstall {
            install_dir,
            all_users,
            edition,
            preserve_setup_exe,
            preserve_user_data,
        } => {
            let installed = InstalledFromChat {
                install_dir: PathBuf::from(install_dir),
                all_users,
                version: None,
                edition: parse_edition(&edition)?,
                display_icon: None,
            };
            let preserve = preserve_setup_exe.as_deref().map(Path::new);
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Status {
                    message: "Удаление файлов…".into(),
                })?,
            )?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Progress { fraction: 0.5 })?,
            )?;
            uninstall_fromchat(&installed, preserve, preserve_user_data)?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Progress { fraction: 1.0 })?,
            )?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Uninstalled)?,
            )?;
        }
        HelperCommand::Upgrade {
            dest,
            version,
            all_users,
            edition,
            payload_path,
            setup_exe_path,
            desktop_shortcut,
        } => {
            let dest = PathBuf::from(dest);
            let setup_exe_path = PathBuf::from(setup_exe_path);
            let edition = parse_edition(&edition)?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Status {
                    message: "Удаление старых файлов…".into(),
                })?,
            )?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Progress { fraction: 0.15 })?,
            )?;
            wipe_install_dir(&dest, None)?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Status {
                    message: "Распаковка…".into(),
                })?,
            )?;
            let payload = fs::read(&payload_path)?;
            fs::create_dir_all(&dest)?;
            extract_zstd_tar(&payload, &dest)?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Progress { fraction: 0.7 })?,
            )?;
            let app_exe = find_app_exe(&dest)?;
            let icon = fromchat_setup_common::write_install_icon(&dest)?;
            let uninstaller = copy_uninstall_setup(&setup_exe_path, &dest)?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Status {
                    message: "Регистрация…".into(),
                })?,
            )?;
            write_uninstall_registry(
                all_users,
                edition,
                &version,
                &dest,
                &uninstaller,
                &icon,
            )?;
            let shortcut = format!("{}.lnk", edition.shortcut_name());
            let link = programs_folder(all_users)?.join(&shortcut);
            create_shortcut(
                &link,
                &app_exe,
                &dest,
                edition.display_name(),
                &icon,
            )?;
            if desktop_shortcut {
                let link = desktop_folder()?.join(&shortcut);
                create_shortcut(&link, &app_exe, &dest, edition.display_name(), &icon)?;
            }
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Progress { fraction: 1.0 })?,
            )?;
            pipe_write_line(
                handle,
                &serde_json::to_string(&ProgressEvent::Done {
                    launch_path: app_exe.to_string_lossy().into_owned(),
                })?,
            )?;
        }
    }
    Ok(())
}

fn parse_edition(raw: &str) -> Result<FromChatEdition> {
    match raw {
        "release" => Ok(FromChatEdition::Release),
        "beta" => Ok(FromChatEdition::Beta),
        _ => anyhow::bail!("unknown edition {raw}"),
    }
}

fn find_app_exe(dest: &PathBuf) -> Result<PathBuf> {
    for c in [
        dest.join("FromChat.exe"),
        dest.join("bin").join("FromChat.exe"),
    ] {
        if c.is_file() {
            return Ok(c);
        }
    }
    for entry in walk(dest)? {
        if entry
            .file_name()
            .is_some_and(|n| n.eq_ignore_ascii_case("FromChat.exe"))
        {
            return Ok(entry);
        }
    }
    anyhow::bail!("FromChat.exe not found");
}

fn walk(root: &PathBuf) -> Result<Vec<PathBuf>> {
    let mut out = Vec::new();
    fn rec(dir: &PathBuf, out: &mut Vec<PathBuf>) -> Result<()> {
        for e in fs::read_dir(dir)? {
            let e = e?;
            let p = e.path();
            if e.file_type()?.is_dir() {
                rec(&p, out)?;
            } else {
                out.push(p);
            }
        }
        Ok(())
    }
    rec(root, &mut out)?;
    Ok(out)
}

//! Startup / panic logging to a local file, debugger output, and the Windows Application event log.

use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::PathBuf;
use std::sync::Mutex;

const EVENT_SOURCE: &str = "FromChat Setup";

static LOG_FILE: Mutex<Option<PathBuf>> = Mutex::new(None);

pub fn init() {
    if let Ok(mut guard) = LOG_FILE.lock() {
        if guard.is_some() {
            return;
        }
        let path = log_file_path();
        if let Some(parent) = path.parent() {
            let _ = fs::create_dir_all(parent);
        }
        *guard = Some(path);
    }

    std::panic::set_hook(Box::new(|info| {
        let payload = info
            .payload()
            .downcast_ref::<&str>()
            .map(|s| (*s).to_string())
            .or_else(|| {
                info.payload()
                    .downcast_ref::<String>()
                    .map(|s| s.clone())
            })
            .unwrap_or_else(|| "unknown panic payload".to_owned());
        let location = info
            .location()
            .map(|l| format!("{}:{}:{}", l.file(), l.line(), l.column()))
            .unwrap_or_else(|| "unknown location".to_owned());
        error(&format!("PANIC at {location}: {payload}"));
    }));

    info("FromChat Setup starting");
    info(&format!("log file: {}", log_file_path().display()));
    if let Ok(exe) = std::env::current_exe() {
        info(&format!("exe: {}", exe.display()));
    }
    info(&format!("args: {:?}", std::env::args().collect::<Vec<_>>()));
}

pub fn info(message: &str) {
    write("INFO", message);
}

pub fn warn(message: &str) {
    write("WARN", message);
}

pub fn error(message: &str) {
    write("ERROR", message);
}

fn write(level: &str, message: &str) {
    let line = format!(
        "{} [{level}] {message}\n",
        chrono_lite_timestamp()
    );

    if let Ok(guard) = LOG_FILE.lock() {
        if let Some(path) = guard.as_ref() {
            if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(path) {
                let _ = file.write_all(line.as_bytes());
                let _ = file.flush();
            }
        }
    }

    #[cfg(windows)]
    report_event(level, message);

    #[cfg(not(windows))]
    {
        let _ = (level, message);
    }
}

fn log_file_path() -> PathBuf {
    std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("."))
        .join("FromChat")
        .join("logs")
        .join("setup.log")
}

/// Tiny timestamp without pulling in chrono.
fn chrono_lite_timestamp() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    format!("unix={secs}")
}

#[cfg(windows)]
fn report_event(level: &str, message: &str) {
    use std::ffi::OsStr;
    use std::os::windows::ffi::OsStrExt;

    use windows::core::PCWSTR;
    use windows::Win32::System::Diagnostics::Debug::OutputDebugStringW;
    use windows::Win32::System::EventLog::{
        DeregisterEventSource, RegisterEventSourceW, ReportEventW, EVENTLOG_ERROR_TYPE,
        EVENTLOG_INFORMATION_TYPE, EVENTLOG_WARNING_TYPE,
    };

    let debug_line: Vec<u16> = OsStr::new(&format!("[{EVENT_SOURCE}] [{level}] {message}"))
        .encode_wide()
        .chain([0])
        .collect();
    unsafe {
        OutputDebugStringW(PCWSTR(debug_line.as_ptr()));
    }

    let event_type = match level {
        "ERROR" => EVENTLOG_ERROR_TYPE,
        "WARN" => EVENTLOG_WARNING_TYPE,
        _ => EVENTLOG_INFORMATION_TYPE,
    };

    let source: Vec<u16> = EVENT_SOURCE.encode_utf16().chain([0]).collect();
    let body: Vec<u16> = message.encode_utf16().chain([0]).collect();

    unsafe {
        let Ok(handle) = RegisterEventSourceW(None, PCWSTR(source.as_ptr())) else {
            return;
        };
        if handle.is_invalid() {
            return;
        }
        let strings = [PCWSTR(body.as_ptr())];
        let _ = ReportEventW(
            handle,
            event_type,
            0,
            0,
            None,
            0,
            Some(&strings),
            None,
        );
        let _ = DeregisterEventSource(handle);
    }
}

pub fn log_file_hint() -> String {
    LOG_FILE
        .lock()
        .ok()
        .and_then(|g| g.clone())
        .map(|p| p.display().to_string())
        .unwrap_or_else(|| log_file_path().display().to_string())
}

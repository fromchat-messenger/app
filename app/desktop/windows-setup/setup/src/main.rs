//! FromChat Windows setup UI (egui) — animated, no WebView.

#![windows_subsystem = "windows"]

mod anim;
mod args;
mod cookie4_sided_data;
mod cookie6_sided_data;
mod fonts;
mod icons;
mod i18n;
mod theme;
mod ui;
mod widgets;
mod win_chrome;
mod win_console;
mod win_dialog;
mod win_log;

use anyhow::Result;
use eframe::egui;
use fromchat_setup_common::{read_bundle_from_exe, BRANDING_PNG, SETUP_MAGIC};

fn main() {
    #[cfg(windows)]
    win_console::configure();

    win_log::init();

    if let Err(error) = run() {
        win_log::error(&format!("setup exited with error: {error:#}"));
        std::process::exit(1);
    }
    win_log::info("setup exited normally");
}

fn run() -> Result<()> {
    win_log::info("building NativeOptions");

    let registration_id = read_bundle_from_exe(
        &std::env::current_exe().unwrap_or_default(),
        SETUP_MAGIC,
    )
    .map(|bundle| bundle.meta.registration_id)
    .unwrap_or_else(|error| {
        win_log::warn(&format!("bundle registration id read failed: {error:#}"));
        "FromChat".to_owned()
    });

    let options = match args::parse_setup_args(&registration_id) {
        Ok(options) => options,
        Err(error) => {
            #[cfg(windows)]
            {
                crate::win_dialog::show_message(error.title(), error.body());
            }
            return Err(anyhow::anyhow!("{}", error.body()));
        }
    };

    let window_title = args::window_title_for_mode(options.mode).to_owned();

    let icon = match load_window_icon() {
        Ok(icon) => {
            win_log::info("window icon loaded");
            Some(icon)
        }
        Err(error) => {
            win_log::warn(&format!("window icon failed: {error:#}; continuing without icon"));
            None
        }
    };

    let mut viewport = egui::ViewportBuilder::default()
        .with_inner_size([theme::WINDOW_WIDTH, theme::WINDOW_HEIGHT])
        .with_resizable(false)
        .with_decorations(false)
        .with_transparent(false)
        .with_title(&window_title);
    if let Some(icon) = icon {
        viewport = viewport.with_icon(icon);
    }

    let base_options = eframe::NativeOptions {
        viewport,
        multisampling: 0,
        ..Default::default()
    };

    #[cfg(windows)]
    let renderers = [eframe::Renderer::Glow];
    #[cfg(not(windows))]
    let renderers = [eframe::Renderer::Glow];

    let mut last_error = None;
    for renderer in renderers {
        let mut options_native = base_options.clone();
        options_native.renderer = renderer;

        #[cfg(windows)]
        if renderer == eframe::Renderer::Wgpu && std::env::var_os("WGPU_BACKEND").is_none() {
            std::env::set_var("WGPU_BACKEND", "dx12");
            win_log::info("wgpu fallback: WGPU_BACKEND=dx12");
        }

        win_log::info(&format!("calling eframe::run_native (renderer={renderer:?})"));

        let setup_options = options.clone();
        let result = eframe::run_native(
            &window_title,
            options_native,
            Box::new(move |cc| {
                win_log::info("eframe creation callback");
                let app = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                    ui::SetupApp::new(cc, setup_options)
                }))
                .map_err(|_| {
                    win_log::error("panic while constructing SetupApp");
                    Box::from("SetupApp::new panicked") as Box<dyn std::error::Error + Send + Sync>
                })?;
                win_log::info("SetupApp constructed");
                Ok(Box::new(app))
            }),
        );

        match result {
            Ok(()) => {
                win_log::info(&format!("eframe finished OK (renderer={renderer:?})"));
                return Ok(());
            }
            Err(error) => {
                win_log::error(&format!("eframe failed (renderer={renderer:?}): {error}"));
                last_error = Some(error);
            }
        }
    }

    Err(match last_error {
        Some(error) => {
            win_log::error("all renderers failed");
            anyhow::anyhow!("eframe: {error}")
        }
        None => anyhow::anyhow!("eframe: no renderer attempted"),
    })
}

fn load_window_icon() -> anyhow::Result<egui::IconData> {
    let image = image::load_from_memory(BRANDING_PNG)?;
    let rgba = image.to_rgba8();
    let width = rgba.width();
    let height = rgba.height();
    Ok(egui::IconData {
        rgba: rgba.into_raw(),
        width,
        height,
    })
}

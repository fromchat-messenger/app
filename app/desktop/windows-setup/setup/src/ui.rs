use anyhow::Context;
use crate::anim::{Lerped, ScreenTransition};
use crate::args::{SetupMode, SetupOptions};
use crate::fonts::{self, body, body_medium, wordmark_brand, title_large};
use crate::i18n;
use crate::icons::{self, CLOSE, DELETE, DOWNLOAD, FOLDER, DONE_ALL, MINIMIZE, OPEN_IN_NEW, RSS_FEED, SUPPORT, SYSTEM_UPGRADE_ALT};
use crate::theme::{self, ON_SURFACE, ON_SURFACE_VARIANT, ON_ERROR_FILLED, PRIMARY, SURFACE, TITLE_BAR_HEIGHT, TITLE_BAR_INACTIVE};
use crate::widgets::{self, H_PADDING};
use eframe::egui::{self, Color32, Pos2, Rect, Rounding, Sense, TextureHandle, Ui, Vec2};
use fromchat_installer_common::{
    copy_uninstall_setup, extract_zstd_tar, find_installed_by_registration_id,
    load_install_display_icon, read_bundle_from_exe, uninstall_fromchat, wipe_install_dir,
    write_install_icon, finalize_install_launcher, find_jpackage_app_exe, FromChatEdition,
    patch_jpackage_exe_icon,
    HelperCommand, InstalledFromChat, ProgressEvent,
    BRANDING_PNG, SETUP_HELPER_EXE, SETUP_INSTALLER_EXE, SETUP_MAGIC, UNINSTALL_SETUP_EXE, VISIBLE_PORTABLE_EXE,
};
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::mpsc::{self, Receiver, Sender};
use std::thread;

#[derive(Clone, Copy, PartialEq, Eq)]
enum Screen {
    Welcome = 0,
    InstallOptions = 1,
    PortableOptions = 2,
    UninstallConfirm = 3,
    Progress = 4,
    Done = 5,
    UninstallDone = 6,
    UpgradeConfirm = 7,
}

#[cfg(windows)]
struct ElevatedInstallSession {
    pipe: fromchat_installer_common::NamedPipeServer,
    cmd_json: String,
}

pub struct SetupApp {
    mode: SetupMode,
    screen: Screen,
    transition: Option<ScreenTransition<Screen>>,
    time: f32,
    logo: TextureHandle,
    brand_bg: TextureHandle,
    install_icon_textures: HashMap<String, TextureHandle>,
    install_path: String,
    portable_path: String,
    all_users: bool,
    desktop_shortcut: bool,
    was_portable: bool,
    progress: Lerped,
    status: String,
    status_alpha: f32,
    status_pending: Option<String>,
    launch_path: Option<PathBuf>,
    error: Option<String>,
    progress_rx: Option<Receiver<ProgressEvent>>,
    #[cfg(windows)]
    elevation_rx: Option<Receiver<Option<ElevatedInstallSession>>>,
    #[cfg(windows)]
    installed: Option<InstalledFromChat>,
    preserve_user_data: bool,
    silent_upgrade: bool,
    launch_after: bool,
    pending_auto_upgrade: bool,
    is_uninstalling: bool,
    is_upgrading: bool,
    version: String,
    edition: FromChatEdition,
    synced_native_window_title: Option<&'static str>,
    logged_first_frame: bool,
    applied_window_chrome: bool,
}

impl SetupApp {
    pub fn new(cc: &eframe::CreationContext<'_>, options: SetupOptions) -> Self {
        crate::win_log::info("SetupApp::new: style");
        let mut style = (*cc.egui_ctx.style()).clone();
        style.visuals.dark_mode = true;
        style.visuals.panel_fill = theme::SURFACE;
        style.visuals.window_fill = theme::SURFACE;
        style.visuals.override_text_color = Some(ON_SURFACE);
        cc.egui_ctx.set_style(style);

        crate::win_log::info("SetupApp::new: fonts");
        fonts::install(&cc.egui_ctx);
        crate::win_log::info("SetupApp::new: logo texture");
        let logo = load_logo_texture(&cc.egui_ctx);
        crate::win_log::info("SetupApp::new: brand bg texture");
        let brand_bg = load_brand_bg_texture(&cc.egui_ctx);

        crate::win_log::info("SetupApp::new: read bundle version");
        let bundle = read_bundle_from_exe(
            &std::env::current_exe().unwrap_or_default(),
            SETUP_MAGIC,
        );
        let version = bundle
            .as_ref()
            .map(|b| b.meta.version.clone())
            .unwrap_or_else(|error| {
                crate::win_log::warn(&format!("bundle version read failed: {error:#}"));
                env!("CARGO_PKG_VERSION").to_string()
            });
        let registration_id = bundle
            .as_ref()
            .map(|b| b.meta.registration_id.clone())
            .unwrap_or_else(|_| "FromChat".to_owned());
        let edition = FromChatEdition::from_registry_key(&registration_id)
            .unwrap_or(FromChatEdition::Release);

        let install_path = default_install_path(false, edition);
        let portable_path = std::env::current_dir()
            .map(|p| p.join("FromChat").to_string_lossy().into_owned())
            .unwrap_or_else(|_| r".\FromChat".into());

        #[cfg(windows)]
        let installed = find_installed_by_registration_id(&registration_id);
        #[cfg(not(windows))]
        let installed = None;

        let needs_installed = matches!(options.mode, SetupMode::Uninstall);
        let error = if needs_installed && installed.is_none() {
            Some(i18n::NOT_INSTALLED.into())
        } else {
            None
        };

        let silent_upgrade = matches!(options.mode, SetupMode::Upgrade);
        let launch_after = options.launch_after;
        let pending_auto_upgrade = silent_upgrade;

        let screen = match options.mode {
            SetupMode::Uninstall => Screen::UninstallConfirm,
            SetupMode::Upgrade => Screen::Progress,
            SetupMode::Install => Screen::Welcome,
        };

        Self {
            mode: options.mode,
            screen,
            transition: None,
            time: 0.0,
            logo,
            brand_bg,
            install_icon_textures: HashMap::new(),
            install_path,
            portable_path,
            all_users: false,
            desktop_shortcut: true,
            was_portable: false,
            progress: Lerped::new(0.0),
            status: String::new(),
            status_alpha: 1.0,
            status_pending: None,
            launch_path: None,
            error,
            progress_rx: None,
            #[cfg(windows)]
            elevation_rx: None,
            #[cfg(windows)]
            installed,
            preserve_user_data: true,
            silent_upgrade,
            launch_after,
            pending_auto_upgrade,
            is_uninstalling: matches!(options.mode, SetupMode::Uninstall),
            is_upgrading: silent_upgrade,
            version,
            edition,
            synced_native_window_title: None,
            logged_first_frame: false,
            applied_window_chrome: false,
        }
    }

    fn effective_window_title(&self) -> &'static str {
        crate::args::window_title_for_mode(self.mode)
    }

    fn sync_native_window_title(&mut self, ctx: &egui::Context, frame: &eframe::Frame) {
        if self.synced_native_window_title.is_some() {
            return;
        }
        self.synced_native_window_title = Some(i18n::INSTALLER_DISPLAY_NAME);
        ctx.send_viewport_cmd(egui::ViewportCommand::Title(
            i18n::INSTALLER_DISPLAY_NAME.into(),
        ));
        #[cfg(windows)]
        crate::win_chrome::set_window_title(frame, i18n::INSTALLER_DISPLAY_NAME);
    }

    fn should_confirm_close(&self) -> bool {
        !self.silent_upgrade && (self.screen == Screen::Progress || self.elevation_pending())
    }

    fn request_close(&mut self, ctx: &egui::Context, frame: &eframe::Frame) {
        if self.should_confirm_close() {
            #[cfg(windows)]
            {
                let hwnd = crate::win_dialog::hwnd_from_frame(frame);
                if crate::win_dialog::confirm_close(hwnd) {
                    ctx.send_viewport_cmd(egui::ViewportCommand::Close);
                }
                return;
            }
        }
        ctx.send_viewport_cmd(egui::ViewportCommand::Close);
    }

    fn operation_error_title(&self) -> &'static str {
        if self.is_uninstalling {
            i18n::UNINSTALL_ERROR_TITLE
        } else if self.is_upgrading || self.silent_upgrade {
            i18n::UPGRADE_ERROR_TITLE
        } else {
            i18n::INSTALL_ERROR_TITLE
        }
    }

    fn handle_fatal_operation_error(
        &self,
        ctx: &egui::Context,
        frame: &eframe::Frame,
        message: &str,
    ) {
        #[cfg(windows)]
        {
            let hwnd = crate::win_dialog::hwnd_from_frame(frame);
            crate::win_dialog::show_fatal_operation_error(
                hwnd,
                self.operation_error_title(),
                message,
            );
        }
        #[cfg(not(windows))]
        {
            let _ = (ctx, frame, message);
        }
        ctx.send_viewport_cmd(egui::ViewportCommand::Close);
        std::process::exit(1);
    }

    fn go(&mut self, to: Screen) {
        if self.transition.is_some() || self.screen == to {
            return;
        }
        if self.silent_upgrade
            && matches!(to, Screen::UpgradeConfirm | Screen::Done | Screen::Welcome)
        {
            return;
        }
        self.transition = Some(ScreenTransition::start(self.screen, to));
    }

    fn draw_screen(&mut self, ui: &mut Ui, screen: Screen, hover_enabled: bool) {
        // Per-screen Id namespace so hover temps never bleed across slides.
        ui.push_id(screen as u8, |ui| {
            match screen {
                Screen::Welcome => self.ui_welcome(ui, hover_enabled),
                Screen::InstallOptions => {
                    self.ui_install_options(ui, hover_enabled && !self.elevation_pending())
                }
                Screen::PortableOptions => self.ui_portable_options(ui, hover_enabled),
                Screen::Progress => self.ui_progress(ui),
                Screen::Done => self.ui_done(ui, hover_enabled),
                Screen::UninstallDone => self.ui_uninstall_done(ui, hover_enabled),
                Screen::UninstallConfirm => {
                    self.ui_uninstall_confirm(ui, hover_enabled && !self.elevation_pending())
                }
                Screen::UpgradeConfirm => {
                    self.ui_upgrade_confirm(ui, hover_enabled && !self.elevation_pending())
                }
            }
        });
    }
}

impl eframe::App for SetupApp {
    fn update(&mut self, ctx: &egui::Context, frame: &mut eframe::Frame) {
        if !self.applied_window_chrome {
            self.applied_window_chrome = true;
            crate::win_chrome::apply(frame, i18n::INSTALLER_DISPLAY_NAME);
            self.synced_native_window_title = Some(i18n::INSTALLER_DISPLAY_NAME);
        } else {
            self.sync_native_window_title(ctx, frame);
        }
        if !self.logged_first_frame {
            self.logged_first_frame = true;
            crate::win_log::info("first UI frame");
        }
        let dt = ctx.input(|i| i.stable_dt).min(0.05);
        self.time += dt;

        if let Some(t) = self.transition.as_mut() {
            if t.tick(dt) {
                self.screen = t.to;
                self.transition = None;
            }
            ctx.request_repaint();
        }

        self.progress.tick(dt);
        if let Some(pending) = self.status_pending.clone() {
            self.status_alpha = (self.status_alpha - dt * 6.0).max(0.0);
            if self.status_alpha <= 0.01 {
                self.status = pending;
                self.status_pending = None;
                self.status_alpha = 0.0;
            }
            ctx.request_repaint();
        } else if self.status_alpha < 1.0 {
            self.status_alpha = (self.status_alpha + dt * 6.0).min(1.0);
            ctx.request_repaint();
        }


        ctx.request_repaint_after(std::time::Duration::from_millis(16));

        #[cfg(windows)]
        if let Some(rx) = &self.elevation_rx {
            match rx.try_recv() {
                Ok(Some(session)) => {
                    self.elevation_rx = None;
                    self.launch_progress_elevated(session);
                }
                Ok(None) => {
                    self.elevation_rx = None;
                    if self.silent_upgrade || self.is_upgrading || self.is_uninstalling {
                        self.handle_fatal_operation_error(ctx, frame, i18n::ELEVATION_DENIED);
                    }
                }
                Err(mpsc::TryRecvError::Empty) => {
                    ctx.request_repaint();
                }
                Err(mpsc::TryRecvError::Disconnected) => {
                    self.elevation_rx = None;
                }
            }
        }

        if self.pending_auto_upgrade {
            self.pending_auto_upgrade = false;
            self.start_upgrade();
        }

        if let Some(rx) = &self.progress_rx {
            let mut events = Vec::new();
            while let Ok(ev) = rx.try_recv() {
                events.push(ev);
            }
            for ev in events {
                match ev {
                    ProgressEvent::Status { message } => {
                        self.status_pending = Some(message);
                        self.status_alpha = 1.0;
                    }
                    ProgressEvent::Progress { fraction } => {
                        self.progress.set_target(fraction.clamp(0.0, 1.0));
                    }
                    ProgressEvent::Done { launch_path } => {
                        if self.launch_after {
                            let _ = std::process::Command::new(&launch_path).spawn();
                        }
                        if self.silent_upgrade || self.launch_after {
                            ctx.send_viewport_cmd(egui::ViewportCommand::Close);
                        } else {
                            self.launch_path = Some(PathBuf::from(launch_path));
                            self.progress.set_target(1.0);
                            self.go(Screen::Done);
                        }
                    }
                    ProgressEvent::Uninstalled => {
                        self.progress.set_target(1.0);
                        self.go(Screen::UninstallDone);
                    }
                    ProgressEvent::Error { message } => {
                        crate::win_log::error(&message);
                        if self.screen == Screen::Progress {
                            self.handle_fatal_operation_error(ctx, frame, &message);
                        } else {
                            self.error = Some(message);
                        }
                    }
                }
            }
        }

        egui::CentralPanel::default()
            .frame(egui::Frame::none())
            .show(ctx, |ui| {
                let full = ui.max_rect();
                theme::draw_background(ui.painter(), full);

                let welcome_visible = self.screen == Screen::Welcome
                    || self
                        .transition
                        .is_some_and(|t| t.from == Screen::Welcome || t.to == Screen::Welcome);

                ui.allocate_ui_at_rect(full, |ui| {
                    ui.set_clip_rect(full);
                    if let Some(tr) = self.transition {
                        let w = full.width();
                        let e = tr.eased();
                        let forward = (tr.to as i32) > (tr.from as i32);
                        let (out_x, in_x) = if forward {
                            (-e * w, (1.0 - e) * w)
                        } else {
                            (e * w, -(1.0 - e) * w)
                        };
                        let from = tr.from;
                        let to = tr.to;
                        ui.allocate_ui_at_rect(full.translate(Vec2::new(out_x, 0.0)), |ui| {
                            self.draw_screen(ui, from, false);
                        });
                        ui.allocate_ui_at_rect(full.translate(Vec2::new(in_x, 0.0)), |ui| {
                            // Hover off during slide; clicks still register.
                            self.draw_screen(ui, to, false);
                        });
                    } else {
                        self.draw_screen(ui, self.screen, true);
                    }
                });

                // Always paint chrome at the physical top of the window (never allocate in flow).
                let mut close_requested = false;
                let mut minimize_requested = false;
                draw_title_bar(
                    ui,
                    full,
                    welcome_visible,
                    &self.logo,
                    ctx.input(|i| i.focused),
                    self.effective_window_title(),
                    &mut || close_requested = true,
                    &mut || minimize_requested = true,
                );
                if minimize_requested {
                    ctx.send_viewport_cmd(egui::ViewportCommand::Minimized(true));
                }
                if close_requested {
                    self.request_close(ctx, frame);
                }
            });
    }
}

impl SetupApp {
    fn ui_welcome(&mut self, ui: &mut Ui, hover_enabled: bool) {
        let full = ui.max_rect();

        ui.painter().image(
            self.brand_bg.id(),
            full,
            Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)),
            Color32::WHITE,
        );
        draw_welcome_brand_overlay(ui.painter(), full, &self.logo);

        let card_w = (full.width() - H_PADDING * 2.0).max(1.0);
        let installed = self.installed.is_some();
        let gap = 8.0;
        let version_gap = 24.0;
        let version_h = 18.0;

        let upgrade_h = if installed {
            widgets::choice_card_height(card_w, i18n::UPGRADE_DESC, ui.painter())
        } else {
            0.0
        };
        let primary_title = if installed {
            i18n::DELETE
        } else {
            i18n::INSTALL
        };
        let primary_desc = if installed {
            i18n::DELETE_DESC
        } else {
            i18n::INSTALL_DESC
        };
        let primary_icon = if installed { DELETE } else { DOWNLOAD };
        let primary_h = widgets::choice_card_height(card_w, primary_desc, ui.painter());
        let portable_h =
            widgets::choice_card_height(card_w, i18n::PORTABLE_DESC, ui.painter());

        let block_h = (if installed { upgrade_h + gap } else { 0.0 })
            + primary_h
            + gap
            + portable_h
            + version_gap
            + version_h;
        let left = full.left() + H_PADDING;
        let mut y = full.bottom() - H_PADDING - block_h;

        let upgrade_rect = if installed {
            let rect = Rect::from_min_size(Pos2::new(left, y), Vec2::new(card_w, upgrade_h));
            y += upgrade_h + gap;
            Some(rect)
        } else {
            None
        };
        let primary_rect =
            Rect::from_min_size(Pos2::new(left, y), Vec2::new(card_w, primary_h));
        y += primary_h + gap;
        let portable_rect =
            Rect::from_min_size(Pos2::new(left, y), Vec2::new(card_w, portable_h));
        y += portable_h + version_gap;
        let version_rect =
            Rect::from_min_size(Pos2::new(left, y), Vec2::new(card_w, version_h));

        if let Some(upgrade_rect) = upgrade_rect {
            ui.allocate_ui_at_rect(upgrade_rect, |ui| {
                ui.set_clip_rect(upgrade_rect);
                if widgets::choice_card_sized(
                    ui,
                    card_w,
                    i18n::UPGRADE,
                    i18n::UPGRADE_DESC,
                    SYSTEM_UPGRADE_ALT,
                    hover_enabled,
                ) && !self.silent_upgrade {
                    self.go(Screen::UpgradeConfirm);
                }
            });
        }
        ui.allocate_ui_at_rect(primary_rect, |ui| {
            ui.set_clip_rect(primary_rect);
            if widgets::choice_card_sized(
                ui,
                card_w,
                primary_title,
                primary_desc,
                primary_icon,
                hover_enabled,
            ) {
                if installed {
                    self.go(Screen::UninstallConfirm);
                } else {
                    self.go(Screen::InstallOptions);
                }
            }
        });
        ui.allocate_ui_at_rect(portable_rect, |ui| {
            ui.set_clip_rect(portable_rect);
            if widgets::choice_card_sized(
                ui,
                card_w,
                i18n::PORTABLE,
                i18n::PORTABLE_DESC,
                FOLDER,
                hover_enabled,
            ) {
                self.go(Screen::PortableOptions);
            }
        });
        ui.allocate_ui_at_rect(version_rect, |ui| {
            ui.with_layout(egui::Layout::top_down(egui::Align::Center), |ui| {
                ui.set_width(card_w);
                ui.label(
                    egui::RichText::new(i18n::version_label(&self.version))
                        .font(body(12.0))
                        .color(ON_SURFACE_VARIANT),
                );
            });
        });
    }

    fn ui_install_options(&mut self, ui: &mut Ui, hover_enabled: bool) {
        let full = ui.max_rect();
        let bar_rect = Rect::from_min_max(
            Pos2::new(full.left(), full.bottom() - widgets::BOTTOM_BAR_HEIGHT),
            full.right_bottom(),
        );
        let body_rect = Rect::from_min_max(
            full.left_top(),
            Pos2::new(full.right(), bar_rect.top()),
        );

        ui.allocate_ui_at_rect(body_rect, |ui| {
            ui.set_clip_rect(body_rect);
            ui.add_space(TITLE_BAR_HEIGHT + 4.0);
            egui::Frame::none()
                .inner_margin(egui::Margin {
                    left: widgets::H_PADDING,
                    right: widgets::H_PADDING,
                    ..Default::default()
                })
                .show(ui, |ui| {
                    egui::ScrollArea::vertical()
                        .auto_shrink([false, false])
                        .show(ui, |ui| {
                            widgets::expressive_section_header(
                                ui,
                                i18n::INSTALL,
                                DOWNLOAD,
                                widgets::ExpressiveHeroShape::Cookie4Sided,
                            );
                            widgets::field_label(ui, i18n::INSTALL_LOCATION);
                            if widgets::path_field(
                                ui,
                                &mut self.install_path,
                                hover_enabled,
                                ui.id().with("install_path"),
                            ) {
                                #[cfg(windows)]
                                if let Some(path) = rfd::FileDialog::new().pick_folder() {
                                    self.install_path = path.to_string_lossy().into_owned();
                                }
                            }
                            ui.add_space(8.0);
                            let was_all_users = self.all_users;
                            widgets::checkbox(ui, &mut self.all_users, i18n::ALL_USERS, hover_enabled);
                            if self.all_users != was_all_users {
                                self.install_path =
                                    default_install_path(self.all_users, self.edition);
                            }
                            widgets::checkbox(
                                ui,
                                &mut self.desktop_shortcut,
                                i18n::DESKTOP,
                                hover_enabled,
                            );
                            if let Some(err) = &self.error {
                                widgets::error_label(ui, err);
                            }
                        });
                });
        });

        let mut back = false;
        let mut go_next = false;
        ui.allocate_ui_at_rect(bar_rect, |ui| {
            ui.set_clip_rect(bar_rect);
            ui.with_layout(egui::Layout::left_to_right(egui::Align::Center), |ui| {
                egui::Frame::none()
                    .inner_margin(egui::Margin::symmetric(
                        widgets::H_PADDING,
                        widgets::BOTTOM_BAR_PAD,
                    ))
                    .show(ui, |ui| {
                        ui.set_height(widgets::BOTTOM_BAR_CONTROL_HEIGHT);
                        widgets::bottom_bar(
                            ui,
                            hover_enabled,
                            true,
                            || back = true,
                            || go_next = true,
                            widgets::PrimaryAction::Install,
                        );
                    });
            });
        });
        if back {
            self.go(Screen::Welcome);
        }
        if go_next {
            self.start_install(false);
        }
    }

    fn ui_portable_options(&mut self, ui: &mut Ui, hover_enabled: bool) {
        let full = ui.max_rect();
        let bar_rect = Rect::from_min_max(
            Pos2::new(full.left(), full.bottom() - widgets::BOTTOM_BAR_HEIGHT),
            full.right_bottom(),
        );
        let body_rect = Rect::from_min_max(
            full.left_top(),
            Pos2::new(full.right(), bar_rect.top()),
        );

        ui.allocate_ui_at_rect(body_rect, |ui| {
            ui.set_clip_rect(body_rect);
            ui.add_space(TITLE_BAR_HEIGHT + 4.0);
            egui::Frame::none()
                .inner_margin(egui::Margin {
                    left: widgets::H_PADDING,
                    right: widgets::H_PADDING,
                    ..Default::default()
                })
                .show(ui, |ui| {
                    widgets::expressive_section_header(
                        ui,
                        i18n::PORTABLE,
                        FOLDER,
                        widgets::ExpressiveHeroShape::Cookie6Sided,
                    );
                    widgets::field_label(ui, i18n::DESTINATION);
                    if widgets::path_field(
                        ui,
                        &mut self.portable_path,
                        hover_enabled,
                        ui.id().with("portable_path"),
                    ) {
                        #[cfg(windows)]
                        if let Some(path) = rfd::FileDialog::new().pick_folder() {
                            self.portable_path = path.to_string_lossy().into_owned();
                        }
                    }
                    if let Some(err) = &self.error {
                        widgets::error_label(ui, err);
                    }
                });
        });

        let mut back = false;
        let mut go_next = false;
        ui.allocate_ui_at_rect(bar_rect, |ui| {
            ui.set_clip_rect(bar_rect);
            ui.with_layout(egui::Layout::left_to_right(egui::Align::Center), |ui| {
                egui::Frame::none()
                    .inner_margin(egui::Margin::symmetric(
                        widgets::H_PADDING,
                        widgets::BOTTOM_BAR_PAD,
                    ))
                    .show(ui, |ui| {
                        ui.set_height(widgets::BOTTOM_BAR_CONTROL_HEIGHT);
                        widgets::bottom_bar(
                            ui,
                            hover_enabled,
                            true,
                            || back = true,
                            || go_next = true,
                            widgets::PrimaryAction::Extract,
                        );
                    });
            });
        });
        if back {
            self.go(Screen::Welcome);
        }
        if go_next {
            self.start_install(true);
        }
    }

    fn ui_uninstall_confirm(&mut self, ui: &mut Ui, hover_enabled: bool) {
        let full = ui.max_rect();
        let bar_rect = Rect::from_min_max(
            Pos2::new(full.left(), full.bottom() - widgets::BOTTOM_BAR_HEIGHT),
            full.right_bottom(),
        );
        let body_rect = Rect::from_min_max(
            full.left_top(),
            Pos2::new(full.right(), bar_rect.top()),
        );

        ui.allocate_ui_at_rect(body_rect, |ui| {
            ui.set_clip_rect(body_rect);
            ui.add_space(TITLE_BAR_HEIGHT + 4.0);
            egui::Frame::none()
                .inner_margin(egui::Margin {
                    left: widgets::H_PADDING,
                    right: widgets::H_PADDING,
                    ..Default::default()
                })
                .show(ui, |ui| {
                    widgets::expressive_section_header(
                        ui,
                        i18n::UNINSTALL_TITLE,
                        DELETE,
                        widgets::ExpressiveHeroShape::Cookie6Sided,
                    );
                    ui.add_space(8.0);
                    ui.label(
                        egui::RichText::new(i18n::UNINSTALL_CONFIRM_BODY)
                            .font(body(14.0))
                            .color(ON_SURFACE_VARIANT),
                    );
                    ui.add_space(12.0);
                    widgets::checkbox(
                        ui,
                        &mut self.preserve_user_data,
                        i18n::PRESERVE_USER_DATA,
                        hover_enabled,
                    );
                    ui.add_space(12.0);
                    ui.push_id("support", |ui| {
                        widgets::done_list_row(ui, SUPPORT, |ui| {
                            ui.horizontal_wrapped(|ui| {
                                ui.spacing_mut().item_spacing.x = 0.0;
                                ui.label(
                                    egui::RichText::new(i18n::UNINSTALL_SUPPORT_PREFIX)
                                        .font(body(14.0))
                                        .color(ON_SURFACE_VARIANT),
                                );
                                if widgets::inline_link(
                                    ui,
                                    i18n::UNINSTALL_SUPPORT_LABEL,
                                    hover_enabled,
                                ) {
                                    open_url(i18n::DONE_TELEGRAM_SUPPORT_URL);
                                }
                                ui.label(
                                    egui::RichText::new(".")
                                        .font(body(14.0))
                                        .color(ON_SURFACE_VARIANT),
                                );
                            });
                        });
                    });
                    if let Some(err) = &self.error {
                        widgets::error_label(ui, err);
                    }
                });
        });

        let mut back = false;
        let mut go_next = false;
        ui.allocate_ui_at_rect(bar_rect, |ui| {
            ui.set_clip_rect(bar_rect);
            ui.with_layout(egui::Layout::left_to_right(egui::Align::Center), |ui| {
                egui::Frame::none()
                    .inner_margin(egui::Margin::symmetric(
                        widgets::H_PADDING,
                        widgets::BOTTOM_BAR_PAD,
                    ))
                    .show(ui, |ui| {
                        ui.set_height(widgets::BOTTOM_BAR_CONTROL_HEIGHT);
                        widgets::bottom_bar(
                            ui,
                            hover_enabled,
                            !matches!(self.mode, SetupMode::Uninstall),
                            || back = true,
                            || go_next = true,
                            widgets::PrimaryAction::Uninstall,
                        );
                    });
            });
        });
        if back {
            self.go(Screen::Welcome);
        }
        if go_next {
            self.start_uninstall();
        }
    }

    fn ensure_install_icon_texture(&mut self, ctx: &egui::Context, installed: &InstalledFromChat) {
        let key = installed.install_dir.to_string_lossy().into_owned();
        if self.install_icon_textures.contains_key(&key) {
            return;
        }
        let Some(icon) = load_install_display_icon(
            installed.display_icon.as_deref(),
            &installed.install_dir,
        ) else {
            return;
        };
        let size = [icon.width as usize, icon.height as usize];
        let color_image = egui::ColorImage::from_rgba_unmultiplied(size, &icon.pixels);
        let tex = ctx.load_texture(
            format!("install-icon-{key}"),
            color_image,
            egui::TextureOptions::LINEAR,
        );
        self.install_icon_textures.insert(key, tex);
    }

    fn ui_upgrade_confirm(&mut self, ui: &mut Ui, hover_enabled: bool) {
        let ctx = ui.ctx().clone();
        let full = ui.max_rect();
        let bar_rect = Rect::from_min_max(
            Pos2::new(full.left(), full.bottom() - widgets::BOTTOM_BAR_HEIGHT),
            full.right_bottom(),
        );
        let body_rect = Rect::from_min_max(
            full.left_top(),
            Pos2::new(full.right(), bar_rect.top()),
        );
        let installed = self.installed.clone();

        ui.allocate_ui_at_rect(body_rect, |ui| {
            ui.set_clip_rect(body_rect);
            ui.add_space(TITLE_BAR_HEIGHT + 4.0);
            egui::Frame::none()
                .inner_margin(egui::Margin {
                    left: widgets::H_PADDING,
                    right: widgets::H_PADDING,
                    ..Default::default()
                })
                .show(ui, |ui| {
                    widgets::expressive_section_header(
                        ui,
                        i18n::UPGRADE_CONFIRM_TITLE,
                        SYSTEM_UPGRADE_ALT,
                        widgets::ExpressiveHeroShape::Cookie6Sided,
                    );
                    ui.add_space(8.0);
                    ui.label(
                        egui::RichText::new(i18n::UPGRADE_CONFIRM_BODY)
                            .font(body(14.0))
                            .color(ON_SURFACE_VARIANT),
                    );
                    if let Some(installed) = &installed {
                        self.ensure_install_icon_texture(&ctx, installed);
                        let icon_key = installed.install_dir.to_string_lossy().into_owned();
                        let icon_texture = self.install_icon_textures.get(&icon_key);
                        ui.add_space(12.0);
                        widgets::install_copy_card(
                            ui,
                            &i18n::installed_copy_title(
                                installed.edition.display_name(),
                                installed.version.as_deref(),
                            ),
                            &installed.install_dir.to_string_lossy(),
                            icon_texture,
                            SYSTEM_UPGRADE_ALT,
                        );
                    } else if self.error.is_none() {
                        self.error = Some(i18n::NOT_INSTALLED.into());
                    }
                    if let Some(err) = &self.error {
                        widgets::error_label(ui, err);
                    }
                });
        });

        let mut back = false;
        let mut go_next = false;
        let can_upgrade = installed.is_some();
        ui.allocate_ui_at_rect(bar_rect, |ui| {
            ui.set_clip_rect(bar_rect);
            ui.with_layout(egui::Layout::left_to_right(egui::Align::Center), |ui| {
                egui::Frame::none()
                    .inner_margin(egui::Margin::symmetric(
                        widgets::H_PADDING,
                        widgets::BOTTOM_BAR_PAD,
                    ))
                    .show(ui, |ui| {
                        ui.set_height(widgets::BOTTOM_BAR_CONTROL_HEIGHT);
                        widgets::bottom_bar(
                            ui,
                            hover_enabled && can_upgrade,
                            !matches!(self.mode, SetupMode::Uninstall | SetupMode::Upgrade),
                            || back = true,
                            || go_next = true,
                            widgets::PrimaryAction::Upgrade,
                        );
                    });
            });
        });
        if back {
            self.go(Screen::Welcome);
        }
        if go_next && can_upgrade {
            self.start_upgrade();
        }
    }

    fn ui_progress(&mut self, ui: &mut Ui) {
        ui.add_space(TITLE_BAR_HEIGHT);
        ui.vertical_centered(|ui| {
            ui.add_space(48.0);
            draw_logo(ui, &self.logo, 64.0);
            ui.add_space(16.0);
            let title = if self.is_upgrading {
                i18n::UPGRADING
            } else if self.is_uninstalling {
                i18n::UNINSTALLING
            } else {
                i18n::INSTALLING
            };
            ui.label(
                egui::RichText::new(title)
                    .font(title_large())
                    .color(ON_SURFACE),
            );
            ui.add_space(20.0);
            let bar_w = ui.available_width().min(360.0);
            let bar_h = theme::LINEAR_PROGRESS_HEIGHT;
            let (rect, _) = ui.allocate_exact_size(Vec2::new(bar_w, bar_h), Sense::hover());
            ui.painter().rect_filled(
                rect,
                Rounding::same(theme::LINEAR_PROGRESS_CORNER_RADIUS),
                theme::SURFACE_CONTAINER_HIGHEST,
            );
            let fill = Rect::from_min_size(
                rect.min,
                Vec2::new(rect.width() * self.progress.value, bar_h),
            );
            ui.painter().rect_filled(
                fill,
                Rounding::same(theme::LINEAR_PROGRESS_CORNER_RADIUS),
                PRIMARY,
            );
            ui.add_space(16.0);
            ui.label(
                egui::RichText::new(&self.status)
                    .font(body_medium(14.0))
                    .color(ON_SURFACE_VARIANT.gamma_multiply(self.status_alpha)),
            );
            if let Some(err) = &self.error {
                widgets::error_label(ui, err);
            }
        });
    }

    fn ui_done(&mut self, ui: &mut Ui, hover_enabled: bool) {
        let full = ui.max_rect();
        let bar_rect = Rect::from_min_max(
            Pos2::new(full.left(), full.bottom() - widgets::BOTTOM_BAR_HEIGHT),
            full.right_bottom(),
        );
        let body_rect = Rect::from_min_max(
            full.left_top(),
            Pos2::new(full.right(), bar_rect.top()),
        );

        ui.allocate_ui_at_rect(body_rect, |ui| {
            ui.set_clip_rect(body_rect);
            ui.add_space(TITLE_BAR_HEIGHT + 4.0);
            egui::Frame::none()
                .inner_margin(egui::Margin {
                    left: widgets::H_PADDING,
                    right: widgets::H_PADDING,
                    ..Default::default()
                })
                .show(ui, |ui| {
                    widgets::expressive_section_header(
                        ui,
                        i18n::DONE_TITLE,
                        DONE_ALL,
                        widgets::ExpressiveHeroShape::Cookie6Sided,
                    );

                    let hint = if self.was_portable {
                        i18n::DONE_OPEN_HINT_PORTABLE
                    } else if self.desktop_shortcut {
                        i18n::DONE_OPEN_HINT_DESKTOP
                    } else {
                        i18n::DONE_OPEN_HINT
                    };
                    ui.push_id("open_hint", |ui| {
                        widgets::done_list_row(ui, OPEN_IN_NEW, |ui| {
                            ui.label(
                                egui::RichText::new(hint)
                                    .font(body(14.0))
                                    .color(ON_SURFACE),
                            );
                        });
                    });
                    ui.add_space(12.0);
                    ui.push_id("telegram", |ui| {
                        widgets::done_list_row(ui, RSS_FEED, |ui| {
                            ui.horizontal_wrapped(|ui| {
                                ui.spacing_mut().item_spacing.x = 0.0;
                                ui.label(
                                    egui::RichText::new(i18n::DONE_TELEGRAM_PREFIX)
                                        .font(body(14.0))
                                        .color(ON_SURFACE_VARIANT),
                                );
                                if widgets::inline_link(
                                    ui,
                                    i18n::DONE_TELEGRAM_CHANNEL_LABEL,
                                    hover_enabled,
                                ) {
                                    open_url(i18n::DONE_TELEGRAM_CHANNEL_URL);
                                }
                                ui.label(
                                    egui::RichText::new(i18n::DONE_TELEGRAM_MIDDLE)
                                        .font(body(14.0))
                                        .color(ON_SURFACE_VARIANT),
                                );
                                if widgets::inline_link(
                                    ui,
                                    i18n::DONE_TELEGRAM_SUPPORT_LABEL,
                                    hover_enabled,
                                ) {
                                    open_url(i18n::DONE_TELEGRAM_SUPPORT_URL);
                                }
                                ui.label(
                                    egui::RichText::new(".")
                                        .font(body(14.0))
                                        .color(ON_SURFACE_VARIANT),
                                );
                            });
                        });
                    });
                });
        });

        let ctx = ui.ctx().clone();
        ui.allocate_ui_at_rect(bar_rect, |ui| {
            ui.set_clip_rect(bar_rect);
            ui.with_layout(egui::Layout::left_to_right(egui::Align::Center), |ui| {
                egui::Frame::none()
                    .inner_margin(egui::Margin::symmetric(
                        widgets::H_PADDING,
                        widgets::BOTTOM_BAR_PAD,
                    ))
                    .show(ui, |ui| {
                        ui.set_height(widgets::BOTTOM_BAR_CONTROL_HEIGHT);
                        let (launch, close) = widgets::done_bottom_bar(ui, hover_enabled);
                        if launch {
                            if let Some(p) = &self.launch_path {
                                let _ = std::process::Command::new(p).spawn();
                            }
                            ctx.send_viewport_cmd(egui::ViewportCommand::Close);
                        }
                        if close {
                            ctx.send_viewport_cmd(egui::ViewportCommand::Close);
                        }
                    });
            });
        });
    }

    fn ui_uninstall_done(&mut self, ui: &mut Ui, hover_enabled: bool) {
        let full = ui.max_rect();
        let bar_rect = Rect::from_min_max(
            Pos2::new(full.left(), full.bottom() - widgets::BOTTOM_BAR_HEIGHT),
            full.right_bottom(),
        );
        let body_rect = Rect::from_min_max(
            full.left_top(),
            Pos2::new(full.right(), bar_rect.top()),
        );

        ui.allocate_ui_at_rect(body_rect, |ui| {
            ui.set_clip_rect(body_rect);
            ui.add_space(TITLE_BAR_HEIGHT + 4.0);
            egui::Frame::none()
                .inner_margin(egui::Margin {
                    left: widgets::H_PADDING,
                    right: widgets::H_PADDING,
                    ..Default::default()
                })
                .show(ui, |ui| {
                    widgets::expressive_section_header(
                        ui,
                        i18n::UNINSTALL_DONE_TITLE,
                        DONE_ALL,
                        widgets::ExpressiveHeroShape::Cookie6Sided,
                    );
                    ui.add_space(8.0);
                    ui.label(
                        egui::RichText::new(i18n::UNINSTALL_DONE_SUBTITLE)
                            .font(body(14.0))
                            .color(ON_SURFACE_VARIANT),
                    );
                });
        });

        let ctx = ui.ctx().clone();
        ui.allocate_ui_at_rect(bar_rect, |ui| {
            ui.set_clip_rect(bar_rect);
            ui.with_layout(egui::Layout::left_to_right(egui::Align::Center), |ui| {
                egui::Frame::none()
                    .inner_margin(egui::Margin::symmetric(
                        widgets::H_PADDING,
                        widgets::BOTTOM_BAR_PAD,
                    ))
                    .show(ui, |ui| {
                        ui.set_height(widgets::BOTTOM_BAR_CONTROL_HEIGHT);
                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                            ui.set_width(ui.available_width());
                            if widgets::text_button_icon(ui, CLOSE, i18n::CLOSE, hover_enabled) {
                                ctx.send_viewport_cmd(egui::ViewportCommand::Close);
                            }
                        });
                    });
            });
        });
    }

    fn elevation_pending(&self) -> bool {
        #[cfg(windows)]
        {
            return self.elevation_rx.is_some();
        }
        #[cfg(not(windows))]
        {
            false
        }
    }

    fn launch_progress_elevated(&mut self, session: ElevatedInstallSession) {
        self.go(Screen::Progress);
        self.status = i18n::PREPARING.into();
        self.progress = Lerped::new(0.0);
        let (tx, rx) = mpsc::channel();
        self.progress_rx = Some(rx);
        thread::spawn(move || {
            if let Err(error) = run_elevated_install(session, tx.clone()) {
                let _ = tx.send(ProgressEvent::Error {
                    message: format!("{error:#}"),
                });
            }
        });
    }

    fn launch_standard_install(&mut self, portable: bool) {
        self.go(Screen::Progress);
        self.status = i18n::PREPARING.into();
        self.progress = Lerped::new(0.0);
        let (tx, rx) = mpsc::channel();
        self.progress_rx = Some(rx);

        let install_path = self.install_path.clone();
        let portable_path = self.portable_path.clone();
        let all_users = self.all_users;
        let desktop_shortcut = self.desktop_shortcut;

        thread::spawn(move || {
            let result = if portable {
                run_portable(tx.clone(), PathBuf::from(portable_path))
            } else {
                run_full_install(
                    tx.clone(),
                    PathBuf::from(install_path),
                    all_users,
                    desktop_shortcut,
                )
            };
            if let Err(error) = result {
                let _ = tx.send(ProgressEvent::Error {
                    message: format!("{error:#}"),
                });
            }
        });
    }

    fn start_install(&mut self, portable: bool) {
        self.error = None;
        self.was_portable = portable;
        self.is_uninstalling = false;
        self.is_upgrading = false;

        #[cfg(windows)]
        if !portable && self.all_users && !fromchat_installer_common::is_elevated() {
            let install_path = PathBuf::from(&self.install_path);
            let desktop_shortcut = self.desktop_shortcut;
            let (tx, rx) = mpsc::channel();
            self.elevation_rx = Some(rx);
            thread::spawn(move || {
                let session = begin_elevated_install(install_path.as_path(), desktop_shortcut).ok();
                let _ = tx.send(session);
            });
            return;
        }

        #[cfg(not(windows))]
        if !portable && self.all_users {
            return;
        }

        self.launch_standard_install(portable);
    }

    fn launch_standard_uninstall(&mut self, installed: InstalledFromChat) {
        self.go(Screen::Progress);
        self.status = i18n::PREPARING.into();
        self.progress = Lerped::new(0.0);
        let preserve_user_data = self.preserve_user_data;
        let (tx, rx) = mpsc::channel();
        self.progress_rx = Some(rx);
        thread::spawn(move || {
            if let Err(error) = run_uninstall(tx.clone(), installed, preserve_user_data) {
                let _ = tx.send(ProgressEvent::Error {
                    message: format!("{error:#}"),
                });
            }
        });
    }

    fn launch_standard_upgrade(&mut self, installed: InstalledFromChat) {
        self.go(Screen::Progress);
        self.status = i18n::PREPARING.into();
        self.progress = Lerped::new(0.0);
        let (tx, rx) = mpsc::channel();
        self.progress_rx = Some(rx);
        thread::spawn(move || {
            if let Err(error) = run_upgrade(tx.clone(), installed) {
                let _ = tx.send(ProgressEvent::Error {
                    message: format!("{error:#}"),
                });
            }
        });
    }

    fn start_uninstall(&mut self) {
        self.error = None;
        self.is_uninstalling = true;
        self.is_upgrading = false;

        #[cfg(windows)]
        let installed = self.installed.clone();
        #[cfg(not(windows))]
        let installed = None;

        let Some(installed) = installed else {
            self.error = Some(i18n::NOT_INSTALLED.into());
            return;
        };

        let preserve_user_data = self.preserve_user_data;

        #[cfg(windows)]
        if installed.all_users && !fromchat_installer_common::is_elevated() {
            let (tx, rx) = mpsc::channel();
            self.elevation_rx = Some(rx);
            thread::spawn(move || {
                let session =
                    begin_elevated_uninstall(&installed, preserve_user_data).ok();
                let _ = tx.send(session);
            });
            return;
        }

        self.launch_standard_uninstall(installed);
    }

    fn start_upgrade(&mut self) {
        self.error = None;
        self.is_uninstalling = false;
        self.is_upgrading = true;

        #[cfg(windows)]
        let installed = self.installed.clone();
        #[cfg(not(windows))]
        let installed = None;

        let Some(installed) = installed else {
            self.error = Some(i18n::NOT_INSTALLED.into());
            return;
        };

        #[cfg(windows)]
        if installed.all_users && !fromchat_installer_common::is_elevated() {
            let (tx, rx) = mpsc::channel();
            self.elevation_rx = Some(rx);
            thread::spawn(move || {
                let session = begin_elevated_upgrade(&installed).ok();
                let _ = tx.send(session);
            });
            return;
        }

        self.launch_standard_upgrade(installed);
    }
}

fn load_brand_bg_texture(ctx: &egui::Context) -> TextureHandle {
    const BRAND_BG_PNG: &[u8] = include_bytes!("../../assets/welcome_brand_bg.png");
    let color_image = match image::load_from_memory(BRAND_BG_PNG) {
        Ok(image) => {
            let rgba = image.to_rgba8();
            let size = [rgba.width() as usize, rgba.height() as usize];
            egui::ColorImage::from_rgba_unmultiplied(size, rgba.as_raw())
        }
        Err(error) => {
            crate::win_log::error(&format!("welcome brand bg png decode failed: {error}"));
            egui::ColorImage::new([1, 1], theme::SURFACE)
        }
    };
    ctx.load_texture(
        "fromchat-welcome-brand-bg",
        color_image,
        egui::TextureOptions::LINEAR,
    )
}

fn open_url(url: &str) {
    #[cfg(windows)]
    {
        let _ = std::process::Command::new("cmd")
            .args(["/C", "start", "", url])
            .spawn();
    }
    #[cfg(not(windows))]
    {
        let _ = url;
    }
}

fn load_logo_texture(ctx: &egui::Context) -> TextureHandle {
    let color_image = match image::load_from_memory(BRANDING_PNG) {
        Ok(image) => {
            let rgba = image.to_rgba8();
            let size = [rgba.width() as usize, rgba.height() as usize];
            egui::ColorImage::from_rgba_unmultiplied(size, rgba.as_raw())
        }
        Err(error) => {
            crate::win_log::error(&format!("branding png decode failed: {error}"));
            egui::ColorImage::new([64, 64], theme::PRIMARY_CONTAINER)
        }
    };
    ctx.load_texture(
        "fromchat-logo",
        color_image,
        egui::TextureOptions::LINEAR,
    )
}

fn run_portable(tx: Sender<ProgressEvent>, dest: PathBuf) -> anyhow::Result<()> {
    let _ = tx.send(ProgressEvent::Status {
        message: i18n::READING_PACKAGE.into(),
    });
    let exe = std::env::current_exe()?;
    let bundle = read_bundle_from_exe(&exe, SETUP_MAGIC)?;
    let _ = tx.send(ProgressEvent::Progress { fraction: 0.15 });
    std::fs::create_dir_all(&dest)?;
    let _ = tx.send(ProgressEvent::Status {
        message: i18n::EXTRACTING.into(),
    });
    extract_zstd_tar(bundle.payload_zstd()?, &dest)?;
    let _ = tx.send(ProgressEvent::Progress { fraction: 0.7 });
    let launcher_path = dest.join(VISIBLE_PORTABLE_EXE);
    std::fs::write(&launcher_path, &bundle.launcher)?;
    let _ = tx.send(ProgressEvent::Status {
        message: i18n::HIDING_FILES.into(),
    });
    #[cfg(windows)]
    {
        fromchat_installer_common::hide_all_except(&dest, VISIBLE_PORTABLE_EXE)?;
        let data = dest.join("fromchat-data");
        std::fs::create_dir_all(&data)?;
        fromchat_installer_common::set_hidden(&data, true)?;
    }
    let _ = tx.send(ProgressEvent::Progress { fraction: 1.0 });
    let _ = tx.send(ProgressEvent::Done {
        launch_path: launcher_path.to_string_lossy().into_owned(),
    });
    Ok(())
}

fn run_full_install(
    tx: Sender<ProgressEvent>,
    dest: PathBuf,
    all_users: bool,
    desktop_shortcut: bool,
) -> anyhow::Result<()> {
    let _ = tx.send(ProgressEvent::Status {
        message: i18n::READING_PACKAGE.into(),
    });
    let exe = std::env::current_exe()?;
    let bundle = read_bundle_from_exe(&exe, SETUP_MAGIC)?;
    let version = bundle.meta.version.clone();
    let edition = FromChatEdition::from_registry_key(&bundle.meta.registration_id)
        .unwrap_or(FromChatEdition::Release);

    let _ = tx.send(ProgressEvent::Status {
        message: i18n::COPYING.into(),
    });
    let _ = tx.send(ProgressEvent::Progress { fraction: 0.2 });
    std::fs::create_dir_all(&dest)?;
    extract_zstd_tar(bundle.payload_zstd()?, &dest)?;
    let _ = tx.send(ProgressEvent::Progress { fraction: 0.75 });
    let app_exe = find_jpackage_app_exe(&dest)?;
    patch_jpackage_exe_icon(&app_exe)
        .with_context(|| format!("patch icon on {}", app_exe.display()))?;
    let launch_exe = finalize_install_launcher(&dest, edition, &bundle.launcher)?;
    #[cfg(windows)]
    {
        let _ = tx.send(ProgressEvent::Status {
            message: i18n::REGISTERING.into(),
        });
        let icon = write_install_icon(&dest)?;
        let uninstaller = copy_uninstall_setup(&exe, &dest)?;
        fromchat_installer_common::write_uninstall_registry(
            all_users,
            edition,
            &version,
            &dest,
            &uninstaller,
            &icon,
        )?;
        if desktop_shortcut {
            let desk = fromchat_installer_common::desktop_folder()?;
            let link = desk.join(format!("{}.lnk", edition.shortcut_name()));
            fromchat_installer_common::create_shortcut(
                &link,
                &launch_exe,
                &dest,
                edition.display_name(),
                &icon,
            )?;
        }
        let programs = fromchat_installer_common::programs_folder(all_users)?;
        let link = programs.join(format!("{}.lnk", edition.shortcut_name()));
        fromchat_installer_common::create_shortcut(
            &link,
            &launch_exe,
            &dest,
            edition.display_name(),
            &icon,
        )?;
    }
    let _ = app_exe;
    let _ = tx.send(ProgressEvent::Progress { fraction: 1.0 });
    let _ = tx.send(ProgressEvent::Done {
        launch_path: launch_exe.to_string_lossy().into_owned(),
    });
    Ok(())
}

#[cfg(windows)]
fn begin_elevated_install(
    dest: &std::path::Path,
    desktop_shortcut: bool,
) -> anyhow::Result<ElevatedInstallSession> {
    let exe = std::env::current_exe()?;
    let bundle = read_bundle_from_exe(&exe, SETUP_MAGIC)?;
    let edition = FromChatEdition::from_registry_key(&bundle.meta.registration_id)
        .unwrap_or(FromChatEdition::Release);
    let temp = std::env::temp_dir().join("FromChat-Installer-payload");
    let _ = std::fs::remove_dir_all(&temp);
    std::fs::create_dir_all(&temp)?;
    let payload_path = temp.join("payload.zst");
    std::fs::write(&payload_path, bundle.payload_zstd()?)?;
    let launcher_path = temp.join("fromchat-launcher.exe");
    std::fs::write(&launcher_path, &bundle.launcher)?;
    let helper_path = temp.join(SETUP_HELPER_EXE);
    std::fs::write(&helper_path, &bundle.helper)?;
    let setup_copy = temp.join(SETUP_INSTALLER_EXE);
    std::fs::copy(&exe, &setup_copy)?;
    let uninstaller = dest.join(UNINSTALL_SETUP_EXE);
    let cmd = HelperCommand::Install {
        dest: dest.to_string_lossy().into_owned(),
        version: bundle.meta.version.clone(),
        all_users: true,
        edition: edition_tag(edition).into(),
        start_menu: true,
        desktop: desktop_shortcut,
        payload_path: payload_path.to_string_lossy().into_owned(),
        launcher_path: launcher_path.to_string_lossy().into_owned(),
        uninstaller_path: uninstaller.to_string_lossy().into_owned(),
        setup_exe_path: setup_copy.to_string_lossy().into_owned(),
    };
    let cmd_json = serde_json::to_string(&cmd)?;
    let pipe = fromchat_installer_common::NamedPipeServer::create(fromchat_installer_common::PIPE_NAME)?;
    fromchat_installer_common::elevate_helper(
        &helper_path,
        &format!("--pipe {}", fromchat_installer_common::PIPE_NAME),
    )?;
    pipe.accept()?;
    Ok(ElevatedInstallSession { pipe, cmd_json })
}

#[cfg(windows)]
fn begin_elevated_uninstall(
    installed: &InstalledFromChat,
    preserve_user_data: bool,
) -> anyhow::Result<ElevatedInstallSession> {
    let exe = std::env::current_exe()?;
    let bundle = read_bundle_from_exe(&exe, SETUP_MAGIC)?;
    let temp = std::env::temp_dir().join("FromChat-Installer-payload");
    let _ = std::fs::remove_dir_all(&temp);
    std::fs::create_dir_all(&temp)?;
    let helper_path = temp.join(SETUP_HELPER_EXE);
    std::fs::write(&helper_path, &bundle.helper)?;
    let cmd = HelperCommand::Uninstall {
        install_dir: installed.install_dir.to_string_lossy().into_owned(),
        all_users: installed.all_users,
        edition: edition_tag(installed.edition).into(),
        preserve_setup_exe: std::env::current_exe()
            .ok()
            .map(|path| path.to_string_lossy().into_owned()),
        preserve_user_data,
    };
    let cmd_json = serde_json::to_string(&cmd)?;
    let pipe = fromchat_installer_common::NamedPipeServer::create(fromchat_installer_common::PIPE_NAME)?;
    fromchat_installer_common::elevate_helper(
        &helper_path,
        &format!("--pipe {}", fromchat_installer_common::PIPE_NAME),
    )?;
    pipe.accept()?;
    Ok(ElevatedInstallSession { pipe, cmd_json })
}

#[cfg(windows)]
fn begin_elevated_upgrade(installed: &InstalledFromChat) -> anyhow::Result<ElevatedInstallSession> {
    let exe = std::env::current_exe()?;
    let bundle = read_bundle_from_exe(&exe, SETUP_MAGIC)?;
    let temp = std::env::temp_dir().join("FromChat-Installer-payload");
    let _ = std::fs::remove_dir_all(&temp);
    std::fs::create_dir_all(&temp)?;
    let payload_path = temp.join("payload.zst");
    std::fs::write(&payload_path, bundle.payload_zstd()?)?;
    let launcher_path = temp.join("fromchat-launcher.exe");
    std::fs::write(&launcher_path, &bundle.launcher)?;
    let helper_path = temp.join(SETUP_HELPER_EXE);
    std::fs::write(&helper_path, &bundle.helper)?;
    let setup_copy = temp.join(SETUP_INSTALLER_EXE);
    std::fs::copy(&exe, &setup_copy)?;
    let cmd = HelperCommand::Upgrade {
        dest: installed.install_dir.to_string_lossy().into_owned(),
        version: bundle.meta.version.clone(),
        all_users: installed.all_users,
        edition: edition_tag(installed.edition).into(),
        payload_path: payload_path.to_string_lossy().into_owned(),
        launcher_path: launcher_path.to_string_lossy().into_owned(),
        setup_exe_path: setup_copy.to_string_lossy().into_owned(),
        desktop_shortcut: desktop_shortcut_exists(installed),
    };
    let cmd_json = serde_json::to_string(&cmd)?;
    let pipe = fromchat_installer_common::NamedPipeServer::create(fromchat_installer_common::PIPE_NAME)?;
    fromchat_installer_common::elevate_helper(
        &helper_path,
        &format!("--pipe {}", fromchat_installer_common::PIPE_NAME),
    )?;
    pipe.accept()?;
    Ok(ElevatedInstallSession { pipe, cmd_json })
}

fn run_uninstall(
    tx: Sender<ProgressEvent>,
    installed: InstalledFromChat,
    preserve_user_data: bool,
) -> anyhow::Result<()> {
    let _ = tx.send(ProgressEvent::Status {
        message: i18n::REMOVING_FILES.into(),
    });
    let _ = tx.send(ProgressEvent::Progress { fraction: 0.5 });
    let preserve = std::env::current_exe()
        .ok()
        .filter(|path| path.starts_with(&installed.install_dir));
    uninstall_fromchat(&installed, preserve.as_deref(), preserve_user_data)?;
    let _ = tx.send(ProgressEvent::Progress { fraction: 1.0 });
    let _ = tx.send(ProgressEvent::Uninstalled);
    Ok(())
}

fn run_upgrade(
    tx: Sender<ProgressEvent>,
    installed: InstalledFromChat,
) -> anyhow::Result<()> {
    let _ = tx.send(ProgressEvent::Status {
        message: i18n::READING_PACKAGE.into(),
    });
    let exe = std::env::current_exe()?;
    let bundle = read_bundle_from_exe(&exe, SETUP_MAGIC)?;
    let version = bundle.meta.version.clone();
    let dest = installed.install_dir.clone();
    let desktop_shortcut = desktop_shortcut_exists(&installed);

    let _ = tx.send(ProgressEvent::Status {
        message: i18n::UPGRADE_REPLACING_FILES.into(),
    });
    let _ = tx.send(ProgressEvent::Progress { fraction: 0.2 });
    wipe_install_dir(&dest, None)?;
    std::fs::create_dir_all(&dest)?;
    extract_zstd_tar(bundle.payload_zstd()?, &dest)?;
    let _ = tx.send(ProgressEvent::Progress { fraction: 0.75 });
    let app_exe = find_jpackage_app_exe(&dest)?;
    patch_jpackage_exe_icon(&app_exe)
        .with_context(|| format!("patch icon on {}", app_exe.display()))?;
    let launch_exe = finalize_install_launcher(&dest, installed.edition, &bundle.launcher)?;
    #[cfg(windows)]
    {
        let _ = tx.send(ProgressEvent::Status {
            message: i18n::REGISTERING.into(),
        });
        let icon = write_install_icon(&dest)?;
        let uninstaller = copy_uninstall_setup(&exe, &dest)?;
        fromchat_installer_common::write_uninstall_registry(
            installed.all_users,
            installed.edition,
            &version,
            &dest,
            &uninstaller,
            &icon,
        )?;
        let shortcut = format!("{}.lnk", installed.edition.shortcut_name());
        let programs = fromchat_installer_common::programs_folder(installed.all_users)?;
        fromchat_installer_common::create_shortcut(
            &programs.join(&shortcut),
            &launch_exe,
            &dest,
            installed.edition.display_name(),
            &icon,
        )?;
        if desktop_shortcut {
            let desk = fromchat_installer_common::desktop_folder()?;
            fromchat_installer_common::create_shortcut(
                &desk.join(&shortcut),
                &launch_exe,
                &dest,
                installed.edition.display_name(),
                &icon,
            )?;
        }
    }
    let _ = app_exe;
    let _ = tx.send(ProgressEvent::Progress { fraction: 1.0 });
    let _ = tx.send(ProgressEvent::Done {
        launch_path: launch_exe.to_string_lossy().into_owned(),
    });
    Ok(())
}

fn edition_tag(edition: FromChatEdition) -> &'static str {
    match edition {
        FromChatEdition::Release => "release",
        FromChatEdition::Beta => "beta",
    }
}

fn desktop_shortcut_exists(installed: &InstalledFromChat) -> bool {
    #[cfg(windows)]
    {
        fromchat_installer_common::desktop_folder()
            .ok()
            .is_some_and(|desk| {
                desk.join(format!("{}.lnk", installed.edition.shortcut_name()))
                    .is_file()
            })
    }
    #[cfg(not(windows))]
    {
        let _ = installed;
        false
    }
}

#[cfg(windows)]
fn run_elevated_install(
    session: ElevatedInstallSession,
    tx: Sender<ProgressEvent>,
) -> anyhow::Result<()> {
    fromchat_installer_common::pipe_write_line(session.pipe.handle(), &session.cmd_json)?;
    let mut got_terminal = false;
    loop {
        let resp = match fromchat_installer_common::pipe_read_line(session.pipe.handle()) {
            Ok(resp) => resp,
            Err(_error) if got_terminal => return Ok(()),
            Err(error) => return Err(error.into()),
        };
        if resp.is_empty() {
            break;
        }
        if let Ok(ev) = serde_json::from_str::<ProgressEvent>(&resp) {
            let done = matches!(
                ev,
                ProgressEvent::Done { .. }
                    | ProgressEvent::Uninstalled
                    | ProgressEvent::Error { .. }
            );
            let _ = tx.send(ev);
            if done {
                got_terminal = true;
                break;
            }
        }
    }
    Ok(())
}

fn find_app_exe(dest: &PathBuf) -> anyhow::Result<PathBuf> {
    find_jpackage_app_exe(dest).map_err(Into::into)
}

fn walkdir_files(root: &PathBuf) -> anyhow::Result<Vec<PathBuf>> {
    let mut out = Vec::new();
    fn rec(dir: &PathBuf, out: &mut Vec<PathBuf>) -> anyhow::Result<()> {
        for e in std::fs::read_dir(dir)? {
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

fn default_install_path(all_users: bool, edition: FromChatEdition) -> String {
    #[cfg(windows)]
    {
        let folder = edition.install_folder_name();
        fromchat_installer_common::default_install_dir(all_users, edition)
            .map(|p| p.to_string_lossy().into_owned())
            .unwrap_or_else(|_| {
                if all_users {
                    format!(r"C:\Program Files\{folder}")
                } else {
                    format!(
                        r"{}\Programs\{folder}",
                        std::env::var("LOCALAPPDATA").unwrap_or_else(|_| r"C:\Users\Public".into())
                    )
                }
            })
    }
    #[cfg(not(windows))]
    {
        let _ = all_users;
        format!("/tmp/{}", edition.install_folder_name())
    }
}

fn draw_welcome_brand_overlay(painter: &egui::Painter, full: Rect, logo: &TextureHandle) {
    // Match Android `WelcomeScreen` logo at 112dp; sit under the 40px title bar.
    let cx = full.center().x;
    let logo_size = theme::EXPRESSIVE_HERO_SIZE;
    let logo_y = full.top() + TITLE_BAR_HEIGHT + 28.0 + logo_size * 0.5;
    let logo_rect = Rect::from_center_size(Pos2::new(cx, logo_y), Vec2::splat(logo_size));
    painter.image(
        logo.id(),
        logo_rect,
        Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)),
        Color32::WHITE,
    );
    theme::draw_brand_wordmark(
        painter,
        Pos2::new(cx, logo_y + logo_size * 0.5 + 28.0),
        "FromChat",
        wordmark_brand(),
    );
    let tagline = painter.layout(
        i18n::TAGLINE.to_owned(),
        body_medium(13.0),
        ON_SURFACE_VARIANT,
        (full.width() - 48.0).max(120.0),
    );
    painter.galley(
        Pos2::new(
            cx - tagline.size().x * 0.5,
            logo_y + logo_size * 0.5 + 52.0,
        ),
        tagline,
        Color32::from_rgba_unmultiplied(230, 224, 233, 230),
    );
}

fn draw_title_bar(
    ui: &mut Ui,
    window: Rect,
    over_brand: bool,
    logo: &TextureHandle,
    window_focused: bool,
    window_title: &str,
    on_close: &mut dyn FnMut(),
    on_minimize: &mut dyn FnMut(),
) {
    const TITLE_BUTTON_WIDTH: f32 = 46.0;

    let rect = Rect::from_min_size(
        window.left_top(),
        Vec2::new(window.width(), TITLE_BAR_HEIGHT),
    );
    let response = ui.interact(rect, ui.id().with("title_bar"), Sense::click_and_drag());
    let title_color = if window_focused {
        ON_SURFACE_VARIANT
    } else {
        TITLE_BAR_INACTIVE
    };

    if !over_brand {
        ui.painter().rect_filled(
            rect,
            Rounding {
                nw: theme::WINDOW_CORNER_RADIUS,
                ne: theme::WINDOW_CORNER_RADIUS,
                sw: 0.0,
                se: 0.0,
            },
            SURFACE,
        );
    }

    let icon_size = 16.0;
    let icon_rect = Rect::from_center_size(
        Pos2::new(rect.left() + 16.0 + icon_size * 0.5, rect.center().y),
        Vec2::splat(icon_size),
    );
    ui.painter().image(
        logo.id(),
        icon_rect,
        Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)),
        if window_focused {
            Color32::WHITE
        } else {
            Color32::from_rgba_unmultiplied(255, 255, 255, 160)
        },
    );
    ui.painter().text(
        Pos2::new(icon_rect.right() + 8.0, rect.center().y),
        egui::Align2::LEFT_CENTER,
        window_title,
        body_medium(14.0),
        title_color,
    );

    let close_rect = Rect::from_min_size(
        Pos2::new(rect.right() - TITLE_BUTTON_WIDTH, rect.top()),
        Vec2::new(TITLE_BUTTON_WIDTH, rect.height()),
    );
    let minimize_rect = Rect::from_min_size(
        Pos2::new(close_rect.left() - TITLE_BUTTON_WIDTH, rect.top()),
        Vec2::new(TITLE_BUTTON_WIDTH, rect.height()),
    );

    if title_bar_button(ui, minimize_rect, MINIMIZE, window_focused, "minimize", false) {
        on_minimize();
    }
    if title_bar_button(ui, close_rect, CLOSE, window_focused, "close", true) {
        on_close();
    }

    if response.dragged() {
        ui.ctx().send_viewport_cmd(egui::ViewportCommand::StartDrag);
    }
}

fn title_bar_button(
    ui: &mut Ui,
    rect: Rect,
    icon: &str,
    window_focused: bool,
    id_suffix: &str,
    is_close: bool,
) -> bool {
    let response = ui.interact(rect, ui.id().with(id_suffix), Sense::click());
    let hovered = response.hovered();
    let pressed = response.is_pointer_button_down_on();
    let (hover_t, press_t) = widgets::title_bar_interaction_anim(ui, response.id, hovered, pressed);
    if is_close {
        theme::draw_title_bar_close_state(ui.painter(), rect, hover_t, press_t);
    } else {
        theme::draw_surface_state_layer_rect(ui.painter(), rect, hover_t, press_t);
    }
    let icon_color = if is_close && (hovered || pressed) {
        ON_ERROR_FILLED
    } else if hovered {
        ON_SURFACE
    } else if window_focused {
        ON_SURFACE_VARIANT
    } else {
        TITLE_BAR_INACTIVE
    };
    ui.painter().text(
        rect.center(),
        egui::Align2::CENTER_CENTER,
        icon,
        icons::font(16.0),
        icon_color,
    );
    response.clicked()
}

fn draw_logo(ui: &mut Ui, logo: &TextureHandle, size: f32) {
    let (rect, _) = ui.allocate_exact_size(Vec2::splat(size), Sense::hover());
    let logo_rect = Rect::from_center_size(rect.center(), Vec2::splat(size));
    ui.painter().image(
        logo.id(),
        logo_rect,
        Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)),
        Color32::WHITE,
    );
}

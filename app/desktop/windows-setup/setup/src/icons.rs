use eframe::egui::{self, FontFamily, FontId};

pub const CLOSE: &str = "\u{e5cd}";
pub const ROCKET_LAUNCH: &str = "\u{eb9b}";
pub const DONE_ALL: &str = "\u{e877}";
pub const OPEN_IN_NEW: &str = "\u{e89e}";
pub const RSS_FEED: &str = "\u{e0e5}";
pub const ARROW_BACK: &str = "\u{e5c4}";
pub const ARROW_FORWARD: &str = "\u{e5c8}";
pub const DOWNLOAD: &str = "\u{f090}";
pub const FOLDER: &str = "\u{e2c7}";
pub const FOLDER_OPEN: &str = "\u{e2c8}";
pub const CHECK: &str = "\u{e5ca}";
pub const UNARCHIVE: &str = "\u{e169}";
pub const DELETE: &str = "\u{e872}";
pub const SUPPORT: &str = "\u{ef73}";
/// Material Symbols `system_update_alt` (closest match to `system_upgrade_alt` in the font).
pub const SYSTEM_UPGRADE_ALT: &str = "\u{e8d7}";
pub const MINIMIZE: &str = "\u{e15b}";

pub fn font(size: f32) -> FontId {
    FontId::new(size, FontFamily::Name("material".into()))
}

pub fn label(ui: &mut egui::Ui, icon: &str, size: f32, color: egui::Color32) {
    ui.label(egui::RichText::new(icon).font(font(size)).color(color));
}

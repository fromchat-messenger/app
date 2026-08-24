use eframe::egui::{self, FontData, FontDefinitions, FontFamily, FontId};

fn load_font(bytes: &'static [u8]) -> FontData {
    FontData::from_static(bytes)
}

fn load_icon_font(bytes: &'static [u8]) -> FontData {
    let mut font = FontData::from_static(bytes);
    font.tweak.y_offset_factor = 0.0;
    font
}

pub fn install(ctx: &egui::Context) {
    let mut fonts = FontDefinitions::default();

    fonts
        .font_data
        .insert("google_sans".to_owned(), load_font(include_bytes!(
            "../../assets/fonts/google_sans_regular.ttf"
        )));
    fonts.font_data.insert(
        "google_sans_medium".to_owned(),
        load_font(include_bytes!("../../assets/fonts/google_sans_medium.ttf")),
    );
    fonts.font_data.insert(
        "google_sans_bold".to_owned(),
        load_font(include_bytes!("../../assets/fonts/google_sans_bold.ttf")),
    );
    fonts.font_data.insert(
        "montserrat_bold".to_owned(),
        load_font(include_bytes!("../../assets/fonts/montserrat_bold.ttf")),
    );
    fonts
        .font_data
        .insert("montserrat".to_owned(), load_font(include_bytes!(
            "../../assets/fonts/montserrat_latin.ttf"
        )));
    fonts.font_data.insert(
        "montserrat_cyr".to_owned(),
        load_font(include_bytes!("../../assets/fonts/montserrat_cyrillic.ttf")),
    );
    fonts.font_data.insert(
        "material_symbols".to_owned(),
        load_icon_font(include_bytes!("../../assets/MaterialSymbolsOutlined.ttf")),
    );

    // Google Sans for UI; Montserrat only for the brand wordmark. Cyrillic falls back per glyph.
    fonts.families.insert(
        FontFamily::Name("body".into()),
        vec![
            "google_sans".to_owned(),
            "montserrat_cyr".to_owned(),
            "montserrat".to_owned(),
        ],
    );
    fonts.families.insert(
        FontFamily::Name("body_medium".into()),
        vec![
            "google_sans_medium".to_owned(),
            "montserrat_cyr".to_owned(),
            "montserrat".to_owned(),
        ],
    );
    fonts.families.insert(
        FontFamily::Name("body_bold".into()),
        vec![
            "google_sans_bold".to_owned(),
            "montserrat_cyr".to_owned(),
            "montserrat".to_owned(),
        ],
    );
    fonts.families.insert(
        FontFamily::Name("wordmark".into()),
        vec![
            "montserrat_bold".to_owned(),
            "montserrat_cyr".to_owned(),
            "montserrat".to_owned(),
        ],
    );
    fonts
        .families
        .insert(FontFamily::Name("material".into()), vec!["material_symbols".to_owned()]);

    fonts
        .families
        .get_mut(&FontFamily::Proportional)
        .expect("proportional")
        .insert(0, "google_sans".to_owned());
    fonts
        .families
        .get_mut(&FontFamily::Proportional)
        .expect("proportional")
        .push("montserrat_cyr".to_owned());

    ctx.set_fonts(fonts);
    ctx.options_mut(|options| options.preload_font_glyphs = true);
}

pub fn body(size: f32) -> FontId {
    FontId::new(size, FontFamily::Name("body".into()))
}

pub fn body_medium(size: f32) -> FontId {
    FontId::new(size, FontFamily::Name("body_medium".into()))
}

pub fn body_bold(size: f32) -> FontId {
    FontId::new(size, FontFamily::Name("body_bold".into()))
}

pub fn wordmark(size: f32) -> FontId {
    FontId::new(size, FontFamily::Name("wordmark".into()))
}

/// Brand wordmark size matches `BrandTitle.kt` (29sp).
pub fn wordmark_brand() -> FontId {
    wordmark(29.0)
}

/// M3 `titleLarge` (22sp, medium).
pub fn title_large() -> FontId {
    body_medium(22.0)
}

/// M3 `labelSmall` (11sp, medium).
pub fn label_small() -> FontId {
    body_medium(11.0)
}

/// M3 `bodyLarge` (16sp) for text fields.
pub fn body_large() -> FontId {
    body(16.0)
}

/// M3 `labelLarge` (14sp / 500 / 20sp line height) — filled button label.
pub fn label_large() -> FontId {
    body_medium(14.0)
}

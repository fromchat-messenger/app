use crate::cookie4_sided_data;
use crate::cookie6_sided_data;
use eframe::egui::{
    epaint::Mesh,
    Color32, FontId, Painter, Pos2, Rect, Rounding, Shape,
};

// Material 3 darkColorScheme() — matches Android / JVM desktop when Material You is off.
pub const SURFACE: Color32 = Color32::from_rgb(0x14, 0x12, 0x18);
pub const ON_SURFACE: Color32 = Color32::from_rgb(0xE6, 0xE0, 0xE9);
pub const ON_SURFACE_VARIANT: Color32 = Color32::from_rgb(0xCA, 0xC4, 0xD0);
pub const SURFACE_CONTAINER: Color32 = Color32::from_rgb(0x21, 0x1F, 0x26);
pub const SURFACE_CONTAINER_HIGH: Color32 = Color32::from_rgb(0x2B, 0x29, 0x30);
pub const SURFACE_CONTAINER_HIGHEST: Color32 = Color32::from_rgb(0x36, 0x34, 0x3B);
pub const PRIMARY: Color32 = Color32::from_rgb(0xD0, 0xBC, 0xFF);
pub const ON_PRIMARY: Color32 = Color32::from_rgb(0x38, 0x1E, 0x72);
pub const PRIMARY_CONTAINER: Color32 = Color32::from_rgb(0x4F, 0x37, 0x8B);
pub const ON_PRIMARY_CONTAINER: Color32 = Color32::from_rgb(0xE8, 0xDD, 0xFF);
pub const OUTLINE: Color32 = Color32::from_rgb(0x93, 0x8F, 0x99);
pub const OUTLINE_VARIANT: Color32 = Color32::from_rgb(0x49, 0x45, 0x4F);
pub const ERROR: Color32 = Color32::from_rgb(0xF2, 0xB8, 0xB5);
pub const ERROR_FILLED: Color32 = Color32::from_rgb(0xB3, 0x26, 0x1E);
pub const ON_ERROR_FILLED: Color32 = Color32::from_rgb(0xFF, 0xFF, 0xFF);

/// Custom title bar label / chrome when the window is inactive.
pub const TITLE_BAR_INACTIVE: Color32 = OUTLINE;

pub const WINDOW_WIDTH: f32 = 480.0;
pub const WINDOW_HEIGHT: f32 = 588.0;
pub const TITLE_BAR_HEIGHT: f32 = 40.0;
/// Window corner radius (painted fill; Win11 also rounds via DWM).
pub const WINDOW_CORNER_RADIUS: f32 = 12.0;

/// M3 `ButtonDefaults` — small filled button (material.io/components/buttons/specs).
pub const FILLED_BUTTON_HEIGHT: f32 = 40.0;
pub const FILLED_BUTTON_MIN_WIDTH: f32 = 58.0;
pub const FILLED_BUTTON_ICON_SIZE: f32 = 18.0;
pub const FILLED_BUTTON_ICON_GAP: f32 = 8.0;
pub const FILLED_BUTTON_PAD_HORIZONTAL: f32 = 24.0;
pub const FILLED_BUTTON_PAD_START_WITH_ICON: f32 = 16.0;
pub const FILLED_BUTTON_PAD_END_WITH_ICON: f32 = 24.0;
pub const FILLED_BUTTON_PAD_END_WITH_TRAILING_ICON: f32 = 16.0;

/// M3 `IconButtonDefaults` — standard icon button (48dp touch target, 24dp icon).
pub const ICON_BUTTON_SIZE: f32 = 48.0;
pub const ICON_BUTTON_ICON_SIZE: f32 = 24.0;

/// Compact nav back control (40dp — matches small filled button height).
pub const NAV_BACK_BUTTON_SIZE: f32 = FILLED_BUTTON_HEIGHT;
pub const NAV_BACK_STATE_LAYER_SIZE: f32 = 40.0;

/// M3 text button with icon (`ButtonDefaults`: 40dp height, 18dp icon, 8dp gap).
pub const TEXT_BUTTON_HEIGHT: f32 = 40.0;
pub const TEXT_BUTTON_ICON_SIZE: f32 = 18.0;
pub const TEXT_BUTTON_ICON_GAP: f32 = 8.0;
pub const TEXT_BUTTON_PAD_START_WITH_ICON: f32 = 12.0;
pub const TEXT_BUTTON_PAD_END_WITH_ICON: f32 = 16.0;
pub const TEXT_BUTTON_MIN_WIDTH: f32 = 48.0;

/// M3 checkbox state layer (40dp circle centered on 18dp box).
pub const CHECKBOX_STATE_LAYER_SIZE: f32 = 40.0;
pub const CHECKBOX_SIZE: f32 = 18.0;

/// Choice card horizontal insets.
pub const CHOICE_CARD_PAD: f32 = 16.0;
pub const CHOICE_CARD_ICON_GAP: f32 = 12.0;
pub const CHECKBOX_STROKE: f32 = 2.0;
pub const CHECKBOX_CORNER_RADIUS: f32 = 2.0;
pub const CHECKBOX_LABEL_GAP: f32 = 16.0;
pub const CHECKBOX_MIN_TOUCH_HEIGHT: f32 = 48.0;

/// M3 filled / expressive text field (`OutlinedTextField` shape in app flows).
pub const TEXT_FIELD_HEIGHT: f32 = 56.0;
pub const TEXT_FIELD_CORNER_RADIUS: f32 = 18.0;
pub const TEXT_FIELD_PAD_HORIZONTAL: f32 = 16.0;
pub const TEXT_FIELD_TRAILING_ICON_WIDTH: f32 = 48.0;

/// M3 `LinearProgressIndicator` — 4dp track.
pub const LINEAR_PROGRESS_HEIGHT: f32 = 4.0;
pub const LINEAR_PROGRESS_CORNER_RADIUS: f32 = 2.0;

/// M3 list leading icon container (tonal).
pub const LIST_ICON_CONTAINER_SIZE: f32 = 40.0;
pub const LIST_ICON_CONTAINER_RADIUS: f32 = 12.0;
pub const LIST_ICON_SIZE: f32 = 24.0;

/// M3 `labelSmall` → field label gap above control.
pub const FIELD_LABEL_GAP: f32 = 8.0;

/// Expressive hero (`ExpressiveIconFrame.kt`).
pub const EXPRESSIVE_HERO_SIZE: f32 = 112.0;
pub const EXPRESSIVE_HERO_ICON_SIZE: f32 = 52.0;
pub const EXPRESSIVE_HERO_TITLE_GAP: f32 = 16.0;

/// `BrandTitle.kt` horizontal gradient (web / Android).
const TITLE_GRADIENT: [(f32, Color32); 6] = [
    (0.0, Color32::from_rgb(0x63, 0x66, 0xF1)),
    (0.2, Color32::from_rgb(0x3B, 0x82, 0xF6)),
    (0.4, Color32::from_rgb(0x93, 0x33, 0xEA)),
    (0.6, Color32::from_rgb(0xA8, 0x55, 0xF7)),
    (0.8, Color32::from_rgb(0xD9, 0x46, 0xEF)),
    (1.0, Color32::from_rgb(0xEC, 0x48, 0x99)),
];

pub fn draw_background(painter: &Painter, rect: Rect) {
    painter.rect_filled(rect, Rounding::same(WINDOW_CORNER_RADIUS), SURFACE);
}

pub fn lerp_color(a: Color32, b: Color32, t: f32) -> Color32 {
    let t = t.clamp(0.0, 1.0);
    Color32::from_rgba_unmultiplied(
        (a.r() as f32 + (b.r() as f32 - a.r() as f32) * t) as u8,
        (a.g() as f32 + (b.g() as f32 - a.g() as f32) * t) as u8,
        (a.b() as f32 + (b.b() as f32 - a.b() as f32) * t) as u8,
        (a.a() as f32 + (b.a() as f32 - a.a() as f32) * t) as u8,
    )
}

pub fn title_gradient_at(t: f32) -> Color32 {
    let t = t.clamp(0.0, 1.0);
    for i in 0..TITLE_GRADIENT.len() - 1 {
        let (t0, c0) = TITLE_GRADIENT[i];
        let (t1, c1) = TITLE_GRADIENT[i + 1];
        if t <= t1 {
            let f = if (t1 - t0).abs() < f32::EPSILON {
                0.0
            } else {
                (t - t0) / (t1 - t0)
            };
            return lerp_color(c0, c1, f);
        }
    }
    TITLE_GRADIENT[TITLE_GRADIENT.len() - 1].1
}

pub fn draw_brand_wordmark(painter: &Painter, center: Pos2, text: &str, font: FontId) {
    // Measure with PLACEHOLDER so painter.galley can apply per-glyph brand colors.
    let measure = painter.layout(
        text.to_owned(),
        font.clone(),
        Color32::PLACEHOLDER,
        f32::INFINITY,
    );
    let size = measure.size();
    let left = center.x - size.x * 0.5;
    let top = center.y - size.y * 0.5;
    let mut x = left;
    for ch in text.chars() {
        let piece = ch.to_string();
        let g = painter.layout(piece, font.clone(), Color32::PLACEHOLDER, f32::INFINITY);
        let gw = g.size().x;
        let mid = (x - left + gw * 0.5) / size.x.max(1.0);
        painter.galley(Pos2::new(x, top), g, title_gradient_at(mid));
        x += gw;
    }
}

/// Official `MaterialShapes.Cookie4Sided` outline (from androidx MaterialShapes.cookie4).
pub fn draw_cookie4_sided(painter: &Painter, rect: Rect, fill: Color32) {
    draw_unit_polygon(painter, rect, fill, cookie4_sided_data::COOKIE4_SIDED_UNIT);
}

/// Official `MaterialShapes.Cookie6Sided` outline (from androidx MaterialShapes.cookie6).
pub fn draw_cookie6_sided(painter: &Painter, rect: Rect, fill: Color32) {
    draw_unit_polygon(painter, rect, fill, cookie6_sided_data::COOKIE6_SIDED_UNIT);
}

/// Expressive hero: MaterialShapes.Cookie6Sided (portable flow).
pub fn draw_expressive_cookie6_hero(painter: &Painter, rect: Rect) {
    draw_cookie6_sided(painter, rect, PRIMARY_CONTAINER);
}

/// Expressive hero: symmetric circle (done flow).
pub fn draw_expressive_circle_hero(painter: &Painter, rect: Rect) {
    painter.circle_filled(rect.center(), rect.width() * 0.5, PRIMARY_CONTAINER);
}

fn draw_unit_polygon(painter: &Painter, rect: Rect, fill: Color32, unit: &[[f32; 2]]) {
    let mut points: Vec<Pos2> = unit
        .iter()
        .map(|[u, v]| {
            Pos2::new(
                rect.left() + u * rect.width(),
                rect.top() + v * rect.height(),
            )
        })
        .collect();
    simplify_collinear(&mut points, 0.35);
    triangulate_fill(painter, &points, fill);
}

fn simplify_collinear(points: &mut Vec<Pos2>, eps: f32) {
    if points.len() <= 3 {
        return;
    }
    let mut simplified = Vec::with_capacity(points.len());
    simplified.push(points[0]);
    for i in 1..points.len() - 1 {
        let a = simplified[simplified.len() - 1];
        let b = points[i];
        let c = points[i + 1];
        let ab = b - a;
        let bc = c - b;
        let cross = ab.x * bc.y - ab.y * bc.x;
        if cross.abs() > eps {
            simplified.push(b);
        }
    }
    simplified.push(*points.last().expect("non-empty"));
    *points = simplified;
}

fn triangulate_fill(painter: &Painter, points: &[Pos2], fill: Color32) {
    if points.len() < 3 {
        return;
    }
    let flat: Vec<f64> = points
        .iter()
        .flat_map(|p| [f64::from(p.x), f64::from(p.y)])
        .collect();
    let Ok(indices) = earcutr::earcut(&flat, &[], 2) else {
        return;
    };
    let mut mesh = Mesh::default();
    let base = mesh.vertices.len() as u32;
    for p in points {
        mesh.colored_vertex(*p, fill);
    }
    for tri in indices.chunks_exact(3) {
        mesh.add_triangle(
            base + tri[0] as u32,
            base + tri[1] as u32,
            base + tri[2] as u32,
        );
    }
    painter.add(Shape::mesh(mesh));
}

/// Expressive hero: single Cookie4Sided fill (`ExpressiveIconFrame` / step hero).
pub fn draw_expressive_cookie_hero(painter: &Painter, rect: Rect) {
    draw_cookie4_sided(painter, rect, PRIMARY_CONTAINER);
}

/// M3 filled error button (error role).
pub fn draw_filled_error_button(painter: &Painter, rect: Rect, hover_t: f32, press_t: f32) {
    let rounding = Rounding::same(rect.height() * 0.5);
    painter.rect_filled(rect, rounding, ERROR_FILLED);
    let alpha = state_layer_alpha(hover_t, press_t);
    if alpha > 0.001 {
        painter.rect_filled(
            rect,
            rounding,
            Color32::from_rgba_unmultiplied(
                ON_ERROR_FILLED.r(),
                ON_ERROR_FILLED.g(),
                ON_ERROR_FILLED.b(),
                (alpha * 255.0) as u8,
            ),
        );
    }
}

/// M3 filled button: solid primary + on-primary state layer (8% hover / 12% pressed).
pub fn draw_filled_button(painter: &Painter, rect: Rect, hover_t: f32, press_t: f32) {
    let rounding = Rounding::same(rect.height() * 0.5);
    painter.rect_filled(rect, rounding, PRIMARY);
    let alpha = state_layer_alpha(hover_t, press_t);
    if alpha > 0.001 {
        painter.rect_filled(
            rect,
            rounding,
            Color32::from_rgba_unmultiplied(
                ON_PRIMARY.r(),
                ON_PRIMARY.g(),
                ON_PRIMARY.b(),
                (alpha * 255.0) as u8,
            ),
        );
    }
}

/// M3 surface-tinted state layer (checkbox / nav icon hover).
pub fn draw_surface_state_layer(painter: &Painter, center: Pos2, radius: f32, hover_t: f32, press_t: f32) {
    let alpha = state_layer_alpha(hover_t, press_t);
    if alpha > 0.001 {
        painter.circle_filled(
            center,
            radius,
            Color32::from_rgba_unmultiplied(
                ON_SURFACE.r(),
                ON_SURFACE.g(),
                ON_SURFACE.b(),
                (alpha * 255.0) as u8,
            ),
        );
    }
}

/// Square state layer for custom title-bar controls.
pub fn draw_surface_state_layer_rect(painter: &Painter, rect: Rect, hover_t: f32, press_t: f32) {
    let alpha = state_layer_alpha(hover_t, press_t);
    if alpha > 0.001 {
        painter.rect_filled(
            rect,
            Rounding::ZERO,
            Color32::from_rgba_unmultiplied(
                ON_SURFACE.r(),
                ON_SURFACE.g(),
                ON_SURFACE.b(),
                (alpha * 255.0) as u8,
            ),
        );
    }
}

/// Windows-style close button hover (error container fill).
pub fn draw_title_bar_close_state(painter: &Painter, rect: Rect, hover_t: f32, press_t: f32) {
    let t = hover_t.max(press_t);
    if t <= 0.001 {
        return;
    }
    let color = if press_t > hover_t {
        Color32::from_rgb(0x8C, 0x1D, 0x18)
    } else {
        ERROR_FILLED
    };
    painter.rect_filled(
        rect,
        Rounding::ZERO,
        Color32::from_rgba_unmultiplied(
            color.r(),
            color.g(),
            color.b(),
            (t * 255.0) as u8,
        ),
    );
}

/// M3 text button: primary state layer on surface (8% hover / 12% pressed).
pub fn draw_text_button_state(painter: &Painter, rect: Rect, hover_t: f32, press_t: f32) {
    let alpha = state_layer_alpha(hover_t, press_t);
    if alpha > 0.001 {
        painter.rect_filled(
            rect,
            Rounding::same(rect.height() * 0.5),
            Color32::from_rgba_unmultiplied(
                PRIMARY.r(),
                PRIMARY.g(),
                PRIMARY.b(),
                (alpha * 255.0) as u8,
            ),
        );
    }
}

fn state_layer_alpha(hover_t: f32, press_t: f32) -> f32 {
    press_t * 0.12 + hover_t * 0.08 * (1.0 - press_t)
}

pub fn draw_tonal_icon_container(painter: &Painter, rect: Rect) {
    painter.rect_filled(rect, Rounding::same(LIST_ICON_CONTAINER_RADIUS), PRIMARY_CONTAINER);
}

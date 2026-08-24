use crate::anim::animate_towards;
use crate::fonts::{body, body_medium, label_small, title_large, body_large, label_large};
use crate::icons::{self, ARROW_BACK, ARROW_FORWARD, CHECK, CLOSE, DELETE, DOWNLOAD, FOLDER_OPEN, ROCKET_LAUNCH, UNARCHIVE};
use crate::theme::{
    self, CHECKBOX_CORNER_RADIUS, CHECKBOX_LABEL_GAP, CHECKBOX_MIN_TOUCH_HEIGHT, CHECKBOX_SIZE,
    CHECKBOX_STATE_LAYER_SIZE, CHECKBOX_STROKE, CHOICE_CARD_ICON_GAP, CHOICE_CARD_PAD, ERROR,
    EXPRESSIVE_HERO_ICON_SIZE, EXPRESSIVE_HERO_SIZE, EXPRESSIVE_HERO_TITLE_GAP, FIELD_LABEL_GAP,
    FILLED_BUTTON_HEIGHT, FILLED_BUTTON_ICON_GAP, FILLED_BUTTON_ICON_SIZE, FILLED_BUTTON_MIN_WIDTH,
    FILLED_BUTTON_PAD_END_WITH_ICON, FILLED_BUTTON_PAD_END_WITH_TRAILING_ICON,
    FILLED_BUTTON_PAD_HORIZONTAL, FILLED_BUTTON_PAD_START_WITH_ICON, ICON_BUTTON_ICON_SIZE,
    ICON_BUTTON_SIZE, LIST_ICON_CONTAINER_SIZE, LIST_ICON_SIZE,
    NAV_BACK_BUTTON_SIZE, NAV_BACK_STATE_LAYER_SIZE, ON_ERROR_FILLED, ON_PRIMARY, ON_PRIMARY_CONTAINER,
    ON_SURFACE, ON_SURFACE_VARIANT, OUTLINE, PRIMARY, SURFACE_CONTAINER, SURFACE_CONTAINER_HIGH,
    TEXT_BUTTON_HEIGHT, TEXT_BUTTON_ICON_GAP, TEXT_BUTTON_ICON_SIZE, TEXT_BUTTON_MIN_WIDTH,
    TEXT_BUTTON_PAD_END_WITH_ICON, TEXT_BUTTON_PAD_START_WITH_ICON, TEXT_FIELD_CORNER_RADIUS,
    TEXT_FIELD_HEIGHT, TEXT_FIELD_PAD_HORIZONTAL, TEXT_FIELD_TRAILING_ICON_WIDTH,
};
use eframe::egui::{self, Color32, Id, Pos2, Rect, Rounding, Sense, Stroke, TextureHandle, Ui, Vec2};

pub const H_PADDING: f32 = 24.0;
/// Matches [H_PADDING] so bottom / side insets stay equal.
pub const BOTTOM_BAR_PAD: f32 = H_PADDING;
pub const BUTTON_HEIGHT: f32 = FILLED_BUTTON_HEIGHT;
pub const BOTTOM_BAR_CONTROL_HEIGHT: f32 = FILLED_BUTTON_HEIGHT.max(NAV_BACK_BUTTON_SIZE);
pub const BOTTOM_BAR_HEIGHT: f32 = BOTTOM_BAR_PAD + BOTTOM_BAR_CONTROL_HEIGHT + BOTTOM_BAR_PAD;

fn interaction_anim(ui: &Ui, id: Id, hovered: bool, pressed: bool) -> (f32, f32) {
    let dt = ui.input(|i| i.stable_dt);
    let hover_id = id.with("hover");
    let press_id = id.with("press");

    let (hover_t, press_t) = ui.ctx().data_mut(|d| {
        let hover_t = *d.get_temp_mut_or_insert_with(hover_id, || 0.0);
        let press_t = *d.get_temp_mut_or_insert_with(press_id, || 0.0);
        (hover_t, press_t)
    });

    let new_hover = animate_towards(hover_t, if hovered { 1.0 } else { 0.0 }, dt, 14.0);
    let new_press = animate_towards(press_t, if pressed { 1.0 } else { 0.0 }, dt, 22.0);

    if (new_hover - hover_t).abs() > 0.001 || (new_press - press_t).abs() > 0.001 {
        ui.ctx().data_mut(|d| {
            *d.get_temp_mut_or_insert_with(hover_id, || 0.0) = new_hover;
            *d.get_temp_mut_or_insert_with(press_id, || 0.0) = new_press;
        });
        ui.ctx().request_repaint();
    }

    (new_hover, new_press)
}

pub fn title_bar_interaction_anim(ui: &Ui, id: Id, hovered: bool, pressed: bool) -> (f32, f32) {
    interaction_anim(ui, id, hovered, pressed)
}

fn draw_choice_card_icon(
    ui: &mut Ui,
    icon_rect: Rect,
    icon_texture: Option<&TextureHandle>,
    fallback_icon: &str,
) {
    if let Some(texture) = icon_texture {
        let inset = icon_rect.width() * 0.1;
        let image_rect = icon_rect.shrink(inset);
        ui.painter().image(
            texture.id(),
            image_rect,
            Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)),
            Color32::WHITE,
        );
    } else {
        theme::draw_tonal_icon_container(ui.painter(), icon_rect);
        ui.painter().text(
            icon_rect.center(),
            egui::Align2::CENTER_CENTER,
            fallback_icon,
            icons::font(LIST_ICON_SIZE),
            PRIMARY,
        );
    }
}

#[derive(Clone, Copy)]
pub enum PrimaryAction {
    Install,
    Extract,
    Uninstall,
    Upgrade,
    Next,
    Close,
}

pub enum ExpressiveHeroShape {
    Cookie4Sided,
    Cookie6Sided,
    Circle,
}

#[derive(Clone, Copy)]
enum FilledButtonIcon {
    Leading(&'static str),
    Trailing(&'static str),
}

pub fn bottom_bar(
    ui: &mut Ui,
    hover_enabled: bool,
    show_back: bool,
    on_back: impl FnOnce(),
    on_next: impl FnOnce(),
    primary: PrimaryAction,
) {
    let back_clicked = show_back && nav_back_button(ui, hover_enabled);
    let mut next_clicked = false;
    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
        ui.set_width(ui.available_width());
        next_clicked = match primary {
            PrimaryAction::Install => filled_install_button(ui, hover_enabled),
            PrimaryAction::Extract => filled_extract_button(ui, hover_enabled),
            PrimaryAction::Uninstall => filled_uninstall_button(ui, hover_enabled),
            PrimaryAction::Upgrade => filled_upgrade_button(ui, hover_enabled),
            PrimaryAction::Next => filled_next_button(ui, hover_enabled),
            PrimaryAction::Close => filled_button_label(ui, crate::i18n::CLOSE, hover_enabled),
        };
    });
    if back_clicked {
        on_back();
    }
    if next_clicked {
        on_next();
    }
}

pub fn filled_button_label(ui: &mut Ui, label: &str, hover_enabled: bool) -> bool {
    filled_button(ui, None, label, hover_enabled)
}

pub fn filled_install_button(ui: &mut Ui, hover_enabled: bool) -> bool {
    filled_button(
        ui,
        Some(FilledButtonIcon::Leading(DOWNLOAD)),
        crate::i18n::INSTALL_ACTION,
        hover_enabled,
    )
}

pub fn filled_extract_button(ui: &mut Ui, hover_enabled: bool) -> bool {
    filled_button(
        ui,
        Some(FilledButtonIcon::Leading(UNARCHIVE)),
        crate::i18n::EXTRACT_ACTION,
        hover_enabled,
    )
}

pub fn filled_uninstall_button(ui: &mut Ui, hover_enabled: bool) -> bool {
    filled_error_button(
        ui,
        Some(FilledButtonIcon::Leading(DELETE)),
        crate::i18n::UNINSTALL_ACTION,
        hover_enabled,
    )
}

pub fn filled_upgrade_button(ui: &mut Ui, hover_enabled: bool) -> bool {
    filled_button(
        ui,
        Some(FilledButtonIcon::Leading(DOWNLOAD)),
        crate::i18n::UPGRADE_ACTION,
        hover_enabled,
    )
}

pub fn filled_next_button(ui: &mut Ui, hover_enabled: bool) -> bool {
    filled_button(
        ui,
        Some(FilledButtonIcon::Trailing(ARROW_FORWARD)),
        crate::i18n::NEXT,
        hover_enabled,
    )
}

/// M3 filled error button (`ButtonDefaults`: 40dp height, labelLarge, optional 18dp icon).
fn filled_error_button(
    ui: &mut Ui,
    icon: Option<FilledButtonIcon>,
    label: &str,
    hover_enabled: bool,
) -> bool {
    let label_galley = ui.painter().layout(
        label.to_owned(),
        label_large(),
        ON_ERROR_FILLED,
        f32::INFINITY,
    );

    let label_width = label_galley.size().x;
    let width = match icon {
        Some(FilledButtonIcon::Leading(_)) => (FILLED_BUTTON_PAD_START_WITH_ICON
            + FILLED_BUTTON_ICON_SIZE
            + FILLED_BUTTON_ICON_GAP
            + label_galley.size().x
            + FILLED_BUTTON_PAD_END_WITH_ICON)
            .max(FILLED_BUTTON_MIN_WIDTH),
        Some(FilledButtonIcon::Trailing(_)) => (FILLED_BUTTON_PAD_HORIZONTAL
            + label_width
            + FILLED_BUTTON_ICON_GAP
            + FILLED_BUTTON_ICON_SIZE
            + FILLED_BUTTON_PAD_END_WITH_TRAILING_ICON)
            .max(FILLED_BUTTON_MIN_WIDTH),
        None => (FILLED_BUTTON_PAD_HORIZONTAL * 2.0 + label_width).max(FILLED_BUTTON_MIN_WIDTH),
    };

    let desired = Vec2::new(width, FILLED_BUTTON_HEIGHT);
    let (rect, response) = ui.allocate_exact_size(desired, Sense::click());
    let hovered = hover_enabled && response.hovered();
    let pressed = hover_enabled && response.is_pointer_button_down_on();
    let (hover_t, press_t) = interaction_anim(ui, response.id, hovered, pressed);
    theme::draw_filled_error_button(ui.painter(), rect, hover_t, press_t);

    let cy = rect.center().y;
    match icon {
        Some(FilledButtonIcon::Leading(icon)) => {
            let mut x = rect.left() + FILLED_BUTTON_PAD_START_WITH_ICON;
            ui.painter().text(
                Pos2::new(x + FILLED_BUTTON_ICON_SIZE * 0.5, cy),
                egui::Align2::CENTER_CENTER,
                icon,
                icons::font(FILLED_BUTTON_ICON_SIZE),
                ON_ERROR_FILLED,
            );
            x += FILLED_BUTTON_ICON_SIZE + FILLED_BUTTON_ICON_GAP;
            ui.painter().galley(
                Pos2::new(x, cy - label_galley.size().y * 0.5),
                label_galley,
                ON_ERROR_FILLED,
            );
        }
        Some(FilledButtonIcon::Trailing(icon)) => {
            let mut x = rect.left() + FILLED_BUTTON_PAD_HORIZONTAL;
            ui.painter().galley(
                Pos2::new(x, cy - label_galley.size().y * 0.5),
                label_galley,
                ON_ERROR_FILLED,
            );
            x += label_width + FILLED_BUTTON_ICON_GAP;
            ui.painter().text(
                Pos2::new(x + FILLED_BUTTON_ICON_SIZE * 0.5, cy),
                egui::Align2::CENTER_CENTER,
                icon,
                icons::font(FILLED_BUTTON_ICON_SIZE),
                ON_ERROR_FILLED,
            );
        }
        None => {
            ui.painter().galley(
                Pos2::new(
                    rect.center().x - label_galley.size().x * 0.5,
                    cy - label_galley.size().y * 0.5,
                ),
                label_galley,
                ON_ERROR_FILLED,
            );
        }
    }

    response.clicked()
}

/// M3 filled button (`ButtonDefaults`: 40dp height, labelLarge, optional 18dp icon).
fn filled_button(
    ui: &mut Ui,
    icon: Option<FilledButtonIcon>,
    label: &str,
    hover_enabled: bool,
) -> bool {
    let label_galley = ui.painter().layout(
        label.to_owned(),
        label_large(),
        ON_PRIMARY,
        f32::INFINITY,
    );

    let label_width = label_galley.size().x;
    let width = match icon {
        Some(FilledButtonIcon::Leading(_)) => (FILLED_BUTTON_PAD_START_WITH_ICON
            + FILLED_BUTTON_ICON_SIZE
            + FILLED_BUTTON_ICON_GAP
            + label_galley.size().x
            + FILLED_BUTTON_PAD_END_WITH_ICON)
            .max(FILLED_BUTTON_MIN_WIDTH),
        Some(FilledButtonIcon::Trailing(_)) => (FILLED_BUTTON_PAD_HORIZONTAL
            + label_width
            + FILLED_BUTTON_ICON_GAP
            + FILLED_BUTTON_ICON_SIZE
            + FILLED_BUTTON_PAD_END_WITH_TRAILING_ICON)
            .max(FILLED_BUTTON_MIN_WIDTH),
        None => (FILLED_BUTTON_PAD_HORIZONTAL * 2.0 + label_width).max(FILLED_BUTTON_MIN_WIDTH),
    };

    let desired = Vec2::new(width, FILLED_BUTTON_HEIGHT);
    let (rect, response) = ui.allocate_exact_size(desired, Sense::click());
    let hovered = hover_enabled && response.hovered();
    let pressed = hover_enabled && response.is_pointer_button_down_on();
    let (hover_t, press_t) = interaction_anim(ui, response.id, hovered, pressed);
    theme::draw_filled_button(ui.painter(), rect, hover_t, press_t);

    let cy = rect.center().y;
    match icon {
        Some(FilledButtonIcon::Leading(icon)) => {
            let mut x = rect.left() + FILLED_BUTTON_PAD_START_WITH_ICON;
            ui.painter().text(
                Pos2::new(x + FILLED_BUTTON_ICON_SIZE * 0.5, cy),
                egui::Align2::CENTER_CENTER,
                icon,
                icons::font(FILLED_BUTTON_ICON_SIZE),
                ON_PRIMARY,
            );
            x += FILLED_BUTTON_ICON_SIZE + FILLED_BUTTON_ICON_GAP;
            ui.painter().galley(
                Pos2::new(x, cy - label_galley.size().y * 0.5),
                label_galley,
                ON_PRIMARY,
            );
        }
        Some(FilledButtonIcon::Trailing(icon)) => {
            let mut x = rect.left() + FILLED_BUTTON_PAD_HORIZONTAL;
            ui.painter().galley(
                Pos2::new(x, cy - label_galley.size().y * 0.5),
                label_galley,
                ON_PRIMARY,
            );
            x += label_width + FILLED_BUTTON_ICON_GAP;
            ui.painter().text(
                Pos2::new(x + FILLED_BUTTON_ICON_SIZE * 0.5, cy),
                egui::Align2::CENTER_CENTER,
                icon,
                icons::font(FILLED_BUTTON_ICON_SIZE),
                ON_PRIMARY,
            );
        }
        None => {
            ui.painter().galley(
                Pos2::new(
                    rect.center().x - label_galley.size().x * 0.5,
                    cy - label_galley.size().y * 0.5,
                ),
                label_galley,
                ON_PRIMARY,
            );
        }
    }

    response.clicked()
}

pub fn filled_launch_button(ui: &mut Ui, hover_enabled: bool) -> bool {
    filled_button(
        ui,
        Some(FilledButtonIcon::Leading(ROCKET_LAUNCH)),
        crate::i18n::LAUNCH_ACTION,
        hover_enabled,
    )
}

pub fn done_bottom_bar(ui: &mut Ui, hover_enabled: bool) -> (bool, bool) {
    let mut launch = false;
    let mut close = false;
    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
        ui.set_width(ui.available_width());
        launch = filled_launch_button(ui, hover_enabled);
        ui.add_space(8.0);
        close = text_button_icon(ui, CLOSE, crate::i18n::CLOSE, hover_enabled);
    });
    (launch, close)
}

/// Compact back control for bottom bars (40dp hit target, 24dp icon).
pub fn nav_back_button(ui: &mut Ui, hover_enabled: bool) -> bool {
    let size = NAV_BACK_BUTTON_SIZE;
    let (rect, response) = ui.allocate_exact_size(Vec2::splat(size), Sense::click());
    let hovered = hover_enabled && response.hovered();
    let pressed = hover_enabled && response.is_pointer_button_down_on();
    let (hover_t, press_t) = interaction_anim(ui, response.id, hovered, pressed);
    theme::draw_surface_state_layer(
        ui.painter(),
        rect.center(),
        NAV_BACK_STATE_LAYER_SIZE * 0.5,
        hover_t,
        press_t,
    );
    ui.painter().text(
        rect.center(),
        egui::Align2::CENTER_CENTER,
        ARROW_BACK,
        icons::font(ICON_BUTTON_ICON_SIZE),
        ON_SURFACE,
    );
    response.clicked()
}

/// M3 text button with leading icon (`ButtonDefaults` text button + icon).
pub fn text_button_icon(ui: &mut Ui, icon: &str, label: &str, hover_enabled: bool) -> bool {
    let label_galley = ui.painter().layout(
        label.to_owned(),
        label_large(),
        PRIMARY,
        f32::INFINITY,
    );
    let width = (TEXT_BUTTON_PAD_START_WITH_ICON
        + TEXT_BUTTON_ICON_SIZE
        + TEXT_BUTTON_ICON_GAP
        + label_galley.size().x
        + TEXT_BUTTON_PAD_END_WITH_ICON)
        .max(TEXT_BUTTON_MIN_WIDTH);
    let (rect, response) =
        ui.allocate_exact_size(Vec2::new(width, TEXT_BUTTON_HEIGHT), Sense::click());
    let hovered = hover_enabled && response.hovered();
    let pressed = hover_enabled && response.is_pointer_button_down_on();
    let (hover_t, press_t) = interaction_anim(ui, response.id, hovered, pressed);
    theme::draw_text_button_state(ui.painter(), rect, hover_t, press_t);

    let cy = rect.center().y;
    let mut x = rect.left() + TEXT_BUTTON_PAD_START_WITH_ICON;
    ui.painter().text(
        Pos2::new(x + TEXT_BUTTON_ICON_SIZE * 0.5, cy),
        egui::Align2::CENTER_CENTER,
        icon,
        icons::font(TEXT_BUTTON_ICON_SIZE),
        PRIMARY,
    );
    x += TEXT_BUTTON_ICON_SIZE + TEXT_BUTTON_ICON_GAP;
    ui.painter().galley(
        Pos2::new(x, cy - label_galley.size().y * 0.5),
        label_galley,
        PRIMARY,
    );
    response.clicked()
}

pub fn icon_button(ui: &mut Ui, icon: &str, hover_enabled: bool, icon_color: Color32) -> bool {
    let size = ICON_BUTTON_SIZE;
    let (rect, response) = ui.allocate_exact_size(Vec2::splat(size), Sense::click());
    let hovered = hover_enabled && response.hovered();
    let pressed = hover_enabled && response.is_pointer_button_down_on();
    let (hover_t, press_t) = interaction_anim(ui, response.id, hovered, pressed);
    theme::draw_surface_state_layer(
        ui.painter(),
        rect.center(),
        CHECKBOX_STATE_LAYER_SIZE * 0.5,
        hover_t,
        press_t,
    );
    ui.painter().text(
        rect.center(),
        egui::Align2::CENTER_CENTER,
        icon,
        icons::font(ICON_BUTTON_ICON_SIZE),
        icon_color,
    );
    response.clicked()
}

pub fn text_button_label(ui: &mut Ui, label: &str, hover_enabled: bool) -> bool {
    let label_galley = ui.painter().layout(
        label.to_owned(),
        label_large(),
        PRIMARY,
        f32::INFINITY,
    );
    let width = (label_galley.size().x + 24.0).max(48.0);
    let (rect, response) = ui.allocate_exact_size(Vec2::new(width, 48.0), Sense::click());
    let hovered = hover_enabled && response.hovered();
    let pressed = hover_enabled && response.is_pointer_button_down_on();
    let (hover_t, press_t) = interaction_anim(ui, response.id, hovered, pressed);
    theme::draw_text_button_state(ui.painter(), rect.expand(4.0), hover_t, press_t);
    ui.painter().galley(
        Pos2::new(
            rect.center().x - label_galley.size().x * 0.5,
            rect.center().y - label_galley.size().y * 0.5,
        ),
        label_galley,
        PRIMARY,
    );
    response.clicked()
}

/// Checkbox: M3 18dp box, 40dp state layer on box, animated check fade.
pub fn checkbox(ui: &mut Ui, checked: &mut bool, label: &str, hover_enabled: bool) -> bool {
    let wrap_w = (ui.available_width() - CHECKBOX_SIZE - CHECKBOX_LABEL_GAP).max(80.0);
    let label_galley = ui.painter().layout(
        label.to_owned(),
        body_large(),
        ON_SURFACE,
        wrap_w,
    );
    let row_h = label_galley
        .size()
        .y
        .max(CHECKBOX_SIZE)
        .max(CHECKBOX_MIN_TOUCH_HEIGHT);
    let (rect, response) =
        ui.allocate_exact_size(Vec2::new(ui.available_width(), row_h), Sense::click());
    let box_hovered = hover_enabled && {
        let box_top = rect.top() + (row_h - CHECKBOX_SIZE) * 0.5;
        let box_rect = Rect::from_min_size(
            Pos2::new(rect.left() + 2.0, box_top),
            Vec2::splat(CHECKBOX_SIZE),
        );
        let layer = Rect::from_center_size(
            box_rect.center(),
            Vec2::splat(CHECKBOX_STATE_LAYER_SIZE),
        );
        ui.ctx().input(|i| {
            i.pointer
                .hover_pos()
                .is_some_and(|p| layer.contains(p) || box_rect.contains(p))
        }) || response.hovered()
    };
    let pressed = hover_enabled && response.is_pointer_button_down_on();
    let (hover_t, press_t) = interaction_anim(ui, response.id, box_hovered, pressed);

    if response.clicked() {
        *checked = !*checked;
    }

    let dt = ui.input(|i| i.stable_dt);
    let checked_id = response.id.with("checked_t");
    let checked_t = {
        let (t, changed) = ui.ctx().data_mut(|d| {
            let t = *d.get_temp_mut_or_insert_with(checked_id, || {
                if *checked { 1.0 } else { 0.0 }
            });
            let target = if *checked { 1.0 } else { 0.0 };
            let next = animate_towards(t, target, dt, 18.0);
            let changed = (next - t).abs() > 0.001;
            if changed {
                *d.get_temp_mut_or_insert_with(checked_id, || t) = next;
            }
            (next, changed)
        });
        if changed {
            ui.ctx().request_repaint();
        }
        t
    };

    let box_top = rect.top() + (row_h - CHECKBOX_SIZE) * 0.5;
    let label_top = rect.top() + (row_h - label_galley.size().y) * 0.5;
    let box_rect = Rect::from_min_size(
        Pos2::new(rect.left() + 2.0, box_top),
        Vec2::splat(CHECKBOX_SIZE),
    );

    theme::draw_surface_state_layer(
        ui.painter(),
        box_rect.center(),
        CHECKBOX_STATE_LAYER_SIZE * 0.5,
        hover_t,
        press_t,
    );

    if checked_t > 0.001 {
        let fill = theme::lerp_color(
            Color32::TRANSPARENT,
            PRIMARY,
            checked_t,
        );
        ui.painter()
            .rect_filled(box_rect, Rounding::same(CHECKBOX_CORNER_RADIUS), fill);
        ui.painter().text(
            box_rect.center(),
            egui::Align2::CENTER_CENTER,
            CHECK,
            icons::font(14.0),
            Color32::from_rgba_unmultiplied(
                ON_PRIMARY.r(),
                ON_PRIMARY.g(),
                ON_PRIMARY.b(),
                (checked_t * 255.0) as u8,
            ),
        );
    }
    if checked_t < 0.999 {
        let stroke_alpha = ((1.0 - checked_t) * 255.0) as u8;
        ui.painter().rect_stroke(
            box_rect,
            Rounding::same(CHECKBOX_CORNER_RADIUS),
            egui::Stroke::new(
                CHECKBOX_STROKE,
                Color32::from_rgba_unmultiplied(OUTLINE.r(), OUTLINE.g(), OUTLINE.b(), stroke_alpha),
            ),
        );
    }

    ui.painter().galley(
        Pos2::new(box_rect.right() + CHECKBOX_LABEL_GAP, label_top),
        label_galley,
        ON_SURFACE,
    );
    response.clicked()
}

/// Rounded surface-container path field with trailing folder icon.
pub fn path_field(ui: &mut Ui, value: &mut String, enabled: bool, field_id: Id) -> bool {
    let w = ui.available_width();
    let h = TEXT_FIELD_HEIGHT;
    let (row, _row_resp) = ui.allocate_exact_size(Vec2::new(w, h), Sense::hover());
    let rounding = Rounding::same(TEXT_FIELD_CORNER_RADIUS);

    ui.painter()
        .rect_filled(row, rounding, SURFACE_CONTAINER);

    let icon_w = TEXT_FIELD_TRAILING_ICON_WIDTH;
    let pad_x = TEXT_FIELD_PAD_HORIZONTAL;
    let text_rect = Rect::from_min_max(
        row.left_top() + Vec2::new(pad_x, 0.0),
        Pos2::new(row.right() - icon_w, row.bottom()),
    );
    let icon_rect = Rect::from_min_max(
        Pos2::new(row.right() - icon_w, row.top()),
        row.right_bottom(),
    );

    let edit_id = field_id.with("edit");
    let dt = ui.input(|i| i.stable_dt);
    let focused = ui.ctx().memory(|m| m.has_focus(edit_id));

    let (from_text, to_text, fade, animating) = ui.ctx().data_mut(|d| {
        let shown_id = field_id.with("shown");
        let from_id = field_id.with("from");
        let to_id = field_id.with("to");
        let fade_id = field_id.with("fade");

        let mut shown = d
            .get_temp::<String>(shown_id)
            .unwrap_or_else(|| value.clone());
        let mut fade = *d.get_temp_mut_or_insert_with(fade_id, || 1.0);
        let mut from_text = d
            .get_temp::<String>(from_id)
            .unwrap_or_else(|| shown.clone());
        let mut to_text = d
            .get_temp::<String>(to_id)
            .unwrap_or_else(|| value.clone());

        if focused {
            shown = value.clone();
            fade = 1.0;
            d.insert_temp(shown_id, shown);
            d.insert_temp(fade_id, fade);
        } else if *value != shown {
            if fade >= 0.99 {
                from_text = shown.clone();
                to_text = value.clone();
                fade = 0.0;
                d.insert_temp(from_id, from_text.clone());
                d.insert_temp(to_id, to_text.clone());
            }
            fade = (fade + dt * 8.0).min(1.0);
            if fade >= 0.99 {
                shown = value.clone();
                d.insert_temp(shown_id, shown);
            }
            *d.get_temp_mut_or_insert_with(fade_id, || fade) = fade;
        }

        let animating = !focused && fade < 0.99 && from_text != to_text;
        (from_text, to_text, fade, animating)
    });
    if animating {
        ui.ctx().request_repaint();
    }

    let paint_path_line = |painter: &egui::Painter, text: &str, alpha: f32| {
        if alpha <= 0.01 || text.is_empty() {
            return;
        }
        let galley = painter.layout(
            text.to_owned(),
            body_large(),
            Color32::from_rgba_unmultiplied(
                ON_SURFACE.r(),
                ON_SURFACE.g(),
                ON_SURFACE.b(),
                (alpha * 255.0) as u8,
            ),
            f32::INFINITY,
        );
        let mut x = text_rect.left();
        if galley.size().x > text_rect.width() {
            x = text_rect.right() - galley.size().x;
        }
        let y = text_rect.center().y - galley.size().y * 0.5;
        painter
            .with_clip_rect(text_rect)
            .galley(Pos2::new(x, y), galley, Color32::WHITE);
    };

    if animating {
        paint_path_line(ui.painter(), &from_text, 1.0 - fade);
        paint_path_line(ui.painter(), &to_text, fade);
    }

    ui.allocate_ui_at_rect(text_rect, |ui| {
        ui.set_clip_rect(text_rect);
        ui.with_layout(
            egui::Layout::left_to_right(egui::Align::Center),
            |ui| {
                ui.set_height(text_rect.height());
                ui.set_width(text_rect.width());
                let edit = egui::TextEdit::singleline(value)
                    .id(edit_id)
                    .frame(false)
                    .margin(egui::Margin::symmetric(0.0, 0.0))
                    .clip_text(true)
                    .desired_width(f32::INFINITY)
                    .text_color(if animating {
                        Color32::TRANSPARENT
                    } else {
                        ON_SURFACE
                    })
                    .font(body_large());
                ui.add_enabled(enabled, edit);
            },
        );
    });

    let mut browse = false;
    ui.allocate_ui_at_rect(icon_rect, |ui| {
        ui.with_layout(
            egui::Layout::centered_and_justified(egui::Direction::LeftToRight),
            |ui| {
                browse = icon_button(ui, FOLDER_OPEN, enabled, ON_SURFACE);
            },
        );
    });
    browse
}

/// Centered expressive header with Material expressive polygon + title.
pub fn expressive_section_header(
    ui: &mut Ui,
    title: &str,
    icon: &str,
    shape: ExpressiveHeroShape,
) {
    ui.add_space(4.0);
    ui.vertical_centered(|ui| {
        let hero = EXPRESSIVE_HERO_SIZE;
        let (rect, _) = ui.allocate_exact_size(Vec2::splat(hero), Sense::hover());
        match shape {
            ExpressiveHeroShape::Cookie4Sided => {
                theme::draw_expressive_cookie_hero(ui.painter(), rect);
            }
            ExpressiveHeroShape::Cookie6Sided => {
                theme::draw_expressive_cookie6_hero(ui.painter(), rect);
            }
            ExpressiveHeroShape::Circle => {
                theme::draw_expressive_circle_hero(ui.painter(), rect);
            }
        }
        ui.painter().text(
            rect.center(),
            egui::Align2::CENTER_CENTER,
            icon,
            icons::font(EXPRESSIVE_HERO_ICON_SIZE),
            ON_PRIMARY_CONTAINER,
        );
        ui.add_space(EXPRESSIVE_HERO_TITLE_GAP);
        ui.label(
            egui::RichText::new(title)
                .font(title_large())
                .color(ON_SURFACE),
        );
    });
    ui.add_space(16.0);
}

/// Done-screen list row: leading icon (no container) + wrapped body.
pub fn done_list_row(ui: &mut Ui, icon: &str, content: impl FnOnce(&mut Ui)) {
    let row_w = ui.available_width();
    let icon_col = LIST_ICON_SIZE;
    let gap = CHOICE_CARD_ICON_GAP;
    let text_w = (row_w - icon_col - gap).max(1.0);

    let row = ui.horizontal_top(|ui| {
        ui.set_width(row_w);
        ui.allocate_exact_size(Vec2::new(icon_col + gap, 0.0), Sense::hover());
        ui.vertical(|ui| {
            ui.set_width(text_w);
            content(ui);
        });
    });

    let row_rect = row.response.rect;
    ui.painter().text(
        Pos2::new(row_rect.left() + icon_col * 0.5, row_rect.center().y),
        egui::Align2::CENTER_CENTER,
        icon,
        icons::font(LIST_ICON_SIZE),
        ON_SURFACE_VARIANT,
    );
}

/// Inline text link sized tightly to its label (no extra hit padding).
pub fn inline_link(ui: &mut Ui, label: &str, hover_enabled: bool) -> bool {
    let galley = ui.painter().layout(
        label.to_owned(),
        body(14.0),
        PRIMARY,
        f32::INFINITY,
    );
    let size = galley.size();
    let (rect, response) = ui.allocate_exact_size(size, Sense::click());
    let hovered = hover_enabled && response.hovered();
    ui.painter().galley(rect.min, galley, PRIMARY);
    if hovered {
        let y = rect.bottom() - 1.0;
        ui.painter().hline(
            rect.left()..=rect.right(),
            y,
            Stroke::new(1.0_f32, PRIMARY),
        );
        ui.ctx().set_cursor_icon(egui::CursorIcon::PointingHand);
    }
    response.clicked()
}

pub fn section_title(ui: &mut Ui, title: &str) {
    ui.add_space(4.0);
    ui.label(
        egui::RichText::new(title)
            .font(title_large())
            .color(ON_SURFACE),
    );
    ui.add_space(12.0);
}

pub fn field_label(ui: &mut Ui, label: &str) {
    ui.label(
        egui::RichText::new(label)
            .font(label_small())
            .color(ON_SURFACE_VARIANT),
    );
    ui.add_space(FIELD_LABEL_GAP);
}

fn choice_card_text_left() -> f32 {
    CHOICE_CARD_PAD + LIST_ICON_CONTAINER_SIZE + CHOICE_CARD_ICON_GAP
}

fn choice_card_text_width(width: f32) -> f32 {
    (width - choice_card_text_left() - CHOICE_CARD_PAD).max(80.0)
}

pub fn choice_card_height(width: f32, subtitle: &str, painter: &egui::Painter) -> f32 {
    let text_w = choice_card_text_width(width);
    let subtitle_galley = painter.layout(
        subtitle.to_owned(),
        body(12.0),
        ON_SURFACE_VARIANT,
        text_w,
    );
    (16.0 + 20.0 + subtitle_galley.size().y + 16.0).max(72.0)
}

pub fn choice_card_sized(
    ui: &mut Ui,
    width: f32,
    title: &str,
    subtitle: &str,
    icon: &str,
    hover_enabled: bool,
) -> bool {
    let text_left = choice_card_text_left();
    let text_w = choice_card_text_width(width);
    let subtitle_galley = ui.painter().layout(
        subtitle.to_owned(),
        body(12.0),
        ON_SURFACE_VARIANT,
        text_w,
    );
    let card_h = (16.0 + 20.0 + subtitle_galley.size().y + 16.0).max(72.0);
    let (rect, response) = ui.allocate_exact_size(Vec2::new(width, card_h), Sense::click());
    let hovered = hover_enabled && response.hovered();
    let pressed = hover_enabled && response.is_pointer_button_down_on();
    let (hover_t, press_t) = interaction_anim(ui, response.id, hovered, pressed);

    let rest = SURFACE_CONTAINER;
    let hover = SURFACE_CONTAINER_HIGH;
    let mut bg = theme::lerp_color(rest, hover, hover_t);
    if press_t > 0.001 {
        bg = theme::lerp_color(bg, Color32::from_rgba_unmultiplied(0xD0, 0xBC, 0xFF, 20), press_t);
    }
    let rest_r = 12.0;
    let hover_r = (card_h * 0.5).min(28.0);
    let rounding = Rounding::same(rest_r + (hover_r - rest_r) * hover_t);
    ui.painter().rect_filled(rect, rounding, bg);

    let icon_rect = Rect::from_min_size(
        Pos2::new(rect.left() + CHOICE_CARD_PAD, rect.top() + (card_h - LIST_ICON_CONTAINER_SIZE) * 0.5),
        Vec2::splat(LIST_ICON_CONTAINER_SIZE),
    );
    theme::draw_tonal_icon_container(ui.painter(), icon_rect);
    ui.painter().text(
        icon_rect.center(),
        egui::Align2::CENTER_CENTER,
        icon,
        icons::font(LIST_ICON_SIZE),
        PRIMARY,
    );
    ui.painter().text(
        rect.left_top() + Vec2::new(text_left, 16.0),
        egui::Align2::LEFT_TOP,
        title,
        body_medium(16.0),
        ON_SURFACE,
    );
    ui.painter().galley(
        rect.left_top() + Vec2::new(text_left, 38.0),
        subtitle_galley,
        ON_SURFACE_VARIANT,
    );
    response.clicked()
}

pub fn selectable_choice_card(
    ui: &mut Ui,
    title: &str,
    subtitle: &str,
    icon_texture: Option<&TextureHandle>,
    fallback_icon: &str,
    selected: bool,
    hover_enabled: bool,
) -> bool {
    let width = ui.available_width();
    let text_left = choice_card_text_left();
    let text_w = choice_card_text_width(width);
    let subtitle_galley = ui.painter().layout(
        subtitle.to_owned(),
        body(12.0),
        ON_SURFACE_VARIANT,
        text_w,
    );
    let card_h = (16.0 + 20.0 + subtitle_galley.size().y + 16.0).max(72.0);
    let (rect, response) = ui.allocate_exact_size(Vec2::new(width, card_h), Sense::click());
    let hovered = hover_enabled && response.hovered();
    let pressed = hover_enabled && response.is_pointer_button_down_on();
    let (hover_t, press_t) = interaction_anim(ui, response.id, hovered, pressed);

    let rest = if selected {
        SURFACE_CONTAINER_HIGH
    } else {
        SURFACE_CONTAINER
    };
    let hover = SURFACE_CONTAINER_HIGH;
    let mut bg = theme::lerp_color(rest, hover, hover_t);
    if press_t > 0.001 {
        bg = theme::lerp_color(bg, Color32::from_rgba_unmultiplied(0xD0, 0xBC, 0xFF, 20), press_t);
    }
    let rest_r = 12.0;
    let hover_r = (card_h * 0.5).min(28.0);
    let rounding = Rounding::same(rest_r + (hover_r - rest_r) * hover_t);
    ui.painter().rect_filled(rect, rounding, bg);
    if selected {
        ui.painter().rect_stroke(
            rect,
            rounding,
            Stroke::new(2.0_f32, PRIMARY),
        );
    }

    let icon_rect = Rect::from_min_size(
        Pos2::new(
            rect.left() + CHOICE_CARD_PAD,
            rect.top() + (card_h - LIST_ICON_CONTAINER_SIZE) * 0.5,
        ),
        Vec2::splat(LIST_ICON_CONTAINER_SIZE),
    );
    draw_choice_card_icon(ui, icon_rect, icon_texture, fallback_icon);
    ui.painter().text(
        rect.left_top() + Vec2::new(text_left, 16.0),
        egui::Align2::LEFT_TOP,
        title,
        body_medium(16.0),
        ON_SURFACE,
    );
    ui.painter().galley(
        rect.left_top() + Vec2::new(text_left, 38.0),
        subtitle_galley,
        ON_SURFACE_VARIANT,
    );
    response.clicked()
}

/// Read-only install copy summary (upgrade confirm, etc.) — surface container, no border.
pub fn install_copy_card(
    ui: &mut Ui,
    title: &str,
    subtitle: &str,
    icon_texture: Option<&TextureHandle>,
    fallback_icon: &str,
) {
    let width = ui.available_width();
    let text_left = choice_card_text_left();
    let text_w = choice_card_text_width(width);
    let subtitle_galley = ui.painter().layout(
        subtitle.to_owned(),
        body(12.0),
        ON_SURFACE_VARIANT,
        text_w,
    );
    let card_h = (16.0 + 20.0 + subtitle_galley.size().y + 16.0).max(72.0);
    let (rect, _) = ui.allocate_exact_size(Vec2::new(width, card_h), Sense::hover());
    let rounding = Rounding::same(12.0);
    ui.painter().rect_filled(rect, rounding, SURFACE_CONTAINER);

    let icon_rect = Rect::from_min_size(
        Pos2::new(
            rect.left() + CHOICE_CARD_PAD,
            rect.top() + (card_h - LIST_ICON_CONTAINER_SIZE) * 0.5,
        ),
        Vec2::splat(LIST_ICON_CONTAINER_SIZE),
    );
    draw_choice_card_icon(ui, icon_rect, icon_texture, fallback_icon);
    ui.painter().text(
        rect.left_top() + Vec2::new(text_left, 16.0),
        egui::Align2::LEFT_TOP,
        title,
        body_medium(16.0),
        ON_SURFACE,
    );
    ui.painter().galley(
        rect.left_top() + Vec2::new(text_left, 38.0),
        subtitle_galley,
        ON_SURFACE_VARIANT,
    );
}

pub fn error_label(ui: &mut Ui, message: &str) {
    ui.add_space(8.0);
    ui.colored_label(ERROR, message);
}

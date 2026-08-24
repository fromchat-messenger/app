pub fn ease_in_out_cubic(t: f32) -> f32 {
    let t = t.clamp(0.0, 1.0);
    if t < 0.5 {
        4.0 * t * t * t
    } else {
        1.0 - (-2.0 * t + 2.0).powi(3) / 2.0
    }
}

pub fn ease_out_cubic(t: f32) -> f32 {
    let t = t.clamp(0.0, 1.0);
    1.0 - (1.0 - t).powi(3)
}

#[derive(Clone, Copy)]
pub struct ScreenTransition<S: Copy> {
    pub from: S,
    pub to: S,
    pub t: f32,
    pub duration: f32,
}

impl<S: Copy> ScreenTransition<S> {
    pub fn start(from: S, to: S) -> Self {
        Self {
            from,
            to,
            t: 0.0,
            duration: 0.32,
        }
    }

    pub fn tick(&mut self, dt: f32) -> bool {
        self.t = (self.t + dt / self.duration).min(1.0);
        self.t >= 1.0
    }

    pub fn eased(&self) -> f32 {
        ease_in_out_cubic(self.t)
    }
}

pub struct Lerped {
    pub value: f32,
    pub target: f32,
}

impl Lerped {
    pub fn new(v: f32) -> Self {
        Self { value: v, target: v }
    }

    pub fn set_target(&mut self, t: f32) {
        self.target = t;
    }

    pub fn tick(&mut self, dt: f32) {
        self.value = animate_towards(self.value, self.target, dt, 8.0);
    }
}

/// Exponential ease toward `target` (MD3 standard ~250ms utility motion).
pub fn animate_towards(current: f32, target: f32, dt: f32, speed: f32) -> f32 {
    if (current - target).abs() < 0.0005 {
        return target;
    }
    current + (target - current) * (1.0 - (-speed * dt).exp())
}

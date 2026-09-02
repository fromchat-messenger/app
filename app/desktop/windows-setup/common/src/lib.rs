//! Shared payload format, extract, and Windows install helpers for FromChat setup.

mod branding;
mod exe_icon;
mod install_icon;
mod install_layout;
mod payload;
mod progress;

#[cfg(windows)]
mod win;

pub use branding::*;
pub use exe_icon::*;
pub use install_icon::*;
pub use install_layout::*;
pub use payload::*;
pub use progress::*;

#[cfg(windows)]
pub use win::*;

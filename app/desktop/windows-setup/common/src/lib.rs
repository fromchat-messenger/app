//! Shared payload format, extract, and Windows install helpers for FromChat setup.

mod branding;
mod install_icon;
mod payload;
mod progress;

#[cfg(windows)]
mod win;

pub use branding::*;
pub use install_icon::*;
pub use payload::*;
pub use progress::*;

#[cfg(windows)]
pub use win::*;

use fromchat_setup_common::{find_installed_by_registration_id, UNINSTALL_ARG, UPGRADE_ARG};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SetupMode {
    Install,
    Uninstall,
    /// `--upgrade`: progress only, then exit (no confirm / done screens).
    Upgrade,
}

#[derive(Debug, Clone)]
pub struct SetupOptions {
    pub mode: SetupMode,
}

impl Default for SetupOptions {
    fn default() -> Self {
        Self {
            mode: SetupMode::Install,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SetupLaunchError {
    ConflictingFlags,
    NotInstalled,
}

impl SetupLaunchError {
    pub fn title(self) -> &'static str {
        match self {
            Self::ConflictingFlags => crate::i18n::INSTALL_ERROR_TITLE,
            Self::NotInstalled => crate::i18n::UPGRADE_ERROR_TITLE,
        }
    }

    pub fn body(self) -> &'static str {
        match self {
            Self::ConflictingFlags => {
                "Нельзя одновременно использовать параметры --uninstall и --upgrade."
            }
            Self::NotInstalled => crate::i18n::NOT_INSTALLED,
        }
    }
}

pub fn parse_setup_args(registration_id: &str) -> Result<SetupOptions, SetupLaunchError> {
    let args: Vec<String> = std::env::args().collect();
    let uninstall = args.iter().any(|arg| arg == UNINSTALL_ARG);
    let upgrade = args.iter().any(|arg| arg == UPGRADE_ARG);

    if uninstall && upgrade {
        return Err(SetupLaunchError::ConflictingFlags);
    }

    if upgrade
        && find_installed_by_registration_id(registration_id).is_none()
    {
        return Err(SetupLaunchError::NotInstalled);
    }

    Ok(SetupOptions {
        mode: if uninstall {
            SetupMode::Uninstall
        } else if upgrade {
            SetupMode::Upgrade
        } else {
            SetupMode::Install
        },
    })
}

pub fn window_title_for_mode(mode: SetupMode) -> &'static str {
    match mode {
        SetupMode::Install => crate::i18n::WINDOW_TITLE,
        SetupMode::Uninstall => crate::i18n::WINDOW_TITLE_UNINSTALL,
        SetupMode::Upgrade => crate::i18n::WINDOW_TITLE_UPGRADE,
    }
}

use std::{fs, path::Path};

use log::{debug, error, info};
use once_cell::sync::OnceCell;
use serde::{Deserialize, Serialize};

use onekeepass_core::db_service as kp_service;

use crate::{
    app_state::AppState,
    udl_types::{CommonDeviceService, EventDispatch, FileInfo},
    util, OkpError, OkpResult,
};

#[derive(Clone, Serialize, Deserialize, Default)]
pub struct RecentlyUsed1 {
    pub(crate) file_name: String,
    // This is full file url str.
    // It will start with "content://" for android, "file://" in case of ios
    // or "Sftp" or "Webdav"
    // This is also db_key
    pub(crate) db_file_path: String,
}

#[derive(Clone, Serialize, Deserialize, Default)]
pub struct RecentlyUsed {
    pub(crate) file_name: String,
    // This is full file url str.
    // It will start with "content://" for android, "file://" in case of ios
    // or "Sftp" or "Webdav"
    // This is also db_key
    pub(crate) db_file_path: String,
    pub(crate) biometric_enabled_db_open: bool,
}

#[derive(Debug, Deserialize)]
pub struct PreferenceData {
    pub db_session_timeout: Option<i64>,
    pub clipboard_timeout: Option<i64>,
    pub default_entry_category_groupings: Option<String>,
    pub theme: Option<String>,
    pub language: Option<String>,
}

// This struct matches any previous verion (0.0.2) of preference.json
#[derive(Clone, Serialize, Deserialize)]
pub struct Preference1 {
    pub version: String,
    pub recent_dbs_info: Vec<RecentlyUsed>,
    pub db_session_timeout: i64,
    pub clipboard_timeout: i64,
}

// This struct matches any previous verion (0.0.3) of preference.json
// This uses the old struct 'RecentlyUsed1'
#[derive(Clone, Serialize, Deserialize)]
pub struct Preference2 {
    pub version: String,
    pub recent_dbs_info: Vec<RecentlyUsed1>,
    // Session will time out in these milli seconds
    pub db_session_timeout: i64,
    // clipboard will be cleared in these milli seconds
    pub clipboard_timeout: i64,
    // Determines the theme colors etc
    pub theme: String,
    // Should be a two letters language id
    pub language: String,
    // Valid values one of Types,Categories,Groups,Tags
    pub default_entry_category_groupings: String,
}

// This struct matches current verion (0.0.4) of preference.json
// The struct RecentlyUsed changed
#[derive(Clone, Serialize, Deserialize)]
pub(crate) struct Preference {
    pub(crate) version: String,
    pub(crate) recent_dbs_info: Vec<RecentlyUsed>,
    // Session will time out in these milli seconds
    pub(crate) db_session_timeout: i64,
    // clipboard will be cleared in these milli seconds
    pub(crate) clipboard_timeout: i64,
    // Determines the theme colors etc
    pub(crate) theme: String,
    // Should be a two letters language id
    pub(crate) language: String,
    // Valid values one of Types,Categories,Groups,Tags
    pub(crate) default_entry_category_groupings: String,
}

impl Default for Preference {
    fn default() -> Self {
        Self {
            // Here we are using the version to determine the preference data struct used
            // and not the app release version
            version: "0.0.4".into(),
            recent_dbs_info: vec![],
            db_session_timeout: 1_800_000, // 30 minutes
            clipboard_timeout: 10_000,     // 10 secondds
            theme: "system".into(),
            language: util::current_locale_language(),
            default_entry_category_groupings: "Types".into(),
        }
    }
}

impl Preference {
    pub(crate) fn read(app_home_dir: &str) -> Self {
        let pref_file_name = Path::new(app_home_dir).join("preference.json");
        info!("pref_file_name is {:?} ", &pref_file_name);
        let json_str = fs::read_to_string(pref_file_name).unwrap_or("".into());
        debug!("Pref json_str is {}", &json_str);
        if json_str.is_empty() {
            info!("Preference is empty and default used ");
            Self::default()
        } else {
            serde_json::from_str(&json_str).unwrap_or_else(|_| {
                let mut pref_new = Self::default();
                // Need to use serde_json::from_str::<Preference1>(&json_str) if we do not use @ Bindings
                // See https://doc.rust-lang.org/book/ch18-03-pattern-syntax.html#-bindings
                match serde_json::from_str(&json_str) {
                    Ok(p @ Preference2 { .. }) => {
                        debug!("Returning the new pref with old pref values from Preference2");
                        Self::from_prev_version_to_recent(&mut pref_new, &p);
                        // Update the preference json with the copied values from old preference
                        pref_new.write(app_home_dir);
                    }
                    Err(_) => {
                        debug!("Returning the default pref");
                    }
                }
                pref_new
            })
        }
    }

    fn from_prev_version_to_recent(new_pref: &mut Preference, old_pref: &Preference2) {
        let info: Vec<RecentlyUsed> = old_pref
            .recent_dbs_info
            .iter()
            .map(|p| RecentlyUsed {
                biometric_enabled_db_open: false,
                file_name: p.file_name.clone(),
                db_file_path: p.db_file_path.clone(),
            })
            .collect();
        new_pref.recent_dbs_info = info;
    }

    fn write(&self, app_home_dir: &str) {
        // Remove old file names from the list before writing
        //self.remove_old_db_use_info();
        let json_str_result = serde_json::to_string_pretty(self);
        let pref_file_name = Path::new(app_home_dir).join("preference.json");
        if let Ok(json_str) = json_str_result {
            if let Err(err) = fs::write(pref_file_name, json_str.as_bytes()) {
                error!(
                    "Preference file write failed and error is {}",
                    err.to_string()
                );
            }
        }
    }

    pub(crate) fn write_to_app_dir(&self) {
        self.write(&AppState::shared().app_home_dir);
    }

    // Update the preference with any non null values
    pub(crate) fn update(&mut self, preference_data: PreferenceData) {
        let mut updated = false;
        if let Some(v) = preference_data.language {
            self.language = v;
            updated = true;
        }

        if let Some(v) = preference_data.theme {
            self.theme = v;
            updated = true;
        }

        if let Some(v) = preference_data.default_entry_category_groupings {
            self.default_entry_category_groupings = v;
            updated = true;
        }

        if let Some(v) = preference_data.db_session_timeout {
            self.db_session_timeout = v;
            updated = true;
        }

        if let Some(v) = preference_data.clipboard_timeout {
            self.clipboard_timeout = v;
            updated = true;
        }

        if updated {
            self.write_to_app_dir();
        }
    }

    pub(crate) fn backup_history_count(&self) -> usize {
        3
    }

    pub(crate) fn add_recent_db_use_info(
        &mut self,
        app_home_dir: &str,
        recently_used: RecentlyUsed,
    ) -> &mut Self {
        // First we need to remove any previously added if any
        self.recent_dbs_info
            .retain(|s| s.db_file_path != recently_used.db_file_path);

        self.recent_dbs_info.insert(0, recently_used);
        // Write the preference to the file system immediately
        self.write(app_home_dir);
        self
    }

    pub(crate) fn remove_recent_db_use_info(&mut self, full_file_name_uri: &str) {
        self.recent_dbs_info
            .retain(|s| s.db_file_path != full_file_name_uri);

        // Write the preference to the file system immediately
        self.write(&AppState::shared().app_home_dir);
    }

    pub fn set_db_open_biometric(&mut self, db_key: &str, enabled: bool) {
        if let Some(info) = self
            .recent_dbs_info
            .iter_mut()
            .find(|r| r.db_file_path == db_key)
        {
            info.biometric_enabled_db_open = enabled;
            self.write_to_app_dir();
        }
    }

    pub fn db_open_biometeric_enabled(&self, db_key: &str) -> bool {
        self.find_db_info(db_key)
            .map_or(false, |r| r.biometric_enabled_db_open)
    }

    fn find_db_info(&self, db_key: &str) -> Option<&RecentlyUsed> {
        self.recent_dbs_info
            .iter()
            .find(|r| r.db_file_path == db_key)
    }

    fn _remove_old_db_use_info(&mut self) { //-> &mut Self
                                            // Keeps the most recet 5 entries
                                            //self.recent_files.truncate(5);
                                            //self
    }
}

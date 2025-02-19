use std::{fs, path::Path};

use log::{debug, error, info};
use once_cell::sync::OnceCell;
use serde::{Deserialize, Serialize};

use onekeepass_core::db_service as kp_service;

use crate::{
    app_state::AppState,
    biometric_auth,
    udl_types::{CommonDeviceService, EventDispatch, FileInfo},
    util, OkpError, OkpResult,
};

// Used for the update of preferences from the app settings page
#[derive(Debug, Deserialize)]
pub struct PreferenceData {
    db_session_timeout: Option<i64>,
    clipboard_timeout: Option<i64>,
    default_entry_category_groupings: Option<String>,
    theme: Option<String>,
    language: Option<String>,
    database_preference: Option<DatabasePreference>,
}

#[derive(Clone, Serialize, Deserialize, Default, Debug)]
pub struct RecentlyUsed {
    // Just the file name part
    pub(crate) file_name: String,

    // This is full file url str.
    // It will start with "content://" for android, "file://" in case of ios
    // or "Sftp" or "Webdav"
    // This is also db_key
    pub(crate) db_file_path: String,

    // In milli seconds
    pub last_modified: Option<i64>,

    // In milli seconds
    pub last_accessed: Option<i64>,

    pub location: Option<String>,

    pub file_size: Option<i64>,
}

// Database specific preferences
#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct DatabasePreference {
    db_key: String,
    db_open_biometric_enabled: bool,
    db_unlock_biometric_enabled: bool,
    //TDOO:
    // Add after how many times of using biometric, we need to ask user to enter password something similar MacOS does
    // Add PIN protection for each db  - db_open_pin_enabled:bool,; Need to store the PIN in secure enclave
    // Flag to indicate whether to use biometric during autofill (iOS specific?)
}

#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct AppLockPreference {
    // app will be locked in these milli seconds. None means no delay
    lock_timeout: Option<i64>,

    // When this is enabled, the app will be reset when there are failures > attempts_allowed  
    reset_on_lock_failures:bool,

    // Number of attempts allowed after which app is reset
    attempts_allowed: usize,

    // When this is true, user can access the 'App Settings' only after reentering the PIN again
    lock_app_settings:bool,
}

impl Default for AppLockPreference {
    fn default() -> Self {
        Self { lock_timeout: Default::default(), 
                reset_on_lock_failures: true,
                attempts_allowed: 10, 
                lock_app_settings: Default::default() }
    }
}


pub(crate) const PREFERENCE_JSON_FILE_NAME: &str = "preference.json";

const PREFERENCE_JSON_FILE_VERSION: &str = "5.0.0"; // started using 4.0.0 instead of 0.0.4

// This struct matches current verion of preference.json
// The struct RecentlyUsed changed
#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct Preference {
    version: String,

    recent_dbs_info: Vec<RecentlyUsed>,

    // Session will time out in these milli seconds
    db_session_timeout: i64,

    // clipboard will be cleared in these milli seconds
    clipboard_timeout: i64,

    // Determines the theme colors etc
    theme: String,

    // Should be a two letters language id
    language: String,

    // Valid values one of Types,Categories,Groups,Tags
    default_entry_category_groupings: String,

    // All dbs that can be opened using biometric
    // biometric_enabled_dbs: Vec<String>,

    // Number of backs to keep for all databases
    backup_history_count: u8,

    // All databases specific preferences
    database_preferences: Vec<DatabasePreference>,
}

impl Default for Preference {
    fn default() -> Self {
        Self {
            // Here we are using the version to determine the preference data struct used
            // and not the app release version
            version: PREFERENCE_JSON_FILE_VERSION.into(),
            recent_dbs_info: vec![],
            db_session_timeout: 1_800_000, // 30 minutes
            clipboard_timeout: 10_000,     // 10 secondds
            theme: "system".into(),
            language: util::current_locale_language(),
            default_entry_category_groupings: "Types".into(),
            backup_history_count: 3,
            // biometric_enabled_dbs: vec![],
            database_preferences: vec![],
        }
    }
}

impl Preference {
    pub(crate) fn read<P: AsRef<Path>>(preference_home_dir: P) -> Self {
        let pref_file_name =
            Path::new(preference_home_dir.as_ref()).join(PREFERENCE_JSON_FILE_NAME);
        info!("pref_file_name is {:?} ", &pref_file_name);
        let json_str = fs::read_to_string(pref_file_name).unwrap_or("".into());
        debug!("Pref json_str is {}", &json_str);

        let mut pref = if json_str.is_empty() {
            info!("Preference is empty and default used ");
            Self::default()
        } else {
            // If the new field added are Option<.> type, then  
            // the parsing will be successful with None set for all Option fields. 
            // In that case, we need to ensure version number is correct
            // See at the end before returning 'final_pref'
            serde_json::from_str(&json_str).unwrap_or_else(|_| {
                // Json parsing failed as the file loaded may be from a previous version

                // Need to use serde_json::from_str::<PreferenceV400>(&json_str) if we do not use @ Bindings
                // See https://doc.rust-lang.org/book/ch18-03-pattern-syntax.html#-bindings

                match serde_json::from_str(&json_str) {
                    // Prior version
                    Ok(prev_pref @ PreferenceV400 { .. }) => {
                        debug!("Returning the new pref with old pref values from PreferenceV400");
                        // Self::from_prev_version_v400_to_recent(&mut pref_new, &p);

                        let pref_new: Self = prev_pref.into();

                        // Update the preference json with the copied values from old preference
                        pref_new.write(preference_home_dir.as_ref());
                    }
                    Err(_) => {
                        // We could not parse the existing json file even to the prior version.
                        // This may happen if the 'preference.json' found is older than the previous version.
                        // Returns the latest default pref
                        // Should we write this default one?
                        debug!("Returning the default pref");
                    }
                }
                Self::default()
            })
        };

        // Ensure that version is updated
        let final_pref = if pref.version == PREFERENCE_JSON_FILE_VERSION {
            debug!("Returning the current pref as version is the latest");
            pref
        } else {
            // If the new field added are only Option<> type in Preference, 
            // then parsing goes through with new Preference itself, but the version will be old 
            pref.version = PREFERENCE_JSON_FILE_VERSION.into();
            pref.write(preference_home_dir.as_ref());
            debug!("Returning the current pref after conversion from old pref as versions are not the same");
            pref
        };

        // debug!("Returning pref obj in read {:?}", &final_pref);

        final_pref
    }

    fn write<P: AsRef<Path>>(&self, preference_home_dir: P) {
        // Remove old file names from the list before writing
        //self.remove_old_db_use_info();
        let json_str_result = serde_json::to_string_pretty(self);
        let pref_file_name =
            Path::new(preference_home_dir.as_ref()).join(PREFERENCE_JSON_FILE_NAME);
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
        self.write(AppState::preference_home_dir());
    }

    // Update the preference with any non null values
    pub(crate) fn update(&mut self, preference_data: PreferenceData) -> OkpResult<()> {
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

        if let Some(db_pref) = preference_data.database_preference {
            self.upate_or_insert_database_preference(db_pref);
            updated = true;
        }

        if updated {
            self.write_to_app_dir();
        }

        Ok(())
    }

    fn upate_or_insert_database_preference(&mut self, db_pref: DatabasePreference) {
        let (db_key, db_open_flag) = (db_pref.db_key.clone(), db_pref.db_open_biometric_enabled);

        if let Some(m) = self
            .database_preferences
            .iter_mut()
            .find(|d| d.db_key == db_pref.db_key)
        {
            *m = db_pref;
        } else {
            self.database_preferences.push(db_pref);
        }

        // Remove any previously stored credentials when the flag is false
        if !db_open_flag {
            let _ = biometric_auth::StoredCredential::remove_credentials(&db_key);
        }
    }

    pub fn update_session_timeout(
        &mut self,
        db_session_timeout: Option<i64>,
        clipboard_timeout: Option<i64>,
    ) -> OkpResult<()> {
        if let Some(t) = db_session_timeout {
            self.db_session_timeout = t;
        }
        if let Some(t) = clipboard_timeout {
            self.clipboard_timeout = t;
        }

        self.write_to_app_dir();
        Ok(())
    }

    pub(crate) fn remove_database_preference(&mut self, db_key: &str) {
        self.database_preferences.retain(|s| s.db_key != db_key);
    }

    pub(crate) fn backup_history_count(&self) -> u8 {
        self.backup_history_count
    }

    pub(crate) fn add_recent_db_use_info(&mut self, recently_used: RecentlyUsed) -> &mut Self {
        // First we need to remove any previously added if any
        self.recent_dbs_info
            .retain(|s| s.db_file_path != recently_used.db_file_path);

        self.recent_dbs_info.insert(0, recently_used);
        // Write the preference to the file system immediately
        self.write_to_app_dir();
        self
    }

    pub(crate) fn remove_recent_db_use_info(
        &mut self,
        full_file_name_uri: &str,
        delete_db_pref: bool,
    ) {
        self.recent_dbs_info
            .retain(|s| s.db_file_path != full_file_name_uri);

        if delete_db_pref {
            self.remove_database_preference(full_file_name_uri)
        }

        // Write the preference to the file system immediately
        self.write(&AppState::preference_home_dir());
    }

    // pub(crate) fn set_db_open_biometric(&mut self, db_key: &str, enabled: bool) {
    //     let key = db_key.to_string();

    //     // First remove the matching key if any
    //     self.biometric_enabled_dbs.retain(|s| s != &key);

    //     // We add the db-key when it is enabled
    //     if enabled {
    //         self.biometric_enabled_dbs.push(key);
    //     }

    //     self.write_to_app_dir();
    // }

    // pub(crate) fn db_open_biometeric_enabled(&self, db_key: &str) -> bool {
    //     self.biometric_enabled_dbs.contains(&db_key.to_string())
    // }

    pub(crate) fn db_open_biometeric_enabled(&self, db_key: &str) -> bool {
        self.database_preferences
            .iter()
            .find(|p| p.db_key == db_key)
            .map_or(false, |d| d.db_open_biometric_enabled)
    }

    pub fn database_preferences(&self) -> &Vec<DatabasePreference> {
        &self.database_preferences
    }

    pub fn get_recently_used(&self, db_key: &str) -> Option<RecentlyUsed> {
        self.recent_dbs_info
            .iter()
            .find(|r| r.db_file_path == db_key)
            .map(|r| RecentlyUsed { ..r.clone() })
    }

    pub fn file_name_in_recently_used(&self, db_key: &str) -> Option<String> {
        self.find_db_info(db_key).map(|r| r.file_name.clone())
    }

    pub(crate) fn recent_dbs_info(&self) -> Vec<RecentlyUsed> {
        self.recent_dbs_info.clone()
    }

    pub(crate) fn recent_dbs_info_ref(&self) -> &Vec<RecentlyUsed> {
        &self.recent_dbs_info
    }

    pub(crate) fn recent_dbs_info_mut_ref(&mut self) -> &mut Vec<RecentlyUsed> {
        &mut self.recent_dbs_info
    }

    pub(crate) fn language(&self) -> &str {
        self.language.as_str()
    }

    pub(crate) fn find_db_key(&self, file_name: &str) -> Option<String> {
        self.recent_dbs_info.iter().find_map(|r| {
            if r.file_name == file_name {
                Some(r.db_file_path.clone())
            } else {
                None
            }
        })
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

////////////   Previous version ////////////
///
#[derive(Clone, Serialize, Deserialize, Default)]
pub struct RecentlyUsedV400 {
    // Just the file name part
    pub(crate) file_name: String,

    // This is full file url str.
    // It will start with "content://" for android, "file://" in case of ios
    // or "Sftp" or "Webdav"
    // This is also db_key
    pub(crate) db_file_path: String,
}

#[derive(Clone, Serialize, Deserialize)]
pub(crate) struct PreferenceV400 {
    version: String,

    recent_dbs_info: Vec<RecentlyUsedV400>,

    // Session will time out in these milli seconds
    db_session_timeout: i64,

    // clipboard will be cleared in these milli seconds
    clipboard_timeout: i64,

    // Determines the theme colors etc
    theme: String,

    // Should be a two letters language id
    language: String,

    // Valid values one of Types,Categories,Groups,Tags
    default_entry_category_groupings: String,

    // All dbs that can be opened using biometric
    // biometric_enabled_dbs: Vec<String>,

    // Number of backs to keep for all databases
    backup_history_count: u8,

    // All databases specific preferences
    database_preferences: Vec<DatabasePreference>,
}

impl From<PreferenceV400> for Preference {
    fn from(old_pref: PreferenceV400) -> Self {
        // Destructuring of old struct
        let PreferenceV400 {
            version: _,
            recent_dbs_info,
            db_session_timeout,
            clipboard_timeout,
            theme,
            backup_history_count,
            language,
            database_preferences,
            default_entry_category_groupings,
        } = old_pref;

        let mut current_pref = Preference {
            version: PREFERENCE_JSON_FILE_VERSION.into(),
            recent_dbs_info: vec![],
            db_session_timeout,
            clipboard_timeout,
            theme,
            language,
            default_entry_category_groupings,
            backup_history_count,
            database_preferences,
        };

        // RecentlyUsed is changed in the new Preference struct
        let info: Vec<RecentlyUsed> = recent_dbs_info
            .iter()
            .map(|p: &RecentlyUsedV400| {
                let mut dbs = RecentlyUsed::default();
                dbs.file_name = p.file_name.clone();
                dbs.db_file_path = p.db_file_path.clone();
                dbs
            })
            .collect();
        current_pref.recent_dbs_info = info;

        current_pref
    }
}

////////////

/*
fn from_prev_version_v400_to_recent(new_pref: &mut Preference, old_pref: &PreferenceV400) {

        // old_pref uses RecentlyUsedV400
        let info: Vec<RecentlyUsed> = old_pref
            .recent_dbs_info
            .iter()
            .map(|p: &RecentlyUsedV400| {
                let mut dbs = RecentlyUsed::default();
                dbs.file_name = p.file_name.clone();
                dbs.db_file_path = p.db_file_path.clone();
                dbs
            })
            .collect();
        new_pref.recent_dbs_info = info;

        new_pref.default_entry_category_groupings = old_pref.default_entry_category_groupings
    }
fn from_prev_version_to_recent(new_pref: &mut Preference, old_pref: &Preference2) {
        let info: Vec<RecentlyUsed> = old_pref
            .recent_dbs_info
            .iter()
            .map(|p| RecentlyUsed {
                file_name: p.file_name.clone(),
                db_file_path: p.db_file_path.clone(),
            })
            .collect();
        new_pref.recent_dbs_info = info;
    }

// This struct matches any previous verion (0.0.3) of preference.json
// This uses the old struct 'RecentlyUsed1'
#[derive(Clone, Serialize, Deserialize)]
pub struct Preference2 {
    pub version: String,
    pub recent_dbs_info: Vec<RecentlyUsed>,
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
*/

/*
// This struct matches any previous verion (0.0.2) of preference.json
#[derive(Clone, Serialize, Deserialize)]
pub struct Preference1 {
    pub version: String,
    pub recent_dbs_info: Vec<RecentlyUsed>,
    pub db_session_timeout: i64,
    pub clipboard_timeout: i64,
}
*/

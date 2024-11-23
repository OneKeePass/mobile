use log::{debug, error, info};
use once_cell::sync::OnceCell;
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    fs,
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
};

use onekeepass_core::db_service as kp_service;

use crate::{udl_types::SecureKeyOperation, udl_uniffi_exports::{CommonDeviceServiceEx, SecureEnclaveCbService}, util};
use crate::{
    udl_types::{CommonDeviceService, EventDispatch, FileInfo},
    OkpError, OkpResult,
};

// Any mutable field needs to be behind Mutex
pub struct AppState {
    pub app_home_dir: String,
    // iOS specific
    pub app_group_home_dir: Option<String>,
    // Android specific use (in save_attachment_as_temp_file) ? 
    pub cache_dir: String,
    // Not used ?
    temp_dir: String,
    // Dir where all db files backups are created 
    backup_dir_path: PathBuf,

    // We keep last 'n' number of backups for a db that was edited 
    backup_history_dir_path: PathBuf,

    // Dir path where all remote storage related files are stored
    remote_storage_path:PathBuf,
    
    // The dir where an edited db file is stored if the remote connection is not avilable
    // pub local_db_dir_path: PathBuf,

    // Dir path where db file for export is created and used in export calls
    pub export_data_dir_path: PathBuf,
    // Dir where all key files are copied for latter use 
    pub key_files_dir_path: PathBuf,
    
    // Used to keep the last backup file ref which is used for 'Save as' 
    // when db save fails (as the orginal db content changed) 
    last_backup_on_error: Mutex<HashMap<String, String>>,

    preference: Mutex<Preference>,

    // Callback service implemented in Swift/Kotlin and called from rust side
    pub common_device_service: Box<dyn CommonDeviceService>,
    // Callback service implemented in Swift/Kotlin and called from rust side
    pub secure_key_operation: Box<dyn SecureKeyOperation>,
    // Callback service implemented in Swift/Kotlin and called from rust side
    pub event_dispatcher: Arc<dyn EventDispatch>,

    common_device_service_ex: Arc<dyn CommonDeviceServiceEx>,
    secure_enclave_cb_service: Arc<dyn SecureEnclaveCbService>,
}

static APP_STATE: OnceCell<AppState> = OnceCell::new();

// iOS specific idea to move all internal dirs and files from the current app_home dir to app_group home dir
// Also see comments in Swift impl 'CommonDeviceServiceImpl appHoemDir' 
fn _temp_move_documents_to_okp_app_dir(app_dir:&str,app_group_dir:&Option<String>) {
    // Check if there is the sub dir 'okp_app'  under app_group_dir
    // If the dir is available then, app_group_dir/okp_app is already created 
    // Return app_dir,app_group_dir where app_dir = app_group_dir

    // If not, create app_group_dir/okp_app, then move app_dir/[bookmarks,backups,key_files,preference.json] to
    // app_group_dir/okp_app[bookmarks,backups,key_files,preference.json]

    // Remove app_dir/[bookmarks,backups,key_files,preference.json]

    // Return app_dir,app_group_dir where app_dir = app_group_dir
}

impl AppState {
    pub fn shared() -> &'static AppState {
        // Panics if no global state object was set. ??
        APP_STATE.get().unwrap()
    }

    pub fn setup(
        common_device_service: Box<dyn CommonDeviceService>,
        secure_key_operation: Box<dyn SecureKeyOperation>,
        event_dispatcher: Arc<dyn EventDispatch>,
        common_device_service_ex: Arc<dyn CommonDeviceServiceEx>,
        secure_enclave_cb_service: Arc<dyn SecureEnclaveCbService>,
    ) {
        let app_dir = util::url_to_unix_file_name(&common_device_service.app_home_dir());
        let cache_dir = util::url_to_unix_file_name(&common_device_service.cache_dir());
        let temp_dir = util::url_to_unix_file_name(&common_device_service.temp_dir());

        // iOS specific
        let app_group_home_dir = if let Some(d) = &common_device_service.app_group_home_dir() {
            Some(util::url_to_unix_file_name(&d))
        } else {
            None
        };

        debug!(
            "app_dir {}, cache_dir {}, temp_dir {}, app_group_home_dir {:?}",
            &app_dir, &cache_dir, &temp_dir,&app_group_home_dir
        );

        let pref = Preference::read(&app_dir);

        let export_data_dir_path = util::create_sub_dir(&app_dir, "export_data");
        log::debug!("export_data_dir_path is {:?}", &export_data_dir_path);

        // To be removed after the complete use of 'backup_history_dir_path'
        let backup_dir_path = util::create_sub_dir(&app_dir, "backups");
        log::debug!("backup_dir_path is {:?}", backup_dir_path);

        let backup_history_dir_path = util::create_sub_dirs(&app_dir, vec!["backups", "history"]);
        log::debug!("backup_dir_path is {:?}", backup_dir_path);

        // Meant for SFTP,WebDAV files save when offline ?
        // let local_db_dir_path = util::create_sub_dir(&app_dir, "local_dbs");
        // log::debug!("local_db_dir_path is {:?}", backup_dir_path);

        let key_files_dir_path = util::create_sub_dir(&app_dir, "key_files");
        log::debug!("key_files_dir_path is {:?}", key_files_dir_path);

        // All remote storage related dirs
        // TODO: Need to use app group home for ios
        let remote_storage_path = util::create_sub_dir(&app_dir, "remote_storage");
        util::create_sub_dir_path(&remote_storage_path, "sftp");

        let app_state = AppState {
            app_home_dir: app_dir.into(),
            app_group_home_dir,
            cache_dir,
            temp_dir,
            backup_dir_path,
            backup_history_dir_path,
            // local_db_dir_path,
            remote_storage_path,
            export_data_dir_path,
            key_files_dir_path,
            last_backup_on_error: Mutex::new(HashMap::default()),
            preference: Mutex::new(pref),

            common_device_service,
            secure_key_operation,
            event_dispatcher,
            common_device_service_ex,
            secure_enclave_cb_service,

        };

        if APP_STATE.get().is_none() {
            if APP_STATE.set(app_state).is_err() {
                log::error!(
                    "Global APP_STATE object is initialized already. This probably happened concurrently."
                );
            }
        }
    }

    pub fn add_last_backup_name_on_error(
        &self,
        full_file_name_uri: &str,
        backup_file_full_name: &str,
    ) {
        let mut bkp = self.last_backup_on_error.lock().unwrap();
        bkp.insert(full_file_name_uri.into(), backup_file_full_name.into());
    }

    // Used in android and ios specific module
    // See ios::complete_save_as_on_error and  android::complete_save_as_on_error
    pub fn remove_last_backup_name_on_error(&self, full_file_name_uri: &str) {
        let mut bkp = self.last_backup_on_error.lock().unwrap();
        bkp.remove(full_file_name_uri);
    }

    // Used in android and ios specific module
    // See ios::copy_last_backup_to_temp_file, android::complete_save_as_on_error
    pub fn get_last_backup_on_error(&self, full_file_name_uri: &str) -> Option<String> {
        self.last_backup_on_error
            .lock()
            .unwrap()
            .get(full_file_name_uri)
            .map(|s| s.clone())
    }

    pub fn add_recent_db_use_info(&self, full_file_name_uri: &str) {
        let file_name = self
            .common_device_service
            .uri_to_file_name(full_file_name_uri.into())
            .map_or_else(|| "".into(), |s| s);

        let recently_used = RecentlyUsed {
            file_name,
            db_file_path: full_file_name_uri.into(),
        };

        let mut pref = self.preference.lock().unwrap();
        pref.add_recent_db_use_info(&self.app_home_dir, recently_used);
    }

    // Finds the recently used info for a given uri
    pub fn get_recently_used(&self, full_file_name_uri: &str) -> Option<RecentlyUsed> {
        let pref = self.preference.lock().unwrap();
        pref.recent_dbs_info
            .iter()
            .find(|r| r.db_file_path == full_file_name_uri)
            .map(|r| RecentlyUsed { ..r.clone() })
    }

    pub fn language(&self) -> String {
        let pref = self.preference.lock().unwrap();
        pref.language.clone()
    }

    pub fn file_name_in_recently_used(&self, full_file_name_uri: &str) -> Option<String> {
        let pref = self.preference.lock().unwrap();
        pref.recent_dbs_info
            .iter()
            .find(|r| r.db_file_path == full_file_name_uri)
            .map(|r| r.file_name.clone())
    }

    pub fn remove_recent_db_use_info(&self, full_file_name_uri: &str) {
        let mut pref = self.preference.lock().unwrap();
        pref.remove_recent_db_use_info(full_file_name_uri);
    }

    pub fn update_preference(&self, preference_data: PreferenceData) {
        let mut store_pref = self.preference.lock().unwrap();
        store_pref.update(preference_data);
    }

    // Called to get the file name from the platform specific full file uri passed as arg 'full_file_name_uri' 
    // The uri may start with file: or content:
    pub fn uri_to_file_name(&self, full_file_name_uri: &str) -> String {
        // We use platform specific callback fn to get the file name
        // e.g uri file:///Users/jeyasankar/Library/Developer/CoreSimulator/Devices/A45B3252-1AA4-4D50-9E6E-89AB1E873B1F/data/Containers/Shared/AppGroup/6CFFA9FC-169B-482E-A817-9C0D2A6F5241/File%20Provider%20Storage/TJ-fixit.kdbx
        // Note the encoding in the uri
        self.common_device_service
            .uri_to_file_name(full_file_name_uri.into())
            .map_or_else(|| "".into(), |s| s)
    }

    pub fn uri_to_file_info(&self, full_file_name_uri: &str) -> Option<FileInfo> {
        let info = self
            .common_device_service
            .uri_to_file_info(full_file_name_uri.into());
        info
    }

    pub fn backup_dir_path() -> &'static PathBuf {
        &Self::shared().backup_dir_path
    }

    pub fn backup_history_dir_path() -> &'static PathBuf {
        &Self::shared().backup_history_dir_path
    }

    pub fn remote_storage_path() -> &'static PathBuf {
        &Self::shared().remote_storage_path
    }

    // Root dir where all the private key files of one or more SFTP connections are stored
    pub fn sftp_private_keys_path() -> PathBuf {
        // Sub dir "sftp" should exist
        let p = Self::shared().remote_storage_path.join("sftp");
        p
    }

    pub fn preference() -> &'static Mutex<Preference> {
        &Self::shared().preference
    }

    pub fn common_device_service_ex() -> &'static dyn CommonDeviceServiceEx {
        Self::shared().common_device_service_ex.as_ref()
    }

    pub fn secure_enclave_cb_service() -> &'static dyn SecureEnclaveCbService {
        Self::shared().secure_enclave_cb_service.as_ref()
    }

    // pub fn read_preference(&self) {
    //     let pref = Preference::read(&self.app_home_dir);
    //     // store_pref is MutexGuard
    //     let mut store_pref = self.preference.lock().unwrap();
    //     //sets the preference struct value inside the MutexGuard by dereferencing
    //     *store_pref = pref;
    // }
}

#[derive(Clone, Serialize, Deserialize)]
pub struct RecentlyUsed {
    pub(crate) file_name: String,
    // This is full file url str. In case of android, it will start with "content://" and in case of ios, it will
    // start with "file://".  This is also db_key
    pub(crate) db_file_path: String,
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

#[derive(Clone, Serialize, Deserialize)]
pub struct Preference {
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
    //Valid values one of Types,Categories,Groups,Tags
    pub default_entry_category_groupings: String,
}

impl Default for Preference {
    fn default() -> Self {
        Self {
            // Here we are using the version to determine the preference data struct used
            // and not the app release version
            version: "0.0.3".into(),
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
    fn read(app_home_dir: &str) -> Self {
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
                match serde_json::from_str::<Preference1>(&json_str) {
                    Ok(p) => {
                        debug!("Returning the new pref with old pref values");
                        pref_new.recent_dbs_info = p.recent_dbs_info;
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

    pub fn write_to_app_dir(&self) {
        self.write(&AppState::shared().app_home_dir);
    }

    // Update the preference with any non null values
    pub fn update(&mut self, preference_data: PreferenceData) {
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

    fn add_recent_db_use_info(
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

    fn remove_recent_db_use_info(&mut self, full_file_name_uri: &str) {
        self.recent_dbs_info
            .retain(|s| s.db_file_path != full_file_name_uri);

        // Write the preference to the file system immediately
        self.write(&AppState::shared().app_home_dir);
    }

    fn _remove_old_db_use_info(&mut self) { //-> &mut Self
                                            // Keeps the most recet 5 entries
                                            //self.recent_files.truncate(5);
                                            //self
    }
}

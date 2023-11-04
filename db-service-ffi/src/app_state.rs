use log::{debug, error, info};
use once_cell::sync::OnceCell;
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    fs,
    path::{Path, PathBuf},
    sync::Mutex,
};

use onekeepass_core::db_service as kp_service;

use crate::udl_types::{FileInfo, CommonDeviceService};
use crate::{udl_types::SecureKeyOperation, util};


// Any mutable field needs to be behind Mutex
pub struct AppState {
    pub app_home_dir: String,
    pub cache_dir: String,
    pub temp_dir: String,
    pub backup_dir_path: PathBuf,
    pub export_data_dir_path: PathBuf,
    pub key_files_dir_path:PathBuf,
    pub common_device_service: Box<dyn CommonDeviceService>,
    pub secure_key_operation: Box<dyn SecureKeyOperation>,
    last_backup_on_error: Mutex<HashMap<String, String>>,
    pub preference: Mutex<Preference>,
}

static APP_STATE: OnceCell<AppState> = OnceCell::new();

impl AppState {
    pub fn global() -> &'static AppState {
        // Panics if no global state object was set. ??
        APP_STATE.get().unwrap()
    }

    pub fn setup(
        common_device_service: Box<dyn CommonDeviceService>,
        secure_key_operation: Box<dyn SecureKeyOperation>,
    ) {
        let app_dir = util::url_to_unix_file_name(&common_device_service.app_home_dir());
        let cache_dir = util::url_to_unix_file_name(&common_device_service.cache_dir());
        let temp_dir = util::url_to_unix_file_name(&common_device_service.temp_dir());

        debug!("app_dir {}, cache_dir {}, temp_dir {}",&app_dir,&cache_dir,&temp_dir);

        let pref = Preference::read(&app_dir);

        let export_data_dir_path = util::create_sub_dir(&app_dir, "export_data");
        log::debug!("export_data_dir_path is {:?}", &export_data_dir_path);

        let backup_dir_path = util::create_sub_dir(&app_dir, "backups");
        log::debug!("backup_dir_path is {:?}", backup_dir_path);

        let key_files_dir_path = util::create_sub_dir(&app_dir, "key_files");
        log::debug!("key_files_dir_path is {:?}", key_files_dir_path);

        let app_state = AppState {
            app_home_dir: app_dir.into(),
            cache_dir,
            temp_dir,
            backup_dir_path,
            export_data_dir_path,
            key_files_dir_path,
            common_device_service,
            secure_key_operation,
            last_backup_on_error: Mutex::new(HashMap::default()),
            preference: Mutex::new(pref),
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

    pub fn uri_to_file_name(&self, full_file_name_uri: &str) -> String {
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

#[derive(Clone, Serialize, Deserialize)]
pub struct Preference {
    pub version: String,
    pub recent_dbs_info: Vec<RecentlyUsed>,
}

impl Default for Preference {
    fn default() -> Self {
        Self {
            version: "0.0.1".into(),
            recent_dbs_info: vec![],
        }
    }
}

impl Preference {
    fn read(app_home_dir: &str) -> Self {
        let pref_file_name = Path::new(app_home_dir).join("preference.json");
        info!("pref_file_name is {:?} ", &pref_file_name);
        let json_str = fs::read_to_string(pref_file_name).unwrap_or("".into());
        //debug!("Pref json_str is {}", &json_str);
        if json_str.is_empty() {
            info!("Preference is empty and default used ");
            Self::default()
        } else {
            serde_json::from_str(&json_str).unwrap_or(Self::default())
        }
    }

    fn write(&mut self, app_home_dir: &str) {
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

    pub fn add_recent_db_use_info(
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

    pub fn remove_recent_db_use_info(&mut self, full_file_name_uri: &str) {
        self.recent_dbs_info
            .retain(|s| s.db_file_path != full_file_name_uri);

        // Write the preference to the file system immediately
        self.write(&AppState::global().app_home_dir);
    }

    fn _remove_old_db_use_info(&mut self) { //-> &mut Self
                                            // Keeps the most recet 5 entries
                                            //self.recent_files.truncate(5);
                                            //self
    }
}

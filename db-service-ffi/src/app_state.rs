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

use crate::{
    app_preference::{Preference, PreferenceData, RecentlyUsed},
    remote_storage,
    udl_types::SecureKeyOperation,
    udl_uniffi_exports::{CommonDeviceServiceEx, SecureEnclaveCbService},
    util,
};
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
    remote_storage_path: PathBuf,

    // The dir where an edited db file is stored if the remote connection is not avilable
    // pub local_db_dir_path: PathBuf,

    // Dir path where db file for export is created and used in export calls
    pub export_data_dir_path: PathBuf,
    // Dir where all key files are copied for latter use
    pub key_files_dir_path: PathBuf,

    // Used to keep the last backup file ref which is used for 'Save as'
    // when db save fails (as the orginal db content changed)
    // This is reset to empty when the app starts
    last_backup_on_error: Mutex<HashMap<String, String>>,

    preference: Mutex<Preference>,

    // Callback service implemented in Swift/Kotlin and called from rust side
    pub common_device_service: Box<dyn CommonDeviceService>,
    // Callback service implemented in Swift/Kotlin and called from rust side
    pub event_dispatcher: Arc<dyn EventDispatch>,
    // Callback service implemented in Swift/Kotlin and called from rust side
    secure_key_operation: Box<dyn SecureKeyOperation>,

    common_device_service_ex: Arc<dyn CommonDeviceServiceEx>,

    // This trait is implemented in Swift (class SecureEnclaveServiceSupport) and in Kotlin as callbacks from rust side
    // The trait is a udlffi exported. See src/udl_uniffi_exports.rs
    secure_enclave_cb_service: Arc<dyn SecureEnclaveCbService>,
}

static APP_STATE: OnceCell<AppState> = OnceCell::new();

// iOS specific idea to move all internal dirs and files from the current app_home dir to app_group home dir
// Also see comments in Swift impl 'CommonDeviceServiceImpl appHoemDir'
fn _temp_move_documents_to_okp_app_dir(app_dir: &str, app_group_dir: &Option<String>) {
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
            &app_dir, &cache_dir, &temp_dir, &app_group_home_dir
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
    pub fn remove_last_backup_name_on_error(&self, full_file_name_uri: &str) -> Option<String> {
        let mut bkp = self.last_backup_on_error.lock().unwrap();
        bkp.remove(full_file_name_uri)
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

    // Called to get the file name from the platform specific full file uri passed as arg 'full_file_name_uri'
    // The uri may start with file: or content: or Sftp or WebDav
    pub fn uri_to_file_name(&self, full_file_name_uri: &str) -> String {
        // Need to find out whether this is a remote storage db_key and then find the file name accordingly
        if let Some(s) = remote_storage::uri_to_file_name(full_file_name_uri) {
            return s.to_string();
        }

        // We use platform specific callback fn to get the file name
        // e.g uri file:///Users/jeyasankar/Library/Developer/CoreSimulator/Devices/A45B3252-1AA4-4D50-9E6E-89AB1E873B1F/data/Containers/Shared/AppGroup/6CFFA9FC-169B-482E-A817-9C0D2A6F5241/File%20Provider%20Storage/TJ-fixit.kdbx
        // Note the encoding in the uri
        self.common_device_service
            .uri_to_file_name(full_file_name_uri.into())
            .map_or_else(|| "".into(), |s| s)
    }

    pub fn uri_to_file_info(&self, full_file_name_uri: &str) -> Option<FileInfo> {
        let info = remote_storage::uri_to_file_info(full_file_name_uri);
        if info.is_some() {
            return info;
        }

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

    pub fn temp_dir_path() -> PathBuf {
        PathBuf::from(&Self::shared().temp_dir)
    }

    // Root dir where all the private key files of one or more SFTP connections are stored
    pub fn sftp_private_keys_path() -> PathBuf {
        // Sub dir "sftp" should exist
        let p = Self::shared().remote_storage_path.join("sftp");
        p
    }

    pub fn common_device_service_ex() -> &'static dyn CommonDeviceServiceEx {
        Self::shared().common_device_service_ex.as_ref()
    }

    pub fn secure_enclave_cb_service() -> &'static dyn SecureEnclaveCbService {
        Self::shared().secure_enclave_cb_service.as_ref()
    }

    pub fn secure_key_operation() -> &'static dyn SecureKeyOperation {
        Self::shared().secure_key_operation.as_ref()
    }

    // pub fn read_preference(&self) {
    //     let pref = Preference::read(&self.app_home_dir);
    //     // store_pref is MutexGuard
    //     let mut store_pref = self.preference.lock().unwrap();
    //     //sets the preference struct value inside the MutexGuard by dereferencing
    //     *store_pref = pref;
    // }
}

// All fns using the Preference object are implemented here
impl AppState {
    // TODO: Remove dir reference to Preference and route to all calls to preference struct through AppState?
    pub fn preference() -> &'static Mutex<Preference> {
        &Self::shared().preference
    }

    pub fn backup_history_count() -> usize {
        let p = Self::shared().preference.lock().unwrap();
        p.backup_history_count()
    }

    pub fn remove_recent_db_use_info(&self, db_key: &str) {
        let mut pref = self.preference.lock().unwrap();
        pref.remove_recent_db_use_info(db_key);
    }

    pub fn file_name_in_recently_used(&self, db_key: &str) -> Option<String> {
        let pref = self.preference.lock().unwrap();
        find_db_info(&pref, db_key).map(|r| r.file_name.clone())
    }

    
    pub fn set_db_open_biometric(&self, db_key: &str, enabled: bool) {
        let mut pref = self.preference.lock().unwrap();
        pref.set_db_open_biometric(db_key, enabled);
    }

    pub fn db_open_biometeric_enabled(&self, db_key: &str) -> bool {
        self.preference
            .lock()
            .unwrap()
            .db_open_biometeric_enabled(db_key)
    }

    // Finds the recently used info for a given uri
    pub fn get_recently_used(&self, db_key: &str) -> Option<RecentlyUsed> {
        let pref = self.preference.lock().unwrap();
        pref.recent_dbs_info
            .iter()
            .find(|r| r.db_file_path == db_key)
            .map(|r| RecentlyUsed { ..r.clone() })
    }

    pub fn add_recent_db_use_info(&self, db_key: &str) {
        let file_name = self
            .common_device_service
            .uri_to_file_name(db_key.into())
            .map_or_else(|| "".into(), |s| s);

        let recently_used = RecentlyUsed {
            file_name,
            db_file_path: db_key.into(),
            biometric_enabled_db_open: false,
        };

        let mut pref = self.preference.lock().unwrap();
        pref.add_recent_db_use_info(&self.app_home_dir, recently_used);
    }

    // The file_name is given
    // Used with remote storage related FileInfo call
    pub fn add_recent_db_use_info2(&self, db_key: &str, file_name: &str) {
        let recently_used = RecentlyUsed {
            file_name: file_name.into(),
            db_file_path: db_key.into(),
            biometric_enabled_db_open: false,
        };

        let mut pref = self.preference.lock().unwrap();
        pref.add_recent_db_use_info(&self.app_home_dir, recently_used);
    }

    pub fn language(&self) -> String {
        let pref = self.preference.lock().unwrap();
        pref.language.clone()
    }

    pub fn update_preference(&self, preference_data: PreferenceData) {
        let mut store_pref = self.preference.lock().unwrap();
        store_pref.update(preference_data);
    }
}

fn find_db_info<'a>(pref: &'a Preference, db_key: &'a str) -> Option<&'a RecentlyUsed> {
    pref.recent_dbs_info
        .iter()
        .find(|r| r.db_file_path == db_key)
}

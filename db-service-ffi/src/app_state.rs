use log::{debug, error, info};
use once_cell::sync::OnceCell;
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    fs,
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
};

use onekeepass_core::{db_service as kp_service, service_util};

use crate::{
    app_preference::{
        AppLockPreference, DatabasePreference, Preference, PreferenceData, RecentlyUsed,
        PREFERENCE_JSON_FILE_NAME,
    },
    remote_storage,
    udl_types::SecureKeyOperation,
    udl_uniffi_exports::{CommonDeviceServiceEx, SecureEnclaveCbService},
    util,
};
use crate::{
    udl_types::{CommonDeviceService, EventDispatch, FileInfo},
    OkpError, OkpResult,
};

pub(crate) const EXPORT_DATA_DIR: &str = "export_data";

pub(crate) const BACKUPS_DIR: &str = "backups";

pub(crate) const BACKUPS_HIST_DIR: &str = "history";

pub(crate) const REMOTE_STORAGE_DIR: &str = "remote_storage";

pub(crate) const REMOTE_STORAGE_SFTP_SUB_DIR: &str = "sftp";

pub(crate) const OKP_SHARED_DIR: &str = "okp_shared";

pub(crate) const KEY_FILES_DIR: &str = "key_files";

// Any mutable field needs to be behind Mutex
pub struct AppState {
    app_home_dir: String,

    // iOS specific
    app_group_home_dir: Option<String>,

    prefrence_home_dir: PathBuf,

    // Android specific use (in save_attachment_as_temp_file) ?
    cache_dir: String,

    temp_dir: String,

    // We keep last 'n' number of backups for a db that was edited
    backup_history_dir_path: PathBuf,

    // Dir path where all remote storage related files are stored
    remote_storage_path: PathBuf,

    // Dir path where db file for export is created and used in export calls
    export_data_dir_path: PathBuf,

    // Dir where all key files are copied for latter use
    key_files_dir_path: PathBuf,

    // Used to keep the last backup file ref which is used for 'Save as'
    // when db save fails (as the orginal db content changed)
    // This is reset to empty when the app starts
    last_backup_on_error: Mutex<HashMap<String, String>>,

    preference: Mutex<Preference>,

    // Callback service implemented in Swift/Kotlin and called from rust side
    common_device_service: Box<dyn CommonDeviceService>,

    // Callback service implemented in Swift/Kotlin and called from rust side
    event_dispatcher: Arc<dyn EventDispatch>,

    // Callback service implemented in Swift/Kotlin and called from rust side
    secure_key_operation: Box<dyn SecureKeyOperation>,

    common_device_service_ex: Arc<dyn CommonDeviceServiceEx>,

    // This trait is implemented in Swift (class SecureEnclaveServiceSupport) and in Kotlin as callbacks from rust side
    // The trait is a udlffi exported. See src/udl_uniffi_exports.rs
    secure_enclave_cb_service: Arc<dyn SecureEnclaveCbService>,
}

static APP_STATE: OnceCell<AppState> = OnceCell::new();

impl AppState {
    // TODO:
    // This is not yet used
    // Should this be used instead of the one in onekeepass/mobile/about.cljs?
    // There is a possibility we may have separate release number for iOS and Android
    const APP_VERSION: &str = "0.19.0";

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

        // Ensure that preference home dir is available before preference reading
        let preference_home_dir = Self::create_preference_dir_path(&app_dir, &app_group_home_dir);

        // debug!("app_dir {}, cache_dir {}, temp_dir {}, app_group_home_dir {:?},prefrence_home_dir {:?}",&app_dir, &cache_dir, &temp_dir, &app_group_home_dir,&preference_home_dir,);

        // IMPORTANT: 'preference_home_dir' should have a valid path
        let preference = Preference::read(&preference_home_dir);

        let export_data_dir_path = util::create_sub_dir(&app_dir, EXPORT_DATA_DIR);
        log::debug!("export_data_dir_path is {:?}", &export_data_dir_path);

        let backup_history_dir_path =
            util::create_sub_dirs(&app_dir, vec![BACKUPS_DIR, BACKUPS_HIST_DIR]);
        log::debug!("backup_history_dir_path is {:?}", &backup_history_dir_path);

        let key_files_dir_path = Self::form_key_file_path(&app_dir, &app_group_home_dir);
        log::debug!("key_files_dir_path is {:?}", &key_files_dir_path);

        // All remote storage related dirs
        let remote_storage_path = util::create_sub_dir(&app_dir, REMOTE_STORAGE_DIR);
        util::create_sub_dir_path(&remote_storage_path, REMOTE_STORAGE_SFTP_SUB_DIR);

        // As we started storing backup files to the folder 'backups/history' since 0.15.0 rlease
        // we remove the old backup files
        remove_old_0140v_backup_files(&app_dir);

        let app_state = AppState {
            app_home_dir: app_dir.into(),
            app_group_home_dir,
            prefrence_home_dir: preference_home_dir,
            cache_dir,
            temp_dir,
            backup_history_dir_path,
            remote_storage_path,
            export_data_dir_path,
            key_files_dir_path,
            last_backup_on_error: Mutex::new(HashMap::default()),
            preference: Mutex::new(preference),

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

    #[allow(unused_variables)]
    fn form_key_file_path(app_dir: &String, app_group_dir: &Option<String>) -> PathBuf {
        // log::debug!("AppState form_key_file_path is called");

        cfg_if::cfg_if! {
          if #[cfg(target_os="ios")] {
            // in iOS, we should have a valid app group dir by this time and will panic! otherwise

            // e.g dir /private/var/mobile/Containers/Shared/AppGroup/DD57ED77-97ED-4851-88F3-06EAEF19EBCD/
            let app_group_root_dir = app_group_dir.as_ref().unwrap();

            let app_group_key_file_dir =
                util::create_sub_dirs(app_group_root_dir, vec![OKP_SHARED_DIR, KEY_FILES_DIR]);

            // app_dir is something like /var/mobile/Containers/Data/Application/9E0D75F4-4172-429B-9177-E4633F7B96FB/Documents
            let app_root_key_file_dir = Path::new(app_dir).join(KEY_FILES_DIR);

            // log::debug!( "Old app key file dir {:?} and the new app group key files dir {:?} ",&app_root_key_file_dir, &app_group_key_file_dir);

            // Copy all key files from dir like
            // /var/mobile/Containers/Data/Application/9E0D75F4-4172-429B-9177-E4633F7B96FB/Documents/key_files/
            // to
            // /private/var/mobile/Containers/Shared/AppGroup/DD57ED77-97ED-4851-88F3-06EAEF19EBCD/okp_shared/key_files/
            if app_root_key_file_dir.exists() {
                util::copy_files(&app_root_key_file_dir, &app_group_key_file_dir);

                // log::debug!( "Copied key files from app key file dir {:?} to app group key files dir {:?} ", &app_root_key_file_dir, &app_group_key_file_dir);

                let _r = fs::remove_dir_all(&app_root_key_file_dir);
                
                // log::debug!("Removed key files from old app level key root dir {:?} with result  {:?} ",&app_root_key_file_dir, r );

                let old_autofill_key_file_dir = Path::new(app_group_root_dir).join(crate::ios::autofill_app_group::EXTENSION_ROOT_DIR).join(KEY_FILES_DIR);

                let _r = fs::remove_dir_all(&old_autofill_key_file_dir);
                
                // log::debug!("Removed key files from old Autofill key root dir {:?} with result  {:?}", &old_autofill_key_file_dir,r);

            }

            // Both main app and auto fill app share this key_files dir
            app_group_key_file_dir
          } else {
            // For android no changes in the key files dir location
            util::create_sub_dir(&app_dir, KEY_FILES_DIR)
          }
        }
    }

    fn create_preference_dir_path(app_dir: &String, app_group_dir: &Option<String>) -> PathBuf {
        if cfg!(target_os = "ios") {
            // in iOS, we should have a valid app group dir
            let gd = app_group_dir.as_ref().unwrap();
            let new_pref_file_home_dir = util::create_sub_dir(gd, OKP_SHARED_DIR);

            // debug!("Ios: Created preference_dir_path {:?}", &new_pref_file_home_dir);

            let old_pref_file_p = Path::new(app_dir).join(PREFERENCE_JSON_FILE_NAME);

            // Need to copy the prefernce file from old location to new
            if old_pref_file_p.exists() {
                // debug!("Found old preference file at {:?}", &old_pref_file_p);
                let new_pref_file_p = new_pref_file_home_dir.join(PREFERENCE_JSON_FILE_NAME);
                let r = fs::copy(&old_pref_file_p, &new_pref_file_p);

                debug!("Preference file copied with result {:?}", r);

                // Need to delete old pref file
                if r.is_ok() {
                    let _r = fs::remove_file(&old_pref_file_p);
                    // debug!("Old Preference file is deleted with result {:?}", r);
                }
            }

            new_pref_file_home_dir
        } else {
            let pref_file_home_dir = PathBuf::from(app_dir);
            debug!(
                "Android: Created preference_dir_path {:?}",
                &pref_file_home_dir
            );
            pref_file_home_dir
        }
    }

    pub fn app_version() -> &'static str {
        Self::APP_VERSION
    }

    pub fn app_home_dir() -> &'static String {
        &Self::shared().app_home_dir
    }

    pub fn preference_home_dir() -> &'static PathBuf {
        &Self::shared().prefrence_home_dir
    }

    pub fn app_group_home_dir() -> &'static Option<String> {
        &Self::shared().app_group_home_dir
    }

    pub fn key_files_dir_path() -> &'static PathBuf {
        &Self::shared().key_files_dir_path
    }

    pub fn export_data_dir_path() -> &'static PathBuf {
        &Self::shared().export_data_dir_path
    }

    // pub fn backup_dir_path() -> &'static PathBuf {
    //     &Self::shared().backup_dir_path
    // }

    pub fn backup_history_dir_path() -> &'static PathBuf {
        &Self::shared().backup_history_dir_path
    }

    pub fn remote_storage_path() -> &'static PathBuf {
        &Self::shared().remote_storage_path
    }

    pub fn temp_dir_path() -> PathBuf {
        PathBuf::from(&Self::shared().temp_dir)
    }

    pub fn cache_dir() -> &'static String {
        &Self::shared().cache_dir
    }

    // Root dir where all the private key files of one or more SFTP connections are stored
    pub fn sftp_private_keys_path() -> PathBuf {
        // Sub dir "sftp" should exist
        let p = Self::shared()
            .remote_storage_path
            .join(REMOTE_STORAGE_SFTP_SUB_DIR);
        p
    }

    pub fn common_device_service_ex() -> &'static dyn CommonDeviceServiceEx {
        Self::shared().common_device_service_ex.as_ref()
    }

    pub fn event_dispatcher() -> &'static dyn EventDispatch {
        Self::shared().event_dispatcher.as_ref()
    }

    pub fn common_device_service() -> &'static dyn CommonDeviceService {
        Self::shared().common_device_service.as_ref()
    }

    pub fn secure_enclave_cb_service() -> &'static dyn SecureEnclaveCbService {
        Self::shared().secure_enclave_cb_service.as_ref()
    }

    pub fn secure_key_operation() -> &'static dyn SecureKeyOperation {
        Self::shared().secure_key_operation.as_ref()
    }

    ///   

    pub fn add_last_backup_name_on_error(full_file_name_uri: &str, backup_file_full_name: &str) {
        let mut bkp = Self::shared().last_backup_on_error.lock().unwrap();
        bkp.insert(full_file_name_uri.into(), backup_file_full_name.into());
    }

    // Used in android and ios specific module
    // See ios::complete_save_as_on_error and  android::complete_save_as_on_error
    pub fn remove_last_backup_name_on_error(full_file_name_uri: &str) -> Option<String> {
        let mut bkp = Self::shared().last_backup_on_error.lock().unwrap();
        bkp.remove(full_file_name_uri)
    }

    // Used in android and ios specific module
    // See ios::copy_last_backup_to_temp_file, android::complete_save_as_on_error
    pub fn get_last_backup_on_error(full_file_name_uri: &str) -> Option<String> {
        Self::shared()
            .last_backup_on_error
            .lock()
            .unwrap()
            .get(full_file_name_uri)
            .map(|s| s.clone())
    }

    // Called to get the file name from the platform specific full file uri passed as arg 'full_file_name_uri'
    // The uri may start with file: or content: or Sftp or WebDav
    pub fn uri_to_file_name(full_file_name_uri: &str) -> String {
        // Need to find out whether this is a remote storage db_key and then find the file name accordingly
        if let Some(s) = remote_storage::uri_to_file_name(full_file_name_uri) {
            return s.to_string();
        }

        // We use platform specific callback fn to get the file name
        // e.g uri file:///Users/jeyasankar/Library/Developer/CoreSimulator/Devices/A45B3252-1AA4-4D50-9E6E-89AB1E873B1F/data/Containers/Shared/AppGroup/6CFFA9FC-169B-482E-A817-9C0D2A6F5241/File%20Provider%20Storage/TJ-fixit.kdbx
        // Note the encoding in the uri
        Self::common_device_service()
            .uri_to_file_name(full_file_name_uri.into())
            .map_or_else(|| "".into(), |s| s)
    }

    pub fn uri_to_file_info(full_file_name_uri: &str) -> Option<FileInfo> {
        // First we attempt to see whether the 'full_file_name_uri' is meant fo the remote storage 'Sftp' or 'Webdav'
        let info = remote_storage::uri_to_file_info(full_file_name_uri);
        if info.is_some() {
            debug!("Returning RS file info {:?}", &info);
            return info;
        }

        // Uses the iOS or Android service to get the file info as the 'full_file_name_uri' is from
        // the device 'File App' picked url
        let info = Self::shared()
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

// All fns using the Preference object are implemented here
impl AppState {
    // TODO: Remove dir reference to Preference and route to all calls to preference struct through AppState?
    // pub fn preference() -> &'static Mutex<Preference> {
    //     &Self::shared().preference
    // }

    // Creates a default Preference, writes to the json file and AppState is updated with this new pref
    pub(crate) fn reset_preference() {
        let mut store_pref = Self::shared().preference.lock().unwrap();
        let new_pref = Preference::write_default();
        *store_pref = new_pref;
    }

    pub fn preference_clone() -> Preference {
        let store_pref = Self::shared().preference.lock().unwrap();
        store_pref.clone()
    }

    pub fn update_preference(preference_data: PreferenceData) -> OkpResult<()> {
        let mut store_pref = Self::shared().preference.lock().unwrap();
        store_pref.update(preference_data)
    }

    // TODO: Need to change UI side to use 'update_preference' and then deprecate this method
    pub fn update_session_timeout(
        db_session_timeout: Option<i64>,
        clipboard_timeout: Option<i64>,
    ) -> OkpResult<()> {
        let mut pref = Self::shared().preference.lock().unwrap();
        pref.update_session_timeout(db_session_timeout, clipboard_timeout)
    }

    // Updates PIN lock enable / disbale flag and also writes the pref file
    pub(crate) fn update_app_lock_with_pin_enabled(pin_lock_enabled: bool) {
        let mut pref = Self::shared().preference.lock().unwrap();
        pref.update_app_lock_with_pin_enabled(pin_lock_enabled);
    }

    #[inline]
    pub fn backup_history_count() -> u8 {
        Self::shared()
            .preference
            .lock()
            .unwrap()
            .backup_history_count()
    }

    #[inline]
    pub fn language() -> String {
        Self::shared()
            .preference
            .lock()
            .unwrap()
            .language()
            .to_string()
    }

    #[inline]
    pub fn database_preferences() -> Vec<DatabasePreference> {
        Self::shared()
            .preference
            .lock()
            .unwrap()
            .database_preferences()
            .clone()
    }

    #[inline]
    pub fn app_lock_preference() -> AppLockPreference {
        Self::shared()
            .preference
            .lock()
            .unwrap()
            .app_lock_preference()
            .clone()
    }

    // Removes this db related info from recent db info list and also removes this db preference
    #[inline]
    pub fn remove_recent_db_use_info(db_key: &str, delete_db_pref: bool) {
        let mut pref = Self::shared().preference.lock().unwrap();
        pref.remove_recent_db_use_info(db_key, delete_db_pref);
    }

    #[inline]
    pub fn file_name_in_recently_used(db_key: &str) -> Option<String> {
        Self::shared()
            .preference
            .lock()
            .unwrap()
            .file_name_in_recently_used(db_key)
    }

    #[inline]
    pub fn db_open_biometeric_enabled(db_key: &str) -> bool {
        Self::shared()
            .preference
            .lock()
            .unwrap()
            .db_open_biometeric_enabled(db_key)
    }

    // Finds the recently used info for a given uri
    #[inline]
    pub fn get_recently_used(db_key: &str) -> Option<RecentlyUsed> {
        Self::shared()
            .preference
            .lock()
            .unwrap()
            .get_recently_used(db_key)
    }

    // Should be used only for the Local device files
    // See add_recent_db_use_info2 below
    // TODO: Combine these two functions
    pub fn add_recent_db_use_info(db_key: &str) {
        let file_name = Self::shared()
            .common_device_service
            .uri_to_file_name(db_key.into())
            .map_or_else(|| "".into(), |s| s);

        let mut recently_used = RecentlyUsed::default();
        recently_used.file_name = file_name;
        recently_used.db_file_path = db_key.into();

        let mut pref = Self::shared().preference.lock().unwrap();

        pref.add_recent_db_use_info(recently_used);
    }

    // The file_name is given
    // Used with remote storage related FileInfo call
    pub fn add_recent_db_use_info2(db_key: &str, file_name: &str) {
        let mut recently_used = RecentlyUsed::default();
        recently_used.file_name = file_name.into();
        recently_used.db_file_path = db_key.into();

        let mut pref = Self::shared().preference.lock().unwrap();
        pref.add_recent_db_use_info(recently_used);
    }

    pub fn add_recently_used_with_file_info(db_key: &str, file_info: &Option<FileInfo>) {
        
        // debug!( "add_recently_used_with_file_info is called with file_info {:?}",&file_info);
        
        let file_info = if file_info.is_none() {
            Self::uri_to_file_info(db_key)
        } else {
            file_info.clone()
        };

        let mut recently_used = RecentlyUsed::default();
        recently_used.db_file_path = db_key.into();

        if let Some(info) = file_info {
            recently_used.file_name = info.file_name.map_or("".into(), |f| f);
            recently_used.file_size = info.file_size;
            recently_used.last_modified = info.last_modified;
            recently_used.last_accessed = Some(service_util::now_utc_milli_seconds());
            recently_used.location = info.location;
        }

        let mut pref = Self::shared().preference.lock().unwrap();
        pref.add_recent_db_use_info(recently_used);
    }

    #[inline]
    pub fn recent_dbs_info() -> Vec<RecentlyUsed> {
        Self::shared().preference.lock().unwrap().recent_dbs_info()
    }

    // Called to update the modified time after a "save" call
    pub(crate) fn update_recent_db_modified_time(db_key: &str, last_modified: &Option<i64>) {
        let mut pref = Self::shared().preference.lock().unwrap();

        let v = pref.recent_dbs_info_mut_ref();
        let v1 = v.iter_mut().find(|r| r.db_file_path == db_key);
        if let Some(v2) = v1 {
            v2.last_modified = last_modified.clone();
            v2.last_accessed = Some(service_util::now_utc_milli_seconds());
        }
    }

    pub(crate) fn update_recent_db_file_info(db_key: &str) {
        let file_info = Self::uri_to_file_info(db_key);
        let mut pref = Self::shared().preference.lock().unwrap();

        let v = pref.recent_dbs_info_mut_ref();
        let recently_used_opt = v.iter_mut().find(|r| r.db_file_path == db_key);
        if let Some(recently_used) = recently_used_opt {
            if let Some(info) = file_info {
                recently_used.file_size = info.file_size;
                recently_used.last_modified = info.last_modified;
                recently_used.last_accessed = Some(service_util::now_utc_milli_seconds());
            }
        }
    }

    #[inline]
    pub(crate) fn find_db_key(file_name: &str) -> Option<String> {
        Self::shared()
            .preference
            .lock()
            .unwrap()
            .find_db_key(file_name)
    }

    // pub(crate) fn is_file_name_found(file_name: &str) -> bool {
    //     let pref = Self::shared().preference.lock().unwrap();
    //     // Finds any first matching recent dbs with the given file name (not full path)
    //     pref.recent_dbs_info_ref()
    //         .iter()
    //         .find(|d| d.file_name == file_name)
    //         .is_some()
    // }

    // pub fn recent_dbs_info_ref<'a>() ->  std::sync::MutexGuard<'a, Preference> {
    //     Self::shared().preference.lock().unwrap()
    // }

    // pub fn check_file_name_in_recent_dbs_info<F: Fn(&RecentlyUsed) -> bool>(apply:F) {
    //     let p = Self::shared().preference.lock().unwrap();
    //     let f = p.recent_dbs_info_ref();
    //     let d = f.iter().find(|v| apply(v));

    // }
}
// Added in 0.15.0 version as we changed backups creation and location
// Should be removed in later release
fn remove_old_0140v_backup_files(app_dir: &str) {
    let bk_dir_path = Path::new(app_dir).join("backups");
    if bk_dir_path.exists() {
        let _ = util::remove_files(bk_dir_path);
    }
}

// iOS specific idea to move all internal dirs and files from the current app_home dir to app_group home dir
// Also see comments in Swift impl 'CommonDeviceServiceImpl appHoemDir'
fn _temp_move_documents_to_okp_app_dir(_app_dir: &str, _app_group_dir: &Option<String>) {
    // Check if there is the sub dir 'okp_app'  under app_group_dir
    // If the dir is available then, app_group_dir/okp_app is already created
    // Return app_dir,app_group_dir where app_dir = app_group_dir

    // If not, create app_group_dir/okp_app, then move app_dir/[bookmarks,backups,key_files,preference.json] to
    // app_group_dir/okp_app[bookmarks,backups,key_files,preference.json]

    // Remove app_dir/[bookmarks,backups,key_files,preference.json]

    // Return app_dir,app_group_dir where app_dir = app_group_dir
}

/*
fn form_key_file_path_1(app_dir: &String, app_group_dir: &Option<String>) -> PathBuf {
        log::debug!("AppState form_key_file_path is called");

        if cfg!(target_os = "ios") {
            // in iOS, we should have a valid app group dir and will panic! otherwise

            let app_group_root_dir = app_group_dir.as_ref().unwrap();

            let app_group_key_file_dir =
                util::create_sub_dirs(app_group_root_dir, vec![OKP_SHARED_DIR, KEY_FILES_DIR]);

            let app_root_key_file_dir = Path::new(app_dir).join(KEY_FILES_DIR);

            log::debug!(
                "Old app key file dir {:?} and the new app group key files dir {:?} ",
                &app_root_key_file_dir,
                &app_group_key_file_dir
            );

            // Copy all key files from dir like
            // /var/mobile/Containers/Data/Application/9E0D75F4-4172-429B-9177-E4633F7B96FB/Documents/key_files/
            // to
            // /private/var/mobile/Containers/Shared/AppGroup/DD57ED77-97ED-4851-88F3-06EAEF19EBCD/okp_shared/key_files/
            if app_root_key_file_dir.exists() {
                util::copy_files(&app_root_key_file_dir, &app_group_key_file_dir);

                log::debug!(
                    "Copied key files from app key file dir {:?} to app group key files dir {:?} ",
                    &app_root_key_file_dir,
                    &app_group_key_file_dir
                );

                let r = fs::remove_dir_all(&app_root_key_file_dir);
                log::debug!("Removed key files from old app level key root dir {:?} ", r);

                // let old_autofill_key_file_dir = Path::new(app_group_root_dir).join("").join(KEY_FILES_DIR);
            }

            // Both main app and Auto Fill app share this key_files dir
            app_group_key_file_dir
        } else {
            util::create_sub_dir(&app_dir, KEY_FILES_DIR)
        }
    }
*/

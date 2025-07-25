use std::{
    fs::{self, File},
    path::{Path, PathBuf},
};

use log::debug;
use onekeepass_core::db_service::{
    self,
    service_util::{self, now_utc_milli_seconds, string_to_simple_hash},
    EntrySummary,
};
use serde::{Deserialize, Serialize};
use url::Url;

use onekeepass_core::error;

use crate::{
    app_lock,
    app_preference::{AppLockPreference, DatabasePreference},
    app_state::AppState,
    commands::{
        error_json_str, ok_json_str, result_json_str, CommandArg, InvokeResult, ResponseJson,
    },
    parse_command_args_or_err,
    util::{self, remove_dir_contents},
    OkpError, OkpResult,
};

use super::IosApiCallbackImpl;

const EXTENSION_ROOT_DIR: &str = "okp";

pub(crate) const AG_DATA_FILES_DIR: &str = "db_files";

pub(crate) const AG_KEY_FILES_DIR: &str = "key_files";

const META_JSON_FILE_NAME: &str = "autofill_meta.json";

const META_JSON_FILE_VERSION: &str = "2.0.0";

/////////  Some public exposed fns ////////////////

pub(crate) fn delete_copied_autofill_details(db_key: &str) -> OkpResult<()> {
    let db_file_root = app_group_root_sub_dir(AG_DATA_FILES_DIR)?;
    let hash_of_db_key = string_to_simple_hash(&db_key).to_string();

    let group_db_file_dir = Path::new(&db_file_root).join(&hash_of_db_key);

    let r = fs::remove_dir_all(&group_db_file_dir);
    log::debug!(
        "Delete data file dir {:?} result {:?}",
        &group_db_file_dir,
        r
    );

    if util::is_dir_empty(&db_file_root) {
        let app_group_key_file_dir = app_group_root_sub_dir(AG_KEY_FILES_DIR)?;
        let r = fs::remove_dir_all(&app_group_key_file_dir);
        log::debug!(
            "Delete key files dir {:?} result {:?}",
            &group_db_file_dir,
            r
        );
    }
    AutoFillMeta::read().remove_copied_db_info(&db_key);
    Ok(())
}

// If this db is used in Autofill extension, then we need to copy the database (with essentail data and kdf adjusted) when it is
// read or saved

// We need to copy autofill files during read in addition to save time
// so that we can ensure the latest opened database is used in autofill
pub(crate) fn copy_files_to_app_group_on_save_or_read(db_key: &str) {
    // Ensure that we copy files only if this db is used in autofill
    let Some(_) = AutoFillMeta::read().find_copied_dbs_info(&db_key) else {
        return;
    };

    if let Err(e) = copy_files_to_app_group(db_key) {
        log::error!(
            "Unexpected error in copying the files for autofill on save: {} ",
            e
        );
    }
}

// Called during app reset
// Removes all data and key files. Also the autofill config removed
pub(crate) fn remove_all_app_extension_contents() {
    if let Ok(path) = app_group_root_sub_dir(AG_DATA_FILES_DIR) {
        let _ = remove_dir_contents(path);
    }

    if let Ok(path) = app_group_root_sub_dir(AG_KEY_FILES_DIR) {
        let _ = remove_dir_contents(path);
    }

    if let Some(path) = autofill_meta_json_file() {
        let _ = fs::remove_file(path);
    }
}

/////////////////////////////////////////////

// Gets the app extension root
// e.g app_group_root/okp

fn app_extension_root() -> OkpResult<PathBuf> {
    let Some(app_group_home_dir) = AppState::app_group_home_dir() else {
        return Err(OkpError::UnexpectedError(
            "No app group home dir is found".into(),
        ));
    };

    let full_path_dir = Path::new(app_group_home_dir).join(EXTENSION_ROOT_DIR);
    Ok(full_path_dir.to_path_buf())
}

// Creates a sub dir with the given name under the app group root
fn app_group_root_sub_dir(sub_dir_name: &str) -> OkpResult<PathBuf> {
    let app_group_home_dir = app_extension_root()?;
    let p = util::create_sub_dir(app_group_home_dir.to_string_lossy().as_ref(), sub_dir_name);
    Ok(p)
}

// Gets the full path of the autofill specific meta data json file
fn autofill_meta_json_file() -> Option<PathBuf> {
    // let Some(app_group_home_dir) = &AppState::global().app_group_home_dir else {
    //     return None;
    // };

    let Ok(app_group_home_dir) = app_extension_root() else {
        return None;
    };

    let pref_file_name = app_group_home_dir.join(META_JSON_FILE_NAME); //Path::new(app_group_home_dir).join(META_JSON_FILE_NAME);
    debug!("AutoFillMeta is {:?} ", &pref_file_name);

    Some(pref_file_name)
}

// This is called to copy the selected db's data content and all key file used to the shared location for the extension to use
fn copy_files_to_app_group(db_key: &str) -> OkpResult<CopiedDbFileInfo> {
    let file_name = AppState::uri_to_file_name(&db_key);
    debug!("File name from db_file_name  is {} ", &file_name);

    let db_file_root = app_group_root_sub_dir(AG_DATA_FILES_DIR)?;

    let hash_of_db_key = string_to_simple_hash(&db_key).to_string();

    let group_db_file_name = Path::new(&db_file_root)
        .join(&hash_of_db_key)
        .join(&file_name);

    debug!("group_db_file_name is {:?} ", &group_db_file_name);

    db_service::copy_and_write_autofill_ready_db(
        &db_key,
        &group_db_file_name.as_os_str().to_string_lossy(),
    )?;

    // Add and persist the copied file info
    let db_file_path = group_db_file_name.as_os_str().to_string_lossy().to_string();
    let copied_db_info = CopiedDbFileInfo::new(file_name, db_file_path, db_key.to_string());
    AutoFillMeta::read().add_copied_db_info(copied_db_info.clone());

    let app_group_key_file_dir = app_group_root_sub_dir(AG_KEY_FILES_DIR)?;

    // Copies all the key files available
    util::copy_files(&AppState::key_files_dir_path(), &app_group_key_file_dir);

    Ok(copied_db_info)
}

// Possible Enhancement:
// We may add a field that has the original db's checksum and also copy the bookmarked data to app group side
// When autofill is opened, we can verify whether the selected db in autofill is changed after the last copy
// and inform the user accordingly (may be in some background thread or callback from js)
// We need to use a call similar to readKdbx using the copied bookmarked data and calculate checksum and compare

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct CopiedDbFileInfo {
    pub(crate) file_name: String,
    // This is also db_key
    pub(crate) db_file_path: String,
    // This is the original db_key or file url used in the main app
    pub(crate) org_db_file_path: String,
}

impl CopiedDbFileInfo {
    fn new(file_name: String, db_file_path: String, org_db_file_path: String) -> Self {
        Self {
            file_name,
            db_file_path,
            org_db_file_path,
        }
    }
}

// Used for the auto fill meta data persistence
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AutoFillMeta {
    pub version: String,
    pub copied_dbs_info: Vec<CopiedDbFileInfo>,
    // Adding a field to hold last pin auth done
    // This time is used to determine whether to ask PIN auth again or to skip for a predetermined time
    last_pin_auth_success_time: Option<i64>,
}

impl Default for AutoFillMeta {
    fn default() -> Self {
        Self {
            version: META_JSON_FILE_VERSION.to_string(),
            copied_dbs_info: vec![],
            last_pin_auth_success_time: None,
        }
    }
}

impl AutoFillMeta {
    // Reads any previously created config file or creates a default config
    // TODO: Need to keep a copy of AutoFillMeta in memory so that we can avoid reading the file again and again
    fn read() -> Self {
        let Some(pref_file_name) = autofill_meta_json_file() else {
            return Self::default();
        };

        // debug!("AutoFillMeta is {:?} ", &pref_file_name);

        let json_str = fs::read_to_string(pref_file_name).unwrap_or("".into());
        
        // debug!("AutoFillMeta json_str is {}", &json_str);

        let mut af_meta = if json_str.is_empty() {
            // log::info!("AutoFillMeta is empty and default used ");
            Self::default()
        } else {
            serde_json::from_str(&json_str).unwrap_or_else(|_| {
                // If there is any change in struct AutoFillMeta, then we need to load earlier version as done in Preference
                match serde_json::from_str(&json_str) {
                    // Prior version
                    Ok(prev_af_meta @ AutoFillMetaV100 { .. }) => {
                        // debug!( "Returning the new AutoFillMeta with old values from AutoFillMeta100");

                        let new_af_meta: Self = prev_af_meta.into();

                        // debug!("Converted new_af is {:?}", &new_af_meta);

                        // Write the AutoFillMeta json with the copied values from old AutoFillMeta
                        new_af_meta.write_to_app_group_dir();
                        new_af_meta
                    }
                    Err(e) => {
                        // We could not parse the existing json file even to the prior version.
                        // This may happen if the 'autofill_meta.json' found is older than the previous version.
                        // Returns the latest default af_meta
                        debug!(
                            "Returning the default af_meta because of json parsing error {}",
                            e
                        );
                        Self::default()
                    }
                }
            })
        };

        // Ensure that version is updated if required
        let final_af_meta = if af_meta.version == META_JSON_FILE_VERSION {
            // debug!("Version Checked: Returning the current af_meta as version is the latest");
            af_meta
        } else {
            // If the new field added are only Option<> type in AutoFillMeta,
            // then parsing may through with new AutoFillMeta itself, but the version will be old
            af_meta.version = META_JSON_FILE_VERSION.into();
            af_meta.write_to_app_group_dir();
            af_meta
        };

        final_af_meta
    }

    fn write_to_app_group_dir(&self) {
        if let Some(pref_file_name) = autofill_meta_json_file() {
            let json_str_result = serde_json::to_string_pretty(self);
            if let Ok(json_str) = json_str_result {
                // log::debug!("Writing AutoFillMeta file");
                if let Err(err) = fs::write(pref_file_name, json_str.as_bytes()) {
                    log::error!(
                        "AutoFillMeta file write failed and error is {}",
                        err.to_string()
                    );
                }
            }
        };
    }

    fn find_copied_dbs_info(&self, org_db_file_path: &str) -> Option<CopiedDbFileInfo> {
        self.copied_dbs_info
            .iter()
            .find(|v| v.org_db_file_path == org_db_file_path)
            .cloned()
    }

    fn get_copied_dbs_info(&self) -> &Vec<CopiedDbFileInfo> {
        &self.copied_dbs_info
    }

    fn add_copied_db_info(&mut self, copied_db_info: CopiedDbFileInfo) -> &mut Self {
        // First we need to remove any previously added if any
        // Note: We use org_db_file_path instead of db_file_path
        self.copied_dbs_info
            .retain(|s| s.org_db_file_path != copied_db_info.org_db_file_path);

        self.copied_dbs_info.insert(0, copied_db_info);
        // Write the autofillmeta to the file system immediately
        self.write_to_app_group_dir();
        self
    }

    // org_db_file_path is the original file uri of the db that was copied for autofill use
    fn remove_copied_db_info(&mut self, org_db_file_path: &str) {
        self.copied_dbs_info
            .retain(|s| s.org_db_file_path != org_db_file_path);

        // Write the autofillmeta to the file system immediately
        self.write_to_app_group_dir();
    }

    fn last_pin_auth_success_time(&self) -> Option<i64> {
        self.last_pin_auth_success_time
    }

    fn set_last_pin_auth_success_time(&mut self, time: Option<i64>) {
        self.last_pin_auth_success_time = time;
        self.write_to_app_group_dir();
    }
}

#[derive(Clone, Serialize, Deserialize)]
struct AutoFillInitData {
    copied_dbs_info: Vec<CopiedDbFileInfo>,
    database_preferences: Vec<DatabasePreference>,
    app_lock_preference: AppLockPreference,
}

//////////////

// Previous version

#[derive(Clone, Serialize, Deserialize)]
pub struct AutoFillMetaV100 {
    pub version: String,
    pub copied_dbs_info: Vec<CopiedDbFileInfo>,
}

impl From<AutoFillMetaV100> for AutoFillMeta {
    fn from(old: AutoFillMetaV100) -> Self {
        let AutoFillMetaV100 {
            copied_dbs_info,
            version: _,
        } = old;

        let new_af = AutoFillMeta {
            version: META_JSON_FILE_VERSION.to_string(),
            copied_dbs_info,
            last_pin_auth_success_time: None,
        };

        new_af
    }
}

//////////////

// When we use proc-macros (uniffi::export, uniffi::Object etc):
// For generation of Swift code, in addition to udl, we need to specify the lib generated
// so that uuiffi_generate can generate swift code for all macro based exports

// See uniffi-bindgen generate option
// --lib-file <LIB_FILE>  Extract proc-macro metadata from a native lib (cdylib or staticlib) for this crate

// See the targets 'generate-swift-sim-debug', 'generate-swift-device-release' where we use '--lib-file'  option

// #[uniffi::export]
// pub fn my_app_group_init() {
//     debug!("app_group_init is called");
// }

// Corresponding UDL:
// interface IosAppGroupSupportService {};
#[derive(uniffi::Object)]
struct IosAppGroupSupportService {}

// Here we implement all fns of this struct that are not exported as part of 'interface IosAppGroupSupportService'
// To use any of these internal fns in the background.cljs, that fn should be used in  "invoke" fn of this struct- See below
impl IosAppGroupSupportService {
    fn internal_copy_files_to_app_group(&self, json_args: &str) -> OkpResult<CopiedDbFileInfo> {
        let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });
        copy_files_to_app_group(&db_key)
    }

    // Copies the selected db's data content and all key file used to the shared location
    // for the extension to use
    fn copy_files_to_app_group(&self, json_args: &str) -> ResponseJson {
        let d = self.internal_copy_files_to_app_group(json_args);
        result_json_str(d)
    }

    // Called to delete any previously copied data file for this database and removes
    // any reference to this databse in the app group's autofill_meta file
    fn delete_copied_autofill_details(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<()> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });
            delete_copied_autofill_details(&db_key)?;
            Ok(())
        };
        result_json_str(inner_fn())
    }

    // Finds out whether the given database is enabled for autofill or not
    fn query_autofill_db_info(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<Option<CopiedDbFileInfo>> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });
            let r = AutoFillMeta::read().find_copied_dbs_info(&db_key);
            Ok(r)
        };

        result_json_str(inner_fn())
    }

    ////// Used in extension ///////

    fn autofill_init_data(&self) -> ResponseJson {
        let last_time = AutoFillMeta::read().last_pin_auth_success_time();
        let mut app_lock_preference = AppState::app_lock_preference();
        let now = service_util::now_utc_milli_seconds();
        if let Some(t) = last_time {
            if (now - t) <= 60000 {
                app_lock_preference.disable_pin_lock();
            }
        }

        let af_data = AutoFillInitData {
            copied_dbs_info: AutoFillMeta::read().get_copied_dbs_info().clone(),
            database_preferences: AppState::database_preferences(),
            app_lock_preference,
        };
        result_json_str(Ok(af_data))
    }

    fn pin_verify(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<bool> {
            let (pin,) = parse_command_args_or_err!(json_args, AppLockCredentialArg { pin });
            app_lock::pin_verify(pin)
        };

        let r = inner_fn();

        if let Ok(success) = r {
            if success {
                // Store the pin verify success so that we avoid asking PIN again for certain duration
                // Sometime user may need to launch the AutoFill again to fill additional field as iOS
                // window may close after filling initial field
                AutoFillMeta::read()
                    .set_last_pin_auth_success_time(Some(service_util::now_utc_milli_seconds()));
            }
        }

        result_json_str(r)
    }

    // Gets the list of database info that are enabled for autofill
    // fn list_of_autofill_db_infos(&self) -> ResponseJson {
    //     let v = AutoFillMeta::read().get_copied_dbs_info().clone();
    //     result_json_str(Ok(v))
    // }

    // Gets the list of key files (full file path) that may be used to authenticate a selected database in autofill extension
    fn list_of_key_files(&self) -> ResponseJson {
        let inner_fn = || -> OkpResult<Vec<String>> {
            let app_group_key_file_dir = app_group_root_sub_dir(AG_KEY_FILES_DIR)?;
            let v = util::list_dir_files(&app_group_key_file_dir);
            Ok(v)
        };

        result_json_str(inner_fn())
    }

    // Gets the list of all entries in a database that is opened in autofill extension
    fn all_entries_on_db_open(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<Vec<EntrySummary>> {
            let (db_file_name, password, key_file_name, biometric_auth_used) = parse_command_args_or_err!(
                json_args,
                OpenDbArg {
                    db_file_name,
                    password,
                    key_file_name,
                    biometric_auth_used
                }
            );

            // TODO: 
            // Need to share key files between main app and autofill app under agg_group_root/okpshared/key_files
            // instead of app_root/key_files
            // Then we need not adjust the key file path as done here.
            // When we do that, we may need to remove this but add a similar logic in main app
            // to use proper key file path when biometric is used. 
            // Or we may need to update the stored credentials to use the new key files path
            let adjusted_key_file_full_name = if biometric_auth_used && key_file_name.is_some() {
                // We can use unwrap here as key_file_name has some value
                let incoming_kfn = key_file_name.as_ref().unwrap();

                // log::debug!("The incoming full key file name is {} ", &incoming_kfn);

                let key_file_name_part = AppState::common_device_service()
                    .uri_to_file_name(incoming_kfn.clone())
                    .map_or(String::default(), |s| s);

                let app_group_key_file_dir = app_group_root_sub_dir(AG_KEY_FILES_DIR)?;

                let key_file_path = app_group_key_file_dir.join(&key_file_name_part);

                Some(key_file_path.to_string_lossy().to_string())
            } else {
                // When biometric_auth_used is not used, the passed key_file_name value is used as such which may be None or Some value
                key_file_name
            };

            let mut file = File::open(&util::url_to_unix_file_name(&db_file_name))?;
            let file_name = AppState::uri_to_file_name(&db_file_name);

            // First we read the db file
            let kdbx_loaded = db_service::read_kdbx(
                &mut file,
                &db_file_name,
                password.as_deref(),
                adjusted_key_file_full_name.as_deref(),
                // key_file_name.as_deref(),
                Some(&file_name),
            )
            .map_err(|e| match e {
                // Need this when db login fails while using the previously stored credentials
                // and the UI will then popup the usual dialog
                error::Error::HeaderHmacHashCheckFailed if biometric_auth_used => {
                    error::Error::BiometricCredentialsAuthenticationFailed
                }
                _ => e,
            })?;

            let r = db_service::entry_summary_data(
                &kdbx_loaded.db_key,
                db_service::EntryCategory::AllEntries,
            );

            r
        };

        result_json_str(inner_fn())
    }

    fn credential_service_identifier_filtering(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<db_service::EntrySearchResult> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });

            let identifiers =
                IosApiCallbackImpl::api_service().asc_credential_service_identifiers()?;

            // See serviceIdentifiersReceived
            let term = if let Some(domain) = identifiers.get("domain") {
                domain.into()
            } else if let Some(url) = identifiers.get("url") {
                if let Ok(u) = Url::parse(url) {
                    u.host_str()
                        .map_or_else(|| String::default(), |s| s.to_string())
                } else {
                    url.to_string()
                }
            } else {
                String::default()
            };

            debug!("The serviceIdentifiersReceived term is {}", &term);
            let search_result = db_service::search_term(&db_key, &term)?;
            Ok(search_result)
        };

        result_json_str(inner_fn())
    }

    fn clipboard_copy(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<()> {
            let (_field_name, field_value, _protected, cleanup_after) = parse_command_args_or_err!(
                json_args,
                ClipboardCopyArg {
                    field_name,
                    field_value,
                    protected,
                    cleanup_after
                }
            );
            // Delegates to the ios api
            IosApiCallbackImpl::api_service().clipboard_copy_string(field_value, cleanup_after)?;

            Ok(())
        };

        result_json_str(inner_fn())
    }
}

// Here we implement all fns of this struct that are exported
// as part of 'interface IosAppGroupSupportService' and they are bindings
// generated in Swift which are used in ios layer

// All fns implemented here are exported to use in Swift because of '#[uniffi::export]'

#[uniffi::export]
impl IosAppGroupSupportService {
    // Constructors need to be annotated as such.
    // All functions that are not constructors must have a `self` argument
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {}
    }

    pub fn invoke(&self, command_name: &str, json_args: &str) -> ResponseJson {
        let r = match command_name {
            // Used in app
            "copy_files_to_app_group" => self.copy_files_to_app_group(json_args),
            "delete_copied_autofill_details" => self.delete_copied_autofill_details(json_args),
            "query_autofill_db_info" => self.query_autofill_db_info(json_args),

            // Used in extension
            "autofill_init_data" => self.autofill_init_data(),
            "pin_verify" => self.pin_verify(json_args),
            // "list_of_autofill_db_infos" => self.list_of_autofill_db_infos(),
            // "database_preferences" => ok_json_str(AppState::database_preferences()),
            "list_of_key_files" => self.list_of_key_files(),
            "all_entries_on_db_open" => self.all_entries_on_db_open(json_args),
            "credential_service_identifier_filtering" => {
                self.credential_service_identifier_filtering(json_args)
            }

            "clipboard_copy" => self.clipboard_copy(json_args),

            x => error_json_str(&format!(
                "Invalid command or args: Command call {} with args {} failed",
                x, &json_args
            )),
        };

        r
    }
}

// pub(crate) fn app_extension_data_files_dir() -> OkpResult<PathBuf> {
//     // e.g app_group_root/okp/data_files
//     app_group_root_sub_dir(AG_DATA_FILES_DIR)
// }

// pub(crate) fn app_extension_key_files_dir() -> OkpResult<PathBuf> {
//     // e.g app_group_root/okp/key_files
//     app_group_root_sub_dir(AG_KEY_FILES_DIR)
// }

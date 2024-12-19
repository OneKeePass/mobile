use std::{
    fs::{self, File},
    path::{Path, PathBuf},
};

use log::debug;
use onekeepass_core::db_service::{
    self, copy_and_write_autofill_ready_db, service_util::string_to_simple_hash, EntrySummary,
};
use serde::{Deserialize, Serialize};
use url::Url;

use crate::{
    app_state::AppState,
    commands::{error_json_str, result_json_str, CommandArg, InvokeResult, ResponseJson},
    parse_command_args_or_err, util, OkpError, OkpResult,
};

use super::IosApiCallbackImpl;

const AG_DATA_FILES: &str = "db_files";

const AG_KEY_FILES: &str = "key_files";

const META_JSON_FILE_NAME: &str = "autofill_meta.json";

const META_JSON_FILE_VERSION: &str = "1.0.0";

/////////  Some public exposed fns ////////////////

pub(crate) fn delete_copied_autofill_details(db_key: &str) -> OkpResult<()> {
    let db_file_root = app_group_root_sub_dir(AG_DATA_FILES)?;
    let hash_of_db_key = string_to_simple_hash(&db_key).to_string();

    let group_db_file_dir = Path::new(&db_file_root).join(&hash_of_db_key);

    let r = fs::remove_dir_all(&group_db_file_dir);
    log::debug!(
        "Delete data file dir {:?} result {:?}",
        &group_db_file_dir,
        r
    );

    if util::is_dir_empty(&db_file_root) {
        let app_group_key_file_dir = app_group_root_sub_dir(AG_KEY_FILES)?;
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

/////////////////////////////////////////////

fn _temp_delete_old_af_files() {
    let Some(app_group_home_dir) = &AppState::shared().app_group_home_dir else {
        return;
    };
    let pref_file_name = Path::new(app_group_home_dir).join(META_JSON_FILE_NAME);

    // if !pref_file_name.exists() {
    //     return;
    // }

    let _r = fs::remove_file(&pref_file_name);

    debug!("Removed old AutoFillMeta file {:?}  ", &pref_file_name);

    let db_file_root = Path::new(&app_group_home_dir).join(AG_DATA_FILES);
    let _r = fs::remove_dir_all(&db_file_root);
    debug!("Removed old data file dir {:?}  ", &db_file_root);

    let app_group_key_file_dir = Path::new(&app_group_home_dir).join(AG_KEY_FILES);
    let _r = fs::remove_dir_all(&app_group_key_file_dir);
    debug!("Removed old key file dir {:?}  ", &app_group_key_file_dir);
}

fn app_extension_root() -> OkpResult<PathBuf> {
    let Some(app_group_home_dir) = &AppState::shared().app_group_home_dir else {
        return Err(OkpError::UnexpectedError(
            "No app group home dir is found".into(),
        ));
    };

    //temp_delete_old_af_files(); // Need to be removed

    let full_path_dir = Path::new(&app_group_home_dir).join("okp");
    Ok(full_path_dir.to_path_buf())
}

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

fn copy_files_to_app_group(db_key: &str) -> OkpResult<CopiedDbFileInfo> {
    let file_name = AppState::shared().uri_to_file_name(&db_key);
    debug!("File name from db_file_name  is {} ", &file_name);

    let db_file_root = app_group_root_sub_dir(AG_DATA_FILES)?;

    let hash_of_db_key = string_to_simple_hash(&db_key).to_string();

    let group_db_file_name = Path::new(&db_file_root)
        .join(&hash_of_db_key)
        .join(&file_name);

    debug!("group_db_file_name is {:?} ", &group_db_file_name);

    copy_and_write_autofill_ready_db(&db_key, &group_db_file_name.as_os_str().to_string_lossy())?;

    // Add and persist the copied file info
    let db_file_path = group_db_file_name.as_os_str().to_string_lossy().to_string();
    let copied_db_info = CopiedDbFileInfo::new(file_name, db_file_path, db_key.to_string());
    AutoFillMeta::read().add_copied_db_info(copied_db_info.clone());

    let app_group_key_file_dir = app_group_root_sub_dir(AG_KEY_FILES)?;

    // Copies all the key files available
    util::copy_files(
        &AppState::shared().key_files_dir_path,
        &app_group_key_file_dir,
    );

    Ok(copied_db_info)
}

// Possible Enhancement:
// We may add a field that has the original db's checksum and also copy the bookmarked data to app group side
// When autofill is opened, we can verify whether the selected db in autofill is changed after the last copy
// and inform the user accordingly (may be in some background thread or callback from js)
// We need to use a call similar to readKdbx using the copied bookmarked data and calculate checksum and compare

#[derive(Clone, Serialize, Deserialize)]
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
#[derive(Clone, Serialize, Deserialize)]
pub struct AutoFillMeta {
    pub version: String,
    pub copied_dbs_info: Vec<CopiedDbFileInfo>,
}

impl Default for AutoFillMeta {
    fn default() -> Self {
        Self {
            version: META_JSON_FILE_VERSION.to_string(),
            copied_dbs_info: vec![],
        }
    }
}

impl AutoFillMeta {
    fn read() -> Self {
        let Some(pref_file_name) = autofill_meta_json_file() else {
            return Self::default();
        };

        debug!("AutoFillMeta is {:?} ", &pref_file_name);

        let json_str = fs::read_to_string(pref_file_name).unwrap_or("".into());
        debug!("AutoFillMeta json_str is {}", &json_str);
        if json_str.is_empty() {
            debug!("AutoFillMeta json file is empty and default used ");
            Self::default()
        } else {
            // If there is any change in struct AutoFillMeta, then we need to load earlier version as done in Preference
            serde_json::from_str(&json_str).unwrap_or_else(|_| Self::default())
        }
    }

    fn write_to_app_group_dir(&self) {
        if let Some(pref_file_name) = autofill_meta_json_file() {
            let json_str_result = serde_json::to_string_pretty(self);
            if let Ok(json_str) = json_str_result {
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
}

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

    // Finds out whether the given databse is enabled for autofill or not
    fn query_autofill_db_info(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<Option<CopiedDbFileInfo>> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });
            let r = AutoFillMeta::read().find_copied_dbs_info(&db_key);
            Ok(r)
        };

        result_json_str(inner_fn())
    }

    // Gets the list of database info that are enabled for autofill
    fn list_of_autofill_db_infos(&self) -> ResponseJson {
        let v = AutoFillMeta::read().get_copied_dbs_info().clone();
        result_json_str(Ok(v))
    }

    // Gets the list of key files that may be used to authenticate a selected database in autofill extension
    fn list_of_key_files(&self) -> ResponseJson {
        let inner_fn = || -> OkpResult<Vec<String>> {
            let app_group_key_file_dir = app_group_root_sub_dir(AG_KEY_FILES)?;
            let v = util::list_dir_files(&app_group_key_file_dir);
            Ok(v)
        };

        result_json_str(inner_fn())
    }

    // Gets the list of all entries in a database that is opened in autofill extension
    fn all_entries_on_db_open(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<Vec<EntrySummary>> {
            let (db_file_name, password, key_file_name,biometric_auth_success) = parse_command_args_or_err!(
                json_args,
                OpenDbArg {
                    db_file_name,
                    password,
                    key_file_name,
                    biometric_auth_used
                }
            );

            let mut file = File::open(&util::url_to_unix_file_name(&db_file_name))?;
            let file_name = AppState::shared().uri_to_file_name(&db_file_name);

            let kdbx_loaded = db_service::read_kdbx(
                &mut file,
                &db_file_name,
                password.as_deref(),
                key_file_name.as_deref(),
                Some(&file_name),
            )?;

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
                    u.host_str().map_or_else(|| String::default(), |s| s.to_string())
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
            "list_of_autofill_db_infos" => self.list_of_autofill_db_infos(),
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

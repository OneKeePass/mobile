use std::{
    fs,
    path::{Path, PathBuf},
};

use log::debug;
use onekeepass_core::db_service::{copy_and_write_autofill_ready_db, string_to_simple_hash};
use serde::{Deserialize, Serialize};

use crate::{
    app_state::AppState,
    commands::{error_json_str, result_json_str, CommandArg, InvokeResult, ResponseJson},
    parse_command_args_or_err, util, OkpError, OkpResult,
};

const AG_DATA_FILES: &str = "db_files";

const AG_KEY_FILES: &str = "key_files";

const META_JSON_FILE_NAME: &str = "autofill_meta.json";

const META_JSON_FILE_VERSION: &str = "1.0.0";

/////////  Some public exposed fns ////////////////

// Called from app side when a db is removed from the list of dbs shown on the home page
// pub(crate) fn remove_from_copied_db_list(db_key: &str) {
//     AutoFillMeta::read().remove_copied_db_info(db_key);
// }

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

/////////////////////////////////////////////

fn app_group_root_sub_dir(sub_dir_name: &str) -> OkpResult<PathBuf> {
    let Some(app_group_home_dir) = &AppState::global().app_group_home_dir else {
        return Err(OkpError::UnexpectedError(
            "No app group home dir is found".into(),
        ));
    };
    let p = util::create_sub_dir(app_group_home_dir, sub_dir_name);

    Ok(p)
}

fn autofill_meta_json_file() -> Option<PathBuf> {
    let Some(app_group_home_dir) = &AppState::global().app_group_home_dir else {
        return None
    };
    
    let pref_file_name = Path::new(app_group_home_dir).join(META_JSON_FILE_NAME);
        debug!("AutoFillMeta is {:?} ", &pref_file_name);

    Some(pref_file_name)
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
        // let Some(app_group_home_dir) = &AppState::global().app_group_home_dir else {
        //     // This else clause should not happen!
        //     return Self::default();
        // };

        // let pref_file_name = Path::new(app_group_home_dir).join(META_JSON_FILE_NAME);
        


        let Some(pref_file_name) = autofill_meta_json_file() else  {
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

    // fn write(&self, app_group_home_dir: &str) {
    //     let json_str_result = serde_json::to_string_pretty(self);
    //     let pref_file_name = Path::new(app_group_home_dir).join(META_JSON_FILE_NAME);
    //     if let Ok(json_str) = json_str_result {
    //         if let Err(err) = fs::write(pref_file_name, json_str.as_bytes()) {
    //             log::error!(
    //                 "AutoFillMeta file write failed and error is {}",
    //                 err.to_string()
    //             );
    //         }
    //     }
    // }

    fn write_to_app_group_dir(&self) {
        // if let Some(app_group_home_dir) = &AppState::global().app_group_home_dir {
        //     self.write(&app_group_home_dir);
        // };

        if let Some(pref_file_name) = autofill_meta_json_file()   {
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
impl IosAppGroupSupportService {
    fn internal_copy_files_to_app_group(&self, json_args: &str) -> OkpResult<CopiedDbFileInfo> {
        let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });

        //let vals1 = command_ok_vals!(json_args,EntrySummaryArg {db_key,entry_category});

        let file_name = AppState::global().uri_to_file_name(&db_key);
        debug!("File name from db_file_name  is {} ", &file_name);

        let db_file_root = app_group_root_sub_dir(AG_DATA_FILES)?;

        let hash_of_db_key = string_to_simple_hash(&db_key).to_string();

        let group_db_file_name = Path::new(&db_file_root)
            .join(&hash_of_db_key)
            .join(&file_name);

        debug!("group_db_file_name is {:?} ", &group_db_file_name);

        copy_and_write_autofill_ready_db(
            &db_key,
            &group_db_file_name.as_os_str().to_string_lossy(),
        )?;

        // Add and persist the copied file info
        let db_file_path = group_db_file_name.as_os_str().to_string_lossy().to_string();
        let copied_db_info = CopiedDbFileInfo::new(file_name, db_file_path, db_key);
        AutoFillMeta::read().add_copied_db_info(copied_db_info.clone());

        let app_group_key_file_dir = app_group_root_sub_dir(AG_KEY_FILES)?;

        util::copy_files(
            &AppState::global().key_files_dir_path,
            &app_group_key_file_dir,
        );

        Ok(copied_db_info)
    }

    fn copy_files_to_app_group(&self, json_args: &str) -> ResponseJson {
        let d = self.internal_copy_files_to_app_group(json_args);
        result_json_str(d)
    }

    fn delete_copied_autofill_details(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<()> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });
            delete_copied_autofill_details(&db_key)?;
            Ok(())
        };
        result_json_str(inner_fn())
    }

    fn query_autofill_db_info(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<Option<CopiedDbFileInfo>> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });
            let r = AutoFillMeta::read().find_copied_dbs_info(&db_key);
            Ok(r)
        };

        result_json_str(inner_fn())
    }
}

// Here we implement all fns of this struct that are exported
// as part of 'interface IosAppGroupSupportService' and they are bindings
// generated in Swift which are used in ios layer

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
            "copy_files_to_app_group" => self.copy_files_to_app_group(json_args),
            "delete_copied_autofill_details" => self.delete_copied_autofill_details(json_args),
            "query_autofill_db_info" => self.query_autofill_db_info(json_args),
            x => error_json_str(&format!("Invalid command or args: Command call {} with args {} failed",x,&json_args)),
        };

        r
    }
    /*
    pub fn copy_files_to_app_group(&self, json_args: &str) -> ResponseJson {
        let d = self.internal_copy_files_to_app_group(json_args);
        result_json_str(d)
    }

    pub fn delete_copied_autofill_details(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<()> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });
            delete_copied_autofill_details(&db_key)?;
            Ok(())
        };
        result_json_str(inner_fn())
    }

    pub fn query_autofill_db_info(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<Option<CopiedDbFileInfo>> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });
            let r = AutoFillMeta::read().find_copied_dbs_info(&db_key);
            Ok(r)
        };

        result_json_str(inner_fn())
    }
    */
}

/*
pub fn delete_app_group_files(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<()> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });

            let db_file_root = app_group_root_sub_dir(AG_DATA_FILES)?;
            let hash_of_db_key = string_to_simple_hash(&db_key).to_string();

            let group_db_file_dir = Path::new(&db_file_root).join(&hash_of_db_key);

            let r = fs::remove_dir_all(&group_db_file_dir);
            log::debug!(
                "Delete data file dir {:?} result {:?}",
                &group_db_file_dir,
                r
            );

            // for entry in db_file_root.read_dir().unwrap() {
            //     debug!("Entry is {:?}",entry);
            // }

            // if let Ok(entries) = db_file_root.read_dir() {
            //     let mut cnt = 0;
            //     // In simulator, macOS creates ".DS_Store" file and we need to ignore
            //     // this file to check whether the dir is empty or not
            //     // In iOS, this meta file is not created
            //     for e in entries {
            //         if let Ok(v) = e {
            //             debug!("File name under data dir is {:?}",v.file_name());
            //             if v.file_name() == ".DS_Store" {
            //                 continue;
            //             } else {
            //                 cnt +=1 ;
            //                 break;
            //             }
            //         }
            //     }
            //     if cnt == 0 {
            //         let app_group_key_file_dir = app_group_root_sub_dir(AG_KEY_FILES)?;
            //         let _r = fs::remove_dir_all(&app_group_key_file_dir);
            //         debug!("Removed key files ....")
            //     }
            // }

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
        };

        result_json_str(inner_fn())
    }

*/

/*
fn create_sub_dir(dir_name:&str) -> OkpResult<PathBuf>  {
    let Some(app_group_home_dir) = &AppState::global().app_group_home_dir else {
        return Err(OkpError::UnexpectedError(
            "No app group home dir is found".into(),
        ));
    };
    debug!("App group home dir is {:?} ", &app_group_home_dir);

    let db_file_root = Path::new(&app_group_home_dir).join(dir_name);
    // Ensure that the parent dir exists
    if !db_file_root.exists() {
        if let Err(e) = std::fs::create_dir_all(&db_file_root) {
            log::error!("Directory creation under app group home dir failed {:?}", e);
            return Err(OkpError::UnexpectedError(format!(
                "Directory creation under app group home dir failed {:?}",
                e
            )));
        }
        log::debug!("Created dir {:?}", &db_file_root);
    }
    Ok(db_file_root)
}

*/

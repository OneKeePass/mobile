#[cfg(target_os = "ios")]
pub(crate) mod app_group;
#[cfg(target_os = "ios")]
pub use app_group::*;

#[cfg(target_os = "ios")]
pub(crate) mod callback_services;
#[cfg(target_os = "ios")]
pub(crate) use callback_services::*;


use crate::app_state::AppState;
use crate::commands::{
    error_json_str, remove_app_files, result_json_str, CommandArg, InvokeResult, ResponseJson,
};
use crate::{open_backup_file, util, OkpError, OkpResult};

use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

use log::debug;
use onekeepass_core::db_content::AttachmentHashValue;
use onekeepass_core::db_service::{self, string_to_simple_hash};
use regex::{Regex, RegexSet};

// This is for any iOS specific services
pub struct IosSupportService {}

// Created in iOS side native module and then iOS specific
// support methods are called
#[cfg(target_os = "ios")]
impl IosSupportService {
    pub fn new() -> Self {
        Self {}
    }

    // The implementation for the udl defined fn "boolean save_book_mark_data(string url,sequence<u8> data)"
    // Called in case of iOS app, to save the secure url bookmarking data
    // Note: At this time both kdbx and the key file uri bookmarking use the same way
    pub fn save_book_mark_data(&self, url: String, data: Vec<u8>) -> bool {
        let file_name = string_to_simple_hash(&url).to_string();

        let book_mark_file_root = Path::new(&AppState::global().app_home_dir).join("bookmarks");
        // Ensure that the parent dir exists
        if !book_mark_file_root.exists() {
            if let Err(e) = std::fs::create_dir_all(&book_mark_file_root) {
                log::error!("Bookmark root dir creation failed {:?}", e);
                return false;
            }
        }

        let book_mark_file_path = book_mark_file_root.join(file_name);

        log::info!(
            "Book mark path to save data is {:?} and size of data {}",
            book_mark_file_path,
            data.len()
        );

        match File::create(book_mark_file_path) {
            Ok(mut f) => match f.write(&data) {
                Ok(_) => true,
                Err(e) => {
                    log::error!("Bookmarks saving failed with error: {:?}", e);
                    false
                }
            },
            Err(e) => {
                log::error!("Bookmarks saving failed with error: {:?}", e);
                false
            }
        }
    }

    // Implements the udl defined fn - sequence<u8> load_book_mark_data(string url);
    pub fn load_book_mark_data(&self, url: String) -> Vec<u8> {
        let file_name = string_to_simple_hash(&url).to_string();
        log::debug!(
            "load_book_mark_data is called for url {} and its hash is {} ",
            &url,
            &file_name
        );
        let book_mark_file_path = Path::new(&AppState::global().app_home_dir)
            .join("bookmarks")
            .join(file_name);
        log::info!("Book mark path to load data is {:?}", book_mark_file_path);
        let mut data = vec![];
        match File::open(book_mark_file_path) {
            Ok(mut f) => match f.read_to_end(&mut data) {
                Ok(_) => data,
                Err(e) => {
                    log::error!("Bookmarks reading failed with error: {:?}", e);
                    vec![]
                }
            },
            Err(e) => {
                log::error!("Bookmarks reading failed with error: {:?}", e);
                vec![]
            }
        }
    }

    // Called to delete any previous bookmark data.
    // For now mainly used after saving any key file to a user selected location
    pub fn delete_book_mark_data(&self, full_file_name_uri: &str) {
        delete_book_mark_data(&full_file_name_uri);
    }

    // Called to copy the backup file when user wants to do "Save as" operation 
    // instead of overwriting or discarding the current db
    pub fn copy_last_backup_to_temp_file(
        &self,
        kdbx_file_name: String,
        full_file_name_uri: String,
    ) -> Option<String> {
        if let Some(bkp_file_name) =
            AppState::global().get_last_backup_on_error(&full_file_name_uri)
        {
            let mut temp_file = std::env::temp_dir();
            // kdbx_file_name is the suggested file name to use in the Docuemnt picker
            // and user may change the name
            temp_file.push(kdbx_file_name);
            // Copies backup file to temp file with proper kdbx file name
            if std::fs::copy(&bkp_file_name, &temp_file).is_err() {
                log::error!("Copying the last backup to temp file failed ");
                return None;
            }
            let mut temp_name = temp_file.as_os_str().to_string_lossy().to_string();
            to_ios_file_uri(&mut temp_name);
            Some(temp_name)
        } else {
            // We are assuming there is a backup file written before this call
            // TODO:
            //  To be failsafe, we may need to write from the db content to temp file
            //  using db_service::save_kdbx_to_writer in case there is no backup at all
            None
        }
    }

    pub fn complete_save_as_on_error(&self, json_args: &str) -> String {
        let inner_fn = || -> OkpResult<db_service::KdbxLoaded> {
            if let Ok(CommandArg::SaveAsArg { db_key, new_db_key }) =
                serde_json::from_str(json_args)
            {
                let kdbx_loaded = db_service::rename_db_key(&db_key, &new_db_key)?;
                // Need to ensure that the checksum is reset to the newly saved file
                // Otherwise, Save error modal dialog will popup !
                let bkp_file_opt = AppState::global().get_last_backup_on_error(&db_key);
                if let Some(mut bkp_file) = open_backup_file(bkp_file_opt) {
                    db_service::calculate_db_file_checksum(&new_db_key, &mut bkp_file)?;
                } else {
                    log::error!("Expected backup file is not found. 'Save as' should have this");
                    return Err(OkpError::DataError("Expected backup file is not found"));
                }

                // AppState::global().remove_recent_db_use_info(&db_key);
                remove_app_files(&db_key);
                AppState::global().add_recent_db_use_info(&new_db_key);
                AppState::global().remove_last_backup_name_on_error(&db_key);

                Ok(kdbx_loaded)
            } else {
                Err(OkpError::UnexpectedError(format!(
                    "Call complete_save_as_on_error failed due Invalid args {}",
                    json_args
                )))
            }
        };
        InvokeResult::from(inner_fn()).json_str()
    }

    //
    // pub fn copy_file_to_app_group(&self, json_args: &str) -> ResponseJson {
    //     debug!(
    //         "copy_file_to_app_group received as json_args {}",
    //         &json_args
    //     );

    //     let Ok(CommandArg::DbKey { db_key }) = serde_json::from_str::<CommandArg>(json_args) else {
    //         return error_json_str(&format!(
    //             "Unexpected argument {:?} for copy_file_to_app_group api call",
    //             json_args
    //         ));
    //     };

    //     /////
    //     let Some(app_group_home_dir) = &AppState::global().app_group_home_dir else {
    //         return error_json_str("No app group home dir is found");
    //     };

    //     debug!("App group home dir is {:?} ", &app_group_home_dir);

    //     let db_file_root = Path::new(&app_group_home_dir).join("db_files");
    //     // Ensure that the parent dir exists
    //     if !db_file_root.exists() {
    //         if let Err(e) = std::fs::create_dir_all(&db_file_root) {
    //             log::error!("Directory creation under app group home dir failed {:?}", e);
    //             return error_json_str(&format!(
    //                 "Directory creation under app group home dir failed {:?}",
    //                 e
    //             ));
    //         }
    //         log::debug!("Created dir {:?}", &db_file_root);
    //     }

    //     let file_name = AppState::global().uri_to_file_name(&db_key);
    //     debug!("File name from db_file_name  is {} ", &file_name);

    //     let group_db_file_name = Path::new(&db_file_root).join(&file_name);
    //     ////

    //     let Some(backup_file_name) = util::generate_backup_file_name(&db_key, &file_name) else {
    //         return error_json_str("Getting backup file name failed");
    //     };

    //     if std::fs::copy(&backup_file_name, &group_db_file_name).is_err() {
    //         log::error!("Copying the backup to group data file failed");
    //         return error_json_str("Copying the backup to group data file failed");
    //     }

    //     debug!(
    //         "Database file {:?} copied to the group dir",
    //         &group_db_file_name
    //     );

    //     crate::commands::result_json_str(Ok(()))
    // }

    // pub fn list_app_group_db_files(&self) -> ResponseJson {
    //     InvokeResult::with_ok(list_app_group_db_files()).json_str()
    // }

    // pub fn read_kdbx_from_app_group(&self, json_args: &str) -> ResponseJson {
    //     result_json_str(self.internal_read_kdbx_from_app_group(&json_args))
    // }

    // fn internal_read_kdbx_from_app_group(
    //     &self,
    //     json_args: &str,
    // ) -> OkpResult<db_service::KdbxLoaded> {
    //     let CommandArg::OpenDbArg {
    //         db_file_name,
    //         password,
    //         key_file_name,
    //     } = serde_json::from_str(&json_args)?
    //     else {
    //         return Err(OkpError::UnexpectedError(format!(
    //             "Argument 'json_args' {:?} parsing failed for readkdbx api call",
    //             json_args
    //         )));
    //     };
    //     let mut file = File::open(&util::url_to_unix_file_name(&db_file_name))?;
    //     let file_name = AppState::global().uri_to_file_name(&db_file_name);

    //     let kdbx_loaded = db_service::read_kdbx(
    //         &mut file,
    //         &db_file_name,
    //         password.as_deref(),
    //         key_file_name.as_deref(),
    //         Some(&file_name),
    //     )?;

    //     Ok(kdbx_loaded)
    // }

    // pub fn all_entries_on_db_open(&self, json_args: &str) -> ResponseJson {
    //     let Ok(kdbx_loaded) = self.internal_read_kdbx_from_app_group(json_args) else {
    //         return error_json_str(&format!(
    //             "Opening databse failed from the app group location"
    //         ));
    //     };
    //     let r = db_service::entry_summary_data(
    //         &kdbx_loaded.db_key,
    //         db_service::EntryCategory::AllEntries,
    //     );
    //     result_json_str(r)
    // }


}

// Dummy implemenation
#[cfg(target_os = "android")]
impl IosSupportService {
    pub fn new() -> Self {
        unimplemented!();
    }

    pub fn save_book_mark_data(&self, _url: String, _data: Vec<u8>) -> bool {
        unimplemented!();
    }

    pub fn load_book_mark_data(&self, _url: String) -> Vec<u8> {
        unimplemented!();
    }

    pub fn delete_book_mark_data(&self, _full_file_name_uri: &str) {
        unimplemented!();
    }

    pub fn copy_last_backup_to_temp_file(
        &self,
        _kdbx_file_name: String,
        _full_file_name_uri: String,
    ) -> Option<String> {
        unimplemented!();
    }

    pub fn complete_save_as_on_error(&self, _json_args: &str) -> String {
        unimplemented!();
    }
}

#[cfg(target_os = "ios")]
fn form_bookmark_file_path(full_file_name_uri: &str) -> PathBuf {
    let file_name = string_to_simple_hash(&full_file_name_uri).to_string();
    let book_mark_file_path = Path::new(&AppState::global().app_home_dir)
        .join("bookmarks")
        .join(file_name);

    log::info!("Book mark file path is {:?}", book_mark_file_path);
    book_mark_file_path
}

#[cfg(target_os = "ios")]
pub fn delete_book_mark_data(full_file_name_uri: &str) {
    let book_mark_file_path = form_bookmark_file_path(full_file_name_uri);
    let r = fs::remove_file(book_mark_file_path);
    log::debug!(
        "Delete bookmark file for the file full_file_name_uri {} result {:?}",
        full_file_name_uri,
        r
    );
}

#[cfg(target_os = "ios")]
pub fn get_app_group_root() -> Option<String> {
    let Some(app_group_home_dir) = &AppState::global().app_group_home_dir else {
        log::error!("No app group home dir is found");
        return None;
    };

    debug!("App group home dir is {:?} ", &app_group_home_dir);

    let db_file_root = Path::new(&app_group_home_dir).join("db_files");
    // Ensure that the parent dir exists
    if !db_file_root.exists() {
        if let Err(e) = std::fs::create_dir_all(&db_file_root) {
            log::error!("Directory creation under app group home dir failed {:?}", e);
            return None;
        }
        log::debug!("Created dir {:?}", &db_file_root);
    }
    db_file_root.to_str().map(|s| s.to_string())
}

#[cfg(target_os = "ios")]
pub fn list_app_group_db_files() -> Vec<String> {
    let Some(app_root_path) = get_app_group_root() else {
        return vec![];
    };
    util::list_dir_files(&Path::new(&app_root_path))
}

pub fn list_bookmark_files() -> Vec<String> {
    if cfg!(target_os = "ios") {
        util::list_dir_files(&Path::new(&AppState::global().app_home_dir).join("bookmarks"))
    } else {
        vec![]
    }
}

// The rust side file path should have prefix "file;//" to use in Swift side,
#[inline]
pub fn to_ios_file_uri(full_file_path: &mut String) {
    if !full_file_path.starts_with("file://") {
        full_file_path.insert_str(0, "file://")
    }
}

pub fn to_ios_file_uri_str(file_path: &Option<String>) -> Option<String> {
    file_path
        .clone()
        .as_mut()
        .map(|s| {
            to_ios_file_uri(s);
            s
        })
        .as_deref()
        .cloned()
}

// Generates a hash key based on the bytes data
// fn to_hash(data: &[u8]) -> u64 {
//     let mut hasher = DefaultHasher::new();
//     hasher.write(data);
//     hasher.finish()
// }

#[cfg(target_os = "ios")]
pub fn extract_file_provider(full_file_name_uri: &str) -> String {
    let data = IosSupportService {}.load_book_mark_data(full_file_name_uri.into());
    parse_bookmark_data(data)
}

#[cfg(target_os = "ios")]
const FILE_PROVIDER_IDS: [&str; 8] = [
    r"com.apple.FileProvider.LocalStorage",
    r"com.apple.CloudDocs.MobileDocumentsFileProvider",
    r"com.getdropbox.Dropbox.FileProvider",
    r"com.microsoft.skydrive.onedrivefileprovider",
    r"com.google.Drive.FileProviderExtension",
    r"com.pcloud.pcloud.FileProvider",
    r"net.box.BoxNet.documentPickerFileProvider",
    r"com.apple.filesystems.UserFS.FileProvider",
];

#[cfg(target_os = "ios")]
const FILE_PROVIDER_NAMES: [&str; 8] = [
    "On My Device",
    "iCloud Drive",
    "Dropbox",
    "OneDrive",
    "Google Drive",
    "pCloud",
    "Box",
    "USB drive",
];

#[cfg(target_os = "ios")]
pub fn parse_bookmark_data(data: Vec<u8>) -> String {
    let re = RegexSet::new(&FILE_PROVIDER_IDS).unwrap();
    let s = String::from_utf8_lossy(&data);
    // println!("s is {}", s);
    // println!("Match {:?}", re.is_match(&s));
    let matches: Vec<_> = re.matches(&s).into_iter().collect();
    log::debug!("Matches {:?}, {:?}", matches, matches.first());

    let location_name = matches
        .first()
        .and_then(|i| FILE_PROVIDER_NAMES.get(*i))
        .map_or("Cloud storage / Another app", |s| s);

    location_name.to_string()
}

pub fn save_attachment_as_temp_file(
    db_key: &str,
    name: &str,
    data_hash: &AttachmentHashValue,
) -> OkpResult<String> {
    db_service::save_attachment_as_temp_file(db_key, name, data_hash)
}

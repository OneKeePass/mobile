#[cfg(target_os = "ios")]
pub(crate) mod autofill_app_group;
#[cfg(target_os = "ios")]
pub use autofill_app_group::*;

#[cfg(target_os = "ios")]
pub(crate) mod callback_services;
#[cfg(target_os = "ios")]
pub(crate) use callback_services::*;

#[cfg(target_os = "ios")]
pub(crate) mod support_services;

use crate::app_state::AppState;
use crate::commands::{
    error_json_str, remove_app_files, result_json_str, CommandArg, InvokeResult, ResponseJson,
};
use crate::{backup, open_backup_file, util, OkpError, OkpResult};

use std::fs::{self, File};
use std::io::{Read, Seek, Write};
use std::path::{Path, PathBuf};

use log::debug;
use onekeepass_core::db_content::AttachmentHashValue;
use onekeepass_core::db_service::{self, service_util::string_to_simple_hash};
use regex::{Regex, RegexSet};

#[cfg(target_os = "ios")]
fn form_bookmark_file_path(full_file_name_uri: &str) -> PathBuf {
    let file_name = string_to_simple_hash(&full_file_name_uri).to_string();
    let book_mark_file_path = Path::new(AppState::app_home_dir())
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
    let Some(app_group_home_dir) = AppState::app_group_home_dir() else {
        log::error!("No app group home dir is found");
        return None;
    };

    debug!("App group home dir is {:?} ", &app_group_home_dir);

    let db_file_root = Path::new(app_group_home_dir).join("db_files");
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
        util::list_dir_files(&Path::new(AppState::app_home_dir()).join("bookmarks"))
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
    let data =
        support_services::IosSupportService {}.load_book_mark_data(full_file_name_uri.into());
    parse_bookmark_data(data)
}

const IOS_FP_SIZE: usize = 20;

#[cfg(target_os = "ios")]
const FILE_PROVIDER_IDS: [&str; IOS_FP_SIZE] = [
    r"com.apple.FileProvider.LocalStorage",
    r"com.apple.CloudDocs.iCloudDriveFileProvider",
    r"com.apple.CloudDocs.MobileDocumentsFileProvider", // legacy one
    //
    r"com.getdropbox.Dropbox.FileProvider",
    r"com.microsoft.skydrive.onedrivefileprovider",
    r"com.google.Drive.FileProviderExtension",
    //
    r"com.pcloud.pcloud.FileProvider",
    r"net.box.BoxNet.documentPickerFileProvider",
    r"com.pcloud.pcloud.FileProvider",
    //
    r"it.twsweb.Nextcloud.File-Provider-Extension",
    r"com.owncloud.ios-app.ownCloud-File-Provider",
    r"com.apple.filesystems.UserFS.FileProvider",
    //
    r"mega.ios.MEGAPickerFileProvider",
    r"org.cryptomator.ios.fileprovider",
    r"com.synology.DSdrive.FileProvider",
    //
    r"ch.protonmail.drive.fileprovider",
    r"com.sync.mobileapp.NewFileProvider",
    r"ru.yandex.disk.filesext",
    //
    r"idrive",
    r"IDrive",
];

#[cfg(target_os = "ios")]
const FILE_PROVIDER_NAMES: [&str; IOS_FP_SIZE] = [
    "On My Device",
    "iCloud Drive",
    "iCloud Drive",
    //
    "Dropbox",
    "OneDrive",
    "Google Drive",
    //
    "pCloud",
    "Box",
    "pCloud",
    //
    "Next Cloud",
    "Own Cloud",
    "USB drive",
    //
    "MEGA",
    "Cryptomator",
    "Synology Drive",
    //
    "Proton Drive",
    "Sync.com",
    "Yandex Disk",
    //
    "IDrive",
    "IDrive",
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

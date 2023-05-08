use crate::app_state::AppState;
use crate::util;
use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

use onekeepass_core::db_service::string_to_simple_hash;
use regex::{Regex, RegexSet};

// This is for any iOS specific services
pub struct IosSupportService {}

#[cfg(target_os = "ios")]
impl IosSupportService {
    // Called in iOS side native module and then iOS specific
    // support methods are called
    pub fn new() -> Self {
        Self {}
    }

    // The implementation for the udl defined fn "boolean save_book_mark_data(string url,sequence<u8> data)"
    // Called in case of iOS app, to save the secure url bookmarking data
    pub fn save_book_mark_data(&self, url: String, data: Vec<u8>) -> bool {
        let file_name = string_to_simple_hash(&url).to_string();
        log::debug!(
            "save_book_mark_data is called for url {} and its hash is {} ",
            &url,
            &file_name
        );
        let book_mark_file_root = Path::new(&AppState::global().app_home_dir).join("bookmarks");
        // Ensure that the parent dir exists
        // if let Some(p) = Path::new(&book_mark_file_root) {
        //     if !p.exists() {
        //         if let Err(e) = std::fs::create_dir_all(p) {
        //             log::error!("Bookmark root dir creation failed {:?}",e);
        //             return false;
        //         }
        //     }
        // }

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
}

// Dummy implemenation
#[cfg(target_os = "android")]
impl IosSupportService {
    pub fn new() -> Self {
        unimplemented!();
    }

    pub fn save_book_mark_data(&self, url: String, data: Vec<u8>) -> bool {
        unimplemented!();
    }

    pub fn load_book_mark_data(&self, url: String) -> Vec<u8> {
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

pub fn list_bookmark_files() -> Vec<String> {
    if cfg!(target_os = "ios") {
        util::list_dir_files(&Path::new(&AppState::global().app_home_dir).join("bookmarks"))
    } else {
        vec![]
    }
}

#[inline]
pub fn to_ios_file_uri(full_file_path: &mut String) {
    if !full_file_path.starts_with("file://") {
        full_file_path.insert_str(0, "file://")
    }
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

use crate::app_state::AppState;

use std::fs::{self, File};
use std::io::{Read, Seek, Write};
use std::path::{Path, PathBuf};

use log::debug;
use onekeepass_core::db_service::{self, service_util::string_to_simple_hash};

const BOOK_MARK_FILES_DIR: &str = "bookmarks";

pub(crate) fn bookmark_dir() -> PathBuf {
    Path::new(AppState::app_home_dir()).join(BOOK_MARK_FILES_DIR)
}

pub(crate) fn delete_book_mark_data(full_file_name_uri: &str) {
    let book_mark_file_path = form_bookmark_file_path(full_file_name_uri);
    let r = fs::remove_file(book_mark_file_path);
    log::debug!(
        "Delete bookmark file for the file full_file_name_uri {} result {:?}",
        full_file_name_uri,
        r
    );
}

fn form_bookmark_file_path(full_file_name_uri: &str) -> PathBuf {
    let file_name = string_to_simple_hash(&full_file_name_uri).to_string();
    let book_mark_file_path = Path::new(AppState::app_home_dir())
        .join(BOOK_MARK_FILES_DIR)
        .join(file_name);

    log::info!("Book mark file path is {:?}", book_mark_file_path);
    book_mark_file_path
}

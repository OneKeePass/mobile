#[cfg(target_os = "android")]
pub(crate) mod callback_services;
#[cfg(target_os = "android")]
pub(crate) use callback_services::*;

#[cfg(target_os = "android")]
pub(crate) mod support_services;

use std::fs::OpenOptions;
use std::io::{Read, Seek, Write};
use std::path::Path;
use std::process::CommandArgs;
use std::{fs::File, os::fd::IntoRawFd};

use crate::commands::ResponseJson;
use crate::{
    app_state::AppState,
    as_api_response,
    commands::{remove_app_files, CommandArg, InvokeResult},
    util, ApiResponse, OkpError, OkpResult,
};
use crate::{backup, commands, open_backup_file, return_api_response_failure};
use log::debug;
use onekeepass_core::db_content::AttachmentHashValue;
use onekeepass_core::db_service::{self, KdbxLoaded};
use regex::{Error, RegexSet};

// See https://users.rust-lang.org/t/can-i-stop-vscode-rust-analyzer-from-shading-out-cfgs/58773

const ANDROID_FP_SIZE: usize = 14;

#[cfg(target_os = "android")]
const FILE_PROVIDER_IDS: [&str; ANDROID_FP_SIZE] = [
    r"com.android.externalstorage.documents",
    r"com.android.providers.downloads.documents",
    r"com.google.android.apps.docs.storage",
    //
    r"com.dropbox.product.android.dbapp.document_provider.documents",
    r"com.microsoft.skydrive.content.StorageAccessProvider",
    r"mega.privacy.android.app",
    //
    r"com.nextcloud.client",
    r"com.owncloud.android",
    r"me.proton.android.drive",
    //
    r"org.cryptomator",
    r"com.sync.mobileapp",
    r"com.synology.dsdrive",
    //
    r"com.prosoftnet.android.idriveonline",
    r"ru.yandex.disk",
];

#[cfg(target_os = "android")]
const FILE_PROVIDER_NAMES: [&str; ANDROID_FP_SIZE] = [
    "On My Device",
    "Downloads",
    "Google Drive",
    //
    "Dropbox",
    "OneDrive",
    "MEGA",
    //
    "Next Cloud",
    "Own Cloud",
    "Proton Drive",
    //
    "Cryptomator",
    "Sync.com",
    "Synology Drive",
    //
    "IDrive",
    "Yandex Disk",
];

#[cfg(target_os = "android")]
pub fn extract_file_provider(full_file_name_uri: &str) -> String {
    let re = RegexSet::new(&FILE_PROVIDER_IDS).unwrap();
    let matches: Vec<_> = re.matches(full_file_name_uri).into_iter().collect();
    log::debug!("Matches {:?}, {:?}", matches, matches.first());

    let location_name = matches
        .first()
        .and_then(|i| FILE_PROVIDER_NAMES.get(*i))
        .map_or("Cloud storage / Another app", |s| s);

    location_name.to_string()
}

// Saves the attachment data to a temp file in the app's cache dir
// using db_service one tries to create the file in /data/local/cache'
// and that results in Permission denied error
pub fn save_attachment_as_temp_file(
    db_key: &str,
    name: &str,
    data_hash: &AttachmentHashValue,
) -> OkpResult<String> {
    let path = Path::new(AppState::cache_dir()).join(name);
    let mut file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .open(&path)?;

    db_service::save_attachment_to_writter(db_key, data_hash, &mut file)?;

    let full_file_name = path.to_string_lossy().to_string();

    debug!("Attachment saved to the temp file {}", &full_file_name);

    Ok(full_file_name)
}

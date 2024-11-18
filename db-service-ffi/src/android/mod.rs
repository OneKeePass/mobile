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
use crate::{commands, open_backup_file, return_api_response_failure};
use log::debug;
use onekeepass_core::db_content::AttachmentHashValue;
use onekeepass_core::db_service::{self, KdbxLoaded};
use regex::{Error, RegexSet};

// This is for any Android specific services
pub struct AndroidSupportService {}

// See https://users.rust-lang.org/t/can-i-stop-vscode-rust-analyzer-from-shading-out-cfgs/58773
#[cfg(target_os = "android")]
impl AndroidSupportService {
    // Called from Android Kotlin side native module and then other specific
    // support methods from this struct are called
    pub fn new() -> Self {
        Self {}
    }

    // called after DocumentPickerServiceModule.pickKdbxFileToCreate to do Save as feature
    pub fn complete_save_as_on_error(
        &self,
        file_descriptor: u64,
        old_full_file_name_uri: String,
        new_full_file_name_uri: String,
    ) -> ApiResponse {
        log::debug!("AndroidSupportService complete_save_as_on_error is called ");
        let mut file = unsafe { util::get_file_from_fd(file_descriptor) };

        let mut inner = || -> OkpResult<KdbxLoaded> {
            if let Some(bkp_file_name) =
                AppState::global().get_last_backup_on_error(&old_full_file_name_uri)
            {
                let mut reader = File::open(bkp_file_name)?;
                std::io::copy(&mut reader, &mut file)?;
                // sync_all ensures the file is created and synced in case of dropbbox and one drive
                let _ = file.sync_all();

                let kdbx_loaded =
                    db_service::rename_db_key(&old_full_file_name_uri, &new_full_file_name_uri)?;

                // Need to set the checksum for the newly saved file and checksum is calculated using the backup file itself
                // as using the newly written 'file' from 'file_descriptor' fails
                reader.rewind()?;
                db_service::calculate_db_file_checksum(&new_full_file_name_uri, &mut reader)?;

                remove_app_files(&old_full_file_name_uri);
                AppState::global().add_recent_db_use_info(&new_full_file_name_uri);
                AppState::global().remove_last_backup_name_on_error(&old_full_file_name_uri);
                Ok(kdbx_loaded)
            } else {
                Err(OkpError::UnexpectedError(format!(
                    "Last backup is not found"
                )))
            }
        };

        let api_response = as_api_response(inner());

        {
            log::debug!("AndroidSupportService complete_save_as_on_error: File fd_used is used  and will not be closed here");
            // We need to transfer the ownership of the underlying file descriptor to the caller.
            // By this call, the file instance created using incoming fd will not close the file at the end of
            // this function (Files are automatically closed when they go out of scope) and the caller
            // function from device will be responsible for the file closing.
            let _fd = file.into_raw_fd();
        }
        api_response
    }

    pub fn create_kdbx(&self, file_descriptor: u64, json_args: String) -> ApiResponse {
        log::debug!("AndroidSupportService create_kdbx is called ");

        let mut file = unsafe { util::get_file_from_fd(file_descriptor) };

        let mut full_file_name_uri: String = String::default();
        let r = match serde_json::from_str(&json_args) {
            Ok(CommandArg::NewDbArg { mut new_db }) => {
                full_file_name_uri = new_db.database_file_name.clone();
                // Need to get the file name from full uri
                new_db.file_name = AppState::global()
                    .common_device_service
                    .uri_to_file_name(full_file_name_uri.clone());

                // Need to create a backup file for this newly created database
                let Some(file_name) = new_db.file_name.as_ref() else {
                    return_api_response_failure!(
                        "No valid file name formed from the full file uri"
                    );
                    //return as_api_response::<()>(Err(OkpError::DataError("No valid file name formed from the full file uri")));
                };
                let backup_file_name =
                    util::generate_backup_file_name(&full_file_name_uri, file_name);
                let backup_file = open_backup_file(backup_file_name);

                let Some(mut bf_writer) = backup_file else {
                    return_api_response_failure!(
                        "Backup file could not be opened to write the new database backup"
                    );
                    //return as_api_response::<()>(Err(OkpError::DataError("Backup file could not be opened to write the new database backup")));
                };

                let r = db_service::create_and_write_to_writer(&mut bf_writer, new_db);
                let rewind_r = bf_writer.sync_all().and(bf_writer.rewind());
                log::debug!(
                    "Syncing and rewinding of new db backup file result {:?}",
                    rewind_r
                );

                let _n = std::io::copy(&mut bf_writer, &mut file);
                log::debug!("Copied the backup to the new db file ");
                // sync_all ensures the file is created and synced in case of dropbbox and one drive
                let _ = file.sync_all();

                r
            }
            Ok(_) => Err(OkpError::UnexpectedError(
                "Unexpected arguments for create_kdbx api call".into(),
            )),
            Err(e) => Err(OkpError::UnexpectedError(format!("{:?}", e))),
        };

        {
            log::debug!("File fd_used is used and will not be closed here");
            // We need to transfer the ownership of the underlying file descriptor to the caller.
            // By this call, the file instance created using incoming fd will not close the file at the end of
            // this function (Files are automatically closed when they go out of scope) and the caller
            // function from device will be responsible for the file closing.
            // If this is not done, android app will crash!
            let _fd = file.into_raw_fd();
        }

        let api_response = match r {
            Ok(v) => match serde_json::to_string_pretty(&InvokeResult::with_ok(v)) {
                Ok(s) => {
                    //Add this newly created db file to the recent list
                    AppState::global().add_recent_db_use_info(&full_file_name_uri);
                    ApiResponse::Success { result: s }
                }

                Err(e) => ApiResponse::Failure {
                    result: InvokeResult::<()>::with_error(&format!("{:?}", e)).json_str(),
                },
            },
            Err(e) => ApiResponse::Failure {
                result: InvokeResult::<()>::with_error(&format!("{:?}", e)).json_str(),
            },
        };
        api_response
    }

    pub fn save_key_file(&self, file_descriptor: u64, full_key_file_name: String) -> String {
        log::debug!(
            "AndroidSupportService save_key_file is called with full_key_file_name {}",
            &full_key_file_name
        );

        let inner = || -> OkpResult<()> {
            let p = Path::new(&full_key_file_name);
            if !p.exists() {
                return Err(OkpError::UnexpectedError(format!(
                    "Key file {:?} is not found",
                    p.file_name()
                )));
            }

            let mut reader = File::open(p)?;
            let mut writer = unsafe { util::get_file_from_fd(file_descriptor) };
            std::io::copy(&mut reader, &mut writer).and(writer.sync_all())?;
            {
                log::debug!("Key File fd is used and will not be closed here");
                // IMPORTANT
                // We need to transfer the ownership of the underlying file descriptor to the caller.
                // By this call, the file instance created using incoming fd will not close the file at the end of
                // this function (Files are automatically closed when they go out of scope) and the caller
                // function from device will be responsible for the file closing.
                // If this is not done, android app will crash!
                let _fd = writer.into_raw_fd();
            }
            Ok(())
        };
        commands::result_json_str(inner())
    }

    /*

    pub fn save_attachment(&self, file_descriptor: u64, json_args: String) -> ResponseJson {
        let inner = || -> OkpResult<()> {
            let mut file = unsafe { util::get_file_from_fd(file_descriptor) };
            let Ok(CommandArg::AttachmentArg {
                db_key,
                name,
                data_hash_str,
            }) = serde_json::from_str(&json_args)
            else {
                return Err(OkpError::UnexpectedError(format!(
                    "Save attachment api call argument parsing failed for the args {} ",
                    &json_args
                )));
            };

            let data_hash = db_service::parse_attachment_hash(&data_hash_str)?;
            db_service::save_attachment_to_writter(&db_key, &data_hash, &mut file)?;

            debug!("Attachment saved to the selected file ");

            Ok(())
        };

        commands::result_json_str(inner())
    }
    */
}

#[cfg(target_os = "ios")]
impl AndroidSupportService {
    pub fn new() -> Self {
        unimplemented!();
    }

    pub fn complete_save_as_on_error(
        &self,
        _file_descriptor: u64,
        _old_full_file_name_uri: String,
        _new_full_file_name_uri: String,
    ) -> ApiResponse {
        unimplemented!();
    }

    pub fn create_kdbx(&self, _file_descriptor: u64, _json_args: String) -> ApiResponse {
        unimplemented!();
    }

    pub fn save_key_file(&self, _file_descriptor: u64, _full_key_file_name: String) -> String {
        unimplemented!();
    }

    // pub fn save_attachment(&self, _file_descriptor: u64, _json_args: String) -> ResponseJson {
    //     unimplemented!();
    // }
}

#[cfg(target_os = "android")]
const FILE_PROVIDER_IDS: [&str; 5] = [
    r"com.android.externalstorage.documents",
    r"com.android.providers.downloads.documents",
    r"com.google.android.apps.docs.storage",
    r"com.dropbox.product.android.dbapp.document_provider.documents",
    r"com.microsoft.skydrive.content.StorageAccessProvider",
];

#[cfg(target_os = "android")]
const FILE_PROVIDER_NAMES: [&str; 5] = [
    "On My Device",
    "Downloads",
    "Google Drive",
    "Dropbox",
    "OneDrive",
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
    let path = Path::new(&AppState::shared().cache_dir).join(name);
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

use std::io::{Read, Seek, Write};
use std::{fs::File, os::fd::IntoRawFd};

use crate::{
    app_state::AppState,
    as_api_response,
    commands::{remove_app_files, CommandArg, InvokeResult},
    util, ApiResponse, OkpError, OkpResult,
};
use onekeepass_core::db_service::{self, KdbxLoaded};
use regex::{Error, RegexSet};

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
                Err(OkpError::Other(format!("Last backup is not found")))
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
            Ok(CommandArg::NewDbArg { new_db }) => {
                full_file_name_uri = new_db.database_file_name.clone();
                let r = db_service::create_and_write_to_writer(&mut file, new_db);
                // sync_all ensures the file is created and synced in case of dropbbox and one drive
                let _ = file.sync_all();

                r
            }
            Ok(_) => Err(OkpError::Other(
                "Unexpected arguments for create_kdbx api call".into(),
            )),
            Err(e) => Err(OkpError::Other(format!("{:?}", e))),
        };

        {
            log::debug!("File fd_used is used and will not be closed here");
            // We need to transfer the ownership of the underlying file descriptor to the caller.
            // By this call, the file instance created using incoming fd will not close the file at the end of
            // this function (Files are automatically closed when they go out of scope) and the caller
            // function from device will be responsible for the file closing.
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
}

#[cfg(target_os = "ios")]
impl AndroidSupportService {
    pub fn new() -> Self {
        unimplemented!();
    }

    pub fn complete_save_as_on_error(
        &self,
        file_descriptor: u64,
        old_full_file_name_uri: String,
        new_full_file_name_uri: String,
    ) -> ApiResponse {
        unimplemented!();
    }

    pub fn create_kdbx(&self, file_descriptor: u64, json_args: String) -> ApiResponse {
        unimplemented!();
    }
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

#![allow(dead_code, unused_imports)]
mod android;
mod app_state;
mod commands;
mod event_dispatcher;
mod ios;
mod key_secure;
mod udl_functions;
mod udl_types;
mod util;

use app_state::{AppState, RecentlyUsed};
use commands::{
    error_json_str, full_path_file_to_create, CommandArg, Commands, InvokeResult, ResponseJson,
};
use onekeepass_core::{
    db_service::{self, AttachmentUploadInfo},
    error::Error,
};
use udl_types::{
    ApiCallbackError, ApiResponse, CommonDeviceService, FileArgs, FileInfo, JsonService,
    KdbxCreated, SecureKeyOperation, SecureKeyOperationError,EventDispatch,
};

use udl_functions::{db_service_enable_logging, db_service_initialize, read_kdbx};

use log::{debug, logger};
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    format,
    fs::{File, OpenOptions},
    io::Seek,
    os::unix::prelude::IntoRawFd,
    path::Path,
};
use uuid::Uuid;

pub type OkpResult<T> = db_service::Result<T>;
pub type OkpError = db_service::Error;

// Needs to be added here to expose in the generated rs code
use android::AndroidSupportService;
#[allow(dead_code)]
use ios::IosSupportService;

#[allow(dead_code)]
fn invoke_command(command_name: String, args: String) -> String {
    Commands::invoke(command_name, args)
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct KeyFileInfo {
    pub full_file_name: String,
    pub file_name: String,
    pub file_size: Option<i64>,
}

#[macro_export]
macro_rules! return_api_response_failure {
    ($error:expr) => {
        return ApiResponse::Failure {
            result: InvokeResult::<()>::with_error(&format!("{:?}", $error)).json_str(),
        }
    };

    ($msg:literal) => {
        return ApiResponse::Failure {
            result: InvokeResult::<()>::with_error($msg).json_str(),
        }
    };
}

pub fn open_backup_file(backup_file_path: Option<String>) -> Option<File> {
    match backup_file_path {
        Some(backup_file_name) => OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .open(backup_file_name)
            .ok(),
        None => None,
    }
}

fn internal_read_kdbx(file: &mut File, json_args: &str) -> OkpResult<db_service::KdbxLoaded> {
    let CommandArg::OpenDbArg {
        db_file_name,
        password,
        key_file_name,
    } = serde_json::from_str(json_args)?
    else {
        return Err(OkpError::UnexpectedError(format!(
            "Argument 'json_args' {:?} parsing failed for readkdbx api call",
            json_args
        )));
    };

    let file_name = AppState::global().uri_to_file_name(&db_file_name);

    debug!(
        "File name from db_file_name {} is {} ",
        &db_file_name, &file_name
    );

    let backup_file_name = util::generate_backup_file_name(&db_file_name, &file_name);

    let mut backup_file = open_backup_file(backup_file_name.clone())
        .ok_or(OkpError::DataError("Opening backup file failed"))?;

    // Copy from db file to backup file first
    std::io::copy(file, &mut backup_file)
        .and(backup_file.sync_all())
        .and(backup_file.rewind())?;

    debug!("internal_read_kdbx copied to backup file and synced");

    // We load and parse the database content from the backup file
    let kdbx_loaded = db_service::read_kdbx(
        &mut backup_file,
        &db_file_name,
        password.as_deref(),
        key_file_name.as_deref(),
        Some(&file_name),
    )?;

    debug!("internal_read_kdbx kdbx_loaded  is {:?}", &kdbx_loaded);

    AppState::global().add_recent_db_use_info(&db_file_name);

    Ok(kdbx_loaded)
}

fn save_kdbx(file_args: FileArgs, overwrite: bool) -> ApiResponse {
    log::debug!("save_kdbx: file_args received is {:?}", file_args);

    let mut fd_used = false;
    let (mut writer, db_key, backup_file_name) = match file_args {
        FileArgs::FileDecriptorWithFullFileName {
            fd,
            full_file_name,
            file_name,
        } => {
            fd_used = true;
            let backup_file_name = util::generate_backup_file_name(&full_file_name, &file_name);
            (
                unsafe { util::get_file_from_fd(fd) },
                full_file_name,
                backup_file_name,
            )
        }
        FileArgs::FullFileName { full_file_name } => {
            match full_path_file_to_create(&full_file_name) {
                Ok(f) => {
                    // mainly for ios
                    //let file_name = util::file_name_from_full_path(&full_file_name);
                    let file_name = AppState::global().uri_to_file_name(&full_file_name);
                    let backup_file_name =
                        util::generate_backup_file_name(&full_file_name, &file_name);
                    (f, full_file_name, backup_file_name)
                }
                Err(e) => return_api_response_failure!(e),
            }
        }
        _ => return_api_response_failure!("Unsupported file args passed"),
    };

    let backup_file = open_backup_file(backup_file_name.clone());
    let response = match backup_file {
        Some(mut bf_writer) => {
            let r = match db_service::save_kdbx_to_writer(&mut bf_writer, &db_key) {
                Ok(r) => {
                    let rewind_r = bf_writer.sync_all().and(bf_writer.rewind());
                    log::debug!("Syncing and rewinding of backup file result {:?}", rewind_r);
                    if let Err(e) = rewind_r {
                        return_api_response_failure!(e)
                    }
                    // Call verify checksum here using writer "db_service::verify_db_file_checksum"
                    // Only for iOS.
                    if cfg!(target_os = "ios") && !overwrite {
                        // writer is from the existing db file
                        if let Err(e) = db_service::verify_db_file_checksum(&db_key, &mut writer) {
                            log::error!("Database checksum check failed");
                            // backup_file_name should have a valid back file name
                            if let Some(bkp_file_name) = backup_file_name.as_deref() {
                                AppState::global()
                                    .add_last_backup_name_on_error(&db_key, bkp_file_name);
                            }
                            return_api_response_failure!(e)
                        }
                    }
                    let _n = std::io::copy(&mut bf_writer, &mut writer);

                    if let Err(e) = db_service::calculate_db_file_checksum(&db_key, &mut bf_writer)
                    {
                        return_api_response_failure!(e)
                    }
                    log::debug!("New hash for checksum is done and set");
                    r
                }
                Err(e) => return_api_response_failure!(e),
            };
            r
        }
        None => {
            // This is not used. Will this ever happen?. Need to find use case where we do not have backup file
            log::warn!("No backup file is not found and writting to the db file directly");
            // TODO: Call verify checksum here using writer "db_service::verify_db_file_checksum"
            match db_service::save_kdbx_to_writer(&mut writer, &db_key) {
                Ok(r) => r,
                Err(e) => return_api_response_failure!(e),
            }
        }
    };

    if fd_used {
        // IMPORATNT:
        // We need to transfer the ownership of the underlying file descriptor to the caller so that the file is not closed here
        // and the caller closes the file
        let _fd = writer.into_raw_fd();
    }

    let api_response = match serde_json::to_string_pretty(&InvokeResult::with_ok(response)) {
        Ok(s) => s,
        Err(e) => InvokeResult::<()>::with_error(format!("{:?}", e).as_str()).json_str(),
    };

    ApiResponse::Success {
        result: api_response,
    }
}

// Called to create the backup file whenever some save error happens during the save kdbx api call
// See middle layer (Swift or Kotlin) the method 'saveKdbx'
fn write_to_backup_on_error(full_file_name_uri: String) -> ApiResponse {
    // closure returns -> OkpResult<db_service::KdbxSaved>
    // and that is converted to ApiResponse. Helps to use ?. May be used in fuctions also
    // to avoid using too many match calls
    let f = || {
        // We will get the file name from the recently used list
        // instead of using AppState uri_to_file_name method as that call may fail in case of Android
        let file_name = AppState::global()
            .file_name_in_recently_used(&full_file_name_uri)
            .ok_or(OkpError::UnexpectedError(format!(
                "There is no file name found for the uri {} in the recently used list",
                &full_file_name_uri
            )))?;
        let backup_file_name = util::generate_backup_file_name(&full_file_name_uri, &file_name);
        debug!(
            "Writing to the backup file {:?} for the uri {}",
            &backup_file_name, &file_name
        );
        let mut backup_file = open_backup_file(backup_file_name.clone())
            .ok_or(OkpError::DataError("Backup file could not be created"))?;

        let r = db_service::save_kdbx_to_writer(&mut backup_file, &full_file_name_uri);
        debug!("Writing backup for the uri {} is done", &file_name);

        // Need to store
        if let Some(bkp_file_name) = backup_file_name.as_deref() {
            AppState::global().add_last_backup_name_on_error(&full_file_name_uri, bkp_file_name);
            debug!("Added the backup file key on save error")
        }

        r
    };
    as_api_response(f())
}

// ApiResponse::Failure is returned when there is any db change detected
// No change means no error and hence ApiResponse::Success is returned
fn verify_db_file_checksum(file_args: FileArgs) -> ApiResponse {
    log::debug!(
        "verify_db_file_checksum: file_args received is {:?}",
        file_args
    );
    let (mut reader, db_key) = match file_args {
        FileArgs::FileDecriptorWithFullFileName {
            fd,
            full_file_name,
            file_name: _,
        } => (unsafe { util::get_file_from_fd(fd) }, full_file_name),

        FileArgs::FullFileName { full_file_name } => {
            let full_file_path = util::url_to_unix_file_name(&full_file_name);

            match File::open(&full_file_path) {
                Ok(f) => (f, full_file_name),
                Err(e) => return_api_response_failure!(e),
            }
        }
        _ => return_api_response_failure!("Unsupported file args passed"),
    };

    let response = match db_service::verify_db_file_checksum(&db_key, &mut reader) {
        Ok(r) => r,
        Err(e) => return_api_response_failure!(e),
    };

    let api_response = match serde_json::to_string_pretty(&InvokeResult::with_ok(response)) {
        Ok(s) => s,
        Err(e) => return_api_response_failure!(e),
    };

    // This means no db change detectecd
    ApiResponse::Success {
        result: api_response,
    }
}

// Copies the picked key file to app dir and returns key file info json  or error
fn copy_picked_key_file(file_args: FileArgs) -> String {
    debug!(
        "copy_picked_key_file: file_args received is {:?}",
        file_args
    );

    let inner = || -> OkpResult<KeyFileInfo> {
        // let (mut file, file_name) = match file_args {
        //     // For Android
        //     FileArgs::FileDecriptorWithFullFileName { fd, file_name, .. } => {
        //         (unsafe { util::get_file_from_fd(fd) }, file_name)
        //     }
        //     // For iOS
        //     FileArgs::FullFileName { full_file_name } => {
        //         let name = AppState::global().uri_to_file_name(&full_file_name);
        //         let ux_file_path = util::url_to_unix_file_name(&full_file_name);
        //         debug!("copy_picked_key_file:ux_file_path is {}", &ux_file_path);
        //         let r = File::open(ux_file_path);
        //         debug!("copy_picked_key_file:File::open return is {:?}", &r);
        //         (r?, name)
        //     }
        //     _ => return Err(OkpError::UnexpectedError("Unsupported file args passed".into())),
        // };

        let OpenedFile {
            mut file,
            file_name,
            ..
        } = OpenedFile::open_to_read(file_args)?;

        let key_file_full_path = AppState::global().key_files_dir_path.join(&file_name);
        debug!(
            "copy_picked_key_file:key_file_full_path is {:?}",
            key_file_full_path
        );

        if key_file_full_path.exists() {
            return Err(OkpError::DuplicateKeyFileName(format!(
                "Key file with the same name exists"
            )));
        }

        let mut target_file = File::create(&key_file_full_path)?;
        std::io::copy(&mut file, &mut target_file).and(target_file.sync_all())?;
        debug!("Copied the key file {} locally", &file_name);

        let full_file_name = key_file_full_path.as_os_str().to_string_lossy().to_string();
        Ok(KeyFileInfo {
            full_file_name,
            file_name,
            file_size: None,
        })
    };

    commands::result_json_str(inner())
}

struct OpenedFile {
    file: File,
    file_name: String,
    full_file_name: String,
}

impl OpenedFile {
    fn open_to_read(file_args: FileArgs) -> OkpResult<OpenedFile> {
        Self::open(file_args, false)
    }

    fn open_to_create(file_args: FileArgs) -> OkpResult<OpenedFile> {
        Self::open(file_args, true)
    }

    // Only for iOS, create flag is relevant as file read,write or create is set in Kotlin layer for android
    fn open(file_args: FileArgs, create: bool) -> OkpResult<OpenedFile> {
        let (file, file_name, full_file_name) = match file_args {
            // For Android
            FileArgs::FileDecriptorWithFullFileName {
                fd,
                file_name,
                full_file_name,
            } => (
                unsafe { util::get_file_from_fd(fd) },
                file_name,
                full_file_name,
            ),
            // For iOS
            FileArgs::FullFileName { full_file_name } => {
                let name = AppState::global().uri_to_file_name(&full_file_name);
                let ux_file_path = util::url_to_unix_file_name(&full_file_name);

                let file = if create {
                    full_path_file_to_create(&full_file_name)?
                } else {
                    File::open(ux_file_path)?
                };

                (file, name, full_file_name)
            }
            _ => {
                return Err(OkpError::UnexpectedError(
                    "Unsupported file args passed".into(),
                ))
            }
        };
        let r = OpenedFile {
            file,
            file_name,
            full_file_name,
        };

        Ok(r)
    }
}

fn upload_attachment(file_args: FileArgs, json_args: &str) -> ResponseJson {
    let inner = || -> OkpResult<AttachmentUploadInfo> {
        let OpenedFile {
            mut file,
            file_name,
            ..
        } = OpenedFile::open_to_read(file_args)?;

        let CommandArg::DbKey { db_key } = serde_json::from_str(json_args)? else {
            return Err(OkpError::UnexpectedError(format!(
                "Unexpected argument {:?} for upload_attachment api call",
                json_args
            )));
        };

        let info = db_service::read_entry_attachment(&db_key, &file_name, &mut file)?;

        Ok(info)
    };

    commands::result_json_str(inner())
}

// Does not work if we use From<OkpResult<T:Serialize>> as discussed in  https://github.com/rust-lang/rust/issues/52662
// impl<T> From<OkpResult<T:Serialize>> for ApiResponse {
//     fn from<T: serde::Serialize>(val: OkpResult<T>) -> ApiResponse {
//         match val {
//             Ok(t) => ApiResponse::Success {
//                 result: InvokeResult::with_ok(t).json_str(),
//             },
//             Err(e) => ApiResponse::Failure {
//                 result: InvokeResult::<()>::with_error(&format!("{:?}", e)).json_str(),
//             },
//         }
//     }
// }

fn as_api_response<T: serde::Serialize>(val: OkpResult<T>) -> ApiResponse {
    match val {
        Ok(t) => ApiResponse::Success {
            result: InvokeResult::with_ok(t).json_str(),
        },
        Err(e) => ApiResponse::Failure {
            // Need to use "{}" not "{:?}" for the thiserror display call to work
            // so that the string in #error[...] is returned in response
            result: InvokeResult::<()>::with_error(&format!("{}", e)).json_str(),
        },
    }
}

// Currently this is used for iOS only as we need to create a new temp data before using
// calling to store by picking a location in subsequent call
// See 'pickAndSaveNewKdbxFile' in the Swift class 'OkpDocumentPickerService'
#[cfg(target_os = "ios")]
fn create_temp_kdbx(file_args: FileArgs, json_args: String) -> ApiResponse {
    #[allow(unused_mut)]
    let mut full_file_name_uri;

    let mut file = match file_args {
        FileArgs::FullFileName { full_file_name } => {
            // full_file_name is temp url
            log::debug!(
                "create_temp_kdbx: FullFileName extracted is {}",
                &full_file_name
            );
            full_file_name_uri = full_file_name.clone();
            match full_path_file_to_create(&full_file_name) {
                Ok(f) => f,
                Err(e) => return_api_response_failure!(e),
            }
        }
        _ => {
            return ApiResponse::Failure {
                result: error_json_str("Unsupported file args passed "),
            }
        }
    };

    let r = match serde_json::from_str(&json_args) {
        Ok(CommandArg::NewDbArg { mut new_db }) => {
            // full_file_name is temp url and that is used as db-key
            // That will be replaced with the picked uri in a subsequent load-kdbx call from UI
            new_db.database_file_name = full_file_name_uri.clone();
            let r = db_service::create_and_write_to_writer(&mut file, new_db);
            // sync_all ensures the file is created and synced
            let _ = file.sync_all();
            r
        }
        Ok(_) => Err(OkpError::UnexpectedError(
            "Unexpected arguments for create_temp_kdbx api call".into(),
        )),
        Err(e) => {
            log::error!(
                "Deserialization of {} failed with error {:?} ",
                &json_args,
                e
            );
            Err(OkpError::UnexpectedError(format!("{:?}", e)))
        }
    };

    let api_response = match r {
        Ok(v) => match serde_json::to_string_pretty(&InvokeResult::with_ok(v)) {
            Ok(s) => {
                // Need to remove the new db cached with temp db-key
                let _r = db_service::close_kdbx(&full_file_name_uri);
                log::debug!("Temp file cache is cleared...");
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

#[cfg(target_os = "android")]
fn create_temp_kdbx(_file_args: FileArgs, _json_args: String) -> ApiResponse {
    unimplemented!()
}

#[cfg(target_os = "ios")]
fn extract_file_provider(full_file_name_uri: String) -> String {
    ios::extract_file_provider(&full_file_name_uri)
}

#[cfg(target_os = "android")]
fn extract_file_provider(full_file_name_uri: String) -> String {
    android::extract_file_provider(&full_file_name_uri)
}

// Need to include db_service.uniffi.rs (generated from db_service.udl by uniffi_build)
// only for ios or android build
// This is required if we want to run the units tests on Mac OS
// But we may get all functions that are used in 'db_service.uniffi.rs' marked as
// never used by lint. We may need to use #[allow(dead_code)] to suppress that
// See the use of #![allow(dead_code, unused_imports)] in the top of this crate

#[cfg(any(target_os = "ios", target_os = "android"))]
uniffi::include_scaffolding!("db_service");
//include!(concat!(env!("OUT_DIR"), "/db_service.uniffi.rs"));

//////////////////// To be removed //////////////////
/*
fn _read_kdbx_old(file_args: FileArgs, json_args: String) -> ApiResponse {
    log::debug!("file_args received is {:?}", file_args);
    let mut file = match file_args {
        FileArgs::FileDecriptor { fd } => unsafe { util::get_file_from_fd(fd) },
        FileArgs::FullFileName { full_file_name } => {
            // Opening file in read mode alone is sufficient and works fine
            // full_path_file_to_read_write may be used if we need to open file with read and write permissions
            // match full_path_file_to_read_write(&full_file_name)
            match File::open(util::url_to_unix_file_name(&full_file_name)) {
                Ok(f) => f,
                Err(e) => return_api_response_failure!(e),
            }
        }
        _ => return_api_response_failure!("Unsupported file args passed"),
    };

    let r = match serde_json::from_str(&json_args) {
        Ok(CommandArg::OpenDbArg {
            db_file_name,
            password,
            key_file_name,
        }) => {
            let r = db_service::read_kdbx(
                &mut file,
                &db_file_name, // This is db_key which is the full database file uri
                &password,
                key_file_name.as_deref(),
                None,
            );

            if r.is_ok() {
                AppState::global().add_recent_db_use_info(&db_file_name);
            }
            r
        }
        Ok(x) => Err(OkpError::UnexpectedError(format!(
            "Unexpected argument {:?} for readkdbx api call",
            x
        ))),
        Err(e) => Err(OkpError::UnexpectedError(format!("{:?}", e))),
    };

    ApiResponse::Success {
        result: InvokeResult::from(r).json_str(),
    }
}

 */

#![allow(dead_code, unused_imports)]
mod android;
mod app_preference;
mod auto_open;
mod app_state;
mod backup;
mod biometric_auth;
mod commands;
mod event_dispatcher;
mod file_util;
mod ios;
mod key_secure;
mod remote_storage;
mod util;

mod udl_functions;
mod udl_types;
mod udl_uniffi_exports;

use app_state::AppState;
use commands::{
    error_json_str, full_path_file_to_create, CommandArg, Commands, InvokeResult, ResponseJson,
};
use onekeepass_core::{
    db_service::{self, AttachmentUploadInfo},
    error::Error,
};
use udl_types::{
    ApiCallbackError, ApiResponse, CommonDeviceService, EventDispatch, FileArgs, FileInfo,
    JsonService, KdbxCreated, SecureKeyOperation, SecureKeyOperationError,
};

use udl_functions::{
    copy_picked_key_file, db_service_enable_logging, invoke_command, read_kdbx, save_kdbx,
    upload_attachment, verify_db_file_checksum, write_to_backup_on_error,
};

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
// These are interfaces declared in udl file and implemented in Rust
// use android::AndroidSupportService;
// use ios::IosSupportService;

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

pub fn open_backup_file(backup_file_path: Option<&String>) -> Option<File> {
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
// TODO: Move these to the iOS specific service trait
#[cfg(target_os = "ios")]
fn create_temp_kdbx(file_args: FileArgs, json_args: String) -> ApiResponse {
    use crate::commands::error_json_str;

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
                Err(e) => {
                    log::debug!("full_path_file_to_create failed with error {:?}", &e);
                    return_api_response_failure!(e)
                },
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
    use crate::ios;
    ios::extract_file_provider(&full_file_name_uri)
}

#[cfg(target_os = "android")]
fn extract_file_provider(full_file_name_uri: String) -> String {
    android::extract_file_provider(&full_file_name_uri)
}

///////////////  Including uniffi generated rust source code - uniffi::include_scaffolding   ///////////////

// We may get all functions that are used in 'db_service.uniffi.rs' (generated file) marked as
// never used by lint. We may need to use #[allow(dead_code)] to suppress that
// See the use of #![allow(dead_code, unused_imports)] in the top of this crate

// Note:
// when we want to run unit tests (in masos) in this crate,
// need to use '#[cfg(any(target_os = "ios", target_os = "android"))]'. This worked as long as we used only udl file
// But using '#[uniffi::export]' may create some issues if they do not have same targets

// If we use conditional - #[cfg(any(target_os = "ios", target_os = "android"))] -
// way of using 'uniffi::include_scaffolding', then when we use
// use uniffi::export and other proc macros, we need to mark them all with
// cfg targets - #[cfg(any(target_os = "ios", target_os = "android"))]

// Need to explore to use something like
// cargo test --target aarch64-linux-android  --package db-service-ffi --lib -- util::tests --show-output
// Or use https://github.com/sonos/dinghy etc
// See https://stackoverflow.com/questions/44947640/how-can-i-run-cargo-tests-on-another-machine-without-the-rust-compiler

// #[cfg(any(target_os = "ios", target_os = "android"))] - see the above comments

uniffi::include_scaffolding!("db_service");

// As per uniffi doc, this should only be used when we use only the macros based udl definitions and not udl file
//uniffi::setup_scaffolding!("db_service");

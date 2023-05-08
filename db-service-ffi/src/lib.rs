#![allow(dead_code, unused_imports)]
mod android;
mod app_state;
mod commands;
mod ios;
mod util;

use app_state::{AppState, CommonDeviceService, FileInfo, RecentlyUsed};
use commands::{error_json_str, full_path_file_to_create, CommandArg, Commands, InvokeResult};
use log::{debug, logger};
use onekeepass_core::db_service;

use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    fs::{File, OpenOptions},
    io::Seek,
    os::unix::prelude::IntoRawFd,
    path::Path,
};
use uuid::Uuid;

// The implementation of structs and functions decalared in db_service.udl follows here

#[allow(dead_code)]
use ios::IosSupportService;

#[allow(dead_code)]
fn invoke_command(command_name: String, args: String) -> String {
    Commands::invoke(command_name, args)
}

#[allow(dead_code)]
pub struct KdbxCreated {
    buffer: Vec<u8>,
    api_response: String,
}

macro_rules! return_failure {
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

fn file_to_create(dir_path: &str, file_name: &str) -> db_service::Result<File> {
    let name = util::full_path_str(dir_path, file_name);
    let full_file_path = Path::new(&name);
    if let Some(ref p) = full_file_path.parent() {
        if !p.exists() {
            return Err(db_service::error::Error::Other(format!(
                "Parent dir is not existing"
            )));
        }
    }
    log::debug!(
        "In file_to_create: Creating file object for full file path  {:?}",
        full_file_path
    );

    // IMPORTANT: We need to create a file using OpenOptions so that the file is opened for read and write
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .open(full_file_path)?;
    Ok(file)
}

fn open_backup_file(backup_file_path: Option<String>) -> Option<File> {
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

fn create_kdbx(file_args: FileArgs, json_args: String) -> ApiResponse {
    log::debug!("create_kdbx: file_args received is {:?}", file_args);
    let mut fd_used = false;
    let mut file = match file_args {
        FileArgs::FileDecriptor { fd } => {
            fd_used = true;
            unsafe { util::get_file_from_fd(fd) }
        }
        FileArgs::FullFileName { full_file_name } => {
            log::debug!("create_kdbx: FullFileName extracted is {}", &full_file_name);
            match full_path_file_to_create(&full_file_name) {
                Ok(f) => f,
                Err(e) => return_failure!(e),
            }
        }
        FileArgs::FileNameWithDir {
            dir_path,
            file_name,
        } => match file_to_create(&dir_path, &file_name) {
            Ok(f) => f,
            Err(e) => return_failure!(e),
        },
        _ => {
            return ApiResponse::Failure {
                result: InvokeResult::<()>::with_error("Unsupported file args passed ").json_str(),
            }
        }
    };

    let mut full_file_name_uri: String = "".into();

    let r = match serde_json::from_str(&json_args) {
        Ok(CommandArg::NewDbArg { new_db }) => {
            full_file_name_uri = new_db.database_file_name.clone();
            let r = db_service::create_and_write_to_writer(&mut file, new_db);
            // sync_all ensures the file is created and synced in case of dropbbox and one drive
            let _ = file.sync_all();
            if let Ok(md) = file.metadata() {
                log::debug!("Meta data for {:?}", md);
                log::debug!("Meta data created is {:?}", md.created());
            }
            r
        }
        Ok(_) => Err(db_service::error::Error::Other(
            "Unexpected arguments for create_kdbx api call".into(),
        )),
        Err(e) => {
            log::error!(
                "Deserialization of {} failed with error {:?} ",
                &json_args,
                e
            );
            Err(db_service::error::Error::Other(format!("{:?}", e)))
        }
    };

    if fd_used {
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

fn read_kdbx(file_args: FileArgs, json_args: String) -> ApiResponse {
    log::debug!("file_args received is {:?}", file_args);
    let mut file = match file_args {
        FileArgs::FileDecriptor { fd } => unsafe { util::get_file_from_fd(fd) },
        FileArgs::FullFileName { full_file_name } => {
            // Opening file in read mode alone is sufficient and works fine
            // full_path_file_to_read_write may be used if we need to open file with read and write permissions
            // match full_path_file_to_read_write(&full_file_name)
            match File::open(util::url_to_unix_file_name(&full_file_name)) {
                Ok(f) => f,
                Err(e) => return_failure!(e),
            }
        }
        _ => return_failure!("Unsupported file args passed"),
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
            );

            if r.is_ok() {
                AppState::global().add_recent_db_use_info(&db_file_name);
            }
            r
        }
        Ok(x) => Err(db_service::error::Error::Other(format!(
            "Unexpected argument {:?} for readkdbx api call",
            x
        ))),
        Err(e) => Err(db_service::error::Error::Other(format!("{:?}", e))),
    };

    ApiResponse::Success {
        result: InvokeResult::from(r).json_str(),
    }
}

fn save_kdbx(file_args: FileArgs) -> ApiResponse {
    log::debug!("save_kdbx: file_args received is {:?}", file_args);

    let mut fd_used = false;
    let (mut writter, db_key, backup_file_name) = match file_args {
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
                Err(e) => return_failure!(e),
            }
        }
        _ => return_failure!("Unsupported file args passed"),
    };

    log::debug!("Backup file to write is {:#?}", backup_file_name);
    let backup_file = open_backup_file(backup_file_name);
    let response = match backup_file {
        Some(mut bf_writter) => {
            let r = match db_service::save_kdbx_to_writer(&mut bf_writter, &db_key) {
                Ok(r) => {
                    let rewind_r = bf_writter.sync_all().and(bf_writter.rewind());
                    log::error!("Syncing and rewinding of backup file result {:?}", rewind_r);
                    if let Err(e) = rewind_r {
                        return_failure!(e)
                    }
                    let n = std::io::copy(&mut bf_writter, &mut writter);
                    log::debug!("Bytes copied ...{:?}", n);
                    log::debug!("Backup is successful and copied to db file");
                    r
                }
                Err(e) => return_failure!(e),
            };
            r
        }
        None => {
            log::warn!("No backup file is not found and writting to the db file directly");
            match db_service::save_kdbx_to_writer(&mut writter, &db_key) {
                Ok(r) => r,
                Err(e) => return_failure!(e),
            }
        }
    };

    if fd_used {
        // IMPORATNT:
        // We need to transfer the ownership of the underlying file descriptor to the caller so that the file is not closed here
        // and the caller closes the file
        let _fd = writter.into_raw_fd();
    }

    let api_response = match serde_json::to_string_pretty(&InvokeResult::with_ok(response)) {
        Ok(s) => s,
        Err(e) => InvokeResult::<()>::with_error(format!("{:?}", e).as_str()).json_str(),
    };

    ApiResponse::Success {
        result: api_response,
    }
}

#[derive(Debug)]
pub enum ApiResponse {
    Success { result: String },
    Failure { result: String },
}

#[derive(Debug)]
pub enum FileArgs {
    FileDecriptor {
        fd: u64,
    },
    FileDecriptorWithFullFileName {
        fd: u64,
        full_file_name: String,
        file_name: String,
    },
    FullFileName {
        full_file_name: String,
    },
    // Not used. Deprecate?
    FileNameWithDir {
        dir_path: String,
        file_name: String,
    },
}

#[allow(dead_code)]
struct JsonService {}

impl JsonService {
    pub fn new() -> Self {
        Self {}
    }

    pub fn form_with_file_name(&self, full_file_name_uri: String) -> String {
        let file_name = AppState::global()
            .common_device_service
            .uri_to_file_name(full_file_name_uri.clone())
            .map_or_else(|| "".into(), |s| s);

        let m = HashMap::from([
            ("file_name", file_name),
            ("full_file_name_uri", full_file_name_uri),
        ]);

        InvokeResult::with_ok(m).json_str()
    }
}

fn db_service_initialize(common_device_service: Box<dyn CommonDeviceService>) {
    AppState::setup(common_device_service);
    log::info!("AppState with CommonDeviceService is initialized");
}

fn db_service_enable_logging() {
    #[cfg(target_os = "android")]
    {
        let _ = std::panic::catch_unwind(|| {
            let filter = android_logger::FilterBuilder::new()
                //.filter_module("glean_ffi", log::LevelFilter::Debug)
                // .filter_module("glean_core", log::LevelFilter::Debug)
                // .filter_module("glean", log::LevelFilter::Debug)
                // .filter_module("glean_core::ffi", log::LevelFilter::Info)
                .build();
            android_logger::init_once(
                android_logger::Config::default()
                    .with_min_level(log::Level::Debug)
                    //.with_filter(filter)
                    .with_tag("DbServiceFFI"),
            );
            log::trace!("Android logging should be hooked up!")
        });
    }

    // On iOS enable logging with a level filter.
    #[cfg(target_os = "ios")]
    {
        // Debug logging in debug mode.
        // (Note: `debug_assertions` is the next best thing to determine if this is a debug build)
        #[cfg(debug_assertions)]
        let level = log::LevelFilter::Debug;
        #[cfg(not(debug_assertions))]
        let level = log::LevelFilter::Info;

        let logger = oslog::OsLogger::new("com.onekeepass")
            .level_filter(level)
            // Filter UniFFI log messages
            .category_level_filter("db_service::ffi", log::LevelFilter::Info);

        match logger.init() {
            Ok(_) => log::trace!("os_log should be hooked up!"),
            // Please note that this is only expected to fail during unit tests,
            // where the logger might have already been initialized by a previous
            // test. So it's fine to print with the "logger".
            Err(_) => log::warn!("os_log was already initialized"),
        };
    }
}

// Currently this is used for iOS only as we need to create a new temp data before using
// calling to store by picking a locarion in subsequent call

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
                Err(e) => return_failure!(e),
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
            new_db.database_file_name = full_file_name_uri.clone();
            let r = db_service::create_and_write_to_writer(&mut file, new_db);
            // sync_all ensures the file is created and synced
            let _ = file.sync_all();
            r
        }
        Ok(_) => Err(db_service::error::Error::Other(
            "Unexpected arguments for create_temp_kdbx api call".into(),
        )),
        Err(e) => {
            log::error!(
                "Deserialization of {} failed with error {:?} ",
                &json_args,
                e
            );
            Err(db_service::error::Error::Other(format!("{:?}", e)))
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
fn create_temp_kdbx(file_args: FileArgs, json_args: String) -> ApiResponse {
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
// never used by lint. We may to use #[allow(dead_code)] to suppress
// See the use of #![allow(dead_code, unused_imports)] in the top of this crate

#[cfg(any(target_os = "ios", target_os = "android"))]
include!(concat!(env!("OUT_DIR"), "/db_service.uniffi.rs"));

use log::debug;
use onekeepass_core::db_service;

use crate::{
    commands::{full_path_file_to_create, CommandArg},
    event_dispatcher, open_backup_file,
    udl_types::EventDispatch,
    InvokeResult,
};
use std::{
    fs::{File, OpenOptions},
    io::Seek,
    os::fd::IntoRawFd,
    path::Path,
    sync::Arc,
};

use crate::{
    app_state::AppState,
    as_api_response, key_secure, return_api_response_failure,
    udl_types::{ApiResponse, CommonDeviceService, FileArgs, SecureKeyOperation},
    util, OkpError, OkpResult,
};

// This module will have all the implementation for the top level functions declared in db_service.udl
// TODO: Once this refactoing works for both iOS and Android, we can move all such fn implementations here

// Called from Swift/Kotlin side onetime  - See db_service.udl
pub(crate) fn db_service_enable_logging() {
    #[cfg(target_os = "android")]
    {
        let _ = std::panic::catch_unwind(|| {
            let _filter = android_logger::FilterBuilder::new()
                //.filter_module("commands", log::LevelFilter::Debug)
                // .filter_module("", log::LevelFilter::Debug)
                // .filter_module("", log::LevelFilter::Debug)
                // .filter_module("our_core::ffi", log::LevelFilter::Info)
                .build();
            android_logger::init_once(
                android_logger::Config::default()
                    .with_max_level(log::LevelFilter::Trace)
                    //.with_min_level(log::Level::Debug)
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

// Called from Swift/Kotlin side to initialize callbacks,
// backend tokio runtime etc when Native Modules are loaded - See db_service.udl
pub(crate) fn db_service_initialize(
    common_device_service: Box<dyn CommonDeviceService>,
    secure_key_operation: Box<dyn SecureKeyOperation>,
    event_dispatcher: Arc<dyn EventDispatch>,
) {
    AppState::setup(
        common_device_service,
        secure_key_operation,
        event_dispatcher,
    );
    log::info!(
        "AppState with CommonDeviceService,secure_key_operation,event_dispatcher is initialized"
    );

    key_secure::init_key_main_store();
    log::info!("key_secure::init_key_main_store call done after AppState setup");

    onekeepass_core::async_service::start_runtime();
    log::info!("onekeepass_core::async_service::start_runtime completed");

    event_dispatcher::init_async_listeners();
    log::info!("event_dispatcher::init_async_listeners call completed");
}

pub(crate) fn read_kdbx(file_args: FileArgs, json_args: String) -> ApiResponse {
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

    as_api_response(internal_read_kdbx(&mut file, &json_args))
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

pub(crate) fn save_kdbx(file_args: FileArgs, overwrite: bool) -> ApiResponse {
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


///////////

fn file_to_create(dir_path: &str, file_name: &str) -> OkpResult<File> {
    let name = util::full_path_str(dir_path, file_name);
    let full_file_path = Path::new(&name);
    if let Some(ref p) = full_file_path.parent() {
        if !p.exists() {
            return Err(OkpError::UnexpectedError(format!(
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

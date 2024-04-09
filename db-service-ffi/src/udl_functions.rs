use crate::{event_dispatcher, udl_types::EventDispatch, InvokeResult};
use std::{
    fs::{File, OpenOptions},
    path::Path, sync::Arc,
};

use crate::{
    app_state::AppState,
    as_api_response, internal_read_kdbx, key_secure, return_api_response_failure,
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
    event_dispatcher:Arc<dyn EventDispatch>,
) {
    AppState::setup(common_device_service, secure_key_operation,event_dispatcher,);
    log::info!("AppState with CommonDeviceService,secure_key_operation,event_dispatcher is initialized");

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

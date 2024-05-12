use std::collections::HashMap;

use onekeepass_core::db_service as kp_service;
use serde::{Serialize, Deserialize};

use crate::{app_state::AppState, commands::{InvokeResult, self}};

// Most of types decalred in db_service.udl follows here
// except few like  'IosSupportService' and 'AndroidSupportService' and functions from 'namespace db_service'.
// The implementation of the =top level functions from 'namespace db_service' can be found in crate root lib.rs

#[allow(dead_code)]
pub(crate) struct KdbxCreated {
    pub(crate) buffer: Vec<u8>,
    pub(crate) api_response: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct FileInfo {
    pub file_name: Option<String>,
    pub file_size: Option<i64>,
    pub last_modified: Option<i64>,
    pub location: Option<String>,
}

#[derive(Debug)]
pub enum ApiResponse {
    Success { result: String },
    Failure { result: String },
}

//////////////////////////////////////////////////////////////

// TODO: 
// Combine ApiCallbackError and SecureKeyOperationError as one general callback error
pub type ApiCallbackResult<T> = std::result::Result<T, ApiCallbackError>;

#[derive(Debug, thiserror::Error)]
pub enum ApiCallbackError {
    #[error("InternalCallbackError")]
    InternalCallbackError {reason:String},
}

impl From<uniffi::UnexpectedUniFFICallbackError> for ApiCallbackError {
    fn from(callback_error: uniffi::UnexpectedUniFFICallbackError) -> Self {
        log::error!("UnexpectedUniFFICallbackError is {}", callback_error);
        Self::InternalCallbackError{reason:format!("UnexpectedUniFFICallbackError is {}", callback_error)}
    }
}

impl From<ApiCallbackError> for kp_service::error::Error {
    fn from(err: ApiCallbackError) -> Self {
        Self::UnexpectedError(format!("{}",err))
    }
}
/////////////////////////////////////////////////////////////////

pub trait  EventDispatch: Send + Sync {
    fn send_otp_update(&self,json_string:String) -> ApiCallbackResult<()>;
    fn send_tick_update(&self,json_string:String) -> ApiCallbackResult<()>;
}


// This trait represents a callback declared in 'db_service.udl'
// We need to implement this interface in Swift and Kotlin for the rust side use
pub trait CommonDeviceService: Send + Sync {
    fn app_home_dir(&self) -> String;
    fn cache_dir(&self) -> String;
    fn temp_dir(&self) -> String;
    fn load_language_translation(&self, language_id:String) -> Option<String>;
    fn uri_to_file_name(&self, full_file_name_uri: String) -> Option<String>;
    fn uri_to_file_info(&self, full_file_name_uri: String) -> Option<FileInfo>;
}

// TODO: Combine ApiCallbackError and SecureKeyOperationError as one general callback error
#[derive(Debug, thiserror::Error)]
pub enum SecureKeyOperationError {
    #[error("StoringKeyError")]
    StoringKeyError,
    #[error("StoringKeyDuplicateItemError")]
    StoringKeyDuplicateItemError,
    #[error("QueryKeyError")]
    QueryKeyError,
    #[error("DeleteKeyError")]
    DeleteKeyError,
    #[error("InternalSecureKeyOperationError")]
    InternalSecureKeyOperationError,
}

impl From<uniffi::UnexpectedUniFFICallbackError> for SecureKeyOperationError {
    fn from(callback_error: uniffi::UnexpectedUniFFICallbackError) -> Self {
        log::error!("UnexpectedUniFFICallbackError is {}", callback_error);
        Self::InternalSecureKeyOperationError
    }
}

impl From<SecureKeyOperationError> for kp_service::error::Error {
    fn from(err: SecureKeyOperationError) -> Self {
        Self::SecureKeyOperationError(format!("{}",err))
    }
}

pub type SecureKeyOpsResult<T> = std::result::Result<T, SecureKeyOperationError>;

// This trait represents the callback declared in 'db_service.udl'
// We need to implement this interface in Swift and Kotlin for the rust side use
pub trait SecureKeyOperation: Send + Sync {
    fn store_key(&self, db_key: String, enc_key_data: String) -> SecureKeyOpsResult<()>;
    fn get_key(&self, db_key: String) -> SecureKeyOpsResult<Option<String>>;
    fn delete_key(&self, db_key: String) -> SecureKeyOpsResult<()>;
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
    // ReadWriteFDsWithFullFileName {
    //     read_fd: u64,
    //     write_fd: u64,
    //     full_file_name: String,
    //     file_name: String,
    // },
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
pub struct JsonService {}

impl JsonService {
    pub fn new() -> Self {
        Self {}
    }

    // Forms and returns a parseable (by cljs) json string with "ok"
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

    // Returns the map data as a parseable (by cljs) json string with "ok" key
    pub fn map_as_ok_json_string(&self, info: HashMap<String, String>) -> String {
        InvokeResult::with_ok(info).json_str()
    }

    // Returns a parseable (by cljs) json string with "ok"
    pub fn ok_json_string(&self, info: String) -> String {
        InvokeResult::with_ok(info).json_str()
    }

    // Returns a string of form "{"error": "some error text"}"
    // that can then be deserialized in UI layer
    pub fn error_json_string(&self, error: String) -> String {
        commands::error_json_str(error.as_str())
    }
}


/*
// This trait represents a callback declared in 'db_service.udl'
// We need to implement this interface in Swift and Kotlin for the rust side use
pub trait FileDescriptor {
    fn open_to_read(&self) -> ApiCallbackResult<u64>;
    fn close(&self)-> ApiCallbackResult<()>;
}
impl From<uniffi::UnexpectedUniFFICallbackError> for ApiCallbackError {
    fn from(callback_error: uniffi::UnexpectedUniFFICallbackError) -> Self {
        log::error!("UnexpectedUniFFICallbackError is {}", callback_error);
        Self::InternalCallbackError{reason:callback_error.reason}
    }
}

impl From<ApiCallbackError> for kp_service::error::Error {
    fn from(err: ApiCallbackError) -> Self {
        Self::Other(format!("{}",err))
    }
}

 */




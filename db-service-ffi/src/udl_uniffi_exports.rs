// IMPORTANT
// In this module, all uniffi types and fns that are exported using macros are defined here
// This module is applicable for both 'ios' and 'android' targets

// iOS specific, uniffi types and fns under 'ios' module 'callback_services'
// Android specific, uniffi types and fns under 'android' module 'callback_services'

use log::debug;
use once_cell::sync::OnceCell;
use serde::Deserialize;
use std::{collections::HashMap, sync::Arc};

use crate::{udl_types::ApiCallbackResult, OkpResult};

/////////// All common uniffi exported types  ///////////

// Something similar to 'dictionary' in UDL file?
#[derive(uniffi::Record)]
pub struct AppClipboardCopyData {
    pub field_name: String,
    pub field_value: String,
    pub protected: bool,
    // The field 'cleanup_after' has clipboard timeout in seconds and 0 sec menas no timeout
    pub cleanup_after: u32,
}

// As per uniffi 'callback_interface' is 'soft' deprecated
// If we use this, we need to use Box<dyn SecureEnclaveService> instead of Arc<dyn SecureEnclaveService>
//#[uniffi::export(callback_interface)]
// It is recommended to use uniffi::export(with_foreign)

#[uniffi::export(with_foreign)]
pub trait SecureEnclaveCbService: Send + Sync {
    fn encrypt_bytes(&self, identifier: String, plain_data: Vec<u8>) -> ApiCallbackResult<Vec<u8>>;
    fn decrypt_bytes(&self, identifier: String, encrypted_data: Vec<u8>) -> ApiCallbackResult<Vec<u8>>;
    fn remove_key(&self, identifier: String) -> ApiCallbackResult<bool>;
}

// TODO: Move CommonDeviceService definition etc to this macro based definition

// All common services are implemented in Swift and Kotlin. These are meant to be used by rust

// Corresponding UDL:
// [Trait, WithForeign]
// interface CommonDeviceServiceEx {};
#[uniffi::export(with_foreign)]
pub trait CommonDeviceServiceEx: Send + Sync {
    fn clipboard_copy_string(&self, clip_data: AppClipboardCopyData) -> ApiCallbackResult<()>;

    fn test_secure_store(&self) -> ApiCallbackResult<()>;
}

// A singleton that holds Android or ios specific api callbacks services implemented in Kotlin/Swift
pub struct ApiCallbacksStore {
    common_device_service_ex: Arc<dyn CommonDeviceServiceEx>,
    secure_enclave_cb_service: Arc<dyn SecureEnclaveCbService>,
}

impl ApiCallbacksStore {
    fn global() -> &'static ApiCallbacksStore {
        // Panics if no global state object was set. ??
        API_CALLBACK_STORE.get().unwrap()
    }

    pub fn common_device_service_ex() -> &'static dyn CommonDeviceServiceEx {
        Self::global().common_device_service_ex.as_ref()
    }

    pub fn secure_enclave_cb_service() -> &'static dyn SecureEnclaveCbService {
        Self::global().secure_enclave_cb_service.as_ref()
    }
}

///////////

static API_CALLBACK_STORE: OnceCell<ApiCallbacksStore> = OnceCell::new();

//IMPORTANT:
// This fn should be called once in Kotlin/Swift during intialization of Native modules
// Then only we can use the api callback functions in rust
// Otherwise we get panic 'Caught a panic calling rust code: "called `Option::unwrap()` on a `None` value"'

// Top level functions generated to be called from Kotlin/Swift something similar to 'db_service_initialize'

// TODO:
// Instead of Keeping separate store and separate intit call, plan to use 'db_service_initialize' itself
// after moving fns from CommonDeviceService to CommonDeviceServiceEx

// #[uniffi::export]
// pub fn common_device_service_ex_initialize(
//     common_device_service_ex: Arc<dyn CommonDeviceServiceEx>,
// ) {
//     let service = ApiCallbacksStore {
//         common_device_service_ex,
//     };

//     if API_CALLBACK_STORE.get().is_none() {
//         if API_CALLBACK_STORE.set(service).is_err() {
//             log::error!(
//                 "Global API_CALLBACK_STORE object is initialized already. This probably happened concurrently."
//             );
//         }
//     }

//     debug!("common_device_service_ex_initialize is finished");
// }

#[uniffi::export]
pub fn initialize_callback_services(
    common_device_service_ex: Arc<dyn CommonDeviceServiceEx>,
    secure_enclave_cb_service: Arc<dyn SecureEnclaveCbService>,
) {
    let service = ApiCallbacksStore {
        common_device_service_ex,
        secure_enclave_cb_service,
    };

    if API_CALLBACK_STORE.get().is_none() {
        if API_CALLBACK_STORE.set(service).is_err() {
            log::error!(
                "Global API_CALLBACK_STORE object is initialized already. This probably happened concurrently."
            );
        }
    }

    debug!("initialize_callback_services call is finished");
}

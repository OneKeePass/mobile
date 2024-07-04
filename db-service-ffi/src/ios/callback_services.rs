use log::debug;
use once_cell::sync::OnceCell;
use std::{collections::HashMap, sync::Arc};

use crate::{udl_types::ApiCallbackResult, OkpResult};

// A signleton that holds iOS specific api callbacks services implemented in Swift
pub struct IosApiCallbackImpl {
    ios_api_service: Arc<dyn IosApiService>,
}

impl IosApiCallbackImpl {
    pub fn global() -> &'static IosApiCallbackImpl {
        // Panics if no global state object was set. ??
        IOS_API_SERVICE_STATE.get().unwrap()
    }

    pub fn api_service() -> &'static dyn IosApiService {
        Self::global().ios_api_service.as_ref()
    }

    // pub fn paste_string(text: String, timeout: u32) -> OkpResult<()> {
    //     Ok(Self::global().ios_api_service.paste_string(text, timeout)?)
    // }
}


///////////

static IOS_API_SERVICE_STATE: OnceCell<IosApiCallbackImpl> = OnceCell::new();

//IMPORTANT: 
// This fn should be called once in Swift during intialization of Native modules  
// Then only we can use the api callback functions 
// Otherwise we get panic 'Caught a panic calling rust code: "called `Option::unwrap()` on a `None` value"'

// top level functions generated to be called from Swift something similar to 'db_service_initialize'
// The Swift generated func is 'iosCallbackServiceInitialize'

#[uniffi::export]
pub fn ios_callback_service_initialize(ios_api_service: Arc<dyn IosApiService>) {
    let service = IosApiCallbackImpl { ios_api_service };

    if IOS_API_SERVICE_STATE.get().is_none() {
        if IOS_API_SERVICE_STATE.set(service).is_err() {
            log::error!(
                "Global IOS_API_SERVICE_STATE object is initialized already. This probably happened concurrently."
            );
        }
    }

    debug!("ios_callback_service_initialize is finished");
}

// Corresponding UDL:
// [Trait, WithForeign]
// interface IosApiService {};
#[uniffi::export(with_foreign)]
pub trait IosApiService: Send + Sync {
    fn clipboard_copy_string(&self, text: String, timeout: u32) -> ApiCallbackResult<()>;
    // Autofill specific
    fn asc_credential_service_identifiers(&self,) -> ApiCallbackResult<HashMap<String,String>>;
}

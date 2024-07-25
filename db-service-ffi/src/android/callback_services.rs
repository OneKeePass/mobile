use log::debug;
use once_cell::sync::OnceCell;
use serde::Deserialize;
use std::{collections::HashMap, sync::Arc};

use crate::{udl_types::ApiCallbackResult, OkpResult};


// A signleton that holds Android specific api callbacks services implemented in Kotlin
pub struct AndroidApiCallbackImpl {
    android_api_service: Arc<dyn AndroidApiService>,
}

impl AndroidApiCallbackImpl {
    pub fn global() -> &'static AndroidApiCallbackImpl {
        // Panics if no global state object was set. ??
        ANDROID_API_SERVICE_STATE.get().unwrap()
    }

    pub fn api_service() -> &'static dyn AndroidApiService {
        Self::global().android_api_service.as_ref()
    }

    // pub fn paste_string(text: String, timeout: u32) -> OkpResult<()> {
    //     Ok(Self::global().ios_api_service.paste_string(text, timeout)?)
    // }
}


///////////

static ANDROID_API_SERVICE_STATE: OnceCell<AndroidApiCallbackImpl> = OnceCell::new();

//IMPORTANT: 
// This fn should be called once in Kotlin during intialization of Native modules  
// Then only we can use the api callback functions 
// Otherwise we get panic 'Caught a panic calling rust code: "called `Option::unwrap()` on a `None` value"'

// top level functions generated to be called from Kotlin something similar to 'db_service_initialize'


#[uniffi::export]
pub fn android_callback_service_initialize(android_api_service: Arc<dyn AndroidApiService>) {
    let service = AndroidApiCallbackImpl { android_api_service };

    if ANDROID_API_SERVICE_STATE.get().is_none() {
        if ANDROID_API_SERVICE_STATE.set(service).is_err() {
            log::error!(
                "Global ANDROID_API_SERVICE_STATE object is initialized already. This probably happened concurrently."
            );
        }
    }

    debug!("android_callback_service_initialize is finished");
}

// Something similar to 'dictionary' in UDL file? 
#[derive(uniffi::Record)]
pub struct ClipDataArg {
    pub field_name:String,
    pub field_value:String,
    pub protected:bool,
    pub cleanup_after:u32, 
}

#[derive(uniffi::Enum,Deserialize)]
#[serde(tag = "type")]
pub enum AutoFillDbData {
    Login {
        username:Option<String>,
        password:Option<String>,
    },
    CreditCard {}
}

// Corresponding UDL:
// [Trait, WithForeign]
// interface AndroidApiService {};
#[uniffi::export(with_foreign)]
pub trait AndroidApiService: Send + Sync {
    fn clipboard_copy_string(&self, clip_data:ClipDataArg) -> ApiCallbackResult<()>;
    // Autofill specific
    fn autofill_client_app_url_info(&self,) -> ApiCallbackResult<HashMap<String,String>>;
    fn complete_autofill(&self,auto_fill_data:AutoFillDbData) -> ApiCallbackResult<()>;
}

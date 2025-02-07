use std::{path::PathBuf, sync::{Arc, OnceLock}};
use onekeepass_core::error::Result;
use super::storage_service::RemoteStorageType;

// TODO: 
// Moved this module along with 'storage' module from onekeepass_core crate as it is not yet used for desktop app. 
// We will continue to use  this callback feature for now even after the move. 
// Later we can directly use the 'CommonCallbackServiceImpl' by merging the modules 
// callback_service and callback_service_provider

// However if we plan to use sftp/webdav in desktop also, then these modules are to be moved back to onekeepass_core

// Original comment for the using the callback mechanism
// This module is used by fns in this crate to use any callback servcices
// implemented in the calling crate (e.g db-service-ffi)

// TODO: Should 'server_connection_config::ConnectionConfigReaderWriterStore' also be moved here ?


pub struct CallbackServiceProvider {
    common_callback_service: Arc<dyn CommonCallbackService>,
} 

pub trait CommonCallbackService: Send + Sync {
    fn sftp_private_key_file_full_path(&self,connection_id:&str,file_name:&str) -> PathBuf;
    fn sftp_copy_from_temp_key_file(&self,connection_id:&str,file_name:&str) -> Result<()>;
    fn remote_storage_config_deleted(&self,remote_type:RemoteStorageType,connection_id:&str,) -> Result<()>;
}

static CALLBACK_PROVIDER: OnceLock<CallbackServiceProvider> = OnceLock::new();

impl CallbackServiceProvider {
    fn shared() -> &'static CallbackServiceProvider {
        // Panics if no global state object was set. ??
        CALLBACK_PROVIDER.get().unwrap()
    }

    pub(crate) fn common_callback_service() -> &'static dyn CommonCallbackService {
        Self::shared().common_callback_service.as_ref()
    }

    // Need to be called from the 'db-service-ffi' crate onetime when the app is starting
    pub fn init(common_callback_service: Arc<dyn CommonCallbackService>) {
        let provider = CallbackServiceProvider {
            common_callback_service,
        };

        if CALLBACK_PROVIDER.get().is_none() {
            if CALLBACK_PROVIDER.set(provider).is_err() {
                log::error!(
                    "Global CALLBACK_PROVIDER object is initialized already. This probably happened concurrently."
                );
            }
        }
    }
}
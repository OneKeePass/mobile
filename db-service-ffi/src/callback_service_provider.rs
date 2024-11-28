use crate::{
    app_state::AppState,
    db_service::callback_service::{CallbackServiceProvider, CommonCallbackService},
};
use log::debug;
use std::{
    path::{Path, PathBuf},
    sync::Arc,
};

// This module provides the callback service for the types in onekeepas-core crate


#[derive(Default)]
struct CommonCallbackServiceImpl {}

impl CommonCallbackService for CommonCallbackServiceImpl {
    fn sftp_private_key_file_full_path(&self, file_name: &str) -> std::path::PathBuf {
        PathBuf::from(AppState::sftp_private_keys_path()).join(file_name)
    }
}

// Initalized onetime by calling this fn in 'db_service_initialize'
pub(crate) fn init_callback_service_provider() {
    let instance = Arc::new(CommonCallbackServiceImpl::default());
    // // In case, we need to hold any reference at this module, then we need to Arc::clone and use it
    CallbackServiceProvider::setup(instance);
    debug!("Remote storage connection config_reader_writer is initialized in init_rs_connection_configs_store ");
}

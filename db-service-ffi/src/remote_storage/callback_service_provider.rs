use crate::{
    app_state::AppState, remote_storage::{callback_service::{CallbackServiceProvider, CommonCallbackService}, RemoteStorageType}, util
};
use log::debug;
use std::{
    fs,
    path::{Path, PathBuf},
    sync::Arc,
};

use onekeepass_core::error::Result;

// This module provides the callback service for the types in 'callback_service'
// TODO: See comments in 'callback_service' module what to do in later release

// Initalized onetime by calling this fn in 'db_service_initialize'
pub(crate) fn init_callback_service_provider() {
    let instance = Arc::new(CommonCallbackServiceImpl::default());
    // In case, we need to hold any reference at this module, then we need to Arc::clone and use it
    CallbackServiceProvider::init(instance);
    debug!("init_callback_service_provider is called and CallbackServiceProvider init is done");
}

#[derive(Default)]
struct CommonCallbackServiceImpl {}

impl CommonCallbackService for CommonCallbackServiceImpl {
    // Provides the private key's absolute file path for sftp connection auth 
    fn sftp_private_key_file_full_path(
        &self,
        connection_id: &str,
        file_name: &str,
    ) -> std::path::PathBuf {
        // Private key should be there
        PathBuf::from(AppState::sftp_private_keys_path())
            .join(connection_id)
            .join(file_name)
    }

    // Called to copy the sftp private key file from temp dir to a permanent location for later auth call
    fn sftp_copy_from_temp_key_file(&self, connection_id: &str, file_name: &str) -> Result<()> {
        let temp_file_full_path = AppState::temp_dir_path().join(&file_name);

        let sftp_pk_path = AppState::sftp_private_keys_path();
        let final_full_file_path =
            util::create_sub_dir_path(&sftp_pk_path, connection_id).join(file_name);

        debug!(
            "Copying temp sftp key file {:?} to permanent location {:?} ",
            sftp_pk_path, final_full_file_path
        );

        fs::copy(temp_file_full_path, final_full_file_path)?;

        Ok(())
    }

    fn remote_storage_config_deleted(
        &self,
        remote_type: RemoteStorageType,
        connection_id: &str,
    ) -> Result<()> {
        if let RemoteStorageType::Sftp = remote_type {
            // Get the root dir for this sftp connection only
            // e.g remote_storage/sftp/264226dc-be96-462a-a386-79adb6291ad7

            let sftp_pk_file_root = AppState::sftp_private_keys_path().join(connection_id);
            // Removes a directory at this path, after removing all its contents
            let r = fs::remove_dir_all(&sftp_pk_file_root);

            log::debug!(
                "Delete private key file dir {:?} result {:?}",
                &sftp_pk_file_root,
                r
            );
        }

        Ok(())
    }
}

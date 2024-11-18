use std::{fs, path::Path, sync::Arc};

use log::debug;
use onekeepass_core::db_service::{
    self as kp_service,
    storage::{self,ConnectionConfigReaderWriter},
};

use crate::{app_state::AppState, udl_uniffi_exports::ApiCallbacksStore};

// Should be called on app startup (see db_service_initialize fn and called from middle layer)
// so that services are availble for the db_service layer.
// Without this initialization, the encrypted remote storage connection configs can not be read or written
pub fn init_rs_connection_configs_store() {
    let reader_writer = Arc::new(ConnectionConfigReaderWriterImpl::default());
    // // In case, we need to hold any reference at this module, then we need to Arc::clone and use it
    storage::set_config_reader_writer(reader_writer);
    debug!("Remote storage connection config_reader_writer is initialized in init_rs_connection_configs_store ");
}

const RS_CONFIG_FILE: &str = "rs_storage_configs.enc";

const SECURE_TAG: &str = "rs_config_data";

#[derive(Default)]
pub struct ConnectionConfigReaderWriterImpl {}

impl ConnectionConfigReaderWriter for ConnectionConfigReaderWriterImpl {
    fn read_string(&self) -> kp_service::Result<String> {
        let full_file_path = Path::new(&AppState::remote_storage_path()).join(RS_CONFIG_FILE);
        debug!("Remote storage full_file_path is {:?}", &full_file_path);
        if full_file_path.exists() {
            let encrypted_data = fs::read(full_file_path)?;
            debug!("Read encrypted_data and size is {}", &encrypted_data.len());

            let decrypted_data = ApiCallbacksStore::secure_enclave_cb_service()
                .decrypt_bytes(SECURE_TAG.to_string(), encrypted_data)?;

            debug!("Uncrypted and size is {}", &decrypted_data.len());

            let s = String::from_utf8_lossy(&decrypted_data);

            debug!("Uncrypted string data is  {}", &s);

            Ok(s.to_string())
        } else {
            debug!(
                "Remote storage full_file_path {:?} is not found and returning empty string",
                &full_file_path
            );
            Ok(String::default())
        }
    }

    fn write_string(&self, data: &str) -> kp_service::Result<()> {
        let full_file_path = Path::new(&AppState::remote_storage_path()).join(RS_CONFIG_FILE);
        let plain_data = data.as_bytes().to_vec();
        let encrypted_data = ApiCallbacksStore::secure_enclave_cb_service()
            .encrypt_bytes(SECURE_TAG.to_string(), plain_data)?;

        debug!(
            "Encrypted and size is {} and will write this data",
            &encrypted_data.len()
        );

        // let file = OpenOptions::new().read(true).write(true).create(true).open(full_file_path)?;

        fs::write(full_file_path, encrypted_data)?;

        Ok(())
    }
}

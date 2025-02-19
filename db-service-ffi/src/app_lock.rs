use log::{self, debug};
use onekeepass_core::{error, service_util::string_to_simple_hash};
use serde::{Deserialize, Serialize};

use crate::{app_state::AppState, udl_types::SecureKeyOperationError, OkpResult};

// The encrypted data is stored under this key
const APP_LOCK_PIN_TAG: &str = "OKP_APP_LOCK_PIN_KEY";

#[derive(Serialize, Deserialize, PartialEq)]
pub(crate) struct AppLockCredential {
    pin: usize,
}

impl AppLockCredential {

    pub(crate) fn encrypt_and_store(&self) -> OkpResult<()> {
        // Note: We are using APP_LOCK_PIN_TAG in both secure_enclave_cb_service calls and key store calls

        // Serialize to string
        let plain_data = serde_json::to_string(&self)?;

        // Encrypt the data
        let encrypted_data = AppState::secure_enclave_cb_service()
            .encrypt_bytes(APP_LOCK_PIN_TAG.to_string(), plain_data.as_bytes().to_vec())?;

        // Store the encrypted data in the key store
        let r = keystore_insert_or_update(APP_LOCK_PIN_TAG, &encrypted_data);

        debug!("keystore_insert_or_update is done to store PIN {}", r);

        Ok(())
    }

    pub(crate) fn verify(&self) -> OkpResult<bool> {

        // First we need to get the previously stored encrypted data from key store
        let decoded_enc_data = keystore_get_value(APP_LOCK_PIN_TAG).ok_or_else(|| {
            error::Error::SecureKeyOperationError(format!(
                "Getting expected encrypted app lock data from key store failed"
            ))
        })?;

        // Decrypt the data
        let decrypted_data = AppState::secure_enclave_cb_service()
            .decrypt_bytes(APP_LOCK_PIN_TAG.to_string(), decoded_enc_data)?;

        // Deserialize to the struct
        let decrypted_data_str = String::from_utf8_lossy(&decrypted_data);
        let stored_app_lock = serde_json::from_str::<AppLockCredential>(&decrypted_data_str)?;

        Ok(self == &stored_app_lock)
    }

    // Called to remove any encrypted app lock crdentials from key store
    pub(crate) fn remove_app_lock_credential() -> OkpResult<()> {
        let _r = AppState::secure_key_operation().delete_key(APP_LOCK_PIN_TAG.to_string());
        log::debug!("App lock enc data from key store is deleted..");
        Ok(())
    }
}

fn keystore_insert_or_update(acct_key: &str, encrypted_data: &Vec<u8>) -> bool {
    let ops = AppState::secure_key_operation();
    let encoded_enc_data = hex::encode(encrypted_data);
    match ops.store_key(acct_key.to_string(), encoded_enc_data.to_string()) {
        Ok(()) => {
            log::info!("Encrypted data is stored in key chain");
            true
        }
        Err(e) => {
            // This should not happen. However in case of iOS, we may get this error if the entry
            // is not deleted on closing the database
            if let SecureKeyOperationError::StoringKeyDuplicateItemError = e {
                if ops.delete_key(acct_key.to_string()).is_ok() {
                    let r = ops.store_key(acct_key.to_string(), encoded_enc_data.to_string());
                    log::info!("Second time call store key called and result is {:?}", r);
                    match r {
                        Ok(_) => true,
                        Err(_) => false,
                    }
                } else {
                    log::error!("secure_key_operation.delete call failed for app lock credential store time");
                    false
                }
            } else {
                log::error!("secure_key_operation.store_key failed for app lock credential store time with error: {} ", e);
                false
            }
        }
    }
}

fn keystore_get_value(data_key: &str) -> Option<Vec<u8>> {
    let key_str_opt = match AppState::secure_key_operation().get_key(data_key.to_string()) {
        Ok(v) => v,
        Err(e) => {
            log::error!("Query call to key chain failed {:?}", e);
            None
        }
    };

    log::debug!("Get key returned {:?}", &key_str_opt);

    let val = key_str_opt.and_then(|v| match hex::decode(&v) {
        Ok(v) => Some(v),
        Err(e) => {
            log::error!("Hex decoding failed for the value {} with error {}", v, e);
            None
        }
    });
    val
}

/*
pub(crate) fn store_credentials(&self, db_key: &str) -> OkpResult<()> {
        let plain_data = serde_json::to_string(&self)?;

        let ks_key = key_store_formatted_key(db_key);

        let encrypted_data = AppState::secure_enclave_cb_service()
            .encrypt_bytes(OKP_DB_OPEN_TAG.to_string(), plain_data.as_bytes().to_vec())?;

        log::debug!(
            "Encrypted and size is {} and will write this data",
            &encrypted_data.len()
        );

        KeyStoreServiceImpl {}.store_key(&ks_key, encrypted_data)?;

        log::debug!(
            "The credentials are stored in the key store after encryption for the db_key {db_key}"
        );

        Ok(())
    }
*/

use log::{self, debug};
use onekeepass_core::{db_service, error};
use serde::{Deserialize, Serialize};

use crate::{biometric_auth, ios};
use crate::{app_state::AppState, udl_types::SecureKeyOperationError, OkpResult};

use crate::util::{remove_dir_contents, remove_files};

//////////

// Stores the encrypted pin for later authentication
pub fn pin_entered(pin: usize) -> OkpResult<()> {
    let app_lock_credential = AppLockCredential { pin };
    let r = app_lock_credential.encrypt_and_store();

    // Need to ensure that the preference is enabled and persisted accordingly
    AppState::update_app_lock_with_pin_enabled(true);
    r
}

pub fn pin_removed() -> OkpResult<()> {
    let r = AppLockCredential::remove_app_lock_credential();
    // Need to ensure that the preference is disabled and persisted accordingly
    AppState::update_app_lock_with_pin_enabled(false);
    r
}

pub fn pin_verify(pin: usize) -> OkpResult<bool> {
    let app_lock_credential = AppLockCredential { pin };
    let r = app_lock_credential.verify();
    r
}

// Called to remove all app dirs and files to bring it to a default state
pub fn app_reset() -> OkpResult<()> {

    for db in AppState::recent_dbs_info() {
        // Close any opened database
        let _ = db_service::close_kdbx(&db.db_file_path);

        // Remove all stored credentials
        let _ = biometric_auth::StoredCredential::remove_credentials(&db.db_file_path);
    }

    // Remove all stored app lock credentials (PIN)
    let _r = AppLockCredential::remove_app_lock_credential();

    // Deletes contents of backups/history dir including sub dirs found under this dir
    let _ = remove_dir_contents(AppState::backup_history_dir_path());

    // Deletes contents of export_data dir including sub dirs found under this dir
    let _ = remove_dir_contents(AppState::export_data_dir_path());

    // Deletes contents of key_files dir including sub dirs found under this dir
    let _ = remove_dir_contents(AppState::key_files_dir_path());

    //  Deletes contents of remote_storage/sftp including sub dirs found under this dir
    // This ensure we keep the sftp sub dir
    let _ = remove_dir_contents(AppState::sftp_private_keys_path());

    // Deletes files found under remote_storage
    let _ = remove_files(AppState::remote_storage_path());

    AppState::reset_preference();

    #[cfg(target_os = "ios")]
    {
        let _ = remove_dir_contents(ios::bookmark::bookmark_dir());

        ios::remove_all_app_extension_contents();
    }

    debug!("App reset is done...");

    Ok(())
}

////////

// The encrypted data is stored under this key
const APP_LOCK_PIN_TAG: &str = "OKP_APP_LOCK_PIN_KEY";

#[derive(Serialize, Deserialize, PartialEq)]
pub(crate) struct AppLockCredential {
    pin: usize,
}

impl AppLockCredential {
    fn encrypt_and_store(&self) -> OkpResult<()> {
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

    fn verify(&self) -> OkpResult<bool> {
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
    fn remove_app_lock_credential() -> OkpResult<()> {
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

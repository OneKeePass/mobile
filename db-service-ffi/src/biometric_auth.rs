use log::{self, debug};
use onekeepass_core::{error, service_util::string_to_simple_hash};
use serde::{Deserialize, Serialize};

use crate::{app_state::AppState, udl_types::SecureKeyOperationError, OkpResult};

#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub(crate) struct StoredCredential {
    pub(crate) password: Option<String>,
    pub(crate) key_file_name: Option<String>,
}

#[inline]
fn key_store_formatted_key(db_key: &str) -> String {
    format!("OKP-DB-OPEN-{}", string_to_simple_hash(db_key))
        .as_str()
        .into()
}

// This encrypted data is stored under this key
const OKP_DB_OPEN_TAG: &str = "OKP_DB_OPEN_KEY";

impl StoredCredential {
    pub(crate) fn store_credentials_on_check(
        db_key: &str,
        password: &Option<String>,
        key_file_name: &Option<String>,
        biometric_auth_used: bool,
    ) -> OkpResult<()> {
        let bio_enabled = AppState::db_open_biometeric_enabled(db_key);

        // debug!("Flags are {}, {}", bio_enabled, !biometric_auth_used);

        if bio_enabled && !biometric_auth_used {
            let mut sc = StoredCredential {
                password: password.clone(),
                key_file_name: key_file_name.clone(),
            };

            let r = sc.store_credentials(db_key).inspect_err(|e| {
                log::debug!("Storing credentials failed with eroor {e}");
            });
            // debug!("store_credentials_on_check done {:?}", &r);
            return r;
        } else {
            // debug!("No credentials stored for this key {} as bio_enabled {} and biometric_auth_used {}", &db_key, bio_enabled, biometric_auth_used);
            Ok(())
        }
    }

    fn store_credentials(&mut self, db_key: &str) -> OkpResult<()> {

        self.remove_key_file_root_path(); 

        let plain_data = serde_json::to_string(&self)?;

        let ks_key = key_store_formatted_key(db_key);

        let encrypted_data = AppState::secure_enclave_cb_service()
            .encrypt_bytes(OKP_DB_OPEN_TAG.to_string(), plain_data.as_bytes().to_vec())?;

        // log::debug!( "Encrypted and size is {} and will write this data",&encrypted_data.len());

        KeyStoreServiceImpl {}.store_key(&ks_key, encrypted_data)?;

        // log::debug!("The credentials are stored in the key store after encryption for the db_key {db_key}");

        Ok(())
    }

    // Any previously stored credentials are retrieved if found. It is expected this is called
    // after a successful biometric authentication from the UI side
    pub(crate) fn get_credentials(db_key: &str) -> Option<Self> {
        let ks_key = key_store_formatted_key(db_key);

        let data = KeyStoreServiceImpl {}.get_key(&ks_key);
        if let Some(encrypted_data) = data {
            // Ideally no error should result in the decryption call
            let r = AppState::secure_enclave_cb_service()
                .decrypt_bytes(OKP_DB_OPEN_TAG.to_string(), encrypted_data);

            if let Err(e) = r {
                log::info!(
                    "Decryption of credentials data for the db_key {} failed with error {:?}",
                    &db_key,
                    e
                );
                return None;
            }
            // We can use unwrap as there is no error in decryption
            let decrypted_data = r.unwrap();

            let decrypted_data_str = String::from_utf8_lossy(&decrypted_data);

            let Ok(mut stored_credential) =
                serde_json::from_str::<StoredCredential>(&decrypted_data_str).map_err(|e| {
                    log::error!("Serde json parsing failed with error {}", e);
                    e
                })
            else {
                return None;
            };

            stored_credential.adjust_key_file_path(); 
            
            Some(stored_credential)
        } else {
            None
        }
    }

    // iOS specific
    // Called to remove the dir part from the full key file name before storing it to the secure enclave.
    // We need to do this as each installation of an iOS app generates a unique identifier (UUID) that is used in the path found in Containers/Data/Application
    // Because of this, after each installation of the app, the the key file path will point to an invalid path. 
    // By keeping only the key file name, we can avoid this issue. The full path is formed after reading this stored key file name
    fn remove_key_file_root_path(&mut self){
        #[cfg(target_os = "ios")]
        {
            if let Some(stored_kf_full_name) = self.key_file_name.as_ref() {
                let p = std::path::Path::new(stored_kf_full_name);
                // On Unix, a path has a root if it begins with /
                if p.has_root() {
                    let key_file_name_part = AppState::common_device_service()
                        .uri_to_file_name(stored_kf_full_name.clone())
                        .map_or(String::default(), |s| s);
                    self.key_file_name = Some(key_file_name_part);
                }
            }
        }
    }

    // iOS specific
    // Called to remove any previous dir part from the full key file name
    // and then form the new full key file path using the app group root 
    fn adjust_key_file_path(&mut self) {
        #[cfg(target_os = "ios")]
        {
            // This call ensures that key_file_name is just the file name part and not the full path
            self.remove_key_file_root_path();
            if let Some(kf_name_part) = self.key_file_name.as_ref() {
                // Form the full path
                let path = AppState::key_files_dir_path().join(kf_name_part);
                self.key_file_name = path.to_str().map(|s| s.to_string());
            }
        }
    }

    pub(crate) fn remove_credentials(db_key: &str) -> OkpResult<()> {
        remove_credentials_from_key_store(db_key)
    }
}

// TODO: Need to combine this with key_secure::KeyStoreServiceImpl
// See comments in src/key_secure.rs

#[derive(Default)]
struct KeyStoreServiceImpl {}

impl KeyStoreServiceImpl {
    // On successful loading of database, the keys are encrypted with Aes GCM cipher
    // and the encryption key for keys is stored in the KeyChain for iOS and in key store in Android.

    fn store_key(&mut self, data_key: &str, data: Vec<u8>) -> OkpResult<()> {
        log::debug!("store_key is called and data size {}", data.len());

        let enc_key = hex::encode(&data);

        let ops = AppState::secure_key_operation();

        // log::debug!( "Storing in key chain / key store for the store_key {}",&data_key);

        let _stored = match ops.store_key(data_key.to_string(), enc_key.clone()) {
            Ok(()) => {
                // log::info!("Encrypted key is stored in key chain");
                true
            }
            Err(e) => {
                // This should not happen. However in case of iOS, we may get this error if the entry
                // is not deleted on closing the database
                if let SecureKeyOperationError::StoringKeyDuplicateItemError = e {
                    if ops.delete_key(data_key.to_string()).is_ok() {
                        let r = ops.store_key(data_key.to_string(), enc_key);
                        log::info!("Second time call store key is called after deleting an existing key and result is {:?}", r);
                        match r {
                            Ok(_) => true,
                            Err(_) => false,
                        }
                    } else {
                        log::error!("secure_key_operation.delete call failed");
                        false
                    }
                } else {
                    log::error!("secure_key_operation.store_key failed {}", e);
                    false
                }
            }
        };
        Ok(())
    }

    fn get_key(&self, data_key: &str) -> Option<Vec<u8>> {
        let key_str_opt = match AppState::secure_key_operation().get_key(data_key.to_string()) {
            Ok(v) => v,
            Err(e) => {
                log::error!("Query call to key chain failed {:?}", e);
                None
            }
        };

        // log::debug!("Get key returned {:?}", &key_str_opt);

        let val = key_str_opt.and_then(|v| match hex::decode(&v) {
            Ok(v) => Some(v),
            Err(e) => {
                log::error!("Hex decoding failed for the value {} with error {}", v, e);
                None
            }
        });

        val
    }

    fn delete_key(&mut self, data_key: &str) -> OkpResult<()> {
        let _r = AppState::secure_key_operation().delete_key(data_key.to_string());
        log::debug!("Keys are deleted..");
        Ok(())
    }

    fn copy_key(&mut self, source_key: &str, target_key: &str) -> OkpResult<()> {
        if let Some(source_key) = self.get_key(source_key) {
            let _r = self.store_key(target_key, source_key);
            log::debug!("Keys are copied...");
        }

        Ok(())
    }
}

fn remove_credentials_from_key_store(db_key: &str) -> OkpResult<()> {
    let ks_key = key_store_formatted_key(db_key);
    KeyStoreServiceImpl {}.delete_key(&ks_key)
}

// pub(crate) fn set_db_open_biometric(db_key: &str, enabled: bool) -> OkpResult<()> {

//     AppState::shared().set_db_open_biometric(db_key, enabled);

//     // enabled is false when the biometric is disabled for the db
//     if !enabled {
//         remove_credentials_from_key_store(db_key)?;
//     }
//     Ok(())
// }

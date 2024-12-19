use log::{debug, error, info};
use secstr::SecVec;
use std::{
    collections::HashMap,
    sync::{Arc, Mutex},
};

use onekeepass_core::db_service as kp_service;

use crate::app_state::AppState;
use crate::udl_types::SecureKeyOperationError;

// Should be called on app startup (see db_service_initialize fn and called from middle layer)
// so that services are availble for the db_service layer.
// Without this setup opening or creating any database file will result in 'panic!'
// which we will be able to detect during dev time itself
pub fn init_key_main_store() {
    let kss = Arc::new(Mutex::new(KeyStoreServiceImpl::default()));
    // In case, we need to hold any reference at this module, then we need to Arc::clone and use it
    kp_service::KeyStoreOperation::init(kss);
    debug!("key_secure - key_main_store is initialized in init_key_main_store ");
}

#[derive(Default)]
pub struct KeyStoreServiceImpl {
    // In memory local store as a backup if required
    store: HashMap<String, SecVec<u8>>,
}

impl kp_service::KeyStoreService for KeyStoreServiceImpl {
    // On successful loading of database, the keys are encrypted with Aes GCM cipher
    // and the encryption key for keys is stored in the KeyChain for iOS and in key store in Android.

    fn store_key(&mut self, db_key: &str, data: Vec<u8>) -> kp_service::Result<()> {
        debug!("store_key is called and data size {}", data.len());

        let acct_key = kp_service::service_util::formatted_key(&db_key);
        let enc_key = hex::encode(&data);

        let ops = AppState::secure_key_operation();

        debug!(
            "Storing in key chain / key store for the acct_key {}",
            &acct_key
        );

        let stored = match ops.store_key(acct_key.clone(), enc_key.clone()) {
            Ok(()) => {
                info!("Encrypted key is stored in key chain");
                true
            }
            Err(e) => {
                // This should not happen. However in case of iOS, we may get this error if the entry
                // is not deleted on closing the database
                if let SecureKeyOperationError::StoringKeyDuplicateItemError = e {
                    if ops.delete_key(acct_key.clone()).is_ok() {
                        let r = ops.store_key(acct_key.clone(), enc_key);
                        info!("Second time call store key called and result is {:?}", r);
                        match r {
                            Ok(_) => true,
                            Err(_) => false,
                        }
                    } else {
                        error!("secure_key_operation.delete call failed");
                        false
                    }
                } else {
                    error!("secure_key_operation.store_key failed {}", e);
                    false
                }
            }
        };

        if !stored {
            self.store.insert(acct_key.clone(), SecVec::new(data));
            info!(
                "All attempts to store in key chain/key store failed and using the local storage"
            );
        }

        Ok(())
    }

    fn get_key(&self, db_key: &str) -> Option<Vec<u8>> {
        // This is not expected. As a precautionary, we check the local store
        if let Some(v) = self.store.get(db_key) {
            return Some(Vec::from(v.unsecure()));
        }

        let acct_key = kp_service::service_util::formatted_key(db_key);

        let key_str_opt = match AppState::secure_key_operation().get_key(acct_key) {
            Ok(v) => v,
            Err(e) => {
                error!("Query call to key chain failed {:?}", e);
                None
            }
        };

        debug!("Get key returned {:?}", &key_str_opt);

        let val = key_str_opt.and_then(|v| match hex::decode(&v) {
            Ok(v) => Some(v),
            Err(e) => {
                error!("Hex decoding failed for the value {} with error {}", v, e);
                None
            }
        });

        val
    }

    fn delete_key(&mut self, db_key: &str) -> kp_service::Result<()> {
        let acct_key = kp_service::service_util::formatted_key(db_key);
        //self.store.remove(db_key);
        let _r = AppState::secure_key_operation().delete_key(acct_key);
        debug!("Keys are deleted..");
        Ok(())
    }

    fn copy_key(&mut self, source_db_key: &str, target_db_key: &str) -> kp_service::Result<()> {
        if let Some(source_key) = self.get_key(source_db_key) {
            let _r = self.store_key(target_db_key, source_key);
            debug!("Keys are copied...");
        }

        Ok(())
    }
}

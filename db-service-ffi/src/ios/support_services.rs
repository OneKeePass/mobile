use std::{
    fs::File,
    io::{Read, Seek, Write},
    path::Path,
};

use log::debug;
use onekeepass_core::{db_service, service_util::string_to_simple_hash};

use crate::{
    app_state::AppState,
    backup,
    commands::{error_json_str, remove_app_files, result_json_str, CommandArg, InvokeResult, ResponseJson},
    open_backup_file, OkpError, OkpResult,
};

use super::to_ios_file_uri;

// This is for any iOS specific services and called from the Swift code

#[derive(uniffi::Object)]
pub struct IosSupportService {}

// TODO: Need to delete the bookmark created while loading a kdbx file and then user cancels the login 

// Created in iOS side native module and then iOS specific
// support methods are called
#[uniffi::export]
impl IosSupportService {
    // Constructors need to be annotated as such.
    // All functions that are not constructors must have a `self` argument
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {}
    }

    pub fn invoke(&self, command_name: &str, json_args: &str) -> ResponseJson {
        let r = match command_name {
            
            "test_call" => result_json_str(self.test_call(json_args)),

            x => error_json_str(&format!(
                "Invalid command or args: Command call {} with args {} failed",
                x, &json_args
            )),
        };

        r
        
    }

    // The implementation for the udl defined fn "boolean save_book_mark_data(string url,sequence<u8> data)"
    // Called in case of iOS app, to save the secure url bookmarking data
    // Note: At this time both kdbx and the key file uri bookmarking use the same way
    pub fn save_book_mark_data(&self, url: String, data: Vec<u8>) -> bool {
        let file_name = string_to_simple_hash(&url).to_string();

        let book_mark_file_root = Path::new(AppState::app_home_dir()).join("bookmarks");
        // Ensure that the parent dir exists
        if !book_mark_file_root.exists() {
            if let Err(e) = std::fs::create_dir_all(&book_mark_file_root) {
                log::error!("Bookmark root dir creation failed {:?}", e);
                return false;
            }
        }

        let book_mark_file_path = book_mark_file_root.join(file_name);

        log::info!(
            "Book mark path to save data is {:?} and size of data {}",
            book_mark_file_path,
            data.len()
        );

        match File::create(book_mark_file_path) {
            Ok(mut f) => match f.write(&data) {
                Ok(_) => true,
                Err(e) => {
                    log::error!("Bookmarks saving failed with error: {:?}", e);
                    false
                }
            },
            Err(e) => {
                log::error!("Bookmarks saving failed with error: {:?}", e);
                false
            }
        }
    }

    // Implements the udl defined fn - sequence<u8> load_book_mark_data(string url);
    pub fn load_book_mark_data(&self, url: String) -> Vec<u8> {
        let file_name = string_to_simple_hash(&url).to_string();
        log::debug!(
            "load_book_mark_data is called for url {} and its hash is {} ",
            &url,
            &file_name
        );
        let book_mark_file_path = Path::new(AppState::app_home_dir())
            .join("bookmarks")
            .join(file_name);
        log::info!("Book mark path to load data is {:?}", book_mark_file_path);
        let mut data = vec![];
        match File::open(book_mark_file_path) {
            Ok(mut f) => match f.read_to_end(&mut data) {
                Ok(_) => data,
                Err(e) => {
                    log::error!("Bookmarks reading failed with error: {:?}", e);
                    vec![]
                }
            },
            Err(e) => {
                log::error!("Bookmarks reading failed with error: {:?}", e);
                vec![]
            }
        }
    }

    // Called to delete any previous bookmark data.
    // For now mainly used after saving any key file to a user selected location
    pub fn delete_book_mark_data(&self, full_file_name_uri: &str) {
        super::delete_book_mark_data(&full_file_name_uri);
    }

    // Called to copy the backup file when user wants to do "Save as" operation
    // instead of overwriting or discarding the current db
    pub fn copy_last_backup_to_temp_file(
        &self,
        kdbx_file_name: String,
        full_file_name_uri: String,
    ) -> Option<String> {
        if let Some(bkp_file_name) =
            AppState::get_last_backup_on_error(&full_file_name_uri)
        {
            let mut temp_file = std::env::temp_dir();
            // kdbx_file_name is the suggested file name to use in the Docuemnt picker
            // and user may change the name
            temp_file.push(kdbx_file_name);
            // Copies backup file to temp file with proper kdbx file name
            if std::fs::copy(&bkp_file_name, &temp_file).is_err() {
                log::error!("Copying the last backup to temp file failed ");
                return None;
            }
            let mut temp_name = temp_file.as_os_str().to_string_lossy().to_string();
            to_ios_file_uri(&mut temp_name);
            Some(temp_name)
        } else {
            // We are assuming there is a backup file written before this call
            // TODO:
            //  To be failsafe, we may need to write from the db content to temp file
            //  using db_service::save_kdbx_to_writer in case there is no backup at all
            None
        }
    }

    pub fn complete_save_as_on_error(&self, json_args: &str) -> String {
        let inner_fn = || -> OkpResult<db_service::KdbxLoaded> {
            if let Ok(CommandArg::SaveAsArg {
                db_key,
                new_db_key,
                file_name,
            }) = serde_json::from_str(json_args)
            {
                let kdbx_loaded = db_service::rename_db_key(&db_key, &new_db_key)?;

                // Need to ensure that the checksum is reset to the newly saved file
                // Otherwise, Save error modal dialog will popup !
                let modified_db_bkp_file_opt = AppState::get_last_backup_on_error(&db_key);

                if let Some(mut modified_db_bkp_file) =
                    open_backup_file(modified_db_bkp_file_opt.as_ref())
                {
                    db_service::calculate_and_set_db_file_checksum(
                        &new_db_key,
                        &mut modified_db_bkp_file,
                    )?;

                    // Need to create the backup file for the newly created database
                    let mut new_db_bk_file =
                        backup::generate_and_open_backup_file(&new_db_key, &file_name)?;

                    modified_db_bkp_file.rewind()?;

                    // Copy to the new db backup file
                    let _n = std::io::copy(&mut modified_db_bkp_file, &mut new_db_bk_file)?;
                    //debug!("The new_db_bk_file copied bytes size is {}", n);
                    new_db_bk_file.sync_all()?;
                } else {
                    log::error!("Expected backup file is not found. 'Save as' should have this");
                    return Err(OkpError::DataError("Expected backup file is not found"));
                }

                // AppState::global().remove_recent_db_use_info(&db_key);

                // Why is this call?
                // Possibly to remove all old references of the failed saving with 'db_key' as
                // we have the newly saved db with 'new_db_key'
                remove_app_files(&db_key);

                AppState::add_recent_db_use_info(&new_db_key);
                AppState::remove_last_backup_name_on_error(&db_key);

                Ok(kdbx_loaded)
            } else {
                Err(OkpError::UnexpectedError(format!(
                    "Call complete_save_as_on_error failed due Invalid args {}",
                    json_args
                )))
            }
        };
        InvokeResult::from(inner_fn()).json_str()
    }
}


impl IosSupportService {

    fn test_call(&self, _json_args: &str) -> OkpResult<()> {
        /*
        use crate::biometric_auth::StoredCredential;

        let sc = StoredCredential {password:Some("MyPassword".to_string()), key_file_name:Some("key_file_name1".to_string())};

        let test_db_key = "file:///Users/jeyasankar/Library/Developer/CoreSimulator/Devices/A45B3252-1AA4-4D50-9E6E-89AB1E873B1F/data/Containers/Shared/AppGroup/6CFFA9FC-169B-482E-A817-9C0D2A6F5241/File%20Provider%20Storage/some_db.kdbx";
        
        sc.store_credentials(test_db_key)?;

        if let Some(d_sc) = StoredCredential::get_credentials(test_db_key) {
            debug!("Decrypted data is {:?} and equal? {}", d_sc, sc == d_sc);
        }

        let r = StoredCredential::remove_credentials(test_db_key);

        debug!("Remove credentials result {:?}",r);
         */

        Ok(())
    }
}
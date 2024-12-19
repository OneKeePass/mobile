use std::{fs::File, io::Seek, os::fd::IntoRawFd, path::Path};

use log::debug;
use onekeepass_core::db_service::{self, KdbxLoaded};
use url::Url;

use crate::{
    app_state::AppState,
    as_api_response, backup,
    commands::{
        self, error_json_str, remove_app_files, result_json_str, CommandArg, InvokeResult,
        ResponseJson,
    },
    open_backup_file, parse_command_args_or_err,
    udl_types::ApiResponse,
    udl_uniffi_exports::AppClipboardCopyData,
    util, OkpError, OkpResult,
};

use super::{AndroidApiCallbackImpl, AutoFillDbData};

use crate::return_api_response_failure;

// NOTE: The fns declared in service 'AndroidSupportService' in udl file are moved here

// Corresponding UDL:
// interface AndroidSupportServiceExtra {};
#[derive(uniffi::Object)]
struct AndroidSupportServiceExtra {}

// All fns implemented of struct here are exported to use in Swift because of '#[uniffi::export]'
// See the next 'impl' of this struct for non exported functions

#[uniffi::export]
impl AndroidSupportServiceExtra {
    // Constructors need to be annotated as such.
    // All functions that are not constructors must have a `self` argument
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {}
    }

    pub fn invoke(&self, command_name: &str, json_args: &str) -> ResponseJson {
        let r = match command_name {
            "autofill_filtered_entries" => self.autofill_filtered_entries(json_args),
            "complete_autofill" => self.complete_autofill(json_args),

            "clipboard_copy" => self.clipboard_copy(json_args),

            "test_call" => result_json_str(self.test_call(json_args)),

            x => error_json_str(&format!(
                "Invalid command or args: Command call {} with args {} failed",
                x, &json_args
            )),
        };

        r
    }

    // called after DocumentPickerServiceModule.pickKdbxFileToCreate to do Save as feature
    // Note: This impl is slightly different from iOS
    pub fn complete_save_as_on_error(
        &self,
        file_descriptor: u64,
        old_full_file_name_uri: String,
        new_full_file_name_uri: String,
        file_name: String,
    ) -> ApiResponse {
        log::debug!("AndroidSupportService complete_save_as_on_error is called ");
        let mut file = unsafe { util::get_file_from_fd(file_descriptor) };

        let mut inner = || -> OkpResult<KdbxLoaded> {
            // get_last_backup_on_error should have a valid back file created 
            // for the content of db failed to save
            if let Some(bkp_file_name) =
                AppState::shared().get_last_backup_on_error(&old_full_file_name_uri)
            {
                // The last modified db is written to the 'bkp_file_name' 
                let mut modified_bk_file_reader = File::open(bkp_file_name)?;

                // Writes the modified db data to the new the file selected by the user
                std::io::copy(&mut modified_bk_file_reader, &mut file)?;
                // sync_all ensures the file is created and synced in case of dropbbox and one drive
                let _ = file.sync_all();

                let kdbx_loaded =
                    db_service::rename_db_key(&old_full_file_name_uri, &new_full_file_name_uri)?;

                // Need to set the checksum for the newly saved file and checksum is calculated using the backup file itself
                // as using the newly written 'file' from 'file_descriptor' fails
                modified_bk_file_reader.rewind()?;
                db_service::calculate_and_set_db_file_checksum(
                    &new_full_file_name_uri,
                    &mut modified_bk_file_reader,
                )?;

                // Need to create the backup file for the newly created database
                let mut new_db_bk_file = backup::generate_and_open_backup_file(&new_full_file_name_uri, &file_name)?;

                modified_bk_file_reader.rewind()?;

                // Copies data from the latest modification of old db that we could not save to the new db's backup
                std::io::copy(&mut modified_bk_file_reader, &mut new_db_bk_file)?;
                let _ = new_db_bk_file.sync_all();

                // For now we remove all reference of the db file that failed to save
                remove_app_files(&old_full_file_name_uri);

                AppState::shared().add_recent_db_use_info2(&new_full_file_name_uri,&file_name);

                AppState::shared().remove_last_backup_name_on_error(&old_full_file_name_uri);
                Ok(kdbx_loaded)
            } else {
                Err(OkpError::UnexpectedError(format!(
                    "Last backup is not found"
                )))
            }
        };

        let api_response = as_api_response(inner());

        {
            log::debug!("AndroidSupportService complete_save_as_on_error: File fd_used is used  and will not be closed here");
            // We need to transfer the ownership of the underlying file descriptor to the caller.
            // By this call, the file instance created using incoming fd will not close the file at the end of
            // this function (Files are automatically closed when they go out of scope) and the caller
            // function from device will be responsible for the file closing.
            let _fd = file.into_raw_fd();
        }
        api_response
    }

    pub fn create_kdbx(&self, file_descriptor: u64, json_args: String) -> ApiResponse {
        log::debug!("AndroidSupportService create_kdbx is called ");

        let mut file = unsafe { util::get_file_from_fd(file_descriptor) };

        let mut full_file_name_uri: String = String::default();
        let r = match serde_json::from_str(&json_args) {
            Ok(CommandArg::NewDbArg { mut new_db }) => {
                full_file_name_uri = new_db.database_file_name.clone();
                // Need to get the file name from full uri
                new_db.file_name = AppState::shared()
                    .common_device_service
                    .uri_to_file_name(full_file_name_uri.clone());

                // Need to create a backup file for this newly created database
                let Some(file_name) = new_db.file_name.as_ref() else {
                    return_api_response_failure!(
                        "No valid file name formed from the full file uri"
                    );
                    //return as_api_response::<()>(Err(OkpError::DataError("No valid file name formed from the full file uri")));
                };
                let backup_file_name =
                    backup::generate_backup_history_file_name(&full_file_name_uri, file_name);
                let backup_file = open_backup_file(backup_file_name.as_ref());

                let Some(mut bf_writer) = backup_file else {
                    return_api_response_failure!(
                        "Backup file could not be opened to write the new database backup"
                    );
                    //return as_api_response::<()>(Err(OkpError::DataError("Backup file could not be opened to write the new database backup")));
                };

                let r = db_service::create_and_write_to_writer(&mut bf_writer, new_db);
                let rewind_r = bf_writer.sync_all().and(bf_writer.rewind());
                log::debug!(
                    "Syncing and rewinding of new db backup file result {:?}",
                    rewind_r
                );

                let _n = std::io::copy(&mut bf_writer, &mut file);
                log::debug!("Copied the backup to the new db file ");
                // sync_all ensures the file is created and synced in case of dropbbox and one drive
                let _ = file.sync_all();

                r
            }
            Ok(_) => Err(OkpError::UnexpectedError(
                "Unexpected arguments for create_kdbx api call".into(),
            )),
            Err(e) => Err(OkpError::UnexpectedError(format!("{:?}", e))),
        };

        {
            log::debug!("File fd_used is used and will not be closed here");
            // We need to transfer the ownership of the underlying file descriptor to the caller.
            // By this call, the file instance created using incoming fd will not close the file at the end of
            // this function (Files are automatically closed when they go out of scope) and the caller
            // function from device will be responsible for the file closing.
            // If this is not done, android app will crash!
            let _fd = file.into_raw_fd();
        }

        let api_response = match r {
            Ok(v) => match serde_json::to_string_pretty(&InvokeResult::with_ok(v)) {
                Ok(s) => {
                    //Add this newly created db file to the recent list
                    AppState::shared().add_recent_db_use_info(&full_file_name_uri);
                    ApiResponse::Success { result: s }
                }

                Err(e) => ApiResponse::Failure {
                    result: InvokeResult::<()>::with_error(&format!("{:?}", e)).json_str(),
                },
            },
            Err(e) => ApiResponse::Failure {
                result: InvokeResult::<()>::with_error(&format!("{:?}", e)).json_str(),
            },
        };
        api_response
    }

    pub fn save_key_file(&self, file_descriptor: u64, full_key_file_name: String) -> String {
        log::debug!(
            "AndroidSupportService save_key_file is called with full_key_file_name {}",
            &full_key_file_name
        );

        let inner = || -> OkpResult<()> {
            let p = Path::new(&full_key_file_name);
            if !p.exists() {
                return Err(OkpError::UnexpectedError(format!(
                    "Key file {:?} is not found",
                    p.file_name()
                )));
            }

            let mut reader = File::open(p)?;
            let mut writer = unsafe { util::get_file_from_fd(file_descriptor) };
            std::io::copy(&mut reader, &mut writer).and(writer.sync_all())?;
            {
                log::debug!("Key File fd is used and will not be closed here");
                // IMPORTANT
                // We need to transfer the ownership of the underlying file descriptor to the caller.
                // By this call, the file instance created using incoming fd will not close the file at the end of
                // this function (Files are automatically closed when they go out of scope) and the caller
                // function from device will be responsible for the file closing.
                // If this is not done, android app will crash!
                let _fd = writer.into_raw_fd();
            }
            Ok(())
        };
        commands::result_json_str(inner())
    }
}

// Here we implement all fns of this struct that are not exported as part of 'interface AndroidSupportServiceExtra'
// To use any of these internal fns in the background.cljs, that fn name should be used in this struct's invoke
// fn implemented here - see below

impl AndroidSupportServiceExtra {
    fn clipboard_copy(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<()> {
            let (field_name, field_value, protected, cleanup_after) = parse_command_args_or_err!(
                json_args,
                ClipboardCopyArg {
                    field_name,
                    field_value,
                    protected,
                    cleanup_after
                }
            );
            let clip_data = AppClipboardCopyData {
                field_name,
                field_value,
                protected,
                cleanup_after,
            };
            // Delegates to the android api
            AndroidApiCallbackImpl::api_service().clipboard_copy_string(clip_data)?;
            Ok(())
        };

        result_json_str(inner_fn())
    }

    // Based on the uri of an app that called Okp to autofill, we search entry items
    // of the currently opened database and return the matched entries if any
    fn autofill_filtered_entries(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<db_service::EntrySearchResult> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });

            let identifiers =
                AndroidApiCallbackImpl::api_service().autofill_client_app_url_info()?;

            debug!("Received AF client info {:?}", &identifiers);

            //
            let term = if let Some(url) = identifiers.get("uri") {
                if let Ok(u) = Url::parse(url) {
                    u.domain()
                        .map_or_else(|| String::default(), |s| s.to_string())
                } else {
                    url.to_string()
                }
            } else {
                String::default()
            };

            debug!("The filtered_entries domain term is {}", &term);
            let search_result = db_service::search_term(&db_key, &term)?;
            Ok(search_result)
        };

        result_json_str(inner_fn())
    }

    // Called to complete the pending autofill request
    // The arg json_args should be parseable as AutoFillDbData
    // instead of the usual CommandArgs
    fn complete_autofill(&self, json_args: &str) -> ResponseJson {
        let inner_fn = || -> OkpResult<()> {
            let auto_fill_data = serde_json::from_str::<AutoFillDbData>(json_args)?;

            match auto_fill_data {
                AutoFillDbData::Login { .. } => {
                    AndroidApiCallbackImpl::api_service().complete_autofill(auto_fill_data)?;
                }
                _x => return error_message("AutoFillDbData::Login", json_args),
            }

            Ok(())
        };

        result_json_str(inner_fn())
    }
}

fn error_message(enum_name: &str, json_args: &str) -> OkpResult<()> {
    let error_msg = format!(
        "Invalid command args received {} for the api call. Expected a valid args as {}",
        json_args, enum_name
    );
    return Err(OkpError::UnexpectedError(error_msg));
}

//////

impl AndroidSupportServiceExtra {
    fn test_call(&self, _json_args: &str) -> OkpResult<()> {
        let identifier = "TestAlias";
        let data = "This is my sentence";
        // let r = AppState::secure_enclave_cb_service().remove_key(identifier.to_string());
        // debug!("Removing key done with r {:?}", &r);

        let encrypted_data = AppState::secure_enclave_cb_service().encrypt_bytes(
            identifier.to_string(),
            data.as_bytes().to_vec(),
        )?;

        debug!(
            "Encrypted and size is {} and will write this data {}",
            &encrypted_data.len(),data,
        );

        let decrypted_data = AppState::secure_enclave_cb_service()
            .decrypt_bytes(identifier.to_string(), encrypted_data)?;

        debug!("Uncrypted and size is {}", &decrypted_data.len());

        let s = String::from_utf8_lossy(&decrypted_data);

        debug!("Uncrypted string data is  {}", &s);

        Ok(())
    }
}

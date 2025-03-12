use crate::app_preference::PreferenceData;
use crate::app_state::AppState;
use crate::auto_open::AutoOpenProperties;
use crate::file_util::PickedFileHandler;
use crate::remote_storage::{self, RemoteStorageOperation};
use crate::{android, file_util::KeyFileInfo, ios};
use crate::{app_lock, backup, biometric_auth, util, OkpError, OkpResult};
use onekeepass_core::async_service::{self, OtpTokenTtlInfoByField, TimerID};
use onekeepass_core::db_content::AttachmentHashValue;
use onekeepass_core::db_service::{
    self, DbSettings, EntryCategory, EntryCategoryGrouping, EntryFormData, Group, KdbxLoaded,
    NewDatabase, OtpSettings, PassphraseGenerationOptions, PasswordGenerationOptions,
};

use std::fmt::format;
use std::{
    collections::HashMap,
    fs::{File, OpenOptions},
    io::Seek,
    os::unix::prelude::IntoRawFd,
    path::Path,
};

use log::{debug, logger};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Serialize)]
struct ExportDataInfo {
    full_file_name_uri: Option<String>,
    file_name: Option<String>,
    exported_data_full_file_name: Option<String>,
}

#[derive(Serialize)]
pub struct TranslationResource {
    current_locale_language: String,
    prefered_language: String,
    translations: HashMap<String, String>,
}

//TODO:
// Need to use serde internally-tagged or externally-tagged to deserialize the enum 'CommandArg'
// instead of current 'untagged' feature. Using untagged feature getting more complex. More changes
// may be required in 'mobile/src/onekeepass/mobile/background.cljs' and may in the some native calls where json str are formed
// for this to work
// https://serde.rs/enum-representations.html#internally-tagged
// https://serde.rs/enum-representations.html#externally-tagged

// We use serder untagged so that the UI layer args map need not include the enum variant name
// Just the struct value is sent. The first matching enum variant (order of fields does not matter) is considered by serde.
// Only field names of the struct of each variant is considerd for matching. It is important we declare the
// most specific variants first and so on

// The deserializer does the following:
// 1. pick a variant in the order declared here
// 2. Checks whether all its fields are available (order of fields does not matter) in the deserialized data
// 3. If the previous check is success, returns the variant. Otherwise continue to the next variant

// IMPORTANT:
// If we add a variant with fields of only Option type(nullable), then that variant will be picked first
// Need to make sure to add that variant in a proper order (most probably last) to avoid this happening
// For example, the variant 'SessionTimeoutArg' (commented out one at the bottom) has only Option type fields. if this variant is put
// in the begining, then that variant will be picked for any json str resulting error

#[derive(Debug, Deserialize)]
#[serde(untagged)]
pub enum CommandArg {
    PasswordGeneratorArg {
        password_options: PasswordGenerationOptions,
    },
    PassPhraseGeneratorArg {
        pass_phrase_options: PassphraseGenerationOptions,
    },
    SessionTimeoutArg {
        // timeout_type is a dummy field so that SessionTimeoutArg is matched only we have this
        // field in the incoming json str. See
        timeout_type: u8,
        db_session_timeout: Option<i64>,
        clipboard_timeout: Option<i64>,
    },
    PrefefenceUpdateArg {
        preference_data: PreferenceData,
    },
    AppLockCredentialArg {
        pin:usize,
    },

    // Not used
    // OpenDbArgWithFileName {
    //     file_name: String,
    //     db_file_name: String,
    //     password: String,
    //     key_file_name: Option<String>,
    // },
    OpenDbArg {
        db_file_name: String,
        password: Option<String>,
        key_file_name: Option<String>,
        biometric_auth_used: bool,
    },
    NewDbArgWithFileName {
        file_name: String,
        new_db: NewDatabase,
    },
    NewDbArg {
        new_db: NewDatabase,
    },
    NewBlankGroupArg {
        mark_as_category: bool,
    },
    EntrySummaryArg {
        db_key: String,
        entry_category: EntryCategory,
    },
    CategoryDetailArg {
        db_key: String,
        grouping_kind: EntryCategoryGrouping,
    },
    // Should come before DbKeyWithUUIDArg
    MoveArg {
        db_key: String,
        uuid: Uuid,
        new_parent_id: Uuid,
    },
    EntryHistoryByIndexArg {
        db_key: String,
        uuid: Uuid,
        index: i32,
    },
    DbKeyWithUUIDArg {
        db_key: String,
        uuid: Uuid,
    },
    SaveAsArg {
        db_key: String,
        new_db_key: String,
        file_name: String,
    },
    GroupArg {
        db_key: String,
        group: Group,
    },
    NewEntryArg {
        db_key: String,
        entry_type_uuid: Uuid,
        parent_group_uuid: Option<Uuid>,
    },
    EntryArg {
        db_key: String,
        form_data: EntryFormData,
    },
    SearchArg {
        db_key: String,
        term: String,
    },
    DbSettingsArg {
        db_key: String,
        db_settings: DbSettings,
    },
    AttachmentArg {
        db_key: String,
        name: String,
        data_hash_str: String,
    },

    SaveDbArg {
        db_key: String,
        overwrite: bool,
    },

    DbOpenBiomerticArg {
        db_key: String,
        db_open_enabled: bool,
    },

    OtpSettingsArg {
        otp_settings: OtpSettings,
    },

    StartEntryOtpArg {
        db_key: String,
        entry_uuid: Uuid,
        otp_fields: OtpTokenTtlInfoByField,
    },

    StartTimerArg {
        period_in_milli_seconds: u64,
        timer_id: Option<TimerID>,
    },

    ClipboardCopyArg {
        field_name: String,
        field_value: String,
        protected: bool,
        //The field 'cleanup_after' has clipboard timeout in seconds and 0 sec menas no timeout
        cleanup_after: u32,
    },

    // Should come after StartTimerArg
    StopTimerArg {
        timer_id: TimerID,
    },

    TranslationsArg {
        language_ids: Vec<String>,
    },

    RemoteServerOperationArg {
        rs_operation_type: remote_storage::RemoteStorageOperationType,
    },

    PickedFileHandlerArg {
        picked_file_handler: PickedFileHandler,
    },

    AutoOpenPropsResolveArg {
        auto_open_properties: AutoOpenProperties,
    },

    // This variant needs to come last so that other variants starting with db_key is matched before this
    // and this will be matched only if db_key is passed. A kind of descending order with the same field names
    // in diffrent variant. If this variant put before any other variant with db_key field,
    // other variants starting with 'db_key' will not be considered
    DbKey {
        db_key: String,
    },

    GenericArg {
        key_vals: HashMap<String, String>,
    },
    // Need to come as last variant; Otherwise this will be matched before any variants coming after
    // this. This is because serde_json parses any json str as this variant with any json str. This happens because
    // we are using untagged deserialization and both the fields can be null
    // If we use non nullable fields are used in this variant, this issue will not happen
    // SessionTimeoutArg {
    //     db_session_timeout: Option<i64>,
    //     clipboard_timeout: Option<i64>,
    // },
}

pub type ResponseJson = String;

// Parses the CommandArg enum variants and calls a fn with those args values. This fn is expected to return OkpResult<T>
// This OkpResult is then converted to a json str
macro_rules! service_call  {
    ($args:expr,$enum_name:tt {$($enum_vals:tt)*} => $path:ident $fn_name:tt ($($fn_args:expr),*) ) => {

        if let Ok(CommandArg::$enum_name{$($enum_vals)*}) = serde_json::from_str(&$args) {
            // $path can be either Self or db_service and $fn_name is expected in Self or in db_service
            // $fn_name expected to return OkpResult<T>
            let r = $path::$fn_name($($fn_args),*);
            return  result_json_str(r);
        } else  {
            let fname = stringify!($fn_name);
            let ename = stringify!($enum_name);
            let error_msg = format!("Invalid command args received {} for the api call {}. Expected a valid CommandArg::{}",
                                    &$args.clone(),&fname, &ename);
            return InvokeResult::<()>::with_error(&error_msg.clone()).json_str();
        }
    };
}

// Parses the CommandArg enum variants and calls a fn with those args values. This fn is expected to return any value other than
// OkpResult<T>. The return value is then as Ok value

// How to combine service_ok_call with service_call?
macro_rules! service_ok_call  {
    ($args:expr,$enum_name:tt {$($enum_vals:tt)*} => $path:ident $fn_name:tt ($($fn_args:expr),*) ) => {

        if let Ok(CommandArg::$enum_name{$($enum_vals)*}) = serde_json::from_str(&$args) {
            // $path can be either Self or db_service and $fn_name is expected in Self or in db_service
            // $fn_name expected return non OkpResult<T> value
            let r = $path::$fn_name($($fn_args),*);
            return  result_json_str(Ok(r));
        } else  {
            let fname = stringify!($fn_name);
            let ename = stringify!($enum_name);
            let error_msg = format!("Invalid command args received {} for the api call {}. Expected a valid CommandArg::{}",
                                    &$args.clone(),&fname, &ename);
            return InvokeResult::<()>::with_error(&error_msg.clone()).json_str();
        }
    };
}

macro_rules! service_call_closure {
    ($args:expr,$enum_name:tt {$($enum_vals:tt)*} =>  $closure_fn:expr ) => {
        if let Ok(CommandArg::$enum_name{$($enum_vals)*}) = serde_json::from_str(&$args) {
            let r = $closure_fn();
            return  r;
        } else  {
            let ename = stringify!($enum_name);
            let error_msg = format!("Invalid command args received {} for the api call. Expected a valid CommandArg::{}",
                                    &$args.clone(), &ename);
            return InvokeResult::<()>::with_error(&error_msg.clone()).json_str();
        }
    };
}

macro_rules! db_service_call  {
    ($args:expr,$enum_name:tt {$($enum_vals:tt)*} => $fn_name:tt ($($fn_args:expr),*) ) => {
        service_call!($args,$enum_name {$($enum_vals)*} => db_service $fn_name ($($fn_args),*))
    };
}

// Wraps a no arg fn that returns a value in a OkpResult<T>
macro_rules! wrap_no_arg_ok_call {
    ($path:ident $fn_name:tt) => {{
        let r = $path::$fn_name();
        result_json_str(Ok(r))
    }};
}

// Parses the passed json string to a matched CommandArg enum
// Returns the field values of the matched enum member as tuple or error json string
// TODO: Need to merge this with the macro 'parse_command_args_or_err' as a single macro
#[macro_export]
macro_rules! parse_command_args_or_json_error {
    ($json_args:expr,$enum_name:tt {$($enum_vals:tt)*}) => {
        {
            let Ok(CommandArg::$enum_name{$($enum_vals)*}) = serde_json::from_str::<CommandArg>($json_args) else {
                let ename = stringify!($enum_name);
                let error_msg = format!("Invalid command args received {} for the api call. Expected a valid CommandArg::{}",
                                        &$json_args.clone(), &ename);
                return error_json_str(&error_msg)
            };
            ($($enum_vals)*,)
        }
    };
}

// Parses the passed json string to a matched CommandArg enum
// Returns the field values of the matched enum member as tuple or Err
#[macro_export]
macro_rules! parse_command_args_or_err {
    ($json_args:expr,$enum_name:tt {$($enum_vals:tt)*}) => {
        {
            let Ok(CommandArg::$enum_name{$($enum_vals)*}) = serde_json::from_str::<CommandArg>($json_args) else {
                let ename = stringify!($enum_name);
                let error_msg = format!("Invalid command args received {} for the api call. Expected a valid CommandArg::{}",
                                        &$json_args.clone(), &ename);
                return Err(OkpError::UnexpectedError(error_msg));
                // return Err(OkpError::UnexpectedError(format!(
                //     "Unexpected argument {:?} for copy_file_to_app_group api call",
                //     $json_args
                // )));
            };
            ($($enum_vals)*,)
        }
    };
}

pub struct Commands {}

impl Commands {
    pub fn invoke(command_name: String, args: String) -> String {
        if command_name.trim().is_empty() {
            return InvokeResult::<()>::with_error("Command name is empty").json_str();
        }

        let r = match command_name.as_str() {
            "new_entry_form_data" => {
                db_service_call!(args,NewEntryArg{db_key,entry_type_uuid,parent_group_uuid} =>
                    new_entry_form_data_by_id(&db_key,&entry_type_uuid,parent_group_uuid.as_ref().as_deref()))
            }

            "unlock_kdbx" => {
                service_call!(args, OpenDbArg{db_file_name,password,key_file_name,biometric_auth_used: _} =>
                    Self unlock_kdbx(&db_file_name,password.as_deref(),key_file_name.as_deref()))
            }

            "unlock_kdbx_on_biometric_authentication" => {
                service_call!(args, DbKey{db_key} => Self unlock_kdbx_on_biometric_authentication(&db_key))
            }

            "close_kdbx" => db_service_call!(args, DbKey{db_key} => close_kdbx(&db_key)),

            "combined_category_details" => {
                db_service_call! (args, CategoryDetailArg{db_key,grouping_kind} => combined_category_details(&db_key,&grouping_kind))
            }

            "categories_to_show" => {
                db_service_call! (args, DbKey{db_key} => categories_to_show(&db_key))
            }

            "entry_type_headers" => {
                db_service_call! (args, DbKey{db_key} => entry_type_headers(&db_key))
            }

            "collect_entry_group_tags" => {
                db_service_call! (args, DbKey{db_key} => collect_entry_group_tags(&db_key))
            }

            "get_db_settings" => db_service_call! (args, DbKey{db_key} => get_db_settings(&db_key)),

            "set_db_settings" => {
                db_service_call! (args,DbSettingsArg {db_key,db_settings} => set_db_settings(&db_key, db_settings))
            }

            "groups_summary_data" => {
                db_service_call! (args, DbKey{db_key} => groups_summary_data(&db_key))
            }

            "get_group_by_id" => {
                db_service_call! (args, DbKeyWithUUIDArg{db_key,uuid} => get_group_by_id(&db_key,&uuid))
            }

            "insert_group" => {
                db_service_call! (args, GroupArg{db_key,group} => insert_group(&db_key,group))
            }

            "update_group" => {
                db_service_call! (args, GroupArg{db_key,group} => update_group(&db_key,group))
            }

            "insert_entry_from_form_data" => {
                db_service_call!(args, EntryArg{db_key,form_data} => insert_entry_from_form_data(&db_key,form_data))
            }

            "update_entry_from_form_data" => {
                db_service_call!(args, EntryArg{db_key,form_data} => update_entry_from_form_data(&db_key,form_data))
            }

            "entry_summary_data" => {
                db_service_call! (args, EntrySummaryArg{db_key,entry_category} => entry_summary_data(&db_key,entry_category))
            }

            "get_entry_form_data_by_id" => {
                db_service_call! (args, DbKeyWithUUIDArg{db_key,uuid} => get_entry_form_data_by_id(&db_key,&uuid))
            }

            "form_otp_url" => {
                db_service_call! (args, OtpSettingsArg{otp_settings} => form_otp_url(&otp_settings))
            }

            "move_entry_to_recycle_bin" => {
                db_service_call! (args, DbKeyWithUUIDArg{db_key,uuid} => move_entry_to_recycle_bin(&db_key,uuid))
            }

            "move_group_to_recycle_bin" => {
                db_service_call! (args, DbKeyWithUUIDArg{db_key,uuid} => move_group_to_recycle_bin(&db_key,uuid))
            }

            "remove_entry_permanently" => {
                db_service_call! (args, DbKeyWithUUIDArg{db_key,uuid} => remove_entry_permanently(&db_key,uuid))
            }

            "remove_group_permanently" => {
                db_service_call! (args, DbKeyWithUUIDArg{db_key,uuid} => remove_group_permanently(&db_key,uuid))
            }

            "empty_trash" => db_service_call! (args, DbKey{db_key} => empty_trash(&db_key)),

            "move_entry" => {
                db_service_call! (args, MoveArg{db_key,uuid, new_parent_id} => move_entry(&db_key,uuid, new_parent_id))
            }

            "move_group" => {
                db_service_call! (args, MoveArg{db_key,uuid, new_parent_id} => move_group(&db_key,uuid, new_parent_id))
            }

            "history_entries_summary" => {
                db_service_call! (args, DbKeyWithUUIDArg{db_key,uuid} => history_entries_summary(&db_key,&uuid))
            }

            "delete_history_entries" => {
                db_service_call! (args, DbKeyWithUUIDArg{db_key,uuid} => delete_history_entries(&db_key,&uuid))
            }

            "delete_history_entry_by_index" => {
                db_service_call! (args, EntryHistoryByIndexArg{db_key,uuid,index} => delete_history_entry_by_index(&db_key,&uuid,index))
            }

            "history_entry_by_index" => {
                db_service_call! (args, EntryHistoryByIndexArg{db_key,uuid,index} => history_entry_by_index(&db_key,&uuid,index))
            }

            "search_term" => {
                db_service_call! (args, SearchArg{db_key,term} => search_term(&db_key,&term))
            }

            "analyzed_password" => {
                service_call_closure!(args,PasswordGeneratorArg {password_options}  => move || {
                    result_json_str(password_options.analyzed_password())
                })
            }

            "generate_password_phrase" => {
                service_call!(args, PassPhraseGeneratorArg {pass_phrase_options} => Self generate_pass_phrase(pass_phrase_options))
            }

            "save_attachment_as_temp_file" => {
                service_call! (args, AttachmentArg{db_key,name,data_hash_str} =>
                    Self save_attachment_as_temp_file(&db_key,&name,&data_hash_str))
            }

            "generate_key_file" => {
                service_call!(args, GenericArg { key_vals } => Self generate_key_file(key_vals))
            }

            "delete_key_file" => {
                service_call!(args, GenericArg { key_vals } => Self delete_key_file(key_vals))
            }

            "update_session_timeout" => {
                service_call!(args, SessionTimeoutArg {timeout_type: _,db_session_timeout,clipboard_timeout} =>
                    Self update_session_timeout(db_session_timeout,clipboard_timeout))
            }

            "update_preference" => {
                service_call!(args, PrefefenceUpdateArg {preference_data} => Self update_preference(preference_data))
            }

            "load_language_translations" => {
                service_call!(args, TranslationsArg {language_ids} => Self load_language_translations(language_ids))
            }

            //// Async related
            "start_polling_entry_otp_fields" => {
                service_ok_call! (args, StartEntryOtpArg {db_key,entry_uuid,otp_fields} =>
                    async_service start_polling_entry_otp_fields(&db_key,&entry_uuid,otp_fields))
            }

            "stop_polling_all_entries_otp_fields" => {
                wrap_no_arg_ok_call! (async_service stop_polling_all_entries_otp_fields)
            }

            "set_timeout" => {
                service_ok_call! (args, StartTimerArg {period_in_milli_seconds,timer_id} => async_service set_timeout(period_in_milli_seconds,timer_id))
            }

            "shutdown_async_services" => {
                wrap_no_arg_ok_call! (async_service shutdown_async_services)
            }
            //////
            "remove_from_recently_used" => Self::remove_from_recently_used(&args),

            "new_blank_group" => Self::new_blank_group(args),

            "get_file_info" => Self::get_file_info(&args),

            "prepare_export_kdbx_data" => Self::prepare_export_kdbx_data(&args),

            "resolve_auto_open_properties" => {
                service_call_closure!(args,AutoOpenPropsResolveArg {auto_open_properties}  => move || {
                    result_json_str(auto_open_properties.resolve())
                })
            }

            ///////
            "app_preference" => Self::app_preference(),

            "recently_used_dbs_info" => Self::recently_used_dbs_info(),

            "all_kdbx_cache_keys" => result_json_str(db_service::all_kdbx_cache_keys()),

            "list_key_files" => ok_json_str(util::list_key_files()),

            "clean_export_data_dir" => result_json_str(util::clean_export_data_dir()),

            "clipboard_copy_string" => Self::clipboard_copy_string(&args),

            "save_conflict_resolution_cancel" => {
                service_call!(args, DbKey {db_key} => Self save_conflict_resolution_cancel(&db_key))
            }

            // "set_db_open_biometric" => {
            //     service_call_closure!(args,DbOpenBiomerticArg {db_key,db_open_enabled}  => move || {
            //         result_json_str(biometric_auth::set_db_open_biometric(&db_key,db_open_enabled))
            //     })
            // }
            "stored_db_credentials" => {
                service_call_closure!(args,DbKey {db_key}  => move || {
                    ok_json_str(biometric_auth::StoredCredential::get_credentials(&db_key))
                })
            }

            "pin_entered" => {
                service_call_closure!(args,AppLockCredentialArg {pin}  => move || {
                    result_json_str(app_lock::pin_entered(pin))
                })
            }

            "pin_verify" => {
                service_call_closure!(args,AppLockCredentialArg {pin}  => move || {
                    result_json_str(app_lock::pin_verify(pin))
                })
            }

            "pin_removed" => result_json_str(app_lock::pin_removed()),

            "app_reset" => result_json_str(app_lock::app_reset()),
            
            //// All remote storage related
            "rs_connect_and_retrieve_root_dir" => {
                service_call_closure!(args,RemoteServerOperationArg {rs_operation_type} => move || {
                    result_json_str(rs_operation_type.connect_and_retrieve_root_dir())
                })
            }

            "rs_connect_by_id_and_retrieve_root_dir" => {
                service_call_closure!(args,RemoteServerOperationArg {rs_operation_type} => move || {
                    result_json_str(rs_operation_type.connect_by_id_and_retrieve_root_dir())
                })
            }

            "rs_connect_by_id" => {
                service_call_closure!(args,RemoteServerOperationArg {rs_operation_type} => move || {
                    result_json_str(rs_operation_type.connect_by_id())
                })
            }

            "rs_remote_storage_configs" => {
                service_call_closure!(args,RemoteServerOperationArg {rs_operation_type} => move || {
                    result_json_str(rs_operation_type.remote_storage_configs())
                })
            }

            "rs_list_sub_dir" => {
                service_call_closure!(args,RemoteServerOperationArg {rs_operation_type} => move || {
                    result_json_str(rs_operation_type.list_sub_dir())
                })
            }

            "rs_list_dir" => {
                service_call_closure!(args,RemoteServerOperationArg {rs_operation_type} => move || {
                    result_json_str(rs_operation_type.list_dir())
                })
            }

            "rs_read_kdbx" => crate::remote_storage::rs_read_kdbx(&args),

            "rs_save_kdbx" => crate::remote_storage::rs_save_kdbx(&args),

            "rs_create_kdbx" => crate::remote_storage::rs_create_kdbx(&args),

            "rs_read_configs" => result_json_str(remote_storage::read_configs()),

            "rs_delete_config" => {
                service_call_closure!(args,RemoteServerOperationArg {rs_operation_type} => move || {
                    result_json_str(rs_operation_type.delete_config())
                })
            }
            ////
            
            "read_latest_backup" => result_json_str(crate::db_backup_read::read_latest_backup(&args)),

            // "list_backup_files" => ok_json_str(util::list_backup_files()),
            // "delete_key_file" => Self::delete_key_file(&args),
            "test_call" => Self::test_call(&args),

            x => error_json_str(&format!("Invalid command name {} is passed", x)),
        };
        r
    }

    fn new_blank_group(args: String) -> ResponseJson {
        match serde_json::from_str(&args) {
            Ok(CommandArg::NewBlankGroupArg { mark_as_category }) => {
                let json_str = match serde_json::to_string_pretty(&InvokeResult::with_ok(
                    db_service::new_blank_group(mark_as_category),
                )) {
                    Ok(s) => s,
                    Err(e) => error_json_str(&format!("{:?}", e)),
                };
                json_str
            }
            Ok(_) => error_json_str("Unexpected args passed"),

            Err(e) => error_json_str(&format!("{:?}", e)),
        }
    }

    fn get_file_info(args: &str) -> ResponseJson {
        if let Ok(CommandArg::DbKey { db_key }) = serde_json::from_str(args) {
            let info = AppState::uri_to_file_info(&db_key);
            // log::debug!("FileInfo is {:?}", info);
            ok_json_str(info)
        } else {
            error_json_str(&format!(
                "Unexpected args passed in getting file info {}",
                args
            ))
        }
    }

    fn unlock_kdbx(
        db_key: &str,
        password: Option<&str>,
        key_file_name: Option<&str>,
    ) -> OkpResult<KdbxLoaded> {
        let mut kdbx_loaded = db_service::unlock_kdbx(db_key, password, key_file_name)?;
        // For now, we are replacing any file_name formed in db_service::unlock_kdbx as that is desktop file system specific
        // In case of mobile, the file uri is just some handle and need to get the file name using mobile api
        kdbx_loaded.file_name = AppState::common_device_service().uri_to_file_name(db_key.into());

        Ok(kdbx_loaded)
    }

    fn unlock_kdbx_on_biometric_authentication(db_key: &str) -> OkpResult<KdbxLoaded> {
        let mut kdbx_loaded = db_service::unlock_kdbx_on_biometric_authentication(db_key)?;
        kdbx_loaded.file_name = AppState::common_device_service().uri_to_file_name(db_key.into());
        Ok(kdbx_loaded)
    }

    fn generate_key_file(key_vals: HashMap<String, String>) -> OkpResult<KeyFileInfo> {
        let Some(key_file_name_component) = key_vals.get("file_name") else {
            return Err(OkpError::DataError(
                "Key file name to generate is not found in args",
            ));
        };

        let path = AppState::key_files_dir_path().join(key_file_name_component.trim());

        debug!(
            "Key file Path is {:?} and exists check {}",
            path,
            path.exists()
        );

        if path.exists() {
            return Err(OkpError::DuplicateKeyFileName(format!(
                "Key file with the same name exists"
            )));
        }

        let Some(full_file_name_str) = path.as_os_str().to_str() else {
            return Err(OkpError::DataError(
                "Full Key file name could not be formed",
            ));
        };
        db_service::generate_key_file(full_file_name_str)?;

        debug!("Generated key file is {}", full_file_name_str);

        Ok(KeyFileInfo {
            full_file_name: full_file_name_str.into(),
            file_name: key_file_name_component.clone(),
            file_size: None,
        })
    }

    fn delete_key_file(key_vals: HashMap<String, String>) -> OkpResult<Vec<KeyFileInfo>> {
        let Some(key_file_name) = key_vals.get("file_name") else {
            return Err(OkpError::DataError("Key file name to delete is not found"));
        };
        util::delete_key_file(key_file_name);
        Ok(util::list_key_files())
    }

    fn save_attachment_as_temp_file(
        db_key: &str,
        name: &str,
        data_hash_str: &str,
    ) -> OkpResult<String> {
        let data_hash = db_service::service_util::parse_attachment_hash(data_hash_str)?;

        if cfg!(target_os = "android") {
            android::save_attachment_as_temp_file(db_key, name, &data_hash)
        } else {
            ios::save_attachment_as_temp_file(db_key, name, &data_hash)
        }
    }

    // TODO: Need to change UI side to use 'update_preference' and then deprecate this method
    fn update_session_timeout(
        db_session_timeout: Option<i64>,
        clipboard_timeout: Option<i64>,
    ) -> OkpResult<()> {
        AppState::update_session_timeout(db_session_timeout, clipboard_timeout)
    }

    fn update_preference(preference_data: PreferenceData) -> OkpResult<()> {
        AppState::update_preference(preference_data)
    }

    fn prepare_export_kdbx_data(args: &str) -> String {
        if let Ok(CommandArg::DbKey { db_key }) = serde_json::from_str(args) {
            // For now we remove all previous files of export_data dir
            // TDOO: We need to add 'delete call' of specific exported files in cljs when 'bg/export-kdbx' returns
            let _ = util::clean_export_data_dir();

            // Check whether the db is opened now
            let found = db_service::all_kdbx_cache_keys().map_or(false, |v| v.contains(&db_key));
            let recent_opt = AppState::get_recently_used(&db_key);
            // Form the export data file first by finding the kdbx file name from recent list
            let export_file_path_opt = &recent_opt
                .as_ref()
                .and_then(|r| util::form_export_file_name(&r.file_name));

            debug!("export_file_path is {:?}", &export_file_path_opt);
            let prefixed_path = if cfg!(target_os = "ios") {
                ios::to_ios_file_uri_str(export_file_path_opt)
            } else {
                export_file_path_opt.clone()
            };
            debug!("prefixed_path export_file_path is {:?}", &prefixed_path);

            let export_data_info = ExportDataInfo {
                full_file_name_uri: Some(db_key.clone()),
                file_name: recent_opt.as_ref().and_then(|r| Some(r.file_name.clone())),
                exported_data_full_file_name: prefixed_path,
            };

            if found && export_file_path_opt.is_some() {
                // The db is currently opened and the export file path is formed
                let mut writter =
                    match full_path_file_to_create(&export_file_path_opt.as_ref().unwrap()) {
                        Ok(f) => f,
                        Err(e) => return error_json_str(&format!("{:?}", e)),
                    };

                let r = match db_service::save_kdbx_to_writer(&mut writter, &db_key) {
                    Ok(_k) => ok_json_str(export_data_info),
                    Err(e) => return error_json_str(&format!("{:?}", e)),
                };
                debug!("export_file is created from cache");
                return r;
            } else {
                // The db is not yet opened and we will form the export data from the backup
                let backup_file_name_opt = &recent_opt.and_then(|r| {
                    backup::latest_or_generate_backup_history_file_name(
                        &r.db_file_path,
                        &r.file_name,
                    )
                });

                debug!(
                    "creating export_file from backup_file_name {:?}",
                    &backup_file_name_opt
                );

                if let (Some(backup_file_name), Some(export_file_path)) =
                    (backup_file_name_opt, export_file_path_opt)
                {
                    debug!(
                        "(backup_file_name, export_file_path) are ({:?},{:?}) ",
                        &backup_file_name, &export_file_path
                    );
                    if Path::new(&backup_file_name).exists() {
                        match std::fs::copy(&backup_file_name, &export_file_path) {
                            Ok(_n) => return ok_json_str(export_data_info),
                            Err(e) => return error_json_str(&format!("{:?}", e)),
                        };
                    } else {
                        // There is a possibility that the backup file may not be there
                        // if we have not called save_kdbx for this db any time before this
                        return error_json_str(&format!(
                            "BackupNotFound: Backup file {} is not found ",
                            &backup_file_name
                        ));
                    }
                } else {
                    return error_json_str(&format!(
                        "Export file creation failed with the backup file availablity status: {}",
                        backup_file_name_opt.is_some()
                    ));
                }
            }
        } else {
            error_json_str(&format!(
                "Unexpected args passed for prepared_export_kdbx_data {:?}",
                args
            ))
        }
    }

    fn remove_from_recently_used(args: &str) -> ResponseJson {
        let (db_key,) = parse_command_args_or_json_error!(args, DbKey { db_key });

        // Remove all files that were created for this db
        remove_app_files(&db_key);

        InvokeResult::from(db_service::close_kdbx(&db_key)).json_str()
    }

    // Gets the recent files list
    fn recently_used_dbs_info() -> ResponseJson {
        ok_json_str(AppState::recent_dbs_info())
    }

    // Called to remove any backup files created during save kdbx call that resulted in some
    // saving conflict or some save error. This is called when user opts to cancel the resolutions provided
    fn save_conflict_resolution_cancel(db_key: &str) -> OkpResult<()> {
        if let Some(ref full_backup_file_name) = AppState::remove_last_backup_name_on_error(db_key)
        {
            backup::remove_backup_history_file(db_key, full_backup_file_name);
        }
        Ok(())
    }

    fn app_preference() -> ResponseJson {
        ok_json_str(AppState::preference_clone())
    }

    fn load_language_translations(language_ids: Vec<String>) -> OkpResult<TranslationResource> {
        let current_locale_language = util::current_locale_language();
        debug!("current_locale is {}", &current_locale_language);

        let prefered_language = AppState::language(); //current_locale_language.clone();
        debug!("prefered_language is {}", &prefered_language);

        let language_ids_to_load = if !language_ids.is_empty() {
            language_ids
        } else {
            if prefered_language != "en" {
                vec![String::from("en"), prefered_language.clone()]
            } else {
                // locale is 'en'
                vec![String::from("en")]
            }
        };

        let mut translations: HashMap<String, String> = HashMap::default();

        // if let Some(language_id) = language_ids_to_load.first() {
        //     if let Some(data) = AppState::global()
        //         .common_device_service
        //         .load_language_translation(language_id.clone())
        //     {
        //         debug!("Got translations json from device service");
        //         translations.insert(language_id.clone(), data);
        //     }
        // }

        for lng in language_ids_to_load {
            if let Some(data) =
                AppState::common_device_service().load_language_translation(lng.clone())
            {
                debug!(
                    "Got translations json from resources for language id {}",
                    &lng
                );
                translations.insert(lng.clone(), data);
            } else {
                debug!(
                    "No translations json found in resources for language id {}",
                    &lng
                );
                translations.insert(lng.clone(), "{}".into());
            }
        }

        Ok(TranslationResource {
            current_locale_language,
            prefered_language,
            translations,
        })
    }

    fn clipboard_copy_string(json_args: &str) -> ResponseJson {
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
            let cd = crate::udl_uniffi_exports::AppClipboardCopyData {
                field_name,
                field_value,
                protected,
                cleanup_after,
            };
            AppState::common_device_service_ex().clipboard_copy_string(cd)?;

            // debug!("clipboard_copy_string is called ");

            Ok(())
        };
        result_json_str(inner_fn())
    }

    fn generate_pass_phrase(
        pass_phrase_options: PassphraseGenerationOptions,
    ) -> OkpResult<db_service::GeneratedPassPhrase> {
        struct Loader {}
        impl db_service::WordListLoader for Loader {
            fn load_from_resource(&self, word_list_file_name: &str) -> db_service::Result<String> {
                // We pass the file name part of the passed 'word_list_file_name' removing .txt
                // This is requied for iOS
                let file_name_part = word_list_file_name
                    .split_once(".")
                    .map_or("InvalidWordlistFile".to_string(), |c| c.0.to_string());

                Ok(AppState::common_device_service().load_resource_wordlist(file_name_part)?)
            }
        }
        let loader = Loader {};
        pass_phrase_options.generate(&loader)
    }
}

// Just for some testing. Comment out when testing is done
impl Commands {
    fn test_call(json_args: &str) -> ResponseJson {
        //let r = ApiCallbacksStore::common_device_service_ex().test_secure_store();
        //debug!("Fn test_secure_store called and r is {:?}",&r);

        let inner_fn = || -> OkpResult<()> {
            let (key_vals,) = parse_command_args_or_err!(json_args, GenericArg { key_vals });
            let (Some(id), Some(test_data)) =
                (key_vals.get("identifier"), key_vals.get("test_data"))
            else {
                return Err(OkpError::DataError("Valid key is not found in args"));
            };

            let encrypted_data = AppState::secure_enclave_cb_service()
                .encrypt_bytes(id.clone(), test_data.as_bytes().to_vec())?;
            let dd =
                AppState::secure_enclave_cb_service().decrypt_bytes(id.clone(), encrypted_data)?;

            debug!("Decrypted text is {}", String::from_utf8_lossy(&dd));

            Ok(())
        };

        result_json_str(inner_fn())
        //ok_json_str("Done".to_string())
    }
}

// Called when user initiates a "Remove" call from UI
// Also this is used when user calls "Save As" while resolving save time error

pub fn remove_app_files(db_key: &str) {
    // Using uri_to_file_name may fail if the uri is stale or no more available
    // as this is a callback to native side and any exception there results in rust panic in ffi
    // let file_name = AppState::global().uri_to_file_name(&db_key);
    // util::delete_backup_file(&db_key, &file_name);

    if let Some(_ru) = AppState::get_recently_used(&db_key) {
        backup::delete_backup_history_dir(&db_key);
        // debug!("Backup file {} is deleted", &ru.file_name)
    }

    // Need to remove any stored credentials
    // TODO: Should we make this call only when this db_key is found with flag 'db_open_biometric_enabled' true
    let _ = biometric_auth::StoredCredential::remove_credentials(db_key);

    // For now we remove all files in the export_data dir when
    // this db link remove is called
    // TDOO: We need to delete the temp exported file for this db if any
    let _ = util::clean_export_data_dir();

    // Removes this db related info from recent db info list and also removes this db preference
    AppState::remove_recent_db_use_info(&db_key, true);

    #[cfg(target_os = "ios")]
    ios::delete_book_mark_data(&db_key);

    #[cfg(target_os = "ios")]
    {
        let r = crate::ios::autofill_app_group::delete_copied_autofill_details(&db_key);
        log::debug!("Delete of copied db data done with result {:?}", r);
    }
}

pub fn ok_json_str<T: serde::Serialize>(val: T) -> ResponseJson {
    InvokeResult::with_ok(val).json_str()
}

pub fn error_json_str(val: &str) -> ResponseJson {
    InvokeResult::<()>::with_error(val).json_str()
}

// Converts the passed Result to ok or error response and makes a json string
// from that
pub fn result_json_str<T: serde::Serialize>(val: db_service::Result<T>) -> ResponseJson {
    match val {
        Ok(t) => InvokeResult::with_ok(t).json_str(),
        // Need to use "{}" not "{:?}" for the thiserror display call to work
        // so that the string in #error[...] is returned in response
        Err(e) => InvokeResult::<()>::with_error(&format!("{}", e)).json_str(),
    }
}

// Convertable to a json string as
// "{ok: 'a string value serialized from T', error: null }" or  "{ok: null, error: 'error string'}"
#[derive(Serialize, Deserialize)]
pub struct InvokeResult<T> {
    ok: Option<T>,
    error: Option<String>,
}

impl<T: Serialize> InvokeResult<T> {
    // Creates a ok part which can be converted to a ok response json string
    pub fn with_ok(val: T) -> Self {
        InvokeResult {
            ok: Some(val),
            error: None,
        }
    }

    // Creates a error part which can be converted to an error response json string
    pub fn with_error(val: &str) -> Self {
        InvokeResult {
            ok: None,
            error: Some(val.into()),
        }
    }

    // Converts OK to json string with "ok" key
    fn ok_json_str(val: T) -> String {
        Self::with_ok(val).json_str()
    }

    // Converts Err to json string with "error" key
    pub fn json_str(&self) -> String {
        let json_str = match serde_json::to_string_pretty(self) {
            Ok(s) => s,
            Err(e) => {
                log::error!("InvokeResult conversion failed with error {}", &e);
                r#"{"error" : "InvokeResult conversion failed"}"#.into() // format!("Error {:?}", e)
            }
        };
        json_str
    }
}

impl<T: Serialize> From<db_service::Result<T>> for InvokeResult<T> {
    fn from(result: db_service::Result<T>) -> Self {
        match result {
            Ok(r) => InvokeResult::with_ok(r),
            Err(e) => InvokeResult::with_error(format!("{}", e).as_str()),
        }
    }
}

pub fn full_path_file_to_create(full_file_name: &str) -> db_service::Result<File> {
    let full_file_path = util::url_to_unix_file_name(&full_file_name);
    log::debug!(
        "Creating file object for full file path  {:?} with read,write and create permissions",
        full_file_path
    );

    // let p = Path::new(&full_file_path);
    // log::debug!(".. full_file_path {:?} exists {} ",&p,p.exists());
    // log::debug!("Deleting temp file {:?}", std::fs::remove_file(&p));

    // Sometimes in iOS Simulator, the 'OpenOptions' call failed (called from create_temp_kdbx)
    // for some file names with the following error

    // Io(Os { code: 17, kind: AlreadyExists, message: "File exists" })

    // e.g While checking simulator 'tmp' dir, found a file Test45.kdbx and but when full_file_name file ends in 'test45.kdbx'
    // the error happend and not when ends in 'Test45.kdbx'

    // Not sure this will happen often; Need to watch out this hapepening on devices

    // IMPORTANT: We need to create a file using OpenOptions so that the file is opened for read and write
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .open(full_file_path)?;
    Ok(file)
}

fn _full_path_file_to_read_write(full_file_name: &str) -> db_service::Result<File> {
    let full_file_path = util::url_to_unix_file_name(&full_file_name);
    log::debug!(
        "Creating file object for full file path  {:?} with read and write permissions",
        full_file_path
    );
    // IMPORTANT: We need to create a file using OpenOptions so that the file is opened for read and write
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .open(full_file_path)?;
    Ok(file)
}

#[cfg(test)]
mod tests {
    use crate::commands::CommandArg;

    #[test]
    fn verify_parsing_db_key_arg() {
        let in_json_str = r#"{"db_key":"file:///Users/jeyasankar/Library/Developer/CoreSimulator/Devices/CC0D1909-56BA-4AD0-9499-FB96E10925CD/data/Containers/Shared/AppGroup/2AE43D43-8E49-4A03-AA7E-994442DF99E9/File%20Provider%20Storage/Test3.kdbx"}"#;

        let r = serde_json::from_str::<CommandArg>(in_json_str);
        if let Ok(CommandArg::DbKey { db_key }) = r {
            println!("Parsing success with db_key {}", &db_key);
            assert!(true);
        } else {
            assert!(false, "Invalid parsing of json str as  {:?} ", &r);
        }
    }

    #[test]
    fn verify_parsing_session_timeout_arg() {
        let in_json_str = r#"{ "timeout_type":1, "db_session_timeout":-1}"#;
        let r = serde_json::from_str::<CommandArg>(in_json_str);

        //println!("r is {:?}",&r);

        if let Ok(CommandArg::SessionTimeoutArg {
            timeout_type: _,
            db_session_timeout,
            clipboard_timeout,
        }) = r
        {
            assert_eq!(Some(-1), db_session_timeout);
            assert_eq!(None, clipboard_timeout);
        } else {
            assert!(false, "Invalid parsing of json str as  {:?} ", &r);
        }
    }

    #[test]
    fn verify_parsing_generic_arg() {
        let in_json_str = r#"{"key_vals": {"some_key":"some_value"}}"#;
        let r = serde_json::from_str::<CommandArg>(in_json_str);
        //println!("r is {:?}",&r);
        if let Ok(CommandArg::GenericArg { key_vals: m }) = r {
            assert_eq!(Some(&"some_value".to_string()), m.get("some_key"));
        } else {
            assert!(false, "Invalid parsing of json str as  {:?} ", &r);
        }
    }
}

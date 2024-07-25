use log::debug;
use onekeepass_core::db_service;
use url::Url;

use crate::{
    commands::{error_json_str, result_json_str, CommandArg, ResponseJson},
    parse_command_args_or_err, OkpError, OkpResult,
};

use super::{AndroidApiCallbackImpl, AutoFillDbData, ClipDataArg};

// NOTE: We have another service 'AndroidSupportService' in udl file
// Later need to move those services here

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

            x => error_json_str(&format!(
                "Invalid command or args: Command call {} with args {} failed",
                x, &json_args
            )),
        };

        r
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
            let clip_data = ClipDataArg {
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

    // The arg json_args should be parseable as AutoFillDbData intead of usual CommandArgs
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

use log::debug;
use once_cell::sync::OnceCell;
use serde::Deserialize;
use std::{collections::HashMap, sync::Arc};

use crate::{udl_types::ApiCallbackResult, udl_uniffi_exports::AppClipboardCopyData, OkpResult};

// A signleton that holds Android specific api callbacks services implemented in Kotlin
pub struct AndroidApiCallbackImpl {
    android_api_service: Arc<dyn AndroidApiService>,
}

impl AndroidApiCallbackImpl {
    pub fn global() -> &'static AndroidApiCallbackImpl {
        // Panics if no global state object was set. ??
        ANDROID_API_SERVICE_STATE.get().unwrap()
    }

    pub fn api_service() -> &'static dyn AndroidApiService {
        Self::global().android_api_service.as_ref()
    }

    // pub fn paste_string(text: String, timeout: u32) -> OkpResult<()> {
    //     Ok(Self::global().ios_api_service.paste_string(text, timeout)?)
    // }
}

///////////

static ANDROID_API_SERVICE_STATE: OnceCell<AndroidApiCallbackImpl> = OnceCell::new();

//IMPORTANT:
// This fn should be called once in Kotlin during intialization of Native modules
// Then only we can use the api callback functions
// Otherwise we get panic 'Caught a panic calling rust code: "called `Option::unwrap()` on a `None` value"'

// top level functions generated to be called from Kotlin something similar to 'db_service_initialize'

#[uniffi::export]
pub fn android_callback_service_initialize(android_api_service: Arc<dyn AndroidApiService>) {
    let service = AndroidApiCallbackImpl {
        android_api_service,
    };

    if ANDROID_API_SERVICE_STATE.get().is_none() {
        if ANDROID_API_SERVICE_STATE.set(service).is_err() {
            log::error!(
                "Global ANDROID_API_SERVICE_STATE object is initialized already. This probably happened concurrently."
            );
        }
    }

    debug!("android_callback_service_initialize is finished");
}

#[derive(uniffi::Enum, Deserialize)]
#[serde(tag = "type")]
pub enum AutoFillDbData {
    Login {
        username: Option<String>,
        password: Option<String>,
        // The current TOTP token, when the autofill request had a 2FA code field. Set
        // independently of username/password: a code-only fill (the second page of a
        // two step login) sends this alone
        otp: Option<String>,
    },
    CreditCard {},
}

// Data passed to Kotlin to complete a passkey assertion via Android Credential Manager.
#[derive(uniffi::Record)]
pub struct AndroidPasskeyAssertionCallbackData {
    pub authentication_response_json: String,
}

// Data passed to Kotlin to complete a passkey registration via Android Credential Manager.
// org_db_key is the KDBX content URI needed to save the database to disk.
#[derive(uniffi::Record)]
pub struct AndroidPasskeyRegistrationCallbackData {
    pub registration_response_json: String,
    pub org_db_key: String,
}

// A passkey found for a relying party in an opened db. Returned as it is to Kotlin
// See AndroidSupportServiceExtra::find_matching_passkeys
#[derive(uniffi::Record)]
pub struct AndroidPasskeySummaryData {
    pub entry_uuid: String,
    pub db_key: String,
    pub credential_id_b64url: String,
    pub rp_id: String,
    pub username: String,
    pub user_handle_b64url: String,
}

impl From<onekeepass_core::db_service::passkey::PasskeySummary> for AndroidPasskeySummaryData {
    fn from(s: onekeepass_core::db_service::passkey::PasskeySummary) -> Self {
        Self {
            entry_uuid: s.entry_uuid,
            db_key: s.db_key,
            credential_id_b64url: s.credential_id_b64url,
            rp_id: s.rp_id,
            username: s.username,
            user_handle_b64url: s.user_handle_b64url,
        }
    }
}

// Tells Kotlin that a db is closed so that anything it keeps for that db can be dropped
pub(crate) fn notify_db_closed(db_key: &str) {
    if let Err(e) = AndroidApiCallbackImpl::api_service().db_closed(db_key.to_string()) {
        log::error!("db_closed callback failed with error {}", e);
    }
}

// Corresponding UDL:
// [Trait, WithForeign]
// interface AndroidApiService {};
#[uniffi::export(with_foreign)]
pub trait AndroidApiService: Send + Sync {
    fn clipboard_copy_string(&self, clip_data: AppClipboardCopyData) -> ApiCallbackResult<()>;
    // Autofill specific
    fn autofill_client_app_url_info(&self) -> ApiCallbackResult<HashMap<String, String>>;
    fn complete_autofill(&self, auto_fill_data: AutoFillDbData) -> ApiCallbackResult<()>;
    // Passkey assertion/registration completion (called by Rust after crypto operations)
    fn complete_passkey_assertion(
        &self,
        data: AndroidPasskeyAssertionCallbackData,
    ) -> ApiCallbackResult<()>;

    fn store_passkey_registration_response(
        &self,
        data: AndroidPasskeyRegistrationCallbackData,
    ) -> ApiCallbackResult<()>;

    // Called after a db is closed
    fn db_closed(&self, db_key: String) -> ApiCallbackResult<()>;
}

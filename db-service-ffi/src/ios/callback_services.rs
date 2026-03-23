use log::debug;
use once_cell::sync::OnceCell;
use std::{collections::HashMap, sync::Arc};

use crate::{udl_types::ApiCallbackResult, OkpResult};

#[derive(uniffi::Record)]
pub struct PasskeySummaryData {
    pub entry_uuid: String,
    pub db_key: String,
    pub credential_id_b64url: String,
    pub rp_id: String,
    pub username: String,
    pub user_handle_b64url: String,
}

impl From<onekeepass_core::db_service::passkey::PasskeySummary> for PasskeySummaryData {
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


/// Data passed to Swift to complete a passkey assertion via CredentialProviderViewController.
#[derive(uniffi::Record)]
pub struct PasskeyAssertionCallbackData {
    pub credential_id_b64url: String,
    pub rp_id: String,
    pub user_handle_b64url: String,
    pub signature_b64url: String,
    pub authenticator_data_b64url: String,
}

/// Data passed to Swift to complete a passkey registration via CredentialProviderViewController.
#[derive(uniffi::Record)]
pub struct PasskeyRegistrationCallbackData {
    pub credential_id_b64url: String,
    pub attestation_object_b64url: String,
    /// Passed as `clientDataHash` to iOS; base64url-encoded.
    pub client_data_hash_b64url: String,
}

// A signleton that holds iOS specific api callbacks services implemented in Swift
pub struct IosApiCallbackImpl {
    ios_api_service: Arc<dyn IosApiService>,
}

impl IosApiCallbackImpl {
    pub fn global() -> &'static IosApiCallbackImpl {
        // Panics if no global state object was set. ??
        IOS_API_SERVICE_STATE.get().unwrap()
    }

    pub fn api_service() -> &'static dyn IosApiService {
        Self::global().ios_api_service.as_ref()
    }

    // pub fn paste_string(text: String, timeout: u32) -> OkpResult<()> {
    //     Ok(Self::global().ios_api_service.paste_string(text, timeout)?)
    // }
}

///////////

static IOS_API_SERVICE_STATE: OnceCell<IosApiCallbackImpl> = OnceCell::new();

//IMPORTANT:
// This fn should be called once in Swift during intialization of Native modules
// Then only we can use the api callback functions
// Otherwise we get panic 'Caught a panic calling rust code: "called `Option::unwrap()` on a `None` value"'

// top level functions generated to be called from Swift something similar to 'db_service_initialize'
// The Swift generated func is 'iosCallbackServiceInitialize'

#[uniffi::export]
pub fn ios_callback_service_initialize(ios_api_service: Arc<dyn IosApiService>) {
    let service = IosApiCallbackImpl { ios_api_service };

    if IOS_API_SERVICE_STATE.get().is_none() {
        if IOS_API_SERVICE_STATE.set(service).is_err() {
            log::error!(
                "Global IOS_API_SERVICE_STATE object is initialized already. This probably happened concurrently."
            );
        }
    }

    debug!("ios_callback_service_initialize is finished");
}

// Corresponding UDL:
// [Trait, WithForeign]
// interface IosApiService {};
#[uniffi::export(with_foreign)]
pub trait IosApiService: Send + Sync {
    fn clipboard_copy_string(&self, text: String, timeout: u32) -> ApiCallbackResult<()>;

    fn register_passkey_identities(
        &self,
        db_key: String,
        // Old identities if any to remove from the ASCredentialIdentityStore
        old_passkeys: Vec<PasskeySummaryData>,
        // New identities to add to the ASCredentialIdentityStore
        new_passkeys: Vec<PasskeySummaryData>,
    ) -> ApiCallbackResult<()>;

    // Autofill specific
    fn asc_credential_service_identifiers(&self) -> ApiCallbackResult<HashMap<String, String>>;

    // Called by Rust after signing a passkey assertion; Swift creates ASPasskeyAssertionCredential
    // and calls ctx.completeAssertionRequest(using:).
    fn complete_passkey_assertion(&self, data: PasskeyAssertionCallbackData) -> ApiCallbackResult<()>;

    // Called by Rust after creating a passkey registration; Swift creates ASPasskeyRegistrationCredential
    // and calls ctx.completeRegistrationRequest(using:).
    fn complete_passkey_registration(&self, data: PasskeyRegistrationCallbackData) -> ApiCallbackResult<()>;
}

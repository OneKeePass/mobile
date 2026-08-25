// One time code (TOTP) identity registration with ASCredentialIdentityStore.
//
// iOS only offers a credential provider on a verification code field when that provider has
// registered one time code identities. With an empty store the OS never calls
// prepareOneTimeCodeCredentialList, so this registration is what makes the iOS 18 code path
// reachable at all.
//
// Modelled on passkey_service's identity registration: the identities registered for a db in
// the previous session are kept on disk so they can be handed to the OS as the set to remove
// before the current set is saved. This is a child module of autofill_app_group.

use std::{fs, path::PathBuf};

use log::debug;
use onekeepass_core::db_service;
use serde::{Deserialize, Serialize};

use crate::{OkpResult, ios::OtpIdentityData};

use super::super::IosApiCallbackImpl;

const REGISTERED_OTP_IDS_DIR: &str = "registered_otp_ids";

// The on-disk form. db_key is not stored - it is the file name hash and is supplied when
// loading
#[derive(Serialize, Deserialize)]
struct RegisteredOtpIdentity {
    entry_uuid: String,
    label: String,
    service_urls: Vec<String>,
}

fn registered_otp_ids_dir() -> OkpResult<PathBuf> {
    super::app_group_shared_root_sub_dir(REGISTERED_OTP_IDS_DIR)
}

fn registered_otp_ids_file(db_key: &str) -> OkpResult<PathBuf> {
    use onekeepass_core::db_service::service_util::string_to_simple_hash;
    let dir = registered_otp_ids_dir()?;
    let hash = string_to_simple_hash(db_key).to_string();
    Ok(dir.join(format!("{}.json", hash)))
}

fn save_registered_otp_identities(db_key: &str, identities: &[OtpIdentityData]) {
    let path = match registered_otp_ids_file(db_key) {
        Ok(p) => p,
        Err(e) => {
            log::error!("save_registered_otp_identities: dir error: {:?}", e);
            return;
        }
    };

    if identities.is_empty() {
        let _ = fs::remove_file(&path);
        return;
    }

    let records: Vec<RegisteredOtpIdentity> = identities
        .iter()
        .map(|i| RegisteredOtpIdentity {
            entry_uuid: i.entry_uuid.clone(),
            label: i.label.clone(),
            service_urls: i.service_urls.clone(),
        })
        .collect();

    match serde_json::to_string_pretty(&records) {
        Ok(json) => {
            let _ = fs::write(&path, json.as_bytes());
        }
        Err(e) => log::error!("save_registered_otp_identities: serialize error: {:?}", e),
    }
}

pub(super) fn load_registered_otp_identities(db_key: &str) -> Vec<OtpIdentityData> {
    let Ok(path) = registered_otp_ids_file(db_key) else {
        return vec![];
    };
    let Ok(json_str) = fs::read_to_string(&path) else {
        return vec![];
    };

    let records: Vec<RegisteredOtpIdentity> = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => {
            log::warn!("load_registered_otp_identities: parse error: {:?}", e);
            return vec![];
        }
    };

    records
        .into_iter()
        .map(|r| OtpIdentityData {
            entry_uuid: r.entry_uuid,
            label: r.label,
            service_urls: r.service_urls,
        })
        .collect()
}

// Collects the entries of `db_key` that can produce a TOTP and registers them as one time
// code identities, removing whatever was registered for this db before. Errors are logged
// and never propagated - identity registration must not block a save or a copy, exactly as
// for the passkey identities.
pub(super) fn register_otp_identities_for_db(db_key: &str) {
    let new_identities: Vec<OtpIdentityData> =
        match db_service::autofill::otp_entry_identities(db_key) {
            Ok(v) => v.into_iter().map(Into::into).collect(),
            Err(e) => {
                log::error!("register_otp_identities_for_db: fetch error: {:?}", e);
                return;
            }
        };

    debug!("register_otp_identities_for_db: Will register new otp identities {:?}",&new_identities);

    let old_identities = load_registered_otp_identities(db_key);

    debug!(
        "register_otp_identities_for_db: old count {}, new count {}",
        old_identities.len(),
        new_identities.len()
    );

    save_registered_otp_identities(db_key, &new_identities);

    if let Err(e) = IosApiCallbackImpl::api_service().register_one_time_code_identities(
        db_key.to_string(),
        old_identities,
        new_identities,
    ) {
        log::error!("register_otp_identities_for_db: callback error: {:?}", e);
    }
}

// Drops every persisted registered-identity file. Used on app reset, where the OS side of
// the store is cleared separately along with the rest of the extension contents
pub(super) fn remove_all_registered_otp_identity_files() {
    if let Ok(path) = registered_otp_ids_dir() {
        let _ = crate::util::remove_dir_contents(path);
    }
}

// Removes every one time code identity registered for this db. Used when the db is no longer
// available to autofill, so the OS stops offering codes we can no longer produce
pub(super) fn remove_otp_identities_for_db(db_key: &str) {
    let old_identities = load_registered_otp_identities(db_key);
    if old_identities.is_empty() {
        return;
    }

    debug!(
        "remove_otp_identities_for_db: removing {} identities",
        old_identities.len()
    );

    save_registered_otp_identities(db_key, &[]);

    if let Err(e) = IosApiCallbackImpl::api_service().register_one_time_code_identities(
        db_key.to_string(),
        old_identities,
        vec![],
    ) {
        log::error!("remove_otp_identities_for_db: callback error: {:?}", e);
    }
}

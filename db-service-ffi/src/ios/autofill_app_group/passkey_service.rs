// All passkey-related types, storage helpers, and IosAppGroupSupportService passkey command
// handlers. This is a child module of autofill_app_group; use super:: to access parent items.

use std::{collections::HashSet, fs, path::PathBuf};

use data_encoding::BASE64URL_NOPAD;
use log::debug;
use onekeepass_core::db_service::{
    self,
    passkey::{self, PasskeyStorageInfo},
    service_util,
};
use serde::{Deserialize, Serialize};

use crate::{
    OkpError, OkpResult,
    commands::{CommandArg, ResponseJson, result_json_str},
    ios::{PasskeyAssertionCallbackData, PasskeyRegistrationCallbackData, PasskeySummaryData},
    parse_command_args_or_err,
};

use super::super::IosApiCallbackImpl;

// ── Constants ──────────────────────────────────────────────────────────────────

pub(super) const PENDING_PASSKEYS_DIR: &str = "pending_passkeys";

// Pending records older than 30 days are removed during listing
const PENDING_PASSKEY_TTL_SECS: i64 = 30 * 24 * 60 * 60;

// Persisted record of KDBX passkeys last registered with ASCredentialIdentityStore.
// Used to reconstruct the identity list for removal in future sessions.
pub(super) const REGISTERED_PASSKEY_IDS_DIR: &str = "registered_passkey_ids";

// ── Structs ────────────────────────────────────────────────────────────────────

#[derive(Clone, Debug, Serialize, Deserialize)]
struct RegisteredPasskeySummary {
    entry_uuid: String,
    rp_id: String,
    username: String,
    credential_id_b64url: String,
    user_handle_b64url: String,
}

// A passkey registration record written by the autofill extension.
// The main app later picks it up, commits it to KDBX, and deletes the file.
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct PendingPasskeyRecord {
    pub record_uuid: String,
    pub created_at_unix: i64,
    // The db_key used in the main app (original file URI)
    pub org_db_key: String,
    pub passkey_info: PasskeyStorageInfo,
}

// ── File-system helpers ────────────────────────────────────────────────────────

// Returns AppGroup/okp/pending_passkeys/, creating it if needed
fn pending_passkeys_dir() -> OkpResult<PathBuf> {
    super::app_group_shared_root_sub_dir(PENDING_PASSKEYS_DIR)
}

// Scans the pending_passkeys directory and removes all records whose org_db_key matches db_key.
pub(super) fn remove_pending_passkeys_for_db(db_key: &str) {
    let dir = match pending_passkeys_dir() {
        Ok(d) => d,
        Err(_) => return,
    };
    if !dir.exists() {
        return;
    }
    let entries = match fs::read_dir(&dir) {
        Ok(e) => e,
        Err(_) => return,
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.extension().and_then(|e| e.to_str()) != Some("json") {
            continue;
        }
        if let Ok(json_str) = fs::read_to_string(&path) {
            if let Ok(record) = serde_json::from_str::<PendingPasskeyRecord>(&json_str) {
                if record.org_db_key == db_key {
                    let _ = fs::remove_file(&path);
                    debug!("Removed pending passkey file {:?} for db_key", &path);
                }
            }
        }
    }
}

// Reads all non-expired pending passkey records from disk.
// Does NOT modify or delete any files — callers decide what to do with the records.
pub(super) fn collect_pending_passkeys() -> Vec<PendingPasskeyRecord> {
    let dir = match pending_passkeys_dir() {
        Ok(d) => d,
        Err(_) => return vec![],
    };
    if !dir.exists() {
        return vec![];
    }
    let now = service_util::now_utc_seconds();
    let mut records = Vec::new();
    let entries = match fs::read_dir(&dir) {
        Ok(e) => e,
        Err(_) => return vec![],
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.extension().and_then(|e| e.to_str()) != Some("json") {
            continue;
        }
        let json_str = match fs::read_to_string(&path) {
            Ok(s) => s,
            Err(e) => {
                log::warn!("Could not read pending passkey file {:?}: {}", &path, e);
                continue;
            }
        };
        match serde_json::from_str::<PendingPasskeyRecord>(&json_str) {
            Ok(record) => {
                if (now - record.created_at_unix) <= PENDING_PASSKEY_TTL_SECS {
                    records.push(record);
                }
            }
            Err(e) => {
                log::warn!("Skipping malformed pending passkey file {:?}: {}", &path, e);
            }
        }
    }
    records
}

// Builds a PasskeySummaryData from a PendingPasskeyRecord for use before the passkey is committed
// to KDBX. Uses record_uuid as the entry_uuid placeholder.
pub(super) fn passkey_summary_from_pending(record: &PendingPasskeyRecord) -> PasskeySummaryData {
    PasskeySummaryData {
        entry_uuid: record.record_uuid.clone(),
        db_key: record.org_db_key.clone(),
        credential_id_b64url: record.passkey_info.credential_id_b64url.clone(),
        rp_id: record.passkey_info.rp_id.clone(),
        username: record.passkey_info.username.clone(),
        user_handle_b64url: record.passkey_info.user_handle_b64url.clone(),
    }
}

fn registered_passkeys_dir() -> OkpResult<PathBuf> {
    super::app_group_shared_root_sub_dir(REGISTERED_PASSKEY_IDS_DIR)
}

fn registered_passkeys_file(db_key: &str) -> OkpResult<PathBuf> {
    use onekeepass_core::db_service::service_util::string_to_simple_hash;
    let dir = registered_passkeys_dir()?;
    let hash = string_to_simple_hash(db_key).to_string();
    Ok(dir.join(format!("{}.json", hash)))
}

fn save_registered_passkeys(db_key: &str, passkeys: &[PasskeySummaryData]) {
    let path = match registered_passkeys_file(db_key) {
        Ok(p) => p,
        Err(e) => {
            log::error!("save_registered_passkeys: dir error: {:?}", e);
            return;
        }
    };
    if passkeys.is_empty() {
        let _ = fs::remove_file(&path);
        return;
    }
    let records: Vec<RegisteredPasskeySummary> = passkeys
        .iter()
        .map(|p| RegisteredPasskeySummary {
            entry_uuid: p.entry_uuid.clone(),
            rp_id: p.rp_id.clone(),
            username: p.username.clone(),
            credential_id_b64url: p.credential_id_b64url.clone(),
            user_handle_b64url: p.user_handle_b64url.clone(),
        })
        .collect();
    match serde_json::to_string_pretty(&records) {
        Ok(json) => {
            let _ = fs::write(&path, json.as_bytes());
        }
        Err(e) => {
            log::error!("save_registered_passkeys: serialize error: {:?}", e);
        }
    }
}

pub(super) fn load_registered_passkeys(db_key: &str) -> Vec<PasskeySummaryData> {
    let path = match registered_passkeys_file(db_key) {
        Ok(p) => p,
        Err(_) => return vec![],
    };
    let json_str = match fs::read_to_string(&path) {
        Ok(s) => s,
        Err(_) => return vec![],
    };
    let records: Vec<RegisteredPasskeySummary> = match serde_json::from_str(&json_str) {
        Ok(r) => r,
        Err(e) => {
            log::warn!("load_registered_passkeys: parse error: {:?}", e);
            return vec![];
        }
    };
    records
        .into_iter()
        .map(|r| PasskeySummaryData {
            entry_uuid: r.entry_uuid,
            db_key: db_key.to_string(),
            rp_id: r.rp_id,
            username: r.username,
            credential_id_b64url: r.credential_id_b64url,
            user_handle_b64url: r.user_handle_b64url,
        })
        .collect()
}

pub(super) fn delete_registered_passkeys_file(db_key: &str) {
    if let Ok(path) = registered_passkeys_file(db_key) {
        let _ = fs::remove_file(&path);
    }
}

// Fetches all passkeys for the given db_key and registers them with ASCredentialIdentityStore
// via the Swift callback. Errors are logged but never propagated — passkey identity registration
// must not block copy or commit operations.
pub(super) fn register_passkey_identities_for_db(db_key: &str) {
    // Fetch all passkeys currently committed to KDBX for this database.
    let kdbx_passkeys = match passkey::get_all_passkeys(&[db_key.to_string()]) {
        Ok(p) => p,
        Err(e) => {
            log::error!("register_passkey_identities_for_db: fetch error: {:?}", e);
            return;
        }
    };
    let new_kdbx_passkeys: Vec<PasskeySummaryData> =
        kdbx_passkeys.into_iter().map(Into::into).collect();

    debug!(
        "register_passkey_identities_for_db is called. Passkeys count {}",
        new_kdbx_passkeys.len()
    );

    // Build a set of credential IDs already present in KDBX to detect which
    // pending passkeys have already been committed.
    let kdbx_credential_ids: HashSet<&str> = new_kdbx_passkeys
        .iter()
        .map(|p| p.credential_id_b64url.as_str())
        .collect();

    // Collect all pending passkey records for this database.
    // Kept as PendingPasskeyRecord (not yet converted) so we can map to PasskeySummaryData
    // twice below without requiring Clone on PasskeySummaryData.
    let pending_records: Vec<PendingPasskeyRecord> = collect_pending_passkeys()
        .into_iter()
        .filter(|r| r.org_db_key == db_key)
        .collect();

    // old = previously registered KDBX passkeys + ALL pending passkeys for this db.
    // Every pending entry must appear in old_passkeys so ASCredentialIdentityStore
    // removes the stale pending identity before re-adding the correct one below.
    let mut old_passkeys = load_registered_passkeys(db_key);
    old_passkeys.extend(pending_records.iter().map(|r| passkey_summary_from_pending(r)));

    // Persist only the KDBX list for future sessions. Uncommitted pending passkeys
    // are intentionally excluded — they will be re-evaluated on the next call.
    save_registered_passkeys(db_key, &new_kdbx_passkeys);

    // new = KDBX passkeys + pending passkeys NOT yet in KDBX.
    // Uncommitted pending passkeys must survive in ASCredentialIdentityStore
    // so autofill can offer them before the main app commits them.
    // A pending passkey is considered committed once its credential_id_b64url
    // appears in the KDBX list (i.e. passkey_commit_pending has run for it).
    let uncommitted_pending: Vec<PasskeySummaryData> = pending_records
        .iter()
        .filter(|r| !kdbx_credential_ids.contains(r.passkey_info.credential_id_b64url.as_str()))
        .map(|r| passkey_summary_from_pending(r))
        .collect();

    let mut new_passkeys = new_kdbx_passkeys;
    new_passkeys.extend(uncommitted_pending);

    debug!(
        "register_passkey_identities_for_db: old count {}, new count {}",
        old_passkeys.len(),
        new_passkeys.len()
    );

    if let Err(e) = IosApiCallbackImpl::api_service()
        .register_passkey_identities(db_key.to_string(), old_passkeys, new_passkeys)
    {
        log::error!("register_passkey_identities_for_db: callback error: {:?}", e);
    }
}

// ── Shared passkey storage helper ─────────────────────────────────────────────

// Writes a PendingPasskeyRecord to disk and registers the passkey identity with
// ASCredentialIdentityStore via the iOS callback. Shared by passkey_store_pending and
// passkey_complete_registration to avoid duplicating this logic.
fn persist_pending_passkey(
    org_db_key: &str,
    passkey_info: passkey::PasskeyStorageInfo,
) -> OkpResult<PendingPasskeyRecord> {
    let record_uuid = uuid::Uuid::new_v4().to_string();
    let created_at_unix = service_util::now_utc_seconds();
    let record = PendingPasskeyRecord {
        record_uuid: record_uuid.clone(),
        created_at_unix,
        org_db_key: org_db_key.to_string(),
        passkey_info,
    };
    let dir = pending_passkeys_dir()?;
    let file_path = dir.join(format!("{}.json", &record_uuid));
    let json_str = serde_json::to_string_pretty(&record)
        .map_err(|e| OkpError::UnexpectedError(format!("JSON serialize failed: {}", e)))?;
    fs::write(&file_path, json_str.as_bytes())?;
    debug!("persist_pending_passkey: record written to {:?}", &file_path);
    Ok(record)
}

// ── IosAppGroupSupportService passkey command handlers ────────────────────────

impl super::IosAppGroupSupportService {
    // Returns all passkeys in the given db that match the relying-party ID
    // and optional allow-list of credential IDs.
    pub(super) fn passkey_find_matching(&self, json_args: &str) -> ResponseJson {
        let inner = || -> OkpResult<Vec<passkey::PasskeySummary>> {
            let (db_key, rp_id, allow_credential_ids) = parse_command_args_or_err!(
                json_args,
                PasskeyFindMatchingArg { db_key, rp_id, allow_credential_ids }
            );
            let ids = allow_credential_ids.unwrap_or_default();
            Ok(passkey::find_matching_passkeys(&[db_key], &rp_id, &ids)?)
        };
        result_json_str(inner())
    }

    // Returns all passkeys from all open databases — used by the main app to
    // register ASPasskeyCredentialIdentity objects with ASCredentialIdentityStore.
    pub(super) fn passkey_get_all(&self, _json_args: &str) -> ResponseJson {
        let inner = || -> OkpResult<Vec<passkey::PasskeySummary>> {
            let db_keys = db_service::all_kdbx_cache_keys()?;
            passkey::get_all_passkeys(&db_keys)
        };
        result_json_str(inner())
    }

    // Signs a WebAuthn assertion for the given entry using the pre-computed
    // clientDataHash supplied by the iOS autofill extension.
    pub(super) fn passkey_sign_assertion(&self, json_args: &str) -> ResponseJson {
        debug!("passkey_sign_assertion is called with json_args {}", json_args);

        let inner =
            || -> OkpResult<onekeepass_core::passkey_crypto::PasskeyAssertionWithHashResult> {
                let (db_key, entry_uuid_str, client_data_hash_b64url, _) =
                    parse_command_args_or_err!(
                        json_args,
                        PasskeySignAssertionArg {
                            db_key,
                            entry_uuid,
                            client_data_hash_b64url,
                            client_data_json_b64url
                        }
                    );
                let entry_uuid = uuid::Uuid::parse_str(&entry_uuid_str)
                    .map_err(|e| OkpError::UnexpectedError(e.to_string()))?;

                debug!("Calling onekeepass_core::db_service::passkey::get_passkey_for_assertion");

                let passkey = passkey::get_passkey_for_assertion(&db_key, &entry_uuid)?;

                debug!(
                    "Completed onekeepass_core::db_service::passkey::get_passkey_for_assertion"
                );

                let hash_bytes = BASE64URL_NOPAD
                    .decode(client_data_hash_b64url.as_bytes())
                    .map_err(|e| OkpError::UnexpectedError(e.to_string()))?;
                Ok(onekeepass_core::passkey_crypto::sign_assertion_with_hash(
                    &passkey.credential_id_b64url,
                    &passkey.rp_id,
                    &passkey.user_handle_b64url,
                    &passkey.private_key_pem,
                    &hash_bytes,
                )?)
            };
        result_json_str(inner())
    }

    // ── Pending passkey queue ────────────────────────────────────────────

    // Writes a PendingPasskeyRecord to AppGroup/okp/pending_passkeys/<uuid>.json.
    // Called by the autofill extension after a passkey creation ceremony.
    pub(super) fn passkey_store_pending(&self, json_args: &str) -> ResponseJson {
        debug!("In passkey_store_pending Rust ffi");

        let inner = || -> OkpResult<PendingPasskeyRecord> {
            let (
                org_db_key,
                credential_id_b64url,
                private_key_pem,
                rp_id,
                rp_name,
                username,
                user_handle_b64url,
                origin,
                entry_uuid,
                new_entry_name,
                group_uuid,
                new_group_name,
            ) = parse_command_args_or_err!(
                json_args,
                PasskeyStorePendingArg {
                    org_db_key,
                    credential_id_b64url,
                    private_key_pem,
                    rp_id,
                    rp_name,
                    username,
                    user_handle_b64url,
                    origin,
                    entry_uuid,
                    new_entry_name,
                    group_uuid,
                    new_group_name
                }
            );

            let entry_uuid_parsed = entry_uuid
                .map(|s| uuid::Uuid::parse_str(&s))
                .transpose()
                .map_err(|e| OkpError::UnexpectedError(format!("Invalid entry_uuid: {}", e)))?;

            let group_uuid_parsed = group_uuid
                .map(|s| uuid::Uuid::parse_str(&s))
                .transpose()
                .map_err(|e| OkpError::UnexpectedError(format!("Invalid group_uuid: {}", e)))?;

            let passkey_info = passkey::PasskeyStorageInfo {
                credential_id_b64url,
                private_key_pem, // Phase 5: encrypt before writing
                rp_id,
                rp_name,
                username,
                user_handle_b64url,
                origin,
                entry_uuid: entry_uuid_parsed,
                new_entry_name,
                group_uuid: group_uuid_parsed,
                new_group_name,
            };

            let record = persist_pending_passkey(&org_db_key, passkey_info)?;

            // Register the new passkey identity immediately so it appears in autofill sheet
            let summary = passkey_summary_from_pending(&record);
            if let Err(e) = IosApiCallbackImpl::api_service()
                .register_passkey_identities(record.org_db_key.clone(), vec![], vec![summary])
            {
                log::error!("passkey_store_pending: register identity error: {:?}", e);
            }

            Ok(record)
        };
        result_json_str(inner())
    }

    // ── Bundled assertion (sign + complete iOS request) ──────────────────

    // Single Rust command that signs the passkey assertion and then calls the Swift callback
    // `complete_passkey_assertion` to complete the iOS credential provider request.
    // Called from ClojureScript via autofill-invoke-api "passkey_complete_assertion".
    pub(super) fn passkey_complete_assertion(&self, json_args: &str) -> ResponseJson {
        // debug!("passkey_complete_assertion is called with json_args {}", &json_args);

        let inner = || -> OkpResult<()> {
            let (db_key, entry_uuid_str, client_data_hash_b64url, _) = parse_command_args_or_err!(
                json_args,
                PasskeySignAssertionArg { db_key, entry_uuid, client_data_hash_b64url, client_data_json_b64url }
            );
            let entry_uuid = uuid::Uuid::parse_str(&entry_uuid_str)
                .map_err(|e| OkpError::UnexpectedError(e.to_string()))?;
            let pk = passkey::get_passkey_for_assertion(&db_key, &entry_uuid)?;
            // debug!("get_passkey_for_assertion completed");
            let hash_bytes = BASE64URL_NOPAD
                .decode(client_data_hash_b64url.as_bytes())
                .map_err(|e| OkpError::UnexpectedError(e.to_string()))?;
            let result = onekeepass_core::passkey_crypto::sign_assertion_with_hash(
                &pk.credential_id_b64url,
                &pk.rp_id,
                &pk.user_handle_b64url,
                &pk.private_key_pem,
                &hash_bytes,
            )?;
            // debug!("sign_assertion_with_hash completed and calling IosApiCallbackImpl");
            IosApiCallbackImpl::api_service().complete_passkey_assertion(
                PasskeyAssertionCallbackData {
                    credential_id_b64url: result.credential_id_b64url,
                    rp_id: result.rp_id,
                    user_handle_b64url: result.user_handle_b64url,
                    signature_b64url: result.signature_b64url,
                    authenticator_data_b64url: result.authenticator_data_b64url,
                },
            )?;
            debug!(" IosApiCallbackImpl callback completed");
            Ok(())
        };
        // debug!("passkey_complete_assertion returning to cljs");
        result_json_str(inner())
    }

    // ── Bundled registration (create keypair + store pending + complete iOS request) ──

    // Single Rust command that creates a passkey, stores the pending record, and calls the
    // Swift callback `complete_passkey_registration` to complete the iOS credential provider
    // request. Called from ClojureScript via autofill-invoke-api "passkey_complete_registration".
    pub(super) fn passkey_complete_registration(&self, json_args: &str) -> ResponseJson {
        let inner = || -> OkpResult<()> {
            let (
                org_db_key,
                rp_id,
                rp_name,
                user_name,
                user_handle_b64url,
                client_data_hash_b64url,
                entry_uuid,
                new_entry_name,
                group_uuid,
                new_group_name,
                _,
            ) = parse_command_args_or_err!(
                json_args,
                PasskeyCompleteRegistrationArg {
                    org_db_key,
                    rp_id,
                    rp_name,
                    user_name,
                    user_handle_b64url,
                    client_data_hash_b64url,
                    entry_uuid,
                    new_entry_name,
                    group_uuid,
                    new_group_name,
                    client_data_json_b64url
                }
            );

            // Step 1: Create keypair
            let hash_bytes = BASE64URL_NOPAD
                .decode(client_data_hash_b64url.as_bytes())
                .map_err(|e| OkpError::UnexpectedError(e.to_string()))?;
            let creation = onekeepass_core::passkey_crypto::create_passkey_with_hash(
                &rp_id,
                &rp_name,
                &user_name,
                &user_handle_b64url,
                &hash_bytes,
            )?;

            // Step 2: Store pending record (writes to disk + registers identity)
            let entry_uuid_parsed = entry_uuid
                .map(|s| uuid::Uuid::parse_str(&s))
                .transpose()
                .map_err(|e| OkpError::UnexpectedError(format!("Invalid entry_uuid: {}", e)))?;
            let group_uuid_parsed = group_uuid
                .map(|s| uuid::Uuid::parse_str(&s))
                .transpose()
                .map_err(|e| OkpError::UnexpectedError(format!("Invalid group_uuid: {}", e)))?;
            let passkey_info = passkey::PasskeyStorageInfo {
                credential_id_b64url: creation.credential_id_b64url.clone(),
                private_key_pem: creation.private_key_pem, // Phase 5: encrypt before writing
                rp_id: creation.rp_id.clone(),
                rp_name: creation.rp_name,
                username: creation.username,
                user_handle_b64url: creation.user_handle_b64url,
                origin: format!("https://{}", &creation.rp_id),
                entry_uuid: entry_uuid_parsed,
                new_entry_name,
                group_uuid: group_uuid_parsed,
                new_group_name,
            };
            let record = persist_pending_passkey(&org_db_key, passkey_info)?;

            // Register the new passkey identity so it appears in future autofill sheets
            let summary = passkey_summary_from_pending(&record);
            if let Err(e) = IosApiCallbackImpl::api_service()
                .register_passkey_identities(record.org_db_key.clone(), vec![], vec![summary])
            {
                log::error!("passkey_complete_registration: register identity error: {:?}", e);
            }

            // Step 3: Complete the iOS registration request via Swift callback
            IosApiCallbackImpl::api_service().complete_passkey_registration(
                PasskeyRegistrationCallbackData {
                    credential_id_b64url: creation.credential_id_b64url,
                    attestation_object_b64url: creation.attestation_object_b64url,
                    client_data_hash_b64url,
                },
            )?;
            Ok(())
        };
        result_json_str(inner())
    }

    // Lists all pending passkey records, optionally filtered by org_db_key.
    // Pass empty org_db_key to list records for all databases.
    // Expired records (older than PENDING_PASSKEY_TTL_SECS) are removed lazily.
    pub(super) fn passkey_pending_list(&self, json_args: &str) -> ResponseJson {
        let inner = || -> OkpResult<Vec<PendingPasskeyRecord>> {
            let (org_db_key,) =
                parse_command_args_or_err!(json_args, PasskeyPendingListArg { org_db_key });

            let dir = pending_passkeys_dir()?;

            if !dir.exists() {
                return Ok(vec![]);
            }

            let now = service_util::now_utc_seconds();
            let mut records = Vec::new();
            let entries = fs::read_dir(&dir)?;

            for entry in entries {
                let entry = entry?;
                let path = entry.path();

                if path.extension().and_then(|e| e.to_str()) != Some("json") {
                    continue;
                }

                match fs::read_to_string(&path) {
                    Ok(json_str) => match serde_json::from_str::<PendingPasskeyRecord>(&json_str) {
                        Ok(record) => {
                            // Remove expired records
                            if (now - record.created_at_unix) > PENDING_PASSKEY_TTL_SECS {
                                log::info!(
                                    "Removing expired pending passkey record {:?}",
                                    &path
                                );
                                let _ = fs::remove_file(&path);
                                continue;
                            }

                            // Filter by org_db_key if non-empty
                            if org_db_key.is_empty() || record.org_db_key == org_db_key {
                                records.push(record);
                            }
                        }
                        Err(e) => {
                            log::warn!(
                                "Skipping malformed pending passkey file {:?}: {}",
                                &path,
                                e
                            );
                        }
                    },
                    Err(e) => {
                        log::warn!("Could not read pending passkey file {:?}: {}", &path, e);
                    }
                }
            }

            // Newest first
            records.sort_by(|a, b| b.created_at_unix.cmp(&a.created_at_unix));

            Ok(records)
        };
        result_json_str(inner())
    }

    // Reads a pending passkey record, stores the passkey into the in-memory KDBX
    // via store_passkey_entry, then deletes the pending file.
    // The caller (UI layer) is responsible for triggering the normal save flow afterward.
    pub(super) fn passkey_commit_pending(&self, json_args: &str) -> ResponseJson {
        let inner = || -> OkpResult<()> {
            let (record_uuid, db_key) = parse_command_args_or_err!(
                json_args,
                PasskeyPendingRecordArg { record_uuid, db_key }
            );

            let dir = pending_passkeys_dir()?;
            let file_path = dir.join(format!("{}.json", &record_uuid));

            if !file_path.exists() {
                return Err(OkpError::UnexpectedError(format!(
                    "Pending passkey record {} not found",
                    &record_uuid
                )));
            }

            let json_str = fs::read_to_string(&file_path)?;
            let record: PendingPasskeyRecord = serde_json::from_str(&json_str).map_err(|e| {
                OkpError::UnexpectedError(format!(
                    "Failed to parse pending passkey record: {}",
                    e
                ))
            })?;

            // Phase 5: decrypt record.passkey_info.private_key_pem here

            // store_passkey_entry modifies the in-memory KDBX only.
            // db_key is the main app's runtime db_key (the loaded DB).
            passkey::store_passkey_entry(&db_key, record.passkey_info)?;

            // Update ASCredentialIdentityStore with passkeys for this database
            register_passkey_identities_for_db(&db_key);

            // Delete the pending file only after successful commit
            fs::remove_file(&file_path)?;

            debug!(
                "Pending passkey {} committed to db_key {} and file deleted",
                &record_uuid, &db_key
            );

            Ok(())
        };
        result_json_str(inner())
    }

    // Deletes a pending passkey record without committing it to KDBX.
    pub(super) fn passkey_discard_pending(&self, json_args: &str) -> ResponseJson {
        let inner = || -> OkpResult<()> {
            let (record_uuid, _db_key) = parse_command_args_or_err!(
                json_args,
                PasskeyPendingRecordArg { record_uuid, db_key }
            );

            let dir = pending_passkeys_dir()?;
            let file_path = dir.join(format!("{}.json", &record_uuid));

            if !file_path.exists() {
                return Err(OkpError::UnexpectedError(format!(
                    "Pending passkey record {} not found",
                    &record_uuid
                )));
            }

            fs::remove_file(&file_path)?;

            debug!("Pending passkey record {} discarded", &record_uuid);

            Ok(())
        };
        result_json_str(inner())
    }

    // ── Passkey creation ──────────────────────────────────────────────────

    // Generates a new passkey key pair and returns the attestation object,
    // credential ID, and private key PEM. Called by the autofill extension
    // during a passkey registration ceremony.
    pub(super) fn passkey_create_with_hash(&self, json_args: &str) -> ResponseJson {
        let inner =
            || -> OkpResult<onekeepass_core::passkey_crypto::PasskeyCreationWithHashResult> {
                let (rp_id, rp_name, user_name, user_handle_b64url, client_data_hash_b64url) =
                    parse_command_args_or_err!(
                        json_args,
                        PasskeyCreateWithHashArg {
                            rp_id,
                            rp_name,
                            user_name,
                            user_handle_b64url,
                            client_data_hash_b64url
                        }
                    );

                let hash_bytes = BASE64URL_NOPAD
                    .decode(client_data_hash_b64url.as_bytes())
                    .map_err(|e| OkpError::UnexpectedError(e.to_string()))?;

                Ok(onekeepass_core::passkey_crypto::create_passkey_with_hash(
                    &rp_id,
                    &rp_name,
                    &user_name,
                    &user_handle_b64url,
                    &hash_bytes,
                )?)
            };
        result_json_str(inner())
    }

    // Returns all groups in the opened database for the passkey registration
    // group picker UI.
    pub(super) fn passkey_get_db_groups(&self, json_args: &str) -> ResponseJson {
        let inner = || -> OkpResult<Vec<passkey::GroupInfo>> {
            let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });
            Ok(passkey::get_db_groups(&db_key)?)
        };
        result_json_str(inner())
    }

    // Returns all entries in a specific group for the passkey registration
    // entry picker UI.
    pub(super) fn passkey_get_group_entries(&self, json_args: &str) -> ResponseJson {
        let inner = || -> OkpResult<Vec<passkey::EntryBasicInfo>> {
            let (db_key, group_uuid_str) = parse_command_args_or_err!(
                json_args,
                PasskeyGetGroupEntriesArg { db_key, group_uuid }
            );

            let group_uuid = uuid::Uuid::parse_str(&group_uuid_str)
                .map_err(|e| OkpError::UnexpectedError(format!("Invalid group_uuid: {}", e)))?;

            Ok(passkey::get_group_entries(&db_key, &group_uuid)?)
        };
        result_json_str(inner())
    }
}

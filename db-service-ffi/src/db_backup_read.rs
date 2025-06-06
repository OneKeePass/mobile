use std::fs;

use crate::app_state::AppState;
use crate::backup::latest_backup_file_path;
use crate::CommandArg;
use crate::{parse_command_args_or_err, OkpError, OkpResult};
use onekeepass_core::db_service::KdbxLoaded;
use onekeepass_core::{db_service, error, service_util};
use serde::Serialize;

#[derive(Debug, Serialize)]
struct RsAdditionalInfo {
    no_connection: bool,
}

// This adds an additional info to the existing KdbxLoaded
// For now we set 'no_connection' field only to indicate to the UI side
// that we have opened the db content using the backup and editing should be
// disabled for now
#[derive(Debug, Serialize)]
pub(crate) struct KdbxLoadedEx {
    db_key: String,
    database_name: String,
    file_name: Option<String>,
    key_file_name: Option<String>,
    rs_additional_info: Option<RsAdditionalInfo>,
}

impl KdbxLoadedEx {
    pub(crate) fn set_no_read_connection(mut self) -> Self {
        self.rs_additional_info = Some(RsAdditionalInfo {
            no_connection: true,
        });
        self
    }
}

impl From<KdbxLoaded> for KdbxLoadedEx {
    fn from(kdbx_loaded: KdbxLoaded) -> Self {
        let KdbxLoaded {
            db_key,
            database_name,
            file_name,
            key_file_name,
        } = kdbx_loaded;

        KdbxLoadedEx {
            db_key,
            database_name,
            file_name,
            key_file_name,
            rs_additional_info: None,
        }
    }
}

pub(crate) fn read_latest_backup(json_args: &str) -> OkpResult<KdbxLoadedEx> {
    let (db_file_name, password, key_file_name, _) = parse_command_args_or_err!(
        json_args,
        OpenDbArg {
            db_file_name,
            password,
            key_file_name,
            biometric_auth_used
        }
    );
    let file_name = AppState::file_name_in_recently_used(&db_file_name);
    read_latest_backup_db_arg(&db_file_name, &password, &key_file_name, &file_name)
}

pub(crate) fn read_latest_backup_db_arg(
    db_file_name: &str,
    password: &Option<String>,
    key_file_name: &Option<String>,
    file_name: &Option<String>,
) -> OkpResult<KdbxLoadedEx> {
    let path = latest_backup_file_path(&db_file_name).ok_or(error::Error::UnexpectedError(
        format!("Getting latest backup file failed and read only db call failed"),
    ))?;
    log::debug!("Read only mode: Reading the latest backup file {:?}", &path);

    let mut reader = fs::File::open(path)?;

    // Kdbx content from backup is loaded, parsed and decrypted
    let kdbx_loaded = db_service::read_kdbx(
        &mut reader,
        &db_file_name,
        password.as_deref(),
        key_file_name.as_deref(),
        file_name.as_deref(),
    )?;

    let k: KdbxLoadedEx = kdbx_loaded.into();
    // We return the no connection info so that we can show read only mode
    return Ok(k.set_no_read_connection());
}

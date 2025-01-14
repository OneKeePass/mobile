mod storage_service;
mod callback_service;
pub(crate) mod callback_service_provider;
pub(crate) mod secure_store;

pub use storage_service::{RemoteStorageType,read_configs,RemoteStorageOperationType, RemoteStorageOperation};

use std::fs;
use std::io::{Cursor, Read, Seek, Write};
use std::path::Path;
use std::sync::Arc;

use crate::app_state::AppState;
use crate::backup::{
    self, latest_backup_file_path, latest_backup_full_file_name, matching_backup_exists,
};
use crate::commands::{result_json_str, CommandArg, ResponseJson};
use crate::udl_types::FileInfo;
use crate::{biometric_auth, open_backup_file, parse_command_args_or_err};
use crate::{OkpError, OkpResult};
use nom::Err;


use log::{debug, error, info};
use nom::{
    self,
    bytes::{
        self,
        complete::{take_while1, take_while_m_n},
    },
    character::{
        complete::{alpha1, digit1},
        is_alphabetic, is_hex_digit,
    },
    combinator::rest,
    complete::tag,
    sequence::tuple,
    IResult,
};
use onekeepass_core::db_service::{KdbxLoaded, KdbxSaved};
use onekeepass_core::{db_service, error, service_util};
use serde::Serialize;
use storage_service::ParsedDbKey;


/// -------   All public functions   -------


pub(crate) fn uri_to_file_info(db_key: &str) -> Option<FileInfo> {
    let Ok((remaining, parsed)) = parse_db_key(db_key) else {
        return None;
    };
    if !remaining.is_empty() {
        return None;
    }

    let mut file_info = FileInfo::default();
    // We use the backup file to form this instead of making remote call
    let file_info_opt = backup::latest_backup_file_path(db_key)
        .and_then(|p| p.as_path().metadata().ok())
        .map(|md| {
            //debug!("RS Bk modified Metadata is {:?} ", &md);
            file_info.file_size = Some(md.len() as i64);
            file_info.last_modified = md.modified().ok().map(|t| {
                //debug!(" RS file_info.last_modified (SystemTime) {:?} ",&t);
                let mut r = service_util::system_time_to_seconds(t) as i64;
                // Need to be in milliseconds
                r = r * 1000;
                //debug!(" RS file_info.last_modified since epoch {} ",&r);
                r
            });
            file_info.location = Some(parsed.rs_type_name.to_string());
            file_info
        });

    file_info_opt
}

pub(crate) fn uri_to_file_name(db_key: &str) -> Option<&str> {
    let Ok((remaining, parsed)) = parse_db_key(db_key) else {
        return None;
    };
    if !remaining.is_empty() {
        return None;
    }
    return Some(parsed.file_name);
}

#[inline]
pub(crate) fn rs_read_kdbx(json_args: &str) -> ResponseJson {
    result_json_str(rs_read_file(json_args))
}

#[inline]
pub(crate) fn rs_save_kdbx(json_args: &str) -> ResponseJson {
    result_json_str(rs_write_file(json_args))
}

#[inline]
pub(crate) fn rs_create_kdbx(json_args: &str) -> ResponseJson {
    result_json_str(rs_create_file(json_args))
}

/// ----------------------------------------------------------------------


// We need to parse the passed db_key and extracts the remote operation type, connection_id and the file path part

// e.g WebDav-264226dc-be96-462a-a386-79adb6291ad7-/dav/db1/db1-1/db1-2/Test1-Sp.kdbx or
//     Sftp-264226dc-be96-462a-a386-79adb6291ad7-/dav/db1/db1-1/db1-2/Test1-Sp.kdbx

// A UUID (Universally Unique Identifier) is a 128-bit label typically represented as
// a 36-character string, formatted in five groups of hexadecimal digits
// separated by hyphens, following the pattern 8-4-4-4-12

fn parse_db_key(db_key: &str) -> IResult<&str, ParsedDbKey> {
    let (remaining, (rs_type_name, _, connection_id, _, file_path_part)) = tuple((
        alpha1,
        bytes::complete::tag("-"),
        take_while_m_n(36, 36, |c| is_hex_digit(c as u8) || c == '-'),
        bytes::complete::tag("-"),
        rest,
    ))(db_key)?;

    // remaining should be empty str after a successful db key parsing

    // We extract the file name
    let file_name = file_path_part.rsplit_once("/").map_or_else(|| "", |p| p.1);

    Ok((
        remaining,
        ParsedDbKey {
            rs_type_name,
            connection_id,
            file_path_part,
            file_name,
        },
    ))
}

// The passed db key is parsed as rs operation type
// The expected arg examples
// e.g WebDav-264226dc-be96-462a-a386-79adb6291ad7-/dav/db1/db1-1/db1-2/Test1-Sp.kdbx or
//     Sftp-264226dc-be96-462a-a386-79adb6291ad7-/dav/db1/db1-1/db1-2/Test1-Sp.kdbx

fn parse_db_key_to_rs_type_opertaion(db_key: &str) -> OkpResult<RemoteStorageOperationType> {
    let pasrsed_result = parse_db_key(db_key);
    if let Err(e) = pasrsed_result {
        error!("Db key string parsing error {}", e);
        return Err(error::Error::UnexpectedError(format!(
            "Remote storage db key parsing failed with error: {}",
            e
        )));
    }

    let (remaining, parsed_db_key) = pasrsed_result.unwrap();

    if !remaining.is_empty() {
        return Err(error::Error::UnexpectedError(format!(
            "Db key parsing failed and the unparsed remaining part is {}",
            remaining
        )));
    }

    let rt = RemoteStorageOperationType::try_from_parsed_db_key(parsed_db_key)?;

    debug!(
        "RemoteStorageOperationType is formed after parsing {:?}",
        &rt
    );

    Ok(rt)
}


#[derive(Debug, Serialize)]
struct RsAdditionalInfo {
    no_connection: bool,
}

// This adds an additional info to the existing KdbxLoaded
// For now we set 'no_connection' field only to indicate to the UI side
// that we have opened the db content using the backup and editing should be
// disabled for now
#[derive(Debug, Serialize)]
struct KdbxLoadedEx {
    db_key: String,
    database_name: String,
    file_name: Option<String>,
    key_file_name: Option<String>,
    rs_additional_info: Option<RsAdditionalInfo>,
}

impl KdbxLoadedEx {
    fn set_no_read_connection(mut self) -> Self {
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

fn rs_read_file(json_args: &str) -> OkpResult<KdbxLoadedEx> {
    let (db_file_name, password, key_file_name, biometric_auth_used) = parse_command_args_or_err!(
        json_args,
        OpenDbArg {
            db_file_name,
            password,
            key_file_name,
            biometric_auth_used
        }
    );

    let rs_operation_type = parse_db_key_to_rs_type_opertaion(&db_file_name)?;

    // Ensure that the remote connection is established
    // If the remote server is not available, send an error to UI and user determines what to do

    if let Err(e) = rs_operation_type.connect_by_id() {
        // If the db file is read earlier, then we should have at least one backup. Otherwise an error is returned
        let path = latest_backup_file_path(&db_file_name).ok_or(error::Error::UnexpectedError(
            format!(
                "Connection to remote server is not available and error details:{}",
                e
            ),
        ))?;
        debug!(
            "No remote connection: Reading the latest backup file {:?}",
            &path
        );

        let mut reader = fs::File::open(path)?;

        // Kdbx content from backup is loaded, parsed and decrypted
        let kdbx_loaded = db_service::read_kdbx(
            &mut reader,
            &db_file_name,
            password.as_deref(),
            key_file_name.as_deref(),
            None,
        )?;

        let k: KdbxLoadedEx = kdbx_loaded.into();
        // We return the no connection info so that we can show read only mode
        return Ok(k.set_no_read_connection());
    }

    debug!("Remote server connected");

    let r = rs_operation_type.read()?;

    let file_modified_time = r.meta.modified.map(|x| x as i64);

    let file_name = rs_operation_type
        .file_name()
        .ok_or(error::Error::DataError(
            "File name is not found in the rs operation type formed from the db key parsing",
        ))?;

    // create a memory based reader for db calls to load
    let mut reader = Cursor::new(&r.data);

    let kdbx_loaded = read_with_backup(
        &mut reader,
        &db_file_name,
        password.as_deref(),
        key_file_name.as_deref(),
        biometric_auth_used,
        file_name,
        &file_modified_time,
    )?;

    Ok(kdbx_loaded.into())
}

// Sets the modified time of the backup file to that of the db file
// The arg file_modified_time is in seconds
fn set_backup_modified_time(backup_file_name: &Option<&String>, file_modified_time: &Option<i64>) {
    if let Some(bk_file_name) = backup_file_name.as_deref() {
        debug!(
            "Before setting mtime {:?}",
            Path::new(bk_file_name).metadata()
        );
        if let Some(mtime) = file_modified_time {
            let r = filetime::set_file_mtime(
                bk_file_name,
                filetime::FileTime::from_unix_time(*mtime, 0),
            );
            debug!("Setting modified time of backup file status is {:?}", r);
            debug!(
                "After setting mtime {:?}",
                Path::new(bk_file_name).metadata()
            )
        }
    }
}

// This is based on a part of the fn 'udl_functions::internal_read_kdbx'
// TODO: Reuse this fn in udl_functions::internal_read_kdbx

fn read_with_backup<R: Read + Seek>(
    reader: &mut R,
    db_key: &str,
    password: Option<&str>,
    key_file_name: Option<&str>,
    biometric_auth_used: bool,
    file_name: &str,
    file_modified_time: &Option<i64>,
) -> OkpResult<db_service::KdbxLoaded> {
    // First we read the db file
    let kdbx_loaded =
        db_service::read_kdbx(reader, db_key, password, key_file_name, Some(file_name)).map_err(
            |e| match e {
                // Need this when db login fails while using the previously stored credentials
                // and the UI will then popup the usual dialog
                error::Error::HeaderHmacHashCheckFailed if biometric_auth_used => {
                    error::Error::BiometricCredentialsAuthenticationFailed
                }
                _ => e,
            },
        )?;

    // Gets the checksum of the loaded db in the above 'read_kdbx' call
    let read_db_checksum = db_service::db_checksum_hash(db_key)?;

    // We need to ensure that we create a backup only when the above loaded db is a new content
    // as compared to the latest backup found
    if matching_backup_exists(&db_key, read_db_checksum)
        .ok()
        .flatten()
        .is_none()
    {
        let backup_file_name = backup::generate_backup_history_file_name(&db_key, file_name);

        debug!(
            "Going to create a new backup file {:?} for the db_key {}",
            &backup_file_name, &db_key
        );

        let mut backup_file = open_backup_file(backup_file_name.as_ref())
            .ok_or(OkpError::DataError("Opening backup file failed"))?;

        // Ensure that we are at the begining of the db file stream
        let _rr = reader.rewind();

        // Copy from db file reader to the backup file
        std::io::copy(reader, &mut backup_file)
            .and(backup_file.sync_all())
            .and(backup_file.rewind())?;

        set_backup_modified_time(&backup_file_name.as_ref(), file_modified_time);

        debug!("Created backup file for the db_key {}", &db_key);
    }

    backup::prune_backup_history_files(&db_key);

    AppState::add_recent_db_use_info2(db_key, file_name);

    #[cfg(target_os = "ios")]
    {
        // iOS specific copying when we read a database if this db is used in Autofill extension
        crate::ios::autofill_app_group::copy_files_to_app_group_on_save_or_read(&db_key);
    }

    // Store the crdentials if we will be using biometric
    let _r = biometric_auth::StoredCredential::store_credentials_on_check(
        db_key,
        &password.map(|s| s.to_string()),
        &key_file_name.map(|s| s.to_string()),
        biometric_auth_used,
    );

    Ok(kdbx_loaded)
}

fn is_rs_file_modified(
    db_key: &str,
    rs_operation_type: &RemoteStorageOperationType,
) -> OkpResult<bool> {
    let Some(bk_full_path) = backup::latest_backup_file_path(db_key) else {
        return Err(error::Error::UnRecoverableError(format!(
            "Expected a backup file and it is not found"
        )));
    };

    let rmd = rs_operation_type.file_metadata().map_err(|e| {
        error::Error::UnRecoverableError(format!(
            "Geting modified time remote file failed. Error details {:?}",
            e
        ))
    })?;

    let md = bk_full_path.metadata().map_err(|e| {
        error::Error::UnRecoverableError(format!(
            "Geting backup file metadata failed. Error details {:?}",
            e
        ))
    })?;

    let md = md.modified().map_err(|e| {
        error::Error::UnexpectedError(format!(
            "Geting modified time backup file failed. Error details {:?}",
            e
        ))
    })?;

    debug!(" Rmd is {:?} and md is {:?} ", &rmd, &md);

    if rmd.modified == Some(service_util::system_time_to_seconds(md)) {
        Ok(false)
    } else {
        Ok(true)
    }
}

fn rs_write_file(json_args: &str) -> OkpResult<KdbxSaved> {
    let (db_key, overwrite) =
        parse_command_args_or_err!(json_args, SaveDbArg { db_key, overwrite });

    let rs_operation_type = parse_db_key_to_rs_type_opertaion(&db_key)?;

    let file_name = rs_operation_type
        .file_name()
        .ok_or(error::Error::DataError(
            "File name is not found in the rs operation type formed from the db key parsing",
        ))?;

    // Ensure that the remote connection is established
    // TODO: What to do when there is no connection?
    // let rs_operation_type = rs_operation_type.connect_by_id()?;

    rs_operation_type.connect_by_id().map_err(|e| {
        info!("Remote storage connection error {}", e);

        // Writes to a backup file and sets that backup file name in app state for later Save As call
        crate::udl_functions::write_to_backup_on_error(db_key.clone());

        // At this any error while making connect_by_id, we send a generic error
        error::Error::NoRemoteStorageConnection
    })?;

    debug!("Remote server connected");

    if !overwrite {
        // For remote storage, we use file modified time based checking instead of checksum based to avoid
        // reading whole file for that
        if is_rs_file_modified(&db_key, &rs_operation_type)? {
            crate::udl_functions::write_to_backup_on_error(db_key.clone());
            return Err(error::Error::DbFileContentChangeDetected);
        }
    }

    let backup_file_name = backup::generate_backup_history_file_name(&db_key, file_name);

    // First we write the db content to memory and db_content_mem_buff provides Read+Write fns
    let mut db_content_mem_buff = Cursor::new(Vec::<u8>::new());
    // The db content is also copied to the backup file
    let kdbx_saved = write_with_backup(
        &db_key,
        &mut db_content_mem_buff,
        &backup_file_name.as_ref(),
        overwrite,
    )?;

    // We now write to the remote storage the content of the db writen to the memory in 'db_content_mem_buff'
    // TODO:
    // There is a possibility the remote storage call may fail. However we would have created the backup file
    // and need to send an error to the UI accordingly.
    let data = Arc::new(db_content_mem_buff.into_inner());
    let file_modified_time = rs_operation_type
        .write_file(data.clone())?
        .modified
        .map(|t| t as i64);

    // We set the backup file's modified time to the same as the remote file modified time
    set_backup_modified_time(&backup_file_name.as_ref(), &file_modified_time);

    // Any previous ref stored meant for error resolution is not required
    AppState::remove_last_backup_name_on_error(&db_key);

    Ok(kdbx_saved)
}

// This is based on a part of the fn 'udl_functions::save_kdbx'
// TODO: Reuse this fn in udl_functions::save_kdbx ( need to add arg overwrite and another arg to indicate local vs rs type call)

// Here we save the database to the backup file first and then to the db file
fn write_with_backup<R: Seek + Read + Write>(
    db_key: &str,
    reader_writer: &mut R,
    backup_file_name: &Option<&String>,
    overwrite: bool,
) -> OkpResult<KdbxSaved> {
    // Create a backup file using the back file path passed
    let backup_file = open_backup_file(backup_file_name.as_deref());

    let save_response = match backup_file {
        Some(mut bf_writer) => {
            // First we write to the backup file
            let ks = db_service::save_kdbx_to_writer(&mut bf_writer, &db_key)?;

            // Ensure that we are at the begining of the file stream
            let rewind_r = bf_writer.sync_all().and(bf_writer.rewind());
            //log::debug!("Syncing and rewinding of backup file result {:?}", rewind_r);
            rewind_r?;

            if !overwrite {
                // TODO
                // db_service::verify_db_file_checksum(&db_key, &mut writer) when there is no overwrite
            }

            // Now we copy the content of the backup to the actual db file (reader_writer)
            std::io::copy(&mut bf_writer, reader_writer)?;

            // New checksum is calculated and set in the cached db content
            db_service::calculate_and_set_db_file_checksum(&db_key, &mut bf_writer)?;

            log::debug!("New hash for checksum is done and set");

            // We keep the last backup file ref which may be used to resolve save time error or conflicts
            // in the follow up call from UI. On successful save, this ref from app state is removed
            if let Some(bkp_file_name) = backup_file_name.as_deref() {
                AppState::add_last_backup_name_on_error(&db_key, bkp_file_name);
            }

            #[cfg(target_os = "ios")]
            {
                // iOS specific copying of a database when we save a  database if this db is used in Autofill extension
                crate::ios::autofill_app_group::copy_files_to_app_group_on_save_or_read(&db_key);
            }

            ks
        }
        None => {
            // This is not used. Will this ever happen?. Need to find use case where we do not have backup file
            log::warn!("No backup file is not found and writting to the db file directly");
            // TODO: Call verify checksum here using writer "db_service::verify_db_file_checksum"
            db_service::save_kdbx_to_writer(reader_writer, &db_key)?
        }
    };

    Ok(save_response)
}

// Somewhat similar to 'rs_write_file'

// Also some steps are similar to  AndroidSupportServiceExtra::create_kdbx 

// TODO: Move common steps of 'rs_write_file' and 'rs_create_file' to another fn
fn rs_create_file(json_args: &str) -> OkpResult<KdbxLoaded> {
    let (new_db,) = parse_command_args_or_err!(json_args, NewDbArg { new_db });

    let file_name = new_db.file_name.as_deref().ok_or(error::Error::DataError(
        "Valid file name is not set in new db arg",
    ))?.to_string();

    let db_key = new_db.database_file_name.clone();

    // db_key should have been formed and set in the 'new_db' struct from the frontend side
    let rs_operation_type = parse_db_key_to_rs_type_opertaion(&db_key)?;

    let backup_file_name = backup::generate_backup_history_file_name(&db_key, &file_name);

    let backup_file = open_backup_file(backup_file_name.as_ref());

    let mut bf_writer = backup_file.ok_or(error::Error::DataError(
        "Backup file could not be opened to write the new database backup",
    ))?;

    let kdbx_loaded = db_service::create_and_write_to_writer(&mut bf_writer, new_db)?;

    let _ = bf_writer.sync_all().and(bf_writer.rewind());

    // The memory buffer db_content_mem_buff provides Read+Write fns
    let mut db_content_mem_buff = Cursor::new(Vec::<u8>::new());

    // Now we copy the content of the backup to memory
    std::io::copy(&mut bf_writer, &mut db_content_mem_buff)?;

    // New checksum is calculated and set in the cached db content
    db_service::calculate_and_set_db_file_checksum(&db_key, &mut bf_writer)?;

    // We now write to the remote storage the content of db from the memory 'db_content_mem_buff'
    let data = Arc::new(db_content_mem_buff.into_inner());
    let meta_data = match rs_operation_type.create_file(data.clone()) {
        Ok(v) => v,
        Err(e) => {
            // There is a possibility that the remote storage call may fail. In that case remove the backup file deleted
            if let Some(ref bk) = backup_file_name {
                let _ = fs::remove_file(bk);
            }
            return Err(e);
        }
    };

    // Add this newly created db file to the recent list
    AppState::add_recent_db_use_info2(&db_key, &file_name);

    let file_modified_time = meta_data.modified.map(|t| t as i64);

    // We set the backup file's modified time to the same as the remote file modified time
    set_backup_modified_time(&backup_file_name.as_ref(), &file_modified_time);

    Ok(kdbx_loaded)
}
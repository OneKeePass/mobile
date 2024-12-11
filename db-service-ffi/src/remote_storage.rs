use std::fs;
use std::io::{Cursor, Read, Seek, Write};
use std::path::Path;
use std::sync::Arc;

use crate::app_state::AppState;
use crate::backup::{
    self, latest_backup_file_path, latest_backup_full_file_name, matching_backup_exists,
};
use crate::commands::{result_json_str, CommandArg, ResponseJson};
use crate::{open_backup_file, parse_command_args_or_err};
use crate::{OkpError, OkpResult};
use nom::Err;
use onekeepass_core::db_service::storage::{
    ParsedDbKey, RemoteStorageOperation, RemoteStorageOperationType,
};

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
use onekeepass_core::{db_service, error};
use serde::Serialize;

// We need to parse the passed db_key and extracts the remote operation type, connection_id and the file path part

// e.g WebDav-264226dc-be96-462a-a386-79adb6291ad7-/dav/db1/db1-1/db1-2/Test1-Sp.kdbx or
//     Sftp-264226dc-be96-462a-a386-79adb6291ad7-/dav/db1/db1-1/db1-2/Test1-Sp.kdbx

// A UUID (Universally Unique Identifier) is a 128-bit label typically represented as
// a 36-character string, formatted in five groups of hexadecimal digits
// separated by hyphens, following the pattern 8-4-4-4-12

fn parse_db_key<'a>(db_key: &'a str) -> IResult<&'a str, ParsedDbKey> {
    let (remaining, (rs_type_name, _, connection_id, _, file_path_part)) = tuple((
        alpha1,
        bytes::complete::tag("-"),
        take_while_m_n(36, 36, |c| is_hex_digit(c as u8) || c == '-'),
        bytes::complete::tag("-"),
        rest,
    ))(db_key)?;

    // remaining should be empty str after a successful db key parsing

    Ok((
        remaining,
        ParsedDbKey {
            rs_type_name,
            connection_id,
            file_path_part,
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

pub(crate) fn rs_read_kdbx(json_args: &str) -> ResponseJson {
    result_json_str(rs_read_file(json_args))
}

#[derive(Debug, Serialize)]
struct RsAdditionalInfo {
    no_connection: bool,
}

// This adds an additional info to the existing KdbxLoaded
// For now we set 'no_connection' field only to indicate to the UI side
// that we have opened the db content using the backup and editing should be 
// disbaled for now
#[derive(Debug, Serialize)]
struct KdbxLoadedEx {
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

fn rs_read_file(json_args: &str) -> OkpResult<KdbxLoadedEx> {
    let (db_file_name, password, key_file_name) = parse_command_args_or_err!(
        json_args,
        OpenDbArg {
            db_file_name,
            password,
            key_file_name
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
        debug!("No remote connection: Reading the latest backup file {:?}", &path);
        
        let mut reader = fs::File::open(path)?;

        // Kdbx content from backup is loaded, parsed and decrypted
        let kdbx_loaded = db_service::read_kdbx(
            &mut reader,
            &db_file_name,
            password.as_deref(),
            key_file_name.as_deref(),
            None,
        )?;

        let k:KdbxLoadedEx = kdbx_loaded.into() ;
        // We return the no connection info so that we can show read only mode 
        return Ok(k.set_no_read_connection())
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
    file_name: &str,
    file_modified_time: &Option<i64>,
) -> OkpResult<db_service::KdbxLoaded> {
    // First we read the db file
    let kdbx_loaded =
        db_service::read_kdbx(reader, db_key, password, key_file_name, Some(file_name))?;

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

    AppState::shared().add_recent_db_use_info(db_key);

    #[cfg(target_os = "ios")]
    {
        // iOS specific copying when we read a database if this db is used in Autofill extension
        crate::ios::app_group::copy_files_to_app_group_on_save_or_read(&db_key);
    }

    Ok(kdbx_loaded)
}

pub(crate) fn rs_save_kdbx(json_args: &str) -> ResponseJson {
    result_json_str(rs_write_file(json_args))
}

fn rs_write_file(json_args: &str) -> OkpResult<KdbxSaved> {
    let (db_key,) = parse_command_args_or_err!(json_args, DbKey { db_key });

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
        // At this any error while making connect_by_id, we send a generic error
        error::Error::NoRemoteStorageConnection
    })?;
    

    debug!("Remote server connected");

    let backup_file_name = backup::generate_backup_history_file_name(&db_key, file_name);

    // First we write the db content to memory and db_content_mem_buff provides Read+Write fns
    let mut db_content_mem_buff = Cursor::new(Vec::<u8>::new());
    // The db content is also copied to the backup file
    let kdbx_saved = write_with_backup(
        &db_key,
        &mut db_content_mem_buff,
        &backup_file_name.as_ref(),
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

    Ok(kdbx_saved)
}

// This is based on a part of the fn 'udl_functions::save_kdbx'
// TODO: Reuse this fn in udl_functions::save_kdbx ( need to add arg overwrite and another arg to indicate local vs rs type call)

// Here we save the database to the backup file first and then to the db file
fn write_with_backup<R: Seek + Read + Write>(
    db_key: &str,
    reader_writer: &mut R,
    backup_file_name: &Option<&String>,
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

            // Now we copy the content of the backup to the actual db file (reader_writer)
            std::io::copy(&mut bf_writer, reader_writer)?;

            // New checksum is calculated and set in the cached db content
            db_service::calculate_and_set_db_file_checksum(&db_key, &mut bf_writer)?;

            log::debug!("New hash for checksum is done and set");

            #[cfg(target_os = "ios")]
            {
                // iOS specific copying of a database when we save a  database if this db is used in Autofill extension
                crate::ios::app_group::copy_files_to_app_group_on_save_or_read(&db_key);
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

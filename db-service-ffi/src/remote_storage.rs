use std::io::{Cursor, Read, Seek, Write};
use std::sync::Arc;

use crate::app_state::AppState;
use crate::backup::{self, matching_backup_exists};
use crate::commands::{result_json_str, CommandArg, ResponseJson};
use crate::{open_backup_file, parse_command_args_or_err};
use crate::{OkpError, OkpResult};
use nom::Err;
use onekeepass_core::db_service::storage::{
    ParsedDbKey, RemoteStorageOperation, RemoteStorageOperationType,
};

use log::{debug, error};
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
use onekeepass_core::db_service::KdbxSaved;
use onekeepass_core::{db_service, error};

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

    // remaining should be empty str

    Ok((
        remaining,
        ParsedDbKey {
            rs_type_name,
            connection_id,
            file_path_part,
        },
    ))
}

pub(crate) fn rs_read_kdbx(json_args: &str) -> ResponseJson {
    result_json_str(rs_read_file(json_args))
}

fn rs_read_file(json_args: &str) -> OkpResult<db_service::KdbxLoaded> {
    let (db_file_name, password, key_file_name) = parse_command_args_or_err!(
        json_args,
        OpenDbArg {
            db_file_name,
            password,
            key_file_name
        }
    );

    let pasrsed_result = parse_db_key(&db_file_name);
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

    // Ensure that the remote connection is established
    let _r = rt.connect_by_id()?;
    // TODO: If the remote server is not available, send an error to UI and user determines what to do

    debug!("Remote server connected");

    let r = rt.read()?;

    let Some(file_name) = rt.file_name().map(|s| s.to_string()) else {
        return Err(error::Error::DataError(
            "File name is not found from db key",
        ));
    };

    let mut buff = Cursor::new(&r.data);
    // First we read the db file
    let kdbx_loaded = db_service::read_kdbx(
        &mut buff,
        &db_file_name,
        password.as_deref(),
        key_file_name.as_deref(),
        Some(&file_name),
    )?;

    let read_db_checksum = db_service::db_checksum_hash(&db_file_name)?;
    if matching_backup_exists(&db_file_name, read_db_checksum)
        .ok()
        .flatten()
        .is_none()
    {
        let backup_file_name = backup::generate_backup_history_file_name(&db_file_name, &file_name);
        let mut backup_file = open_backup_file(backup_file_name.clone())
            .ok_or(OkpError::DataError("Opening backup file failed"))?;

        // Ensure that we are at the begining of the db file stream
        let _rr = buff.rewind();

        // Copy from db file to backup file
        std::io::copy(&mut buff, &mut backup_file)
            .and(backup_file.sync_all())
            .and(backup_file.rewind())?;
    }

    Ok(kdbx_loaded)
}


pub(crate) fn rs_write_file(json_args: &str) -> OkpResult<()> {
    let (db_key,) = parse_command_args_or_err!(
        json_args,
        DbKey {
            db_key
        }
    );

    let pasrsed_result = parse_db_key(&db_key);
    if let Err(e) = pasrsed_result {
        error!("Db key string parsing error {}", e);
        return Err(error::Error::UnexpectedError(format!(
            "Remote storage db key parsing failed with error: {}",
            e
        )));
    }

    let (remaining, parsed_db_key) = pasrsed_result.unwrap();

    let rt = RemoteStorageOperationType::try_from_parsed_db_key(parsed_db_key)?;

    
    

    let (Some(file_name), Some(file_path)) = (rt.file_name(), rt.file_path()) else {
        return Err(error::Error::DataError(
            "File name File path are not found from db key",
        ));
    };

    let mut buff = Cursor::new(Vec::<u8>::new());

    let backup_file_name = backup::generate_backup_history_file_name(file_path, file_name);
    write_with_backup(&db_key,backup_file_name,&mut buff);

    let data = Arc::new(buff.into_inner());


    // Ensure that the remote connection is established
    let _r = rt.connect_by_id()?;

    rt.write_file(data.clone());

    Ok(())
}

fn write_with_backup<R:Seek+Read+Write>(db_key:&str,backup_file_name:Option<String>,writer: &mut R) -> OkpResult<KdbxSaved>{
    let backup_file = open_backup_file(backup_file_name.clone());
    let response = match backup_file {
        Some(mut bf_writer) => {
            let r = match db_service::save_kdbx_to_writer(&mut bf_writer, &db_key) {
                Ok(r) => {
                    let rewind_r = bf_writer.sync_all().and(bf_writer.rewind());
                    log::debug!("Syncing and rewinding of backup file result {:?}", rewind_r);
                    rewind_r?;
                    
                    //let _n = std::io::copy(&mut bf_writer, &mut writer);
                    std::io::copy(&mut bf_writer, writer)?;

                    db_service::calculate_and_set_db_file_checksum(&db_key, &mut bf_writer)?;

                    log::debug!("New hash for checksum is done and set");

                    #[cfg(target_os = "ios")]
                    {
                        // iOS specific copying of a database when we save a  database if this db is used in Autofill extension
                        crate::ios::app_group::copy_files_to_app_group_on_save_or_read(&db_key);
                    }

                    r
                }
                Err(e) => return Err(error::Error::UnexpectedError(format!("{}",e))),
            };
            r
        }
        None => {
            // This is not used. Will this ever happen?. Need to find use case where we do not have backup file
            log::warn!("No backup file is not found and writting to the db file directly");
            // TODO: Call verify checksum here using writer "db_service::verify_db_file_checksum"
            db_service::save_kdbx_to_writer(writer, &db_key)?
        }
    };

    Ok(response)
}

/*

fn internal_read_kdbx(file: &mut File, json_args: &str) -> OkpResult<db_service::KdbxLoaded> {
    let CommandArg::OpenDbArg {
        db_file_name,
        password,
        key_file_name,
    } = serde_json::from_str(json_args)?
    else {
        return Err(OkpError::UnexpectedError(format!(
            "Argument 'json_args' {:?} parsing failed for readkdbx api call",
            json_args
        )));
    };

    let file_name = AppState::shared().uri_to_file_name(&db_file_name);

    debug!(
        "File name from db_file_name {} is {} ",
        &db_file_name, &file_name
    );

    // TODO Set the backup file's modified time to the same as the file in 'db_file_name'
    // We are printing this so that we can check these in devices before using this concept instead
    // of checksum in matching_db_reader_backup_exists
    let info = AppState::shared().uri_to_file_info(&db_file_name);
    debug!("File info of db-key {}, is {:?}", &db_file_name, info);


    // First we read the db file
    let kdbx_loaded = db_service::read_kdbx(
        file,
        &db_file_name,
        password.as_deref(),
        key_file_name.as_deref(),
        Some(&file_name),
    )?;

    debug!("internal_read_kdbx kdbx_loaded  is {:?}", &kdbx_loaded);

    let read_db_checksum = db_service::db_checksum_hash(&db_file_name)?;

    // Note:  The db file 'file' object is read two times
    // 1. in 'read_kdbx' call
    // 2. std::io::copy call

    // Create backup only if there is no backup for this db with the matching checksum
    if matching_backup_exists(&db_file_name, read_db_checksum)
        .ok()
        .flatten()
        .is_none()
    {
        let backup_file_name = backup::generate_backup_history_file_name(&db_file_name, &file_name);
        let mut backup_file = open_backup_file(backup_file_name.clone())
            .ok_or(OkpError::DataError("Opening backup file failed"))?;

        // Ensure that we are at the begining of the db file stream
        let _rr = file.rewind();

        // Copy from db file to backup file
        std::io::copy(file, &mut backup_file)
            .and(backup_file.sync_all())
            .and(backup_file.rewind())?;

        if let Some(mtime) = info.map(|f| f.last_modified).flatten() {
            let mtime = mtime/1000; // milli to seconds
            // backup_file_name unwrap will not fail as we have used this path in the previous call
            // in open_backup_file
            let bp = backup_file_name.unwrap();

            debug!("Before setting mtime {:?}", Path::new(&bp).metadata());

            let r = filetime::set_file_mtime(&bp, filetime::FileTime::from_unix_time(mtime, 0));

            debug!("Setting modified time of backup file status is {:?}",r);
            debug!("After setting mtime {:?}", Path::new(&bp).metadata());
        }

        debug!("internal_read_kdbx copied to backup file and synced");
    } else {
        debug!("Backup file already exists for this db");
    }

    AppState::shared().add_recent_db_use_info(&db_file_name);

    #[cfg(target_os = "ios")]
    {
        // iOS specific copying when we read a database if this db is used in Autofill extension
        crate::ios::app_group::copy_files_to_app_group_on_save_or_read(&db_file_name);
    }

    Ok(kdbx_loaded)
}


*/

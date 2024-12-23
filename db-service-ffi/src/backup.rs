use filetime::FileTime;
use onekeepass_core::db_service::{self, service_util};
use onekeepass_core::util::string_to_simple_hash;
use std::fs::{self, DirEntry, Metadata};
use std::io::{Read, Seek};
use std::path::{Path, PathBuf};

use log::debug;

use crate::{app_state::AppState, util::create_sub_dir_path};
use crate::{util, OkpError, OkpResult};

// Returns the full path of the backup file name
// The args 'full_file_uri_str' is the db_key and 'kdbx_file_name' is just the file name part
pub(crate) fn generate_backup_history_file_name(
    full_file_uri_str: &str,
    kdbx_file_name: &str,
) -> Option<String> {
    if kdbx_file_name.trim().is_empty() {
        return None;
    }

    let file_hist_root = backup_file_history_root(full_file_uri_str);

    let fname_no_extension = kdbx_file_name
        .strip_suffix(".kdbx")
        .map_or(kdbx_file_name, |s| s);

    let secs = format!("{}", service_util::now_utc_seconds());

    // The backup_file_name will be of form "MyPassword_10084644638414928086.kdbx" for
    // the original file name "MyPassword.kdbx" where 10084644638414928086 is the seconds from  'now' call
    let backup_file_name = vec![fname_no_extension, "_", &secs, ".kdbx"].join("");

    //debug!("backup_file_name generated is {}", backup_file_name);

    // Note: We should not use any explicit /  like .join("/") while joining components
    file_hist_root
        .join(backup_file_name)
        .to_str()
        .map(|s| s.to_string())
}

pub fn generate_and_open_backup_file(db_key: &str, kdbx_file_name: &str) -> OkpResult<fs::File> {
    let backup_file_path = generate_backup_history_file_name(db_key, kdbx_file_name);

    let bk_file_name = backup_file_path.ok_or(OkpError::DataError("Opening backup file failed"))?;

    //debug!("Creating new backup file {} and going to create File object", &bk_file_name);

    let file = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .open(bk_file_name)?;

    Ok(file)
}

// Gets the latest the full path name of backup file if available. Otherwise new backup file name is generated
pub(crate) fn latest_or_generate_backup_history_file_name(
    full_file_uri_str: &str,
    kdbx_file_name: &str,
) -> Option<String> {
    latest_backup_file_path(full_file_uri_str).map_or_else(
        || generate_backup_history_file_name(full_file_uri_str, kdbx_file_name),
        |p| Some(p.to_string_lossy().to_string()),
    )
}

// Deletes a particular backup file
// Arg db_key is required to determine db file backup history root
// db_key is typically 'full_file_uri_str'
// Arg 'full_backup_file_name' give the back file to be deletted
pub(crate) fn remove_backup_history_file(db_key: &str, full_backup_file_name: &str) {
    let file_hist_root = backup_file_history_root(db_key);

    // debug!("Removing backup file {}", &full_backup_file_name);

    // Remove this backup file and remove the backup dir for this 'full_file_uri_str' if the dir is empty
    let r = fs::remove_file(full_backup_file_name)
        .and_then(|_| fs::read_dir(&file_hist_root))
        .and_then(|d| Ok(d.count()));
    if let Ok(c) = r {
        if c == 0 {
            let _r = fs::remove_dir(&file_hist_root);
        }
    }

    // debug!("Backup dir for this full uri {}  exists {}",&db_key,&file_hist_root.exists());
}

// Deletes all backup of the files found for this full uri
pub(crate) fn delete_backup_history_dir(db_key: &str) {
    let file_hist_root = backup_file_history_root(db_key);
    let _r = fs::remove_dir_all(&file_hist_root);
    
    // debug!("Deleted all files under root {:?} with status {:?}",&file_hist_root, &r);
}

pub(crate) fn prune_backup_history_files(db_key: &str) {
    let limit = AppState::backup_history_count() as usize;
    let file_hist_root = backup_file_history_root(db_key);
    let mut buffer: Vec<(DirEntry, i64)> = list_of_files_with_modified_times(&file_hist_root);

    let files_count = buffer.len();

    // (files_count - limit) will panic if it results in -ve number as both are usize

    // debug!(
    //     "History files count {} , limit {} and  excess {}",
    //     &files_count, limit, (files_count - limit)
    // );

    if files_count > limit {
        // debug!( "History files count {} exceeded the limit {} ", &files_count, limit);
        
        buffer.sort_by_key(|k| k.1);

        // (files_count - limit) will panic if it results in -ve number as both are usize
        // The files_count > limit check ensures that this does not happen
        let excess = files_count - limit;

        for (file, _) in &buffer[0..excess] {
            let _r = fs::remove_file(file.path());
            // debug!("Removing file {:?} and the result is {:?} ",&file.path(),&r);
        }
    }
}

// Gets the latest backup file path for this uri.
// The files are sorted based on the last modified time and the recent one is picked
pub(crate) fn latest_backup_file_path(full_file_uri_str: &str) -> Option<PathBuf> {
    let file_hist_root = backup_file_history_root(full_file_uri_str);
    let buffer: Vec<(DirEntry, i64)> = list_of_files_with_modified_times(&file_hist_root);
    // Find the file with the latest modified time
    buffer.iter().max_by_key(|k| k.1).map(|e| e.0.path())
}

#[inline]
pub(crate) fn latest_backup_full_file_name(full_file_uri_str: &str) -> Option<String> {
    latest_backup_file_path(full_file_uri_str).map(|p| p.to_string_lossy().to_string())
}

// Checks whether the last backup has the same checksum as the db file that is opened
pub(crate) fn matching_db_reader_backup_exists(
    full_file_uri_str: &str,
    db_file: &mut fs::File,
) -> OkpResult<Option<PathBuf>> {
    let cksum1 = db_service::calculate_db_file_checksum(db_file)?;

    if let Some(p) = latest_backup_file_path(full_file_uri_str) {
        let mut f = fs::File::open(&p)?;
        // IMPORATNT Assumption: after reading bytes for checksum calc, the 'db_file' stream is
        // positioned to its original pos
        let cksum2 = db_service::calculate_db_file_checksum(&mut f)?;
        if cksum1 == cksum2 {
            return Ok(Some(p));
        }
    }
    Ok(None)
}

// Checks whether the last backup has the same checksum as the incoming checksum_hash
pub(crate) fn matching_backup_exists(
    full_file_uri_str: &str,
    checksum_hash: Vec<u8>,
) -> OkpResult<Option<PathBuf>> {
    if let Some(bkp_file_path) = latest_backup_file_path(full_file_uri_str) {
        let mut bkp_file = fs::File::open(&bkp_file_path)?;
        let backup_cksum = db_service::calculate_db_file_checksum(&mut bkp_file)?;
        if backup_cksum == checksum_hash {
            return Ok(Some(bkp_file_path));
        }
    }
    Ok(None)
}

// Forms the backup history root for this db file uri and returns
fn backup_file_history_root(db_key: &str) -> PathBuf {
    let bk_dir_root = string_to_simple_hash(db_key).to_string();
    // Creates a sub dir with the 'bk_dir_root' (hashed string) if required
    // e.g /.../Documents/backups/history/10084644638414928086 where 10084644638414928086 is the hash 'bk_dir_root'
    let file_hist_root = create_sub_dir_path(&AppState::backup_history_dir_path(), &bk_dir_root);
    file_hist_root
}

// Gets all backup history files found under the historty root of a db file 'file_hist_root'
fn list_of_files_with_modified_times<P: AsRef<Path>>(file_hist_root: P) -> Vec<(DirEntry, i64)> {
    let mut buffer: Vec<(DirEntry, i64)> = vec![];
    if let Ok(entries) = fs::read_dir(file_hist_root) {
        for entry in entries {
            if let Ok(e) = entry {
                if e.path().is_file() {
                    if let Some(t) = e
                        .metadata()
                        .iter()
                        .map(|m| FileTime::from_last_modification_time(m).unix_seconds())
                        .next()
                    {
                        buffer.push((e, t));
                    }
                }
            }
        }
    }
    buffer
}

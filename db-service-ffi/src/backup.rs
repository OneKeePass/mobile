use filetime::FileTime;
use onekeepass_core::db_service::{self, service_util};
use onekeepass_core::util::string_to_simple_hash;
use std::fs::{self, DirEntry, Metadata};
use std::io::{Read, Seek};
use std::path::{Path, PathBuf};

use log::debug;

use crate::{app_state::AppState, util::create_sub_dir_path};
use crate::{util, OkpResult};

// Returns the full path of the backup file name
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

    debug!("backup_file_name generated is {}", backup_file_name);
    // Note: We should not use any explicit /  like .join("/") while joining components
    file_hist_root
        .join(backup_file_name)
        .to_str()
        .map(|s| s.to_string())
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
// Arg full_file_uri_str is required to determine db file backup history root
// Arg 'full_backup_file_name' give the back file to be deletted
pub(crate) fn remove_backup_history_file(full_file_uri_str: &str, full_backup_file_name: &str) {
    // let full_file_name_hash = string_to_simple_hash(full_file_uri_str).to_string();
    // let file_hist_root =
    //     create_sub_dir_path(&AppState::backup_history_dir_path(), &full_file_name_hash);

    let file_hist_root = backup_file_history_root(full_file_uri_str);
    debug!("Removing backup file {}", &full_backup_file_name);

    // Remove this backup file and remove the backup dir for this 'full_file_uri_str' if the dir is empty
    let r = fs::remove_file(full_backup_file_name)
        .and_then(|_| fs::read_dir(&file_hist_root))
        .and_then(|d| Ok(d.count()));
    if let Ok(c) = r {
        if c == 0 {
            let _r = fs::remove_dir(&file_hist_root);
        }
    }

    debug!(
        "Backup dir for this full uri {}  exists {}",
        &full_file_uri_str,
        &file_hist_root.exists()
    );
}

// Deletes all backup of the files found for this full uri
pub(crate) fn delete_backup_history_dir(full_file_uri_str: &str) {
    // let full_file_name_hash = string_to_simple_hash(full_file_uri_str).to_string();

    // // Creates a sub dir with the full file uri hash if required
    // // e.g /.../Documents/backups/10084644638414928086 where 10084644638414928086 is the hash 'full_file_name_hash'
    // let file_hist_root =
    //     create_sub_dir_path(&AppState::backup_history_dir_path(), &full_file_name_hash);

    let file_hist_root = backup_file_history_root(full_file_uri_str);
    let r = fs::remove_dir_all(&file_hist_root); //remove_dir_contents(file_hist_root);
    debug!(
        "Deleted all files under root {:?} with status {:?}",
        &file_hist_root, &r
    );
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
fn backup_file_history_root(full_file_uri_str: &str) -> PathBuf {
    let full_file_name_hash = string_to_simple_hash(full_file_uri_str).to_string();
    // Creates a sub dir with the full file uri hash if required
    // e.g /.../Documents/backups/history/10084644638414928086 where 10084644638414928086 is the hash 'full_file_name_hash'
    let file_hist_root =
        create_sub_dir_path(&AppState::backup_history_dir_path(), &full_file_name_hash);
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

use log::debug;
use onekeepass_core::db_service::service_util::{self, string_to_simple_hash};
use onekeepass_core::error::Result;
use serde_json::de;
use std::fs::{self, File};
use std::os::unix::io::FromRawFd;
use std::path::{Path, PathBuf};

use crate::app_state::AppState;
use crate::file_util::KeyFileInfo;

pub unsafe fn get_file_from_fd(fd: u64) -> File {
    File::from_raw_fd(fd as i32)
}

// Gets the unix file path from the platform specif file uri which may be url encoded
pub fn url_to_unix_file_name(url_str: &str) -> String {
    // e.g uri file:///Users/jeyasankar/Library/Developer/CoreSimulator/Devices/A45B3252-1AA4-4D50-9E6E-89AB1E873B1F/data/Containers/Shared/AppGroup/6CFFA9FC-169B-482E-A817-9C0D2A6F5241/File%20Provider%20Storage/TJ-fixit.kdbx
    // Note the encoding in the uri
    match urlencoding::decode(url_str) {
        Ok(s) => {
            if let Some(f) = s.strip_prefix("file://") {
                f.into()
            } else {
                s.into()
            }
        }
        Err(_e) => url_str.into(),
    }
}

pub fn full_path_str(dir_path: &str, file_name: &str) -> String {
    let full_name = if dir_path.ends_with("/") {
        [dir_path.strip_suffix("/").map_or("", |x| x), file_name].join("/")
    } else {
        [dir_path, file_name].join("/")
    };
    url_to_unix_file_name(&full_name)
}

// Not used. Instead iOS fn is used through callback service
// Extracts the file name from a full file path url
// In case of any error in extracting, the full path itself returned for now
pub fn file_name_from_full_path(file_full_path: &str) -> String {
    if let Ok(s) = urlencoding::decode(file_full_path) {
        let v: Vec<&str> = s.split("/").collect();
        let s1 = v.last().map_or(file_full_path, |c| c);
        if s1.contains(":") {
            s1.split_once(":")
                .map_or(file_full_path, |c| c.1)
                .to_string()
        } else {
            s1.into()
        }
    } else {
        file_full_path.into()
    }
}

// full_file_uri_str is the db_key and may start with "File:" or "Content:"
// kdbx_file_name is just file name and is not absolute one
// For now only hash str formed using 'full_file_uri_str' is appended to the
// kdbx_file_name before prefix .kdbx. See the example
/*
pub fn generate_backup_file_name(full_file_uri_str: &str, kdbx_file_name: &str) -> Option<String> {
    if kdbx_file_name.trim().is_empty() {
        return None;
    }
    let n = string_to_simple_hash(full_file_uri_str).to_string();
    let fname_no_extension = kdbx_file_name
        .strip_suffix(".kdbx")
        .map_or(kdbx_file_name, |s| s);

    // The backup_file_name will be of form "MyPassword_10084644638414928086.kdbx" for
    // the original file name "MyPassword.kdbx" where 10084644638414928086 is a hash of full path uri str
    let backup_file_name = vec![fname_no_extension, "_", &n, ".kdbx"].join("");

    debug!("backup_file_name generated is {}", backup_file_name);
    // Note: We should not use any explicit /  like .join("/") while joining components
    AppState::backup_dir_path()
        .join(backup_file_name)
        .to_str()
        .map(|s| s.to_string())
}
*/

// Returns the absolute path for the db export call
pub fn form_export_file_name(kdbx_file_name: &str) -> Option<String> {
    if kdbx_file_name.trim().is_empty() {
        return None;
    }
    AppState::export_data_dir_path()
        .join(kdbx_file_name)
        .to_str()
        .map(|s| s.to_string())
}

// Gets only the files (full path) found in a dir are returned
pub fn list_dir_files<P: AsRef<Path>>(path: P) -> Vec<String> {
    let mut bfiles: Vec<String> = vec![];
    if let Ok(entries) = fs::read_dir(path) {
        for entry in entries {
            if let Ok(e) = entry {
                let path = e.path();
                if path.is_file() {
                    if let Some(s) = path.to_str() {
                        bfiles.push(s.into());
                    }
                }
            }
        }
    }
    bfiles
}

// Gets all files and dirs found in a dir are returned
pub fn list_dir_entries(path: &Path) -> Vec<String> {
    let mut bfiles: Vec<String> = vec![];
    if let Ok(entries) = fs::read_dir(path) {
        for entry in entries {
            if let Ok(e) = entry {
                if let Some(s) = e.path().to_str() {
                    bfiles.push(s.into());
                }
            }
        }
    }
    bfiles
}

// pub fn list_backup_files() -> Vec<String> {
//     list_dir_files(&AppState::backup_dir_path())
// }

// pub fn delete_backup_file(full_file_uri_str: &str, kdbx_file_name: &str) {
//     if let Some(bf) = generate_backup_history_file_name(full_file_uri_str, kdbx_file_name) {
//         log::debug!(
//             "Removing backup file {} for the uri {}",
//             bf,
//             full_file_uri_str
//         );
//         let r = fs::remove_file(&bf);
//         log::debug!("Delete backup file {} result {:?}", bf, r);
//     }
// }

// Called to delete only files found in a dir path
// The arg 'dir_path' is expected to be an existing directory path
// No sub dir is removed and also we do not recursively visit the sub dirs and remove files of the sub dirs
pub fn remove_files<P: AsRef<Path>>(dir_path: P) -> Result<()> {
    match fs::read_dir(dir_path) {
        Ok(contents) => {
            for entry in contents {
                if let Ok(dir_entry) = entry {
                    if dir_entry.file_type().map_or(false, |v| v.is_file()) {
                        let _ = fs::remove_file(dir_entry.path());
                    }
                }
            }
        }
        Err(e) => {
            log::error!("Error in remove_files {}", &e);
        }
    }
    Ok(())
}

// Recursively removes only the content of a dir including sub dir
pub fn remove_dir_contents<P: AsRef<Path>>(path: P) -> Result<()> {
    for entry in fs::read_dir(path)? {
        let entry = entry?;
        let path = entry.path();

        if entry.file_type()?.is_dir() {
            remove_dir_contents(&path)?;
            fs::remove_dir(path)?;
        } else {
            fs::remove_file(path)?;
        }
    }
    Ok(())
}

pub fn clean_export_data_dir() -> Result<()> {
    remove_files(AppState::export_data_dir_path())
    //remove_dir_contents(AppState::export_data_dir_path())
}

// TODO: Merge create_sub_dir,create_sub_dirs,create_sub_dir_path
// Called to create sub dir only if the root is present
// We are using &str instead of &Path due to the use of 'url_to_unix_file_name'
pub fn create_sub_dir(root_dir: &str, sub: &str) -> PathBuf {
    // TODO: calling url_to_unix_file_name appears to be redundant and need to be removed after review
    let root = url_to_unix_file_name(root_dir);

    let mut final_full_path_dir = Path::new(&root).to_path_buf();
    let full_path_dir = Path::new(&root).join(sub);

    if !full_path_dir.exists() {
        if let Err(e) = std::fs::create_dir_all(&full_path_dir) {
            // This should not happen!
            log::error!(
                "Directory at {} creation failed {:?}",
                &full_path_dir.display(),
                e
            );
        } else {
            // As fallback cache_dir_path will be at root itself as we cannot create export_dir
            final_full_path_dir = full_path_dir;
        }
    } else {
        final_full_path_dir = full_path_dir;
    }
    final_full_path_dir
}

// Creates the sub dir under the given root and returns the full path
pub fn create_sub_dir_path<P: AsRef<Path>>(root_dir: P, sub: &str) -> PathBuf {
    // Initialize with the root_dir itself
    let mut final_full_path_dir = Path::new(root_dir.as_ref()).to_path_buf();

    let full_path_dir = Path::new(root_dir.as_ref()).join(sub);

    if !full_path_dir.exists() {
        if let Err(e) = std::fs::create_dir_all(&full_path_dir) {
            // This should not happen!
            log::error!(
                "Directory at {} creation failed {:?}",
                &full_path_dir.display(),
                e
            );
        } else {
            // As fallback use the full_path_dir of root_dir
            final_full_path_dir = full_path_dir;
        }
    } else {
        final_full_path_dir = full_path_dir;
    }
    final_full_path_dir
}

pub fn create_sub_dirs<P: AsRef<Path>>(root_dir: P, sub_dirs: Vec<&str>) -> PathBuf {
    // TODO: calling url_to_unix_file_name appears to be redundant and need to be removed after review
    let root = url_to_unix_file_name(&root_dir.as_ref().to_string_lossy());

    // Initializes the final path with root_dir
    let mut final_full_path_dir = Path::new(&root).to_path_buf();

    let sub_path: PathBuf = sub_dirs.iter().collect();

    let full_path_dir = final_full_path_dir.join(sub_path);

    if !full_path_dir.exists() {
        if let Err(e) = std::fs::create_dir_all(&full_path_dir) {
            // This should not happen!
            log::error!(
                "Directory at {} creation failed {:?}",
                &full_path_dir.display(),
                e
            );
        } else {
            // As fallback cache_dir_path will be at root itself as we cannot create export_dir
            final_full_path_dir = full_path_dir;
        }
    } else {
        final_full_path_dir = full_path_dir;
    }
    final_full_path_dir
}

pub fn list_key_files() -> Vec<KeyFileInfo> {
    let path = &AppState::key_files_dir_path();
    let mut bfiles: Vec<KeyFileInfo> = vec![];
    if let Ok(entries) = fs::read_dir(path) {
        for entry in entries {
            if let Ok(e) = entry {
                if let (full_file_name, Some(file_name)) = (
                    e.path().as_os_str().to_string_lossy().to_string(),
                    e.path()
                        .file_name()
                        .map(|s| s.to_string_lossy().to_string()),
                ) {
                    bfiles.push(KeyFileInfo {
                        full_file_name,
                        file_name,
                        file_size: None,
                    });
                }
            }
        }
    }
    bfiles
}

// key_file_name_component is just the file name and not the full uri
pub fn delete_key_file(key_file_name_component: &str) {
    let path = &AppState::key_files_dir_path().join(key_file_name_component);
    let r = fs::remove_file(&path);
    log::debug!("Delete key file  {:?} result {:?}", &path, r);
}

#[inline]
pub fn current_locale_language() -> String {
    // "en-US" language+region
    // We use the language part only to locate translation json files
    let lng = sys_locale::get_locale().unwrap_or_else(|| String::from("en"));

    // Returns the language id ( two letters)
    lng.split("-")
        .map(|s| s.to_string())
        .next()
        .unwrap_or_else(|| String::from("en"))
}

////

/*
fn copy_dir_all(src: impl AsRef<Path>, dst: impl AsRef<Path>) -> io::Result<()> {
    fs::create_dir_all(&dst)?;
    for entry in fs::read_dir(src)? {
        let entry = entry?;
        let ty = entry.file_type()?;
        if ty.is_dir() {
            copy_dir_all(entry.path(), dst.as_ref().join(entry.file_name()))?;
        } else {
            fs::copy(entry.path(), dst.as_ref().join(entry.file_name()))?;
        }
    }
    Ok(())
}
*/

// Copies all files found under a source dir to the target directory
// Sub directories are ignored

pub fn copy_files<P: AsRef<Path>, Q: AsRef<Path>>(src_dir: P, target_dir: Q) {
    if let Ok(entries) = fs::read_dir(src_dir) {
        for entry in entries {
            if let Ok(e) = entry {
                //  we only copy key files and sub dir copying done
                if let Ok(ft) = e.file_type() {
                    if ft.is_file() {
                        match fs::copy(e.path(), target_dir.as_ref().join(e.file_name())) {
                            Ok(_) => {}
                            Err(e) => {
                                log::error!(
                                    "Copying a file to dir {:?} failed with error {} ",
                                    &target_dir.as_ref(),
                                    e
                                );
                            }
                        }
                    }
                }
            }
        }
    }
}

pub fn is_dir_empty<P: AsRef<Path>>(parent_dir: P) -> bool {
    let mut cnt = 0;
    if let Ok(entries) = parent_dir.as_ref().read_dir() {
        // In simulator, macOS creates ".DS_Store" file and we need to ignore
        // this file to check whether the dir is empty or not
        // In iOS, this meta file is not created
        for e in entries {
            if let Ok(v) = e {
                debug!("File name under data dir is {:?}", v.file_name());
                if v.file_name() == ".DS_Store" {
                    continue;
                } else {
                    cnt += 1;
                    break;
                }
            }
        }
    }
    cnt == 0
}

/*
// Returns the full path of the backup file name
pub fn generate_backup_history_file_name(
    full_file_uri_str: &str,
    kdbx_file_name: &str,
) -> Option<String> {
    if kdbx_file_name.trim().is_empty() {
        return None;
    }

    let full_file_name_hash = string_to_simple_hash(full_file_uri_str).to_string();

    // Creates a sub dir with the full file uri hash if required
    // e.g /.../Documents/backups/10084644638414928086 where 10084644638414928086 is the hash 'full_file_name_hash'
    let file_hist_root =
        create_sub_dir_path(&AppState::backup_history_dir_path(), &full_file_name_hash);

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

pub fn remove_backup_history_file(full_file_uri_str: &str, full_backup_file_name: &str) {
    let full_file_name_hash = string_to_simple_hash(full_file_uri_str).to_string();
    let file_hist_root =
        create_sub_dir_path(&AppState::backup_history_dir_path(), &full_file_name_hash);

    debug!("Removing backup file {}",&full_backup_file_name);

    // Remove this backup file and remove the backup dir for this 'full_file_uri_str' if the dir is empty
    let r = fs::remove_file(full_backup_file_name)
        .and_then(|_| fs::read_dir(&file_hist_root))
        .and_then(|d| Ok(d.count()));
    if let Ok(c) = r {
        if c == 0 {
            let _r = fs::remove_dir(&file_hist_root);
        }
    }

    debug!("Backup dir for this full uri {}  exists {}", &full_file_uri_str,&file_hist_root.exists());

}


*/

#[cfg(test)]
mod tests {

    #[test]
    fn verify1() {
        println!("A test module");
    }
}

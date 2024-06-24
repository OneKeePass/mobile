use log::debug;
use onekeepass_core::db_service::string_to_simple_hash;
use onekeepass_core::error::Result;
use std::fs::{self, File};
use std::os::unix::io::FromRawFd;
use std::path::{Path, PathBuf};

use crate::app_state::AppState;
use crate::KeyFileInfo;

pub unsafe fn get_file_from_fd(fd: u64) -> File {
    File::from_raw_fd(fd as i32)
}

pub fn url_to_unix_file_name(url_str: &str) -> String {
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

// kdbx_file_name is just file name and is not absolute one
// For now only hash str formed full_file_uri_str is appended to the kdbx_file_name before prefix .kdbx
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
    // Note: We should not use any explicit /  like .join("/") while joing components
    AppState::global()
        .backup_dir_path
        .join(backup_file_name)
        .to_str()
        .map(|s| s.to_string())
}

pub fn form_export_file_name(kdbx_file_name: &str) -> Option<String> {
    if kdbx_file_name.trim().is_empty() {
        return None;
    }
    AppState::global()
        .export_data_dir_path
        .join(kdbx_file_name)
        .to_str()
        .map(|s| s.to_string())
}

pub fn list_dir_files(path: &Path) -> Vec<String> {
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

pub fn list_backup_files() -> Vec<String> {
    list_dir_files(&AppState::global().backup_dir_path)
}

pub fn delete_backup_file(full_file_uri_str: &str, kdbx_file_name: &str) {
    if let Some(bf) = generate_backup_file_name(full_file_uri_str, kdbx_file_name) {
        log::debug!(
            "Removing backup file {} for the uri {}",
            bf,
            full_file_uri_str
        );
        let r = fs::remove_file(&bf);
        log::debug!("Delete backup file {} result {:?}", bf, r);
    }
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
    remove_dir_contents(&AppState::global().export_data_dir_path)
}

// Called to create sub dir only if the root is present
pub fn create_sub_dir(root_dir: &str, sub: &str) -> PathBuf {
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

pub fn list_key_files() -> Vec<KeyFileInfo> {
    let path = &AppState::global().key_files_dir_path;
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
    let path = &AppState::global()
        .key_files_dir_path
        .join(key_file_name_component);
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

#[cfg(test)]
mod tests {

    #[test]
    fn verify1() {
        println!("A test module");
    }
}

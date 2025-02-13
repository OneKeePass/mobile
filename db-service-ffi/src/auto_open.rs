use onekeepass_core::error::{self, Error, Result};
use serde::{Deserialize, Serialize};

use crate::app_state::AppState;

#[derive(Default, Debug, Deserialize)]
pub(crate) struct AutoOpenProperties {
    // This is the value from "URL" entry field
    url_field_value: Option<String>,
    
    // This is the value from "UserName" entry field
    key_file_path: Option<String>,

    // This is the value from "IfDevice" entry field
    device_if_val: Option<String>,
}

#[derive(Default, Debug, Serialize)]
pub(crate) struct AutoOpenPropertiesResolved {
    // This is the full db file path that is used as db_key
    db_key: Option<String>,

    // The extracted db file name part - e.g Test.kdbx
    db_file_name: Option<String>,

    // This is the app's local full key file path
    // It will have some value if the AutoOpenProperties.key_file_path is having some value
    // and the key file is already uploaded to the apps' key files dir
    key_file_full_path: Option<String>,

    // The extracted key file name part only
    key_file_name: Option<String>,

    can_open: bool,
}

const PATH_SEPARATORS: [char; 2] = ['/', '\\'];

impl AutoOpenProperties {
    pub(crate) fn resolve(&self) -> Result<AutoOpenPropertiesResolved> {
        let mut out = AutoOpenPropertiesResolved::default();

        // The child database path is parsed only if it is not an empty string
        if let Some(ref db_v) = self.url_field_value.as_ref().map(|v| v.trim().to_string()) {
            if !db_v.is_empty() {
                let file_name = extract_file_name_part(&db_v).ok_or(Error::AutoOpenError(
                    "Not able to get the KDBX file name from the url".into(),
                ))?;
                out.db_file_name = Some(file_name.into());
                
                out.db_key = AppState::find_db_key(file_name);
            }
        }

        // The child database key file path is parsed only if it is not an empty string
        if let Some(ref kf_v) = self.key_file_path.as_ref().map(|v| v.trim().to_string()) {
            if !kf_v.is_empty() {
                let key_file_name =
                    extract_key_file_name_part(&kf_v).ok_or(Error::AutoOpenError(
                        "Not able to get the Key file name from the UserName field".into(),
                    ))?;

                out.key_file_name = Some(key_file_name.to_string());

                let path = AppState::key_files_dir_path().join(key_file_name);

                out.key_file_full_path = if path.exists() {
                    path.to_str().map(|s| s.to_string())
                } else {
                    None
                };
            }
        }

        Ok(out)
    }
}

fn extract_file_name_part<'a>(path: &'a str) -> Option<&'a str> {
    // Here are some examples of the incoming file path passed in 'url_field_value'
    // kdbx://{DB_DIR}/f1/PasswordsUsesKeyFile2.kdbx
    // "kdbx://{DB_DIR}\f1\PasswordsUsesKeyFile2.kdbx"
    // kdbx://./PasswordsUsesKeyFile2.kdbx
    // kdbx://PasswordsUsesKeyFile2.kdbx

    path.rsplit_once(PATH_SEPARATORS)
        .map_or(None, |(_, file_name)| {
            if !file_name.is_empty() {
                Some(file_name)
            } else {
                None
            }
        })
}

fn extract_key_file_name_part<'a>(path: &'a str) -> Option<&'a str> {
    // Here are some examples of the incoming key file path passed in 'key_file_path'
    // {DB_DIR}/my_keyfile.keyx
    // ./some_dir/my_keyfile.keyx
    // ./my_keyfile.keyx
    // my_keyfile.keyx
    
    if path.contains(PATH_SEPARATORS) {
        extract_file_name_part(path)
    } else {
        if !path.is_empty() {
            Some(path)
        } else {
            None
        }
    }
}

/*
#[test]
    fn verify_part1() {
        let s = "kdbx://{DB_DIR}/f1/PasswordsUsesKeyFile2.kdbx";

        let pat:[char; 2] = ['/', '\\'];
        let v = s.rsplit_once(pat);

        println!("v is {:?}",&v);

        let s = "kdbx://{DB_DIR}\\f1\\PasswordsUsesKeyFile2.kdbx";
        let v = s.rsplit_once(pat);

        println!("v is {:?}",&v);

        let s = "kdbx://.\\PasswordsUsesKeyFile2.kdbx";
        let v = s.rsplit_once(pat);
        println!("v is {:?}",&v);

        let s = "kdbx://./PasswordsUsesKeyFile2.kdbx";
        let v = s.rsplit_once(pat);
        println!("v is {:?}",&v);

        let s = "kdbx://PasswordsUsesKeyFile2.kdbx";
        let v = s.rsplit_once(pat);
        println!("v is {:?}",&v);

        let s = "kdbx://";
        let v = s.rsplit_once(pat);
        println!("v is {:?}",&v);
    }

*/

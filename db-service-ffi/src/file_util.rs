pub(crate) use std::fs::File;

use log::debug;
use serde::{Deserialize, Serialize};

use crate::{app_state::AppState, commands::full_path_file_to_create, udl_types::FileArgs, util, OkpError, OkpResult};

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct KeyFileInfo {
    pub full_file_name: String,
    pub file_name: String,
    pub file_size: Option<i64>,
}


pub(crate) struct OpenedFile {
    pub(crate) file: File,
    pub(crate) file_name: String,
    pub(crate) full_file_name: String,
}

impl OpenedFile {
    pub(crate) fn open_to_read(file_args: &FileArgs) -> OkpResult<OpenedFile> {
        Self::open(file_args, false)
    }

    fn open_to_create(file_args: &FileArgs) -> OkpResult<OpenedFile> {
        Self::open(file_args, true)
    }

    // Only for iOS, create flag is relevant as file read,write or create is set in Kotlin layer for android
    fn open(file_args: &FileArgs, create: bool) -> OkpResult<OpenedFile> {
        let (file, file_name, full_file_name) = match file_args {
            // For Android
            FileArgs::FileDecriptorWithFullFileName {
                fd,
                file_name,
                full_file_name,
            } => (
                unsafe { util::get_file_from_fd(*fd) },
                file_name.clone(),
                full_file_name.clone(),
            ),
            // For iOS
            FileArgs::FullFileName { full_file_name } => {
                let name = AppState::shared().uri_to_file_name(&full_file_name);
                let ux_file_path = util::url_to_unix_file_name(&full_file_name);

                let file = if create {
                    full_path_file_to_create(&full_file_name)?
                } else {
                    File::open(ux_file_path)?
                };

                (file, name.clone(), full_file_name.clone())
            }
            _ => {
                return Err(OkpError::UnexpectedError(
                    "Unsupported file args passed".into(),
                ))
            }
        };
        let r = OpenedFile {
            file,
            file_name,
            full_file_name,
        };

        Ok(r)
    }
}


#[enum_dispatch::enum_dispatch(PickedFileHandler)]
pub trait HandlePickedFile {
    fn execute(&self, file_args: &FileArgs) -> OkpResult<KeyFileInfo>;
}


// Json string {\"handler\" : \"SftpPrivateKeyFile\"} deserializes to variant PickedFileHandler::SftpPrivateKeyFile

#[derive(Deserialize,Debug)]
#[serde(tag = "handler")] 
#[enum_dispatch::enum_dispatch]
pub enum PickedFileHandler {
    SftpPrivateKeyFile(SftpPrivateKeyFile),
    
    // TDOO: 
    //  Need to add the following variants instead of using 
    // 'copy_picked_key_file' and 'upload_attachment' from udl_functions 
    // Swift/Kotlin calls need to be changed to use 'handle_picked_file' from module udl_uniffi_exports
    
    // DbKeyFile(DbKeyFile),
    // EntryAttachmentFile(EntryAttachmentFile),
}

#[derive(Deserialize,Debug)]
pub struct SftpPrivateKeyFile {}

impl HandlePickedFile for SftpPrivateKeyFile {
    fn execute(&self, file_args: &FileArgs) -> OkpResult<KeyFileInfo> {
        let OpenedFile {
            mut file,
            file_name,
            ..
        } = OpenedFile::open_to_read(file_args)?;

        let file_full_path = AppState::sftp_private_keys_path().join(&file_name);

        // User picked key file is stored in the temp dir and later copied to the 
        // local sftp connection specific path after successful connection
        // let file_full_path = AppState::temp_dir_path().join(&file_name);
        
        debug!(
            "The file_full_path in SftpPrivateKeyFile is {:?}",
            file_full_path
        );

        let mut target_file = File::create(&file_full_path)?;
        std::io::copy(&mut file, &mut target_file).and(target_file.sync_all())?;
        debug!("Copied the private key file {} locally", &file_name);

        let full_file_name = file_full_path.as_os_str().to_string_lossy().to_string();

        Ok(KeyFileInfo {
            full_file_name,
            file_name,
            file_size: None,
        })
    }
}

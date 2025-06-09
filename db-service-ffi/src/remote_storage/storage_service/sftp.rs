use log::{debug, info};
use once_cell::sync::Lazy;
use russh::{
    client::{self, Handle},
    ChannelId,
};

use russh_keys;

use russh;
use russh_sftp::client::SftpSession;
use serde::{Deserialize, Serialize};
use std::{collections::HashMap, sync::Arc};
use tokio::sync::oneshot;
use uuid::Uuid;

use crate::{
    parse_operation_fields_if, receive_from_async_fn,
    remote_storage::callback_service::CallbackServiceProvider, reply_by_async_fn,
};

use onekeepass_core::async_service::async_runtime;
use onekeepass_core::db_service::error::{self, Error, Result};
use onekeepass_core::service_util::system_time_to_seconds;

pub use super::server_connection_config::SftpConnectionConfig;
use super::{
    calls::RemoteStorageOperation,
    filter_entry,
    server_connection_config::{
        ConnectionConfigs, RemoteStorageTypeConfig, RemoteStorageTypeConfigs,
    },
    string_tuple2, string_tuple3, ConnectStatus, RemoteFileMetadata, RemoteReadData,
    RemoteStorageType, ServerDirEntry,
};

macro_rules! reply_by_sftp_async_fn {
    ($fn_name:ident ($($arg1:tt:$arg_type:ty),*),$call:tt ($($arg:expr),*),$channel_ret_val:ty) => {
        reply_by_async_fn!(sftp_connections_store,$fn_name ($($arg1:$arg_type),*),$call ($($arg),*),$channel_ret_val);
    };
}

pub trait CommonCallbackService1 {
    fn sftp_private_key_file_full_path(&self, file_name: &str) -> std::path::PathBuf;
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct Sftp {
    connection_info: Option<SftpConnectionConfig>,
    connection_id: Option<String>,
    parent_dir: Option<String>,
    sub_dir: Option<String>,
    file_path: Option<String>,
    file_name: Option<String>,
}

impl Sftp {
    pub(crate) fn from_parsed_db_key(connection_id: &str, file_path_part: &str) -> Sftp {
        let mut sftp = Sftp::default();

        sftp.file_path = Some(file_path_part.to_string());

        if let Some(parts) = file_path_part.rsplit_once("/") {
            debug!("Sftp Parst of file_path_part are {:?}", &parts);

            let v = if parts.0.is_empty() { "/" } else { parts.0 };
            sftp.parent_dir = Some(v.to_string());
            sftp.connection_id = Some(connection_id.to_string());
            sftp.file_name = Some(parts.1.to_string());
        }
        sftp
    }
}

impl RemoteStorageOperation for Sftp {
    fn connect_and_retrieve_root_dir(&self) -> Result<ConnectStatus> {
        #[allow(unused_parens)]
        let (connection_info) = parse_operation_fields_if!(self, connection_info);
        let c = connection_info.clone();

        receive_from_async_fn!(
            SftpConnection::send_connect_and_retrieve_root_dir(c),
            ConnectStatus
        )?
    }

    fn connect_by_id_and_retrieve_root_dir(&self) -> Result<ConnectStatus> {
        #[allow(unused_parens)]
        let (connection_id) = parse_operation_fields_if!(self, connection_id);

        let c_id = connection_id.clone();

        // Call the async fn in a 'spawn' and wait for the result - Result<Result<RemoteStorageTypeConfig>>)
        receive_from_async_fn!(
            SftpConnection::send_connect_by_id_and_retrieve_root_dir(c_id),
            ConnectStatus
        )?
    }

    fn connect_by_id(&self) -> Result<RemoteStorageTypeConfig> {
        #[allow(unused_parens)]
        let (connection_id) = parse_operation_fields_if!(self, connection_id);

        let c_id = connection_id.clone();

        // Call the async fn in a 'spawn' and wait for the result - Result<Result<RemoteStorageTypeConfig>>)
        let rc = receive_from_async_fn!(
            SftpConnection::send_connect_by_id(c_id),
            RemoteStorageTypeConfig
        )?;

        rc
    }

    // Contents of root dir
    fn list_dir(&self) -> Result<ServerDirEntry> {
        // connection_id should be a valid connection that is completed in an earlier call
        let (connection_id, parent_dir) =
            parse_operation_fields_if!(self, connection_id, parent_dir);

        let (cn, pd) = string_tuple2(&[connection_id, parent_dir]);
        receive_from_async_fn!(SftpConnection::send_list_dir(cn, pd), ServerDirEntry)?
    }

    fn list_sub_dir(&self) -> Result<ServerDirEntry> {
        let (connection_id, parent_dir, sub_dir) =
            parse_operation_fields_if!(self, connection_id, parent_dir, sub_dir);

        debug!(
            "The list_sub_dir fn is called with args {}, {}, {}",
            connection_id, parent_dir, sub_dir
        );

        let (cn, pd, sd) = string_tuple3(&[connection_id, parent_dir, sub_dir]);
        receive_from_async_fn!(
            SftpConnection::send_list_sub_dir(cn, pd, sd),
            ServerDirEntry
        )?
    }

    fn read(&self) -> Result<RemoteReadData> {
        let (connection_id, parent_dir, file_name) =
            parse_operation_fields_if!(self, connection_id, parent_dir, file_name);

        let (cn, pd, name) = string_tuple3(&[connection_id, parent_dir, file_name]);
        receive_from_async_fn!(SftpConnection::send_read(cn, pd, name), RemoteReadData)?
    }

    fn write_file(&self, data: Arc<Vec<u8>>) -> Result<RemoteFileMetadata> {
        let (connection_id, file_path) = parse_operation_fields_if!(self, connection_id, file_path);
        let file_path = file_path.to_string();
        let c_id = connection_id.clone();
        receive_from_async_fn!(
            SftpConnection::send_write_file(c_id, file_path, data),
            RemoteFileMetadata
        )?
    }

    fn create_file(&self, data: Arc<Vec<u8>>) -> Result<RemoteFileMetadata> {
        let (connection_id, file_path) = parse_operation_fields_if!(self, connection_id, file_path);
        let file_path = file_path.to_string();
        let c_id = connection_id.clone();
        receive_from_async_fn!(
            SftpConnection::send_create_file(c_id, file_path, data),
            RemoteFileMetadata
        )?
    }

    fn file_metadata(&self) -> Result<RemoteFileMetadata> {
        let (connection_id, file_path) = parse_operation_fields_if!(self, connection_id, file_path);
        let file_path = file_path.to_string();
        let c_id = connection_id.clone();
        receive_from_async_fn!(
            SftpConnection::send_file_metadta(c_id, file_path),
            RemoteFileMetadata
        )?
    }

    fn remote_storage_configs(&self) -> Result<RemoteStorageTypeConfigs> {
        Ok(ConnectionConfigs::remote_storage_configs(
            RemoteStorageType::Sftp,
        ))
    }

    fn update_config(&self) -> Result<()> {
        #[allow(unused_parens)]
        let (connection_info) = parse_operation_fields_if!(self, connection_info);
        ConnectionConfigs::update_config(RemoteStorageTypeConfig::Sftp(connection_info.clone()))
    }

    fn delete_config(&self) -> Result<()> {
        #[allow(unused_parens)]
        let (connection_id) = parse_operation_fields_if!(self, connection_id);

        let u_id = uuid::Uuid::parse_str(connection_id)?;

        let r = ConnectionConfigs::delete_config_by_id(RemoteStorageType::Sftp, &u_id);
        CallbackServiceProvider::common_callback_service()
            .remote_storage_config_deleted(RemoteStorageType::Sftp, connection_id)?;

        r
    }

    fn file_name(&self) -> Option<&str> {
        self.file_name.as_ref().map(|x| x.as_str())
    }

    fn file_path(&self) -> Option<&str> {
        self.file_path.as_ref().map(|x| x.as_str())
    }
}

//  Exposed functions

//////////

struct Client;

// This macro is to make async fn in traits work with dyn traits
// See https://docs.rs/async-trait/latest/async_trait/
// #[async_trait]
impl client::Handler for Client {
    // our error::Error can also be used
    // Also in the example anyhow::Error was used
    // For now we are using russh::Error directly and used in 'convert_error' fn
    type Error = russh::Error;

    async fn check_server_key(
        &mut self,
        server_public_key: &russh::keys::PublicKey,
    ) -> std::result::Result<bool, Self::Error> {
        info!("check_server_key: {:?}", server_public_key);
        Ok(true)
    }

    async fn data(
        &mut self,
        _channel: ChannelId,
        _data: &[u8],
        _session: &mut client::Session,
    ) -> std::result::Result<(), Self::Error> {
        //info!("data on channel {:?}: {}", channel, data.len());
        Ok(())
    }
}

struct SftpConnection {
    connection_id: Uuid,
    client_handle: Handle<Client>,
}

type SftpConnections = Arc<tokio::sync::Mutex<HashMap<String, SftpConnection>>>;

fn sftp_connections_store() -> &'static SftpConnections {
    static SFTP_CONNECTIONS_STORE: Lazy<SftpConnections> = Lazy::new(Default::default);
    &SFTP_CONNECTIONS_STORE
}

impl SftpConnection {
    // Called when user creates the config first time or when user updates the config
    pub(crate) async fn connect_and_retrieve_root_dir(
        mut connection_info: SftpConnectionConfig,
    ) -> Result<ConnectStatus> {
        connection_info.connection_id =
            ConnectionConfigs::generate_config_id_on_check(connection_info.connection_id); // ConnectionConfigs::generate_config_id_on_check(connection_info);

        let start_dir = connection_info
            .start_dir
            .clone()
            .map_or_else(|| "/".to_string(), |s| s);

        let sftp_connection = Self::connect(&connection_info).await?;

        // private_key_file_name should have a valid file name if we use a private key for auth
        if let Some(ref file_name) = connection_info.private_key_file_name {
            // Need to copy the key file from temp location to permanent location
            CallbackServiceProvider::common_callback_service().sftp_copy_from_temp_key_file(
                &connection_info.connection_id.to_string(),
                file_name,
            )?;
        }

        let dirs = sftp_connection.list_dir(&start_dir).await?;

        // For now we set the start_dir
        connection_info.start_dir = Some(start_dir);

        let store_key = connection_info.connection_id.to_string();

        // Store it for future reference
        let mut connections = sftp_connections_store().lock().await;
        connections.insert(store_key, sftp_connection);

        let conn_status = ConnectStatus {
            connection_id: connection_info.connection_id,
            dir_entries: Some(dirs),
        };

        // Need to add to the configs list
        // TODO: Need to update if the existing config is changed
        ConnectionConfigs::add_config(RemoteStorageTypeConfig::Sftp(connection_info))?;

        Ok(conn_status)
    }

    // Called to create a new remote connection using the connection id and on successful connection
    // the root dir entries are returned
    async fn connect_by_id_and_retrieve_root_dir(connection_id: &str) -> Result<ConnectStatus> {
        // Makes connection to the remote storage and stores the connection in the local static map
        // Note sftp_connections_store().lock() called in this call
        let _r = Self::connect_by_id(connection_id).await?;

        // Previous lock call should have been unlocked by this time. Otherwise deadlock will happen
        let connections = sftp_connections_store().lock().await;

        // A successful connection should be available
        let sftp_connection = connections.get(connection_id).ok_or_else(|| {
            Error::DataError(
                "Previously saved SFTP Connection config is not found in configs for this id",
            )
        })?;

        let dirs = sftp_connection.list_dir("/").await?;

        let u_id = uuid::Uuid::parse_str(connection_id)?;
        let conn_status = ConnectStatus {
            connection_id: u_id,
            dir_entries: Some(dirs),
        };

        Ok(conn_status)
    }

    // Gets the connection config with this id and use that to connect the sftp server if required  and stores
    // that connection for the future use
    async fn connect_by_id(connection_id: &str) -> Result<RemoteStorageTypeConfig> {
        let mut connections = sftp_connections_store().lock().await;

        let u_id = uuid::Uuid::parse_str(connection_id)?;

        let mut rc = ConnectionConfigs::find_remote_storage_config(&u_id, RemoteStorageType::Sftp)
            .ok_or_else(|| {
                Error::DataError(
                    "Previously saved SFTP Connection config is not found in configs for this id",
                )
            })?;

        if let Some(c) = connections.get(connection_id) {
            if !c.client_handle.is_closed() {
                debug!(
                    "SFTP connection is already done and no new connection is created for this id"
                );
                return Ok(rc);
            }
        }

        debug!("Previous connection is not available and will make new connection");

        let RemoteStorageTypeConfig::Sftp(ref mut connection_info) = rc else {
            // Should not happen
            return Err(Error::DataError(
                "SFTP Connection config is expected and not returned from configs",
            ));
        };

        // Need to ensure the full path points to the local key file path correctly to use
        // in the following 'Self::connect' call
        if let Some(ref file_name) = connection_info.private_key_file_name {
            let p = CallbackServiceProvider::common_callback_service()
                .sftp_private_key_file_full_path(connection_id, file_name);
            connection_info.private_key_full_file_name =
                Some(p.as_path().to_string_lossy().to_string());
        }

        let sftp_connection = Self::connect(connection_info).await?;

        // Store it for future reference
        connections.insert(connection_id.to_string(), sftp_connection);

        debug!("Created connection is stored in memory");

        Ok(rc)
    }

    pub(crate) async fn connect(connection_info: &SftpConnectionConfig) -> Result<SftpConnection> {
        debug!(
            "Sftp::connect Received connection_info {:?}",
            connection_info
        );

        let SftpConnectionConfig {
            connection_id,
            host,
            port,
            private_key_full_file_name,
            private_key_file_name: _,
            user_name,
            password,
            // Omits the remaining fields
            ..
        } = connection_info;

        debug!("Sftp::connect Going to russh connect...");

        let config = russh::client::Config::default();
        let sh = Client {};
        let mut client_handle =
            russh::client::connect(Arc::new(config), (host.clone(), port.clone()), sh)
                .await
                .map_err(|e| convert_error(e))?;

        debug!("Sftp::connect russh connected");

        let session_authenticated = if let Some(full_file_path) = private_key_full_file_name {
            // let full_file_path = CallbackServiceProvider::common_callback_service().sftp_private_key_file_full_path(file_name);

            debug!(
                "Sftp::connect Private key full path is {:?}",
                &full_file_path
            );

            // Note load_secret_key calls the fn decode_secret_key(&secret, password)
            // where secret is a String that has the text of the private key
            let key = russh::keys::load_secret_key(full_file_path, password.as_ref().map(|x| x.as_str()))
                .map_err(convert_russh_keys_error)?;
            client_handle
                .authenticate_publickey(user_name, russh::keys::PrivateKeyWithHashAlg::new(Arc::new(key),Some(russh::keys::HashAlg::Sha256)))
                .await
                .map_err(convert_error)?.success()
        } else if let Some(pwd) = password {
            client_handle
                .authenticate_password(user_name, pwd)
                .await
                .map_err(convert_error)?.success()
        } else {
            false
        };

        debug!(
            "Sftp::connect session_authenticated is {}",
            &session_authenticated
        );

        if session_authenticated {
            Ok(SftpConnection {
                connection_id: *connection_id,
                client_handle,
            })
        } else {
            Err(Error::SftpServerAuthenticationFailed)
        }
    }

    async fn list_dir(&self, parent_dir: &str) -> Result<ServerDirEntry> {
        // Should this be stored in 'SftpConnection' and reused?
        let sftp = self.create_sftp_session().await?;

        let dir_info = sftp.read_dir(parent_dir).await?;
        let mut sub_dirs: Vec<String> = vec![];
        let mut files: Vec<String> = vec![];
        for e in dir_info {
            let name = e.file_name();
            if e.file_type().is_dir() {
                if filter_entry(&name) {
                    sub_dirs.push(e.file_name());
                }
            } else {
                if filter_entry(&name) {
                    files.push(name);
                }
            }
        }
        // Should this be called explicitly  or is it closed by RawSftpSession's drop
        // If we store in SftpConnection, we should not call close()
        sftp.close().await?;

        Ok(ServerDirEntry {
            parent_dir: parent_dir.into(),
            sub_dirs,
            files,
        })
    }

    async fn list_sub_dir(&self, parent_dir: &str, sub_dir: &str) -> Result<ServerDirEntry> {
        let full_dir = [parent_dir, sub_dir].join("/");
        self.list_dir(&full_dir).await
    }

    async fn create_sftp_session(&self) -> Result<SftpSession> {
        let channel = self.client_handle.channel_open_session().await?;
        channel.request_subsystem(true, "sftp").await?;
        let sftp = SftpSession::new(channel.into_stream()).await?;
        Ok(sftp)
    }

    async fn read(&self, parent_dir: &str, file_name: &str) -> Result<RemoteReadData> {
        let sftp_session = self.create_sftp_session().await?;
        let full_path = [parent_dir, file_name].join("/");

        debug!("Sftp going to read file path {} ", &full_path);

        // Copies the full file content to memory
        let contents = sftp_session.read(&full_path).await?;

        debug!("Sftp content read and size is {}", contents.len());

        // let md = sftp_session.metadata(&full_path).await?; // Callling metadata makes another server call

        // // Copied from sftp.read implementation so that we can get metadata from file instance
        // // let mut file = sftp.open(&full_path).await?;
        // // let mut contents = Vec::new();
        // // tokio::io::AsyncReadExt::read_to_end(&mut file, &mut contents).await?;
        // // let md = file.metadata().await?;

        // let accessed = md
        //     .accessed()
        //     .map_or_else(|_| None, |t| Some(system_time_to_seconds(t)));
        // let modified = md
        //     .modified()
        //     .map_or_else(|_| None, |t| Some(system_time_to_seconds(t)));

        // let rmd = RemoteFileMetadata {
        //     connection_id: self.connection_id,
        //     storage_type: RemoteStorageType::Sftp,
        //     full_file_name: full_path,
        //     size: md.size,
        //     accessed,
        //     modified,
        //     created: None,
        // };

        let rmd = self
            .create_remote_file_metadata(sftp_session, &full_path)
            .await?;

        Ok(RemoteReadData {
            data: contents,
            meta: rmd,
        })
    }

    async fn write_file(&self, file_path: &str, data: Arc<Vec<u8>>) -> Result<RemoteFileMetadata> {
        let sftp_session = self.create_sftp_session().await?;

        debug!("Sftp going to write file path {} ", &file_path);

        sftp_session.write(file_path, data.as_slice()).await?;

        let md = self
            .create_remote_file_metadata(sftp_session, file_path)
            .await?;

        Ok(md)
    }

    async fn create_file(&self, file_path: &str, data: Arc<Vec<u8>>) -> Result<RemoteFileMetadata> {
        let sftp_session = self.create_sftp_session().await?;

        // debug!("Sftp going to create file path {} ", &file_path);

        // First we need to create a new file on the sftp storage before writing
        sftp_session.create(file_path).await?;

        // debug!("Sftp going to write file path {} ", &file_path);

        sftp_session.write(file_path, data.as_slice()).await?;

        let md = self
            .create_remote_file_metadata(sftp_session, file_path)
            .await?;

        Ok(md)
    }

    async fn create_remote_file_metadata(
        &self,
        sftp_session: SftpSession,
        file_path: &str,
    ) -> Result<RemoteFileMetadata> {
        let md = sftp_session.metadata(file_path).await?; // Callling metadata makes another server call

        // Copied from sftp_session.read implementation so that we can get metadata from file instance
        // let mut file = sftp_session.open(&full_path).await?;
        // let mut contents = Vec::new();
        // tokio::io::AsyncReadExt::read_to_end(&mut file, &mut contents).await?;
        // let md = file.metadata().await?;

        let accessed = md
            .accessed()
            .map_or_else(|_| None, |t| Some(system_time_to_seconds(t)));
        let modified = md
            .modified()
            .map_or_else(|_| None, |t| Some(system_time_to_seconds(t)));

        let rmd = RemoteFileMetadata {
            connection_id: self.connection_id,
            storage_type: RemoteStorageType::Sftp,
            full_file_name: file_path.to_string(),
            size: md.size,
            accessed,
            modified,
            created: None,
        };

        Ok(rmd)
    }

    async fn file_metadata(&self, file_path: &str) -> Result<RemoteFileMetadata> {
        let sftp_session = self.create_sftp_session().await?;
        self.create_remote_file_metadata(sftp_session, file_path)
            .await
    }

    // All instance level send_* calls
    // This async fns are called in a spawn fn and that receives a oneshot channel and sends back the result

    // Called to send the result of async call back to the sync call
    pub(crate) async fn send_connect_and_retrieve_root_dir(
        tx: oneshot::Sender<Result<ConnectStatus>>,
        connection_info: SftpConnectionConfig,
    ) {
        let dir_listing = SftpConnection::connect_and_retrieve_root_dir(connection_info).await;
        let r = tx.send(dir_listing);
        if let Err(_) = r {
            log::error!("In send_connect_and_retrieve_root_dir send channel failed ");
        }
    }

    pub(crate) async fn send_connect_by_id_and_retrieve_root_dir(
        tx: oneshot::Sender<Result<ConnectStatus>>,
        connection_id: String,
    ) {
        let dir_listing = SftpConnection::connect_by_id_and_retrieve_root_dir(&connection_id).await;
        let r = tx.send(dir_listing);
        if let Err(_) = r {
            log::error!("In send_connect_by_id_and_retrieve_root_dir send channel failed ");
        }
    }

    pub(crate) async fn send_connect_by_id(
        tx: oneshot::Sender<Result<RemoteStorageTypeConfig>>,
        connection_id: String,
    ) {
        debug!("In send_connect_by_id");

        let conn_r = SftpConnection::connect_by_id(&connection_id).await;
        let r = tx.send(conn_r);
        if let Err(_) = r {
            log::error!("In send_connect_by_id send channel failed ");
        }
    }

    // Creats a fn with signature

    reply_by_sftp_async_fn!(send_list_dir (parent_dir:String), list_dir (&parent_dir), ServerDirEntry);

    // pub(crate) async fn send_list_sub_dir(tx: oneshot::Sender<Result<ServerDirEntry>>, connection_id: String, parent_dir: String, sub_dir: String)
    reply_by_sftp_async_fn!(send_list_sub_dir (parent_dir:String,sub_dir:String), list_sub_dir (&parent_dir,&sub_dir), ServerDirEntry);

    reply_by_sftp_async_fn!(send_read(parent_dir:String,file_name:String),read(&parent_dir,&file_name),RemoteReadData);

    reply_by_sftp_async_fn!(send_write_file(file_path:String,data:Arc<Vec<u8>>), write_file(&file_path, data), RemoteFileMetadata);

    reply_by_sftp_async_fn!(send_create_file(file_path:String,data:Arc<Vec<u8>>), create_file(&file_path, data), RemoteFileMetadata);

    reply_by_sftp_async_fn!(send_file_metadta(file_path:String), file_metadata(&file_path), RemoteFileMetadata);

    //reply_by_sftp_async_fn!(send_metadata (parent_dir:String,fiile_name:String), metadata (&parent_dir,&fiile_name), RemoteFileMetadata);
}

// For now this custom error messaging is done for russh::Error
// TODO: Need to find out how to incorporate this conversion in the crate::error::Error itself using From

fn convert_error(inner_error: russh::Error) -> error::Error {
    debug!("The incoming inner_error in convert_error is {:?}", &inner_error);
    match inner_error {
        russh::Error::ConnectionTimeout => {
            debug!("russh::Error::ConnectionTimeout happened");
            error::Error::RemoteStorageCallError(format!("Connection timed out"))
        }

        russh::Error::Keys(e) => convert_russh_keys_error(e),

        x => {
            // Connection refused (os error 111) when port is not correct

            let s = format!("{}", x);
            debug!("russh::Error - The formatted s is {}", s);

            // Saw on ios Connection refused (os error 61) 
            // when server was not running or port is wrong
            if s.contains("Connection refused") {
                // error::Error::RemoteStorageCallError(format!("Valid host and port are required"))
                error::Error::RemoteStorageCallError(format!("Connection refused. The server may not be running or connection information is not correct"))
            } else {
                // Sometimes saw "Operation timed out (os error 60)" when host is wrong
                debug!("Unhandled russh::Error {} ", x);
                error::Error::RemoteStorageCallError(format!("{}", x))
            }

            //debug!("Unhandled russh::Error {} ", x);
            //error::Error::RemoteStorageCallError(format!("{}", x))
        }
    }
}

fn convert_russh_keys_error(inner_error: russh::keys::Error) -> error::Error {
    match inner_error {
        russh::keys::Error::SshKey(x) => {
            debug!("russh_keys::Error::SshKey is {}", x);

            // Typically we get the error text as "cryptographic error"
            error::Error::RemoteStorageCallError(format!(
                "Valid private key file and a valid password for the key file are required"
            ))
        }

        russh::keys::Error::SshEncoding(e) => {
            debug!("Unhandled russh_keys::Error::SshEncoding {} ", e);
            error::Error::RemoteStorageCallError(format!("Valid private key file is requied"))
        }

        x => {
            // TDOO:
            // "stream did not contain valid UTF-8" when invalid file is used for private key

            // Connection refused (os error 111) when port or host is not correct
            debug!("Unhandled russh_keys::Error x {} ", x);

            let s = format!("{}", x);
            debug!("russh_keys::Error - The formatted s is {}", s);

            if s.contains("Connection refused") {
                error::Error::RemoteStorageCallError(format!("Connection refused. The server may not be running or connection information is not correct"))
                // error::Error::RemoteStorageCallError(format!("Valid host and port are required"))
            } else if s.contains("stream did not contain valid") {
                error::Error::RemoteStorageCallError(format!("Valid private key file is requied"))
            } else {
                error::Error::RemoteStorageCallError(format!(
                    "{}. Please ensure a valid private key file is used",
                    x
                ))
            }
        }
    }
}

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use log::{debug, info};
use onekeepass_core::db_service::entry_keyvalue_key as kv_key;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::db_service::error::{self, Error, Result};

use super::RemoteStorageType;

pub fn read_configs() -> Result<()> {
    ConnectionConfigs::read_config()
}
// Called from the UI facing rust side when the app is initialized
pub fn set_config_reader_writer(reader_writer: ConnectionConfigReaderWriterType) {
    ConnectionConfigReaderWriterStore::init(reader_writer);
}

// TODO:
// As we have moved 'storage' module from onekeepass_core crate to ffi layer
// need to evaluate whether we need to use CONFIG_READER_WRITER_INSTANCE etc

// Implemented in the ffi module
pub trait ConnectionConfigReaderWriter {
    fn read_string(&self) -> Result<String>;
    fn write_string(&self, data: &str) -> Result<()>;
}

// Need to use Arc (or Box) to hold the concrete implementation of the Trait object
// Mutex is required for any interior mututable operations
// This is a thread safe type

//pub type ConnectionConfigReaderWriterType = Arc<Mutex<dyn ConnectionConfigReaderWriter + Sync + Send>>;
pub type ConnectionConfigReaderWriterType = Arc<dyn ConnectionConfigReaderWriter + Sync + Send>;

static CONFIG_READER_WRITER_INSTANCE: once_cell::sync::OnceCell<ConnectionConfigReaderWriterType> =
    once_cell::sync::OnceCell::new();

// Stores the config reader and writer trait implementation
struct ConnectionConfigReaderWriterStore {}

type Crw = ConnectionConfigReaderWriterStore;

impl ConnectionConfigReaderWriterStore {
    fn init(kss: ConnectionConfigReaderWriterType) {
        let _r = CONFIG_READER_WRITER_INSTANCE.set(kss);
        debug!("ConnectionConfigReaderWriterStore - init call is completed and CONFIG_READER_WRITER_INSTANCE initalized ");
    }

    fn instance() -> &'static ConnectionConfigReaderWriterType {
        CONFIG_READER_WRITER_INSTANCE
            .get()
            .expect("Error: ConnectionConfigReaderWriterStore is not initialzed")
    }

    // fn is_initialized() -> bool {
    //     CONFIG_READER_WRITER_INSTANCE.get().is_some()
    // }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "type", content = "content")]
#[non_exhaustive]
pub enum RemoteStorageTypeConfig {
    Sftp(SftpConnectionConfig),
    Webdav(WebdavConnectionConfig),
}

// Adjacently tagged enum
// will result a json something like {:type Sftp, :content [..]}
#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "type", content = "content")]
#[non_exhaustive]
pub enum RemoteStorageTypeConfigs {
    Sftp(Vec<SftpConnectionConfig>),
    Webdav(Vec<WebdavConnectionConfig>),
}

trait ConnectionId {
    fn connection_id(&self) -> &Uuid;
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct SftpConnectionConfig {
    pub connection_id: Uuid,
    // user selected name for this connection
    pub name: Option<String>,
    pub host: String,
    pub port: u16,
    // required for authenticate_publickey when we use private key
    // This is the full file path
    pub private_key_full_file_name: Option<String>,
    // Just the file name part mainly for UI use
    pub private_key_file_name: Option<String>,
    //
    pub user_name: String,
    // required for authenticate_password when we use password
    pub password: Option<String>,
    // All files and sub dirs from this will be shown as root
    pub start_dir: Option<String>,
    // In-memory only. Populated when the config is built from a kdbx
    // attachment (REMOTE_CONNECTION_SFTP entry). The sftp connect path
    // prefers these bytes over reading private_key_full_file_name from disk.
    #[serde(skip)]
    pub private_key_data: Option<Vec<u8>>,
}

impl ConnectionId for SftpConnectionConfig {
    fn connection_id(&self) -> &Uuid {
        &self.connection_id
    }
}

impl SftpConnectionConfig {
    // Builds a config from a kdbx REMOTE_CONNECTION_SFTP entry's kvs.
    // The connection_id is the entry uuid; the entry's title becomes the
    // connection name. private_key_data must be assigned separately by the
    // caller from the corresponding entry attachment (if any).
    pub fn from_kvs(
        connection_id: Uuid,
        name: Option<String>,
        kvs: &HashMap<String, String>,
    ) -> Result<Self> {
        let host = kvs
            .get(kv_key::HOST)
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .ok_or(Error::DataError("SFTP connection entry: missing Host"))?;

        // Port defaults to the standard SSH port when missing or invalid.
        let port = kvs
            .get(kv_key::PORT)
            .and_then(|s| s.trim().parse::<u16>().ok())
            .unwrap_or(22);

        let user_name = kvs
            .get(kv_key::USER_NAME)
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .ok_or(Error::DataError("SFTP connection entry: missing UserName"))?;

        let password = kvs
            .get(kv_key::PASSWORD)
            .cloned()
            .filter(|s| !s.is_empty());

        let start_dir = kvs
            .get(kv_key::START_DIR)
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty());

        Ok(SftpConnectionConfig {
            connection_id,
            name,
            host,
            port,
            private_key_full_file_name: None,
            private_key_file_name: None,
            user_name,
            password,
            start_dir,
            private_key_data: None,
        })
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct WebdavConnectionConfig {
    pub connection_id: Uuid,
    // user selected name for this connection
    pub name: String,
    // e.g https:://server.com/somefolder or  http:://server.com/somefolder
    pub root_url: String,
    pub user_name: String,
    pub password: String,
    pub allow_untrusted_cert: bool,
    // All files and sub dirs from this will be shown as root
    pub start_dir: Option<String>,
}

impl ConnectionId for WebdavConnectionConfig {
    fn connection_id(&self) -> &Uuid {
        &self.connection_id
    }
}

impl WebdavConnectionConfig {
    // Builds a config from a kdbx REMOTE_CONNECTION_WEBDAV entry's kvs. The
    // connection_id is the entry uuid; the entry's title becomes the
    // connection name. The URL field is used as the root_url.
    pub fn from_kvs(
        connection_id: Uuid,
        name: String,
        kvs: &HashMap<String, String>,
    ) -> Result<Self> {
        let root_url = kvs
            .get(kv_key::URL)
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .ok_or(Error::DataError("WebDAV connection entry: missing URL"))?;

        let user_name = kvs
            .get(kv_key::USER_NAME)
            .map(|s| s.trim().to_string())
            .unwrap_or_default();

        let password = kvs
            .get(kv_key::PASSWORD)
            .cloned()
            .unwrap_or_default();

        // Bool field; absent or unparseable values default to false (untrusted
        // certs are off unless the user explicitly opted in).
        let allow_untrusted_cert = kvs
            .get(kv_key::ALLOW_UNTRUSTED_CERT)
            .map(|s| matches!(s.trim().to_ascii_lowercase().as_str(), "true" | "1" | "yes"))
            .unwrap_or(false);

        let start_dir = kvs
            .get(kv_key::START_DIR)
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty());

        Ok(WebdavConnectionConfig {
            connection_id,
            name,
            root_url,
            user_name,
            password,
            allow_untrusted_cert,
            start_dir,
        })
    }
}

#[derive(Serialize, Deserialize)]
pub struct ConnectionConfigs {
    sftp_connections: Vec<SftpConnectionConfig>,
    webdav_connections: Vec<WebdavConnectionConfig>,
}

impl Default for ConnectionConfigs {
    fn default() -> Self {
        Self {
            sftp_connections: vec![],
            webdav_connections: vec![],
        }
    }
}

// In memory static store of the config data

type ConfigStore = Arc<Mutex<ConnectionConfigs>>;

fn config_store() -> &'static ConfigStore {
    static CONFIG_STORE: once_cell::sync::Lazy<ConfigStore> =
        once_cell::sync::Lazy::new(Default::default);
    &CONFIG_STORE
}

impl ConnectionConfigs {
    // pub fn set_config_reader_writer(reader_writer: ConnectionConfigReaderWriterType) {
    //     Crw::init(reader_writer);
    // }

    pub(crate) fn generate_config_id_on_check(connection_id: Uuid) -> Uuid {
        if connection_id == Uuid::default() {
            Uuid::new_v4()
        } else {
            connection_id
        }
    }

    pub(crate) fn remote_storage_configs(request: RemoteStorageType) -> RemoteStorageTypeConfigs {
        let configs = config_store().lock().unwrap();
        match request {
            RemoteStorageType::Sftp => {
                RemoteStorageTypeConfigs::Sftp(configs.sftp_connections.clone())
            }
            RemoteStorageType::Webdav => {
                RemoteStorageTypeConfigs::Webdav(configs.webdav_connections.clone())
            }
        }
    }

    pub(crate) fn find_remote_storage_config(
        connection_id: &Uuid,
        request: RemoteStorageType,
    ) -> Option<RemoteStorageTypeConfig> {
        // Kdbx-entry source wins: walk all open dbs looking for a
        // REMOTE_CONNECTION_SFTP / _WEBDAV entry whose uuid matches.
        if let Some(config) = Self::find_in_kdbx_entry_source(connection_id, &request) {
            return Some(config);
        }

        // Legacy blob (mobile-only secure-enclave store).
        Self::find_in_blob_store(connection_id, &request)
    }

    fn find_in_kdbx_entry_source(
        connection_id: &Uuid,
        request: &RemoteStorageType,
    ) -> Option<RemoteStorageTypeConfig> {
        let expected_type_uuid = match request {
            RemoteStorageType::Sftp => uuid::Builder::from_slice(
                onekeepass_core::db_service::entry_type_uuid::REMOTE_CONNECTION_SFTP,
            )
            .ok()?
            .into_uuid(),
            RemoteStorageType::Webdav => uuid::Builder::from_slice(
                onekeepass_core::db_service::entry_type_uuid::REMOTE_CONNECTION_WEBDAV,
            )
            .ok()?
            .into_uuid(),
        };

        let located = onekeepass_core::db_service::find_remote_connection_entry(
            connection_id,
            &expected_type_uuid,
        )?;

        let kvs =
            onekeepass_core::db_service::entry_key_value_fields(&located.db_key, connection_id)
                .ok()?;

        // Entry title (if any) becomes the connection name.
        let title = kvs
            .get(kv_key::TITLE)
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty());

        match request {
            RemoteStorageType::Sftp => {
                let mut config =
                    SftpConnectionConfig::from_kvs(*connection_id, title, &kvs).ok()?;

                // Load the private key (if attached) from the entry's first
                // binary attachment. Failure to read attachment bytes is not
                // fatal — the connect step will retry password auth if the
                // entry also has a password.
                if let Ok(Some((file_name, bytes))) =
                    onekeepass_core::db_service::entry_first_attachment(
                        &located.db_key,
                        connection_id,
                    )
                {
                    config.private_key_file_name = Some(file_name);
                    config.private_key_data = Some(bytes);
                }

                Some(RemoteStorageTypeConfig::Sftp(config))
            }
            RemoteStorageType::Webdav => {
                let name = title.unwrap_or_default();
                let config =
                    WebdavConnectionConfig::from_kvs(*connection_id, name, &kvs).ok()?;
                Some(RemoteStorageTypeConfig::Webdav(config))
            }
        }
    }

    fn find_in_blob_store(
        connection_id: &Uuid,
        request: &RemoteStorageType,
    ) -> Option<RemoteStorageTypeConfig> {
        let configs = config_store().lock().unwrap();
        match request {
            RemoteStorageType::Sftp => {
                let configs = &configs.sftp_connections;
                let found = configs.iter().find(|v| v.connection_id() == connection_id);
                found.map(|f| RemoteStorageTypeConfig::Sftp(f.clone()))
            }
            RemoteStorageType::Webdav => configs
                .webdav_connections
                .iter()
                .find(|v| v.connection_id() == connection_id)
                .map(|f| RemoteStorageTypeConfig::Webdav(f.clone())),
        }
    }

    // A new remote config is added or an existing config is updated
    pub(crate) fn add_or_update_config(request: RemoteStorageTypeConfig) -> Result<()> {
        // Need to be in a block so that the config_store().lock() is released before next lock call
        {
            let mut conns = config_store().lock().unwrap();

            match request {
                RemoteStorageTypeConfig::Sftp(config) => {
                    let configs = &mut conns.sftp_connections;
                    Self::internal_add_or_update_config(configs, config);
                }
                RemoteStorageTypeConfig::Webdav(config) => {
                    let configs = &mut conns.webdav_connections;
                    Self::internal_add_or_update_config(configs, config);
                }
            };
        }
        Self::write_config()?;

        Ok(())
    }

    // Not used anymore. Used till app version 0.18.0
    pub(crate) fn add_config(request: RemoteStorageTypeConfig) -> Result<()> {
        // Need to be in a block so that the config_store().lock() is released before next lock call
        {
            let mut conns = config_store().lock().unwrap();

            match request {
                RemoteStorageTypeConfig::Sftp(config) => {
                    let configs = &mut conns.sftp_connections;
                    Self::internal_add_config(configs, config);
                }
                RemoteStorageTypeConfig::Webdav(config) => {
                    let configs = &mut conns.webdav_connections;
                    Self::internal_add_config(configs, config);
                }
            };
        }
        Self::write_config()?;

        Ok(())
    }

    pub(crate) fn delete_config_by_id(
        remote_type: RemoteStorageType,
        connection_id: &Uuid,
    ) -> Result<()> {
        {
            let mut configs = config_store().lock().unwrap();
            match remote_type {
                RemoteStorageType::Sftp => {
                    let conns = &mut configs.sftp_connections;
                    Self::interal_delete_config(connection_id, conns);
                }
                RemoteStorageType::Webdav => {
                    let conns = &mut configs.webdav_connections;
                    Self::interal_delete_config(connection_id, conns);
                }
            }
        }

        Self::write_config()?;

        Ok(())
    }

    // Not used. Deprecate?
    pub(crate) fn update_config(request: RemoteStorageTypeConfig) -> Result<()> {
        {
            let mut configs = config_store().lock().unwrap();
            match request {
                RemoteStorageTypeConfig::Sftp(config) => {
                    let conns = &mut configs.sftp_connections;
                    Self::interal_update_config::<SftpConnectionConfig>(conns, config);
                }
                RemoteStorageTypeConfig::Webdav(config) => {
                    let conns = &mut configs.webdav_connections;
                    Self::interal_update_config::<WebdavConnectionConfig>(conns, config);
                }
            }
        }

        Self::write_config()?;

        Ok(())
    }

    fn internal_add_or_update_config<T: ConnectionId>(configs: &mut Vec<T>, m_config: T) {
        let found = configs
            .iter() // .iter_mut() for mut call
            .find(|v| v.connection_id() == m_config.connection_id());
        if found.is_none() {
            debug!(
                "Config with id {} is not found in the list and adding to the list ",
                &m_config.connection_id()
            );
            configs.push(m_config);
        } else {
            Self::interal_update_config(configs, m_config);
        }
    }

    fn internal_add_config<T: ConnectionId>(configs: &mut Vec<T>, m_config: T) {
        let found = configs
            .iter() // .iter_mut() for mut call
            .find(|v| v.connection_id() == m_config.connection_id());
        if found.is_none() {
            debug!(
                "Config with id {} is not found in the list and adding to the list ",
                &m_config.connection_id()
            );
            configs.push(m_config);
        }
    }

    fn interal_delete_config<T: ConnectionId>(connection_id: &Uuid, configs: &mut Vec<T>) {
        configs.retain(|e| e.connection_id() != connection_id);
    }

    fn interal_update_config<T: ConnectionId>(configs: &mut Vec<T>, m_config: T) {
        for x in configs {
            if x.connection_id() == m_config.connection_id() {
                debug!(
                    "Config with id {} is  found in the list and updating the config",
                    &m_config.connection_id()
                );
                *x = m_config;
                break;
            }
        }
    }

    // Called to read the previously persisted configs
    pub(crate) fn read_config() -> Result<()> {
        let json_str = Crw::instance().read_string()?;
        let config_read = Self::from(&json_str);

        let mut stored_config = config_store().lock().unwrap();
        *stored_config = config_read;

        Ok(())
    }

    fn write_config() -> Result<()> {
        debug!("Going to call store write");
        let conns = config_store().lock().unwrap();
        conns.write()?;
        Ok(())
    }

    // json_str should be parseable as json object
    fn from(json_str: &str) -> Self {
        if json_str.is_empty() {
            info!("App remote connections config is empty and default used ");
            Self::default()
        } else {
            serde_json::from_str(&json_str).unwrap_or_else(|_| {
                info!("App remote connections config parsing failed and returning the empty default config");
                let connection_config_new = Self::default();
                connection_config_new
            })
        }
    }

    fn to_json_string(&self) -> Result<String> {
        Ok(serde_json::to_string_pretty(self)?)
    }

    // Called on this instance whenever sftp or webdav config is modified or added or deleted
    // The serialized data is persisted
    fn write(&self) -> Result<()> {
        let s = self.to_json_string()?;
        debug!("Configs str to write to a file {}", &s);
        let data = self.to_json_string()?;
        Crw::instance().write_string(&data)?;
        Ok(())
    }
}

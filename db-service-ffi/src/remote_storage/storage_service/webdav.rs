use std::{collections::HashMap, sync::Arc};

use log::{debug, info};
use once_cell::sync::Lazy;
use reqwest_dav::{list_cmd::ListEntity, Auth, Client, ClientBuilder, Depth};
use serde::{Deserialize, Serialize};
use tokio::sync::oneshot;
use uuid::Uuid;

use crate::{
    parse_operation_fields_if, receive_from_async_fn,
    remote_storage::callback_service::CallbackServiceProvider, reply_by_async_fn,
};

use onekeepass_core::async_service::async_runtime;
use onekeepass_core::db_service::error::{self, Error, Result};
use onekeepass_core::service_util::system_time_to_seconds;

pub use super::server_connection_config::WebdavConnectionConfig;
use super::ConnectStatus;
use super::{
    calls::RemoteStorageOperation,
    filter_entry,
    server_connection_config::{
        ConnectionConfigs, RemoteStorageTypeConfig, RemoteStorageTypeConfigs,
    },
    string_tuple3, RemoteFileMetadata, RemoteReadData, RemoteStorageType, ServerDirEntry,
};

macro_rules! reply_by_webdav_async_fn {
    ($fn_name:ident ($($arg1:tt:$arg_type:ty),*),$call:tt ($($arg:expr),*), $send_val:ty) => {
        reply_by_async_fn!(webdav_connections_store,$fn_name ($($arg1:$arg_type),*),$call ($($arg),*),$send_val);
    };
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct Webdav {
    connection_info: Option<WebdavConnectionConfig>,
    connection_id: Option<String>,
    parent_dir: Option<String>,
    sub_dir: Option<String>,
    file_path: Option<String>,
    pub(crate) file_name: Option<String>,
}

impl Webdav {
    pub(crate) fn from_parsed_db_key(connection_id: &str, file_path_part: &str) -> Webdav {
        let mut webdav = Webdav::default();
        webdav.file_path = Some(file_path_part.to_string());
        if let Some(parts) = file_path_part.rsplit_once("/") {
            debug!("Webdav Parst of file_path_part are {:?}", &parts);

            let v = if parts.0.is_empty() { "/" } else { parts.0 };
            webdav.parent_dir = Some(v.to_string());
            webdav.connection_id = Some(connection_id.to_string());
            webdav.file_name = Some(parts.1.to_string());
        }
        webdav
    }
}

impl RemoteStorageOperation for Webdav {
    fn connect_and_retrieve_root_dir(&self) -> Result<ConnectStatus> {
        #[allow(unused_parens)]
        let (connection_info) = parse_operation_fields_if!(self, connection_info);
        let c = connection_info.clone();
        receive_from_async_fn!(
            WebdavConnection::send_connect_and_retrieve_root_dir(c),
            ConnectStatus
        )?
    }

    fn connect_by_id_and_retrieve_root_dir(&self) -> Result<ConnectStatus> {
        #[allow(unused_parens)]
        let (connection_id) = parse_operation_fields_if!(self, connection_id);

        let c_id = connection_id.clone();

        // Call the async fn in a 'spawn' and wait for the result - Result<Result<RemoteStorageTypeConfig>>)
        receive_from_async_fn!(
            WebdavConnection::send_connect_by_id_and_retrieve_root_dir(c_id),
            ConnectStatus
        )?
    }

    fn connect_by_id(&self) -> Result<RemoteStorageTypeConfig> {
        #[allow(unused_parens)]
        let (connection_id) = parse_operation_fields_if!(self, connection_id);

        let c_id = connection_id.clone();

        // Call the async fn in a 'spawn' and wait for the result - Result<Result<RemoteStorageTypeConfig>>)
        let rc = receive_from_async_fn!(
            WebdavConnection::send_connect_by_id(c_id),
            RemoteStorageTypeConfig
        )?;

        rc
    }

    fn list_sub_dir(&self) -> Result<ServerDirEntry> {
        let (connection_id, parent_dir, sub_dir) =
            parse_operation_fields_if!(self, connection_id, parent_dir, sub_dir);

        let (cn, pd, sd) = string_tuple3(&[connection_id, parent_dir, sub_dir]);
        receive_from_async_fn!(
            WebdavConnection::send_list_sub_dir(cn, pd, sd),
            ServerDirEntry
        )?
    }

    fn read(&self) -> Result<RemoteReadData> {
        let (connection_id, parent_dir, file_name) =
            parse_operation_fields_if!(self, connection_id, parent_dir, file_name);

        let (cn, pd, name) = string_tuple3(&[connection_id, parent_dir, file_name]);
        receive_from_async_fn!(WebdavConnection::send_read(cn, pd, name), RemoteReadData)?
    }

    fn write_file(&self, data: Arc<Vec<u8>>) -> Result<RemoteFileMetadata> {
        let (connection_id, file_path) = parse_operation_fields_if!(self, connection_id, file_path);
        let file_path = file_path.to_string();
        let c_id = connection_id.clone();
        receive_from_async_fn!(
            WebdavConnection::send_write_file(c_id, file_path, data),
            RemoteFileMetadata
        )?
    }

    fn create_file(&self, data: Arc<Vec<u8>>) -> Result<RemoteFileMetadata> {
        self.write_file(data)
    }

    fn file_metadata(&self) -> Result<RemoteFileMetadata> {
        let (connection_id, file_path) = parse_operation_fields_if!(self, connection_id, file_path);
        let file_path = file_path.to_string();
        let c_id = connection_id.clone();
        receive_from_async_fn!(
            WebdavConnection::send_file_metadta(c_id, file_path),
            RemoteFileMetadata
        )?
    }

    fn remote_storage_configs(&self) -> Result<RemoteStorageTypeConfigs> {
        Ok(ConnectionConfigs::remote_storage_configs(
            RemoteStorageType::Webdav,
        ))
    }

    fn update_config(&self) -> Result<()> {
        #[allow(unused_parens)]
        let (connection_info) = parse_operation_fields_if!(self, connection_info);
        ConnectionConfigs::update_config(RemoteStorageTypeConfig::Webdav(connection_info.clone()))
    }

    fn delete_config(&self) -> Result<()> {
        #[allow(unused_parens)]
        let (connection_id) = parse_operation_fields_if!(self, connection_id);

        let u_id = uuid::Uuid::parse_str(connection_id)?;
        let r = ConnectionConfigs::delete_config_by_id(RemoteStorageType::Webdav, &u_id);
        CallbackServiceProvider::common_callback_service()
            .remote_storage_config_deleted(RemoteStorageType::Webdav, connection_id)?;

        r
    }

    fn list_dir(&self) -> Result<ServerDirEntry> {
        todo!()
    }

    fn file_name(&self) -> Option<&str> {
        self.file_name.as_ref().map(|x| x.as_str())
    }

    fn file_path(&self) -> Option<&str> {
        self.file_path.as_ref().map(|x| x.as_str())
    }
}

struct WebdavConnection {
    client: Client,
}

type WebdavConnections = Arc<tokio::sync::Mutex<HashMap<String, WebdavConnection>>>;

fn webdav_connections_store() -> &'static WebdavConnections {
    static WEBDAV_CONNECTIONS_STORE: Lazy<WebdavConnections> = Lazy::new(Default::default);
    &WEBDAV_CONNECTIONS_STORE
}

pub fn connect_and_retrieve_root_dir(
    connection_info: WebdavConnectionConfig,
) -> Result<ConnectStatus> {
    receive_from_async_fn!(
        WebdavConnection::send_connect_and_retrieve_root_dir(connection_info),
        ConnectStatus
    )?
}

const WEBDAV_ROOT_DIR: &str = "/";

impl WebdavConnection {
    pub(crate) async fn connect_and_retrieve_root_dir(
        mut connection_info: WebdavConnectionConfig,
    ) -> Result<ConnectStatus> {
        connection_info.connection_id =
            ConnectionConfigs::generate_config_id_on_check(connection_info.connection_id);

        let webdav_connection = Self::connect(&connection_info).await?;

        let dirs = webdav_connection.list_dir(WEBDAV_ROOT_DIR).await?;

        let store_key = connection_info.connection_id.to_string();

        // Store it for future reference
        let mut connections = webdav_connections_store().lock().await;
        connections.insert(store_key, webdav_connection);

        let conn_status = ConnectStatus {
            connection_id: connection_info.connection_id,
            dir_entries: Some(dirs),
        };

        // Need to add to the configs list
        // TODO: Need to update if the existing config is changed
        ConnectionConfigs::add_config(RemoteStorageTypeConfig::Webdav(connection_info))?;

        Ok(conn_status)
    }

    // Called to create a new remote connection using the connection id and on successful connection
    // the root dir entries are returned
    async fn connect_by_id_and_retrieve_root_dir(connection_id: &str) -> Result<ConnectStatus> {
        // Makes connection to the remote storage and stores the connection in the local static map
        // Note webdav_connections_store().lock() called in this call
        let _r = Self::connect_by_id(connection_id).await?;

        // Previous lock call should have been unlocked by this time. Otherwise deadlock will happen
        let connections = webdav_connections_store().lock().await;

        // A successful connection should be available
        let webdav_connection = connections.get(connection_id).ok_or_else(|| {
            Error::DataError(
                "Previously saved WebDav Connection config is not found in configs for this id",
            )
        })?;

        let dirs = webdav_connection.list_dir(WEBDAV_ROOT_DIR).await?;

        let u_id = uuid::Uuid::parse_str(connection_id)?;
        let conn_status = ConnectStatus {
            connection_id: u_id,
            dir_entries: Some(dirs),
        };

        Ok(conn_status)
    }

    // Gets the connection config with this id and use that to connect the Webdav server if required  and stores
    // that connection for the future use
    async fn connect_by_id(connection_id: &str) -> Result<RemoteStorageTypeConfig> {
        let mut connections = webdav_connections_store().lock().await;

        let u_id = uuid::Uuid::parse_str(connection_id)?;

        let rc = ConnectionConfigs::find_remote_storage_config(&u_id, RemoteStorageType::Webdav)
            .ok_or_else(|| {
                Error::DataError(
                    "Previously saved WebDav Connection config is not found in configs for this id",
                )
            })?;

        if let Some(_c) = connections.get(connection_id) {
            return Ok(rc);
        }

        debug!("Previous connection is not available and will make new connection");

        let RemoteStorageTypeConfig::Webdav(ref connection_info) = rc else {
            // Should not happen
            return Err(Error::DataError(
                "Webdav Connection config is expected and not returned from configs",
            ));
        };

        let webdav_connection = Self::connect(connection_info).await?;

        // Store it for future reference
        connections.insert(connection_id.to_string(), webdav_connection);

        debug!("Created connection is stored in memory");

        Ok(rc)
    }

    async fn connect(connection_info: &WebdavConnectionConfig) -> Result<WebdavConnection> {
        let agent = reqwest_dav::re_exports::reqwest::ClientBuilder::new()
            .danger_accept_invalid_certs(connection_info.allow_untrusted_cert)
            .build()?;

        info!("Agent is created...");

        // build a client
        let client = ClientBuilder::new()
            .set_agent(agent)
            .set_host(connection_info.root_url.clone())
            .set_auth(Auth::Basic(
                connection_info.user_name.clone(),
                connection_info.password.clone(),
            ))
            .build()?;

        info!("Client is created...{:?}", &client);
        let webdav_connection = WebdavConnection { client };

        debug!("Listing root dir content to verify the client config info");

        let _r = webdav_connection
            .client
            .list(".", Depth::Number(0))
            .await
            .map_err(|e| convert_error(e))?;

        debug!("Connection verification is done");

        Ok(webdav_connection)
    }

    // Caller needs to pass the relative path as parent_dir
    // e.g "." "/dav" "/dav/databases" etc and these parent dir should exists. Oterwiese an error with 404 code raised
    async fn list_dir(&self, parent_dir: &str) -> Result<ServerDirEntry> {
        // A vec of all parts of this parent dir path
        // e.g "dav/db1" -> ["dav" "db1"]
        let paren_dir_parts = parent_dir
            .split("/")
            .filter(|s| filter_entry(s))
            .collect::<Vec<_>>();

        let dir_info = self.client.list(parent_dir, Depth::Number(1)).await?;

        let mut sub_dirs: Vec<String> = vec![];
        let mut files: Vec<String> = vec![];

        for e in dir_info {
            match e {
                ListEntity::File(list_file) => {
                    // info!("List entry is a file and meta is {:?} ", f);
                    // let n = f.href.split_once("/").map_or_else(|| ".", |v| v.1);
                    // files.push(n.to_string());

                    // Need to remove empty "" and mac OS specific dot files
                    let list_file_parts = &list_file
                        .href
                        .split("/")
                        .filter(|s| filter_entry(s))
                        .collect::<Vec<_>>();

                    // There is folder entry corresponding to the passed  'parent_dir'
                    // when dot files are excluded and we need to exclude that
                    // e.g "dav/db1/.DS_Store"  ["dav" "db1" ".DS_Store"] -> after filtering ["dav" "db1"]
                    // and this needs to be excluded

                    if !(&paren_dir_parts == list_file_parts) {
                        if let Some(last_comp) = list_file_parts.last() {
                            files.push(last_comp.to_string());
                        }
                    }
                }
                ListEntity::Folder(list_file) => {
                    // info!("List entry is a folder and meta is {:?} ", f);
                    // let n = f.href.split_once("/").map_or_else(|| ".", |v| v.1);
                    // sub_dirs.push(n.to_string());

                    let list_file_parts = &list_file
                        .href
                        .split("/")
                        .filter(|s| filter_entry(s))
                        .collect::<Vec<_>>();

                    // There is folder entry corresponding to the passed  'parent_dir'
                    // and we need to exclude that
                    // e.g "dav/db1" -> ["dav" "db1"]
                    if !(&paren_dir_parts == list_file_parts) {
                        if let Some(last_comp) = list_file_parts.last() {
                            sub_dirs.push(last_comp.to_string());
                        }
                    }
                }
            }
        }

        Ok(ServerDirEntry {
            parent_dir: parent_dir.into(),
            sub_dirs,
            files,
        })
    }

    async fn list_sub_dir(&self, parent_dir: &str, sub_dir: &str) -> Result<ServerDirEntry> {
        // Assuming parent_dir is the relative dir without host info
        // For now we use this simple join of parent_dir and sub dir using sep "/"
        let full_dir = [parent_dir, sub_dir].join("/");
        self.list_dir(&full_dir).await
    }

    async fn read(&self, parent_dir: &str, file_name: &str) -> Result<RemoteReadData> {
        // In webdav, this is a relative path. E.g /parent_dir/file_name
        let file_path = [parent_dir, file_name].join("/");

        debug!(
            "Webdav call is  going to read using file path {} ",
            &file_path
        );

        let response = self
            .client
            .get(&file_path)
            .await
            .map_err(|e| convert_error(e))?;
        // Copies the full file content to memory
        let contents: Vec<u8> = response.bytes().await?.into();

        debug!("Webdav content read and size is {}", contents.len());

        /*
        // Need to use Depth::Number(0) to get the file info as Depth of "0" applies only to the resource
        let (size, modified) = if let Some(list_entity) = self
            .client
            .list(&full_path, Depth::Number(0))
            .await?
            .first()
        {
            match list_entity {
                ListEntity::File(f) => (
                    Some(f.content_length as u64),
                    // last_modified is DateTime<Utc>
                    Some(f.last_modified.timestamp() as u64),
                ),
                _ => (None, None),
            }
        } else {
            (None, None)
        };

        // Should we make full file name by combining the relative path 'full_path' with host str of self.client.host
        // see the implementation of self.client.start_request where the combined url is formed
        let url = Url::parse(&format!(
            "{}/{}",
            &self.client.host.trim_end_matches("/"),
            &full_path.trim_start_matches("/")
        ))?;

        let full_file_name = url.as_str().to_string();

        let rmd = RemoteFileMetadata {
            connection_id: Uuid::default(),
            storage_type: RemoteStorageType::Webdav,
            full_file_name,
            size,
            accessed: None,
            modified,
            created: None,
        };

        */

        let rmd = self.file_metadata(&file_path).await?;

        Ok(RemoteReadData {
            data: contents,
            meta: rmd,
        })
    }

    async fn file_metadata(&self, file_path: &str) -> Result<RemoteFileMetadata> {
        self.create_remote_file_metadata(file_path).await
    }

    async fn write_file(&self, file_path: &str, data: Arc<Vec<u8>>) -> Result<RemoteFileMetadata> {
        // let inner_data = Vec::from(data.as_slice());
        // self.client.put(file_path, inner_data).await?;

        // Need to create a new Vec<u8> data as  &[u8] from data.as_slice() did not
        // work with error: `data` does not live long enough, `data` dropped here while still borrowed
        let inner_data = data.to_vec();
        self.client.put(file_path, inner_data).await?;

        let rmd = self.create_remote_file_metadata(file_path).await?;

        Ok(rmd)
    }

    async fn create_remote_file_metadata(&self, file_path: &str) -> Result<RemoteFileMetadata> {
        // Need to use Depth::Number(0) to get the file info as Depth of "0" applies only to the resource
        let (size, modified) = if let Some(list_entity) =
            self.client.list(file_path, Depth::Number(0)).await?.first()
        {
            match list_entity {
                ListEntity::File(f) => (
                    Some(f.content_length as u64),
                    // last_modified is DateTime<Utc>
                    Some(f.last_modified.timestamp() as u64),
                ),
                _ => (None, None),
            }
        } else {
            (None, None)
        };

        // Should we make full file name by combining the relative path 'full_path' with host str of self.client.host
        // see the implementation of self.client.start_request where the combined url is formed
        // let url = Url::parse(&format!(
        //     "{}/{}",
        //     &self.client.host.trim_end_matches("/"),
        //     file_path.trim_start_matches("/") // removes the starting one or more "/"
        // ))?;

        // let full_file_name = url.as_str().to_string();

        let full_file_name = file_path.to_string();

        let rmd = RemoteFileMetadata {
            connection_id: Uuid::default(),
            storage_type: RemoteStorageType::Webdav,
            full_file_name,
            size,
            accessed: None,
            modified,
            created: None,
        };

        Ok(rmd)
    }

    async fn send_connect_and_retrieve_root_dir(
        tx: oneshot::Sender<Result<ConnectStatus>>,
        connection_info: WebdavConnectionConfig,
    ) {
        let dir_listing = WebdavConnection::connect_and_retrieve_root_dir(connection_info).await;
        let r = tx.send(dir_listing);
        if let Err(_) = r {
            log::error!("In connect_to_server send channel failed ");
        }
    }

    pub(crate) async fn send_connect_by_id_and_retrieve_root_dir(
        tx: oneshot::Sender<Result<ConnectStatus>>,
        connection_id: String,
    ) {
        let dir_listing =
            WebdavConnection::connect_by_id_and_retrieve_root_dir(&connection_id).await;
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

        let conn_r = WebdavConnection::connect_by_id(&connection_id).await;
        let r = tx.send(conn_r);
        if let Err(_) = r {
            log::error!("In send_connect_by_id send channel failed ");
        }
    }

    reply_by_webdav_async_fn!(send_list_sub_dir(parent_dir:String,sub_dir:String), list_sub_dir (&parent_dir,&sub_dir),ServerDirEntry);

    reply_by_webdav_async_fn!(send_read(parent_dir:String,file_name:String),read(&parent_dir,&file_name),RemoteReadData);

    reply_by_webdav_async_fn!(send_write_file(file_path:String,data:Arc<Vec<u8>>), write_file(&file_path, data), RemoteFileMetadata);

    reply_by_webdav_async_fn!(send_file_metadta(file_path:String), file_metadata(&file_path), RemoteFileMetadata);
}

// For now this custom error messaging is done for reqwest_dav::types::Error
// TODO: Need to find out how to incorporate this conversion in the crate::error::Error itself using From

fn convert_error(inner_error: reqwest_dav::types::Error) -> error::Error {
    debug!(
        "The incoming inner_error in convert_error is {:?}",
        &inner_error
    );

    match inner_error {
        reqwest_dav::Error::Reqwest(e) => {
            debug!("The r is {:?}", e);
            debug!("The r is {}, {}", &e.is_timeout(), &e.is_connect());

            if e.is_connect() {
                // error::Error::RemoteStorageCallError(format!("Invalid root url. Please provide a valid value for host and port"))
                error::Error::RemoteStorageCallError(format!("Connection refused. The server may not be running or connection information is not correct"))
            } else if e.is_timeout() && e.is_connect() {
                error::Error::RemoteStorageCallError(format!("Connection timed out. The server may not be running or connection information is not correct"))
                // error::Error::RemoteStorageCallError(format!(
                //     "Invalid root url. Please provide a valid value for host and port"
                // ))
            } else {
                error::Error::RemoteStorageCallError(format!("{}", e))
            }
        }

        reqwest_dav::Error::Decode(e1) => {
            debug!("reqwest_dav::Error::Decode error is {:?}", &e1);
            match e1 {
                reqwest_dav::DecodeError::StatusMismatched(e) => {
                    if e.response_code == 404 {
                        // && e2.expected_code == 207
                        debug!("The url is not found ..."); // resource is not found
                        error::Error::RemoteStorageCallError(format!("Invalid resource path"))
                    } else if e.response_code == 401 && e.expected_code == 207 {
                        debug!("The user id or password is wrong and returning our error");
                        error::Error::RemoteStorageCallError(format!(
                            "Invalid User Name and/or Password. Please provide valid values"
                        ))
                    } else {
                        error::Error::RemoteStorageCallError(format!("{:?}", e))
                    }
                }
                e => error::Error::RemoteStorageCallError(format!("{:?}", e)),
            }
        }

        reqwest_dav::Error::ReqwestDecode(e1) => {
            debug!("reqwest_dav::Error::ReqwestDecode error is {:?}", &e1);
            match e1 {
                reqwest_dav::ReqwestDecodeError::Url(e) => {
                    let s = format!("{}", &e);
                    let b = s.contains("invalid port number");
                    debug!("Invalid root url {}", &e);
                    if b {
                        error::Error::RemoteStorageCallError(format!(
                            "Invalid port information. Please provide a valid port"
                        ))
                    } else {
                        error::Error::RemoteStorageCallError(format!("{}", e))
                    }
                }
                e => error::Error::RemoteStorageCallError(format!("{:?}", e)),
            }
        }

        e => error::Error::RemoteStorageCallError(format!("{:?}", e)),
    }
}

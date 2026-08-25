(ns onekeepass.mobile.background-remote-server
  (:require
   [onekeepass.mobile.background-common :refer [invoke-api]]
   [onekeepass.mobile.constants :as const]))

(set! *warn-on-infer* true)

(def kw-type-to-enum-tag {:sftp const/V-SFTP :webdav const/V-WEBDAV})

(defn as-rs-type [value]
  (if (keyword? value) (value kw-type-to-enum-tag) value))

(defn read-configs
  "This needs to be called onetime when the app UI launches. This loads the previously saved 
   remote storage connection configs info 
   "
  [dispatch-fn]
  (invoke-api "rs_read_configs" {} dispatch-fn))

;; The args 'connect-request' is a map and is of type enum RemoteStorageOperationType
;; The one or more of following keys with values are passed based on the backend call
;; The :type should is required and all other keys are optional
;; keys = [:type :connection-info :connection-id :parent-dir :sub-dir :file-name]

(defn remote-storage-configs
  "The arg 'connect-request' is a map (type enum RemoteStorageOperationType) and has
   a key :type with value 'Sftp' or 'Webdav'
   Gets a vec of stored connection config infos for Sftp or Webdav "
  [connect-request dispatch-fn]
  (invoke-api "rs_remote_storage_configs" {:rs-operation-type connect-request}  dispatch-fn))

(defn list-kdbx-source-connections
  "Lists every REMOTE_CONNECTION_SFTP / _WEBDAV entry across the currently
   open kdbx databases. Each item is a map with :db-key, :connection-id,
   :title, :entry-type-uuid."
  [type dispatch-fn]
  (invoke-api "rs_list_kdbx_source_connections"
              {:rs-storage-type (as-rs-type type)}
              dispatch-fn))

(defn get-remote-storage-config
  "Read-only fetch of one connection config by id. Resolves from the kdbx
   entry source first, then the legacy blob store. The result is the
   adjacently-tagged enum {:type 'Sftp'/'Webdav' :content {..config..}}."
  [type connection-id dispatch-fn]
  (invoke-api "rs_get_remote_storage_config"
              {:rs-storage-type (as-rs-type type)
               :connection-id connection-id}
              dispatch-fn))

(defn delete-config [type connection-id dispatch-fn]
  (invoke-api "rs_delete_config" {:rs-operation-type
                                  {:type (as-rs-type type)
                                   :connection-id connection-id}} dispatch-fn))

(defn connect-and-retrieve-root-dir
  "The backend 'rs-operation-type' is a map (type enum RemoteStorageOperationType) and has  
   a key :type with value 'Sftp' or 'Webdav' and other keys are [:connection-info]
   Connects to a Sftp or Webdav connection. Connection info fields from type 
   'SftpConnectionConfig' or 'WebdavConnectionConfig' are required "
  [type connection-info dispatch-fn]
  (invoke-api "rs_connect_and_retrieve_root_dir" {:rs-operation-type
                                                  {:type (as-rs-type type)
                                                   :connection-info connection-info}} dispatch-fn))

(defn connect-by-id-and-retrieve-root-dir
  "The rs-operation-type is a map (type enum RemoteStorageOperationType) and has  
   a key :type with value 'Sftp' or 'Webdav' and other key is [:connection-id]"
  [type connection-id dispatch-fn]
  (invoke-api "rs_connect_by_id_and_retrieve_root_dir" {:rs-operation-type
                                                        {:type (as-rs-type type)
                                                         :connection-id connection-id}} dispatch-fn))

(defn list-sub-dir
  "The 'rs-operation-type' is a map and has  a key :type with value 'Sftp' or 'Webdav'
   The other keys are [:connection-id :parent-dir :sub-dir]
  "
  [type connection-id parent-dir sub-dir dispatch-fn]
  (invoke-api "rs_list_sub_dir" {:rs-operation-type {:type (as-rs-type type)
                                                     :connection-id connection-id
                                                     :parent-dir parent-dir
                                                     :sub-dir sub-dir}} dispatch-fn))

(defn read-kdbx
  "The connection-id, file path etc are parsed from the 'db-file-name'"
  [db-file-name password key-file-name biometric-auth-used dispatch-fn]
  (invoke-api "rs_read_kdbx"  {:db-file-name db-file-name
                               :password  password
                               :key-file-name key-file-name
                               :biometric-auth-used biometric-auth-used} dispatch-fn))

(defn save-kdbx
  "The connection-id, file path etc are parsed from the 'db-key'"
  [full-file-name overwrite dispatch-fn]
  (invoke-api "rs_save_kdbx" {:db-key full-file-name :overwrite overwrite} dispatch-fn))

(defn create-kdbx
  "Creates a new db and writes to the remote storage location
   The connection-id, file path etc are parsed using the field new_db.database_file_name
   which has the formed 'db-key'
   "
  [new-db dispatch-fn]
  (invoke-api "rs_create_kdbx" {:new-db new-db} dispatch-fn))

(defn save-as-kdbx
  "Writes a copy of an already prepared local db file to the remote storage location.
   The connection-id, file path etc are parsed from the arg 'db-key' which is the
   newly formed key for the copy that is going to be created
   "
  [db-key local-file-path dispatch-fn]
  (invoke-api "rs_save_as_kdbx" {:db-key db-key
                                 :local-file-path local-file-path} dispatch-fn))

(defn check-remote-modified
  "Asks the backend whether the remote file's mtime has diverged from the
   backup-cached value. Returns a map {:modified bool :remote-mtime <int|nil>}.
   :remote-mtime (seconds) lets callers key the Ignore snooze on the specific
   remote state, so a NEW change re-surfaces the dialog while an already-ignored
   one stays quiet. :remote-mtime is nil only when the server reports no mtime
   (in which case :modified is always false)."
  [db-key dispatch-fn]
  (invoke-api "rs_check_remote_modified" {:db-key db-key} dispatch-fn))

(defn acknowledge-remote-change
  "User accepted the remote divergence (chose 'Ignore'). Refreshes the cached
   mtime so subsequent polls don't re-prompt."
  [db-key dispatch-fn]
  (invoke-api "rs_acknowledge_remote_change" {:db-key db-key} dispatch-fn))

(defn merge-with-remote
  "Downloads remote bytes and three-way-merges into the in-memory db. Sets
   save_pending in the backend; user still needs to save to upload.
   Used by the save-error merge flow."
  [db-key dispatch-fn]
  (invoke-api "rs_merge_with_remote" {:db-key db-key} dispatch-fn))

(defn reload-with-remote
  "Downloads remote bytes, replaces the in-memory db, persists merged content to
   the backup file (not just mtime), copies to the iOS autofill app group, and
   clears save_pending. Returns the same MergeResult shape as merge-with-remote
   (counts are 'what changed on remote' when local has no pending edits).
   Used by the external-db-change flow (foreground poll / post-unlock / manual
   menu check) where save_pending is expected to be false."
  [db-key dispatch-fn]
  (invoke-api "rs_reload_with_remote" {:db-key db-key} dispatch-fn))

;; This is mainly to load the content of root dir using the connection-id
#_(defn list-dir
    "The arg 'connect-request' is a map and has  a key :type with value 'Sftp' or 'Webdav'
   The other keys are [:connection-id :parent-dir]
  "
    [connect-request dispatch-fn]
    (println "list-dir connect-request " connect-request)
    (invoke-api "rs_list_dir" {:rs-operation-type connect-request} dispatch-fn))

#_(defn connect-by-id
    "Creates a new connection if required using id after getting the config data from stored list
  "
    [connect-request dispatch-fn]
    (invoke-api "rs_connect_by_id" {:rs-operation-type connect-request} dispatch-fn))


(comment
  (require '[cljs.pprint]) ;;https://cljs.github.io/api/cljs.pprint/
  (cljs.pprint/pprint someobject)
  ;; daf114d0-a518-4e13-b75b-fbe893e69a9d 8bd81fe1-f786-46c3-b0e4-d215f8247a10
  ;; onekeepass.mobile.constants
  (def UUID-DEFAULT "00000000-0000-0000-0000-000000000000")

  (in-ns 'onekeepass.mobile.background-remote-server)

  (-> @re-frame.db/app-db :remote-storage keys)

  (def ios-c {:connection-id UUID-DEFAULT :name "SftpTest1" :host "192.168.1.4" :port 2022 :private-key "/Users/jeyasankar/mytemp/sftp_keys/sftp_id_rsa" :user-name "sf-user1" :password "Matrix.2" :start-dir "/"})


  ;; enum RemoteStorageConnect
  (def cr {:type "Sftp" :connection-info ios-c})

  (def connect-request {:type "Sftp" :connection-info ios-c})

  (def adroid-c {:connection-id UUID-DEFAULT :name "SftpTest1" :host "192.168.1.4" :port 2022 :private-key "/data/data/com.onekeepassmobile/files/sftp_id_rsa" :user-name "sf-user1" :password "Matrix.2" :start-dir "/"})


  (connect-and-retrieve-root-dir "Sftp" adroid-c)

  (def wc {:connection-id UUID-DEFAULT :name "WebdavTest1", :root-url "https://192.168.1.4:10080/" :user-name "sf-user1" :password "ss" :allow-untrusted-cert true})

  (def dp {:sftp-server-name "SftpTest1" :sftp-server-parent-dir "dav"}))

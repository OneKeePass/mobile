(ns onekeepass.mobile.background-remote-server
  (:require
   [react-native :as rn]
   [onekeepass.mobile.background-common :refer [invoke-api]]))

(set! *warn-on-infer* true)


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
   Gets all stored connection config info for Sftp or Webdav "
  [connect-request dispatch-fn]
  (invoke-api "rs_remote_storage_configs" {:rs-operation-type connect-request}  dispatch-fn))

(defn connect-and-retrieve-root-dir
  "The arg 'connect-request' is a map (type enum RemoteStorageOperationType) and has  
   a key :type with value 'Sftp' or 'Webdav' and other keys are [:connection-info]
   Connects to a Sftp or Webdav connection. Connection info fields from type 
   'SftpConnectionConfig' or 'WebdavConnectionConfig' are required "
  [connect-request dispatch-fn]
  (invoke-api "rs_connect_and_retrieve_root_dir" {:rs-operation-type connect-request} dispatch-fn))

(defn list-sub-dir 
  "The arg 'connect-request' is a map and has  a key :type with value 'Sftp' or 'Webdav'
   The other keys are [:connection-id :parent-dir :sub-dir :file-name]
  "
  [connect-request dispatch-fn]
  (invoke-api "rs_list_sub_dir" {:rs-operation-type connect-request} dispatch-fn))



(comment
  (require '[cljs.pprint]) ;;https://cljs.github.io/api/cljs.pprint/
  (cljs.pprint/pprint someobject)
    ;; daf114d0-a518-4e13-b75b-fbe893e69a9d 8bd81fe1-f786-46c3-b0e4-d215f8247a10
  ;; onekeepass.mobile.constants
  (def UUID-DEFAULT "00000000-0000-0000-0000-000000000000")
  (in-ns 'onekeepass.mobile.background-remote-server)

  (def ios-c {:connection-id UUID-DEFAULT :name "SftpTest1" :host "192.168.1.4" :port 2022 :private-key "/Users/jeyasankar/mytemp/sftp_keys/sftp_id_rsa" :user-name "sf-user1" :password "Matrix.2" :start-dir "/"})


  ;; enum RemoteStorageConnect
  (def cr {:type "Sftp" :connection-info ios-c})
  
  (def connect-request {:type "Sftp" :connection-info ios-c })

  (def adroid-c {:name "SftpTest1" :host "192.168.1.4" :port 2022 :private-key "/data/data/com.onekeepassmobile/files/sftp_id_rsa" :user-name "sf-user1" :password "Matrix.2" :start-dir "/"})


  (def wc {:connection-id UUID-DEFAULT :name "WebdavTest1", :root-url "https://192.168.1.4:10080/" :user-name "sf-user1" :password "ss" :allow-untrusted-cert true})

  (def dp {:sftp-server-name "SftpTest1" :sftp-server-parent-dir "dav"})
  )

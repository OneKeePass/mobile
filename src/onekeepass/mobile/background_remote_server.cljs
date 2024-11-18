(ns onekeepass.mobile.background-remote-server
  (:require
   [react-native :as rn]
   [onekeepass.mobile.background-common :refer [invoke-api]]))

(set! *warn-on-infer* true)


;; "Arg connect-request is of type enum RemoteStorageOperationType"

(defn connect-and-retrieve-root-dir
  "Arg connect-request is of type enum RemoteStorageOperationType"
  [connect-request dispatch-fn]
  #_(invoke-api "rs_connect_and_retrieve_root_dir" {:rs-connect-request connect-request} dispatch-fn)
  (invoke-api "rs_connect_and_retrieve_root_dir" {:rs-operation-type connect-request} dispatch-fn))

(defn list-sub-dir 
  [connect-request dispatch-fn]
  (invoke-api "rs_list_sub_dir" {:rs-operation-type connect-request} dispatch-fn))

;; (defn sftp-connect-and-retrieve-root-dir [sftp-connection-config dispatch-fn]
;;   (invoke-api "sftp_connect_and_retrieve_root_dir1" {:sftp-connection-config sftp-connection-config} dispatch-fn))

;; (defn sftp-list-dir [sftp-server-name  sftp-server-parent-dir dispatch-fn]
;;   (invoke-api "sftp_list_dir" {:sftp-server-name sftp-server-name :sftp-server-parent-dir sftp-server-parent-dir} dispatch-fn))

;; (defn webdav-connect-and-retrieve-root-dir [webdav-connection-config dispatch-fn]
;;   (invoke-api "webdav_connect_and_retrieve_root_dir" {:webdav-connection-config webdav-connection-config} dispatch-fn))

;; (defn webdav-list-dir [webdav-server-name  webdav-server-parent-dir dispatch-fn]
;;   (invoke-api "webdav_list_dir" {:webdav-server-name webdav-server-name :webdav-server-parent-dir webdav-server-parent-dir} dispatch-fn))


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

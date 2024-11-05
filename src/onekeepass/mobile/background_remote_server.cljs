(ns onekeepass.mobile.background-remote-server
  (:require
   [react-native :as rn]
   [onekeepass.mobile.background :refer [invoke-api]]))

(set! *warn-on-infer* true)

(defn sftp-connect-and-retrieve-root-dir [sftp-connection-config dispatch-fn]
  (invoke-api "sftp_connect_and_retrieve_root_dir" {:sftp-connection-config sftp-connection-config} dispatch-fn))

(defn sftp-list-dir [sftp-server-name  sftp-server-parent-dir dispatch-fn]
  (invoke-api "sftp_list_dir" {:sftp-server-name sftp-server-name :sftp-server-parent-dir sftp-server-parent-dir} dispatch-fn))

(defn webdav-connect-and-retrieve-root-dir [webdav-connection-config dispatch-fn]
  (invoke-api "webdav_connect_and_retrieve_root_dir" {:webdav-connection-config webdav-connection-config} dispatch-fn))

(defn webdav-list-dir [webdav-server-name  webdav-server-parent-dir dispatch-fn]
  (invoke-api "webdav_list_dir" {:webdav-server-name webdav-server-name :webdav-server-parent-dir webdav-server-parent-dir} dispatch-fn))


(comment
  (require '[cljs.pprint]) ;;https://cljs.github.io/api/cljs.pprint/
  (cljs.pprint/pprint someobject)
    ;; daf114d0-a518-4e13-b75b-fbe893e69a9d 8bd81fe1-f786-46c3-b0e4-d215f8247a10
  
  (in-ns 'onekeepass.mobile.background-remote-server) 
  
  (def ios-c {:name "SftpTest1" :host "192.168.1.4" :port 2022 :private-key "/Users/jeyasankar/mytemp/sftp_keys/sftp_id_rsa" :user-name "sf-user1" :password "Matrix.2" :start-dir "/"})
  
  (def adroid-c {:name "SftpTest1" :host "192.168.1.4" :port 2022 :private-key "/data/data/com.onekeepassmobile/files/sftp_id_rsa" :user-name "sf-user1" :password "Matrix.2" :start-dir "/"})
  
  
  (def wc {:name "WebdavTest1", :root-url "https://192.168.1.4:10080/" :user-name "sf-user1" :password "ss" :allow-untrusted-cert true})
  
  (def dp {:sftp-server-name "SftpTest1" :sftp-server-parent-dir "dav"})
  )
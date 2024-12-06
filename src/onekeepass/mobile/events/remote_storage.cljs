(ns onekeepass.mobile.events.remote-storage
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.events.common :as cmn-events :refer [on-ok on-error]]
   [onekeepass.mobile.background-remote-server :as bg-rs]
   [onekeepass.mobile.background :as bg]))

;; Maps kw to enum tag 'type''s value
(def kw-type-to-enum-tag {:sftp const/V-SFTP :webdav const/V-WEBDAV})

;; We use (csk/->kebab-case-keyword type) if we want to get the kw from  enum tag 'type'

(defn load-all-remote-connection-configs
  "Called one time during launch of the app to load previously stored configs"
  []
  (bg-rs/read-configs (fn [api-response]
                        (on-error api-response
                                  #(dispatch [:common/default-error
                                              "Error in loading remote storage all connection configs " %])))))

(defn remote-storage-type-selected
  "Should be called when user picks Sftp or Webdav option to create a new database or open existing databases"
  [kw-type]
  (dispatch [:remote-storage-type-selected kw-type]))

(defn remote-storage-rs-type-new-form-page-show []
  (dispatch [:remote-storage-rs-type-new-form-page-show]))

(defn connect-by-id-and-retrieve-root-dir
  "Called when user press on the name of any previously created config in the connections list"
  [kw-type connection-id]
  (dispatch [:remote-storage-connect-by-id-start kw-type connection-id]))

(defn remote-storage-sub-dir-listing-start [connection-id parent-dir sub-dir]
  (dispatch [:remote-storage-sub-dir-listing-start connection-id parent-dir sub-dir]))

;; Used only for Sftp form to pick the private key file if required
(defn pick-private-key-file
  "Called when user presses to select the private key file"
  []
  ;; We make first backend api call 'bg/pick-file' followed 
  ;; by the second backend api call 'bg-rs/copy-private-key-file' based on the response from first
  (bg/pick-file (fn [api-response]
                  (when-let [full-file-name
                             (on-ok
                              api-response
                              (fn [error]
                                (when-not (bg/document-pick-cancelled error)
                                  (dispatch [:common/default-error "Error while picking a file " error]))))]
                    ;; User picked a file and follow up backend call to handle that selection
                    ;; Uses the generic handler
                    (bg/handle-picked-file
                     full-file-name {const/PICKED-FILE-HANDLER-TAG const/V-SFTP-PRIVATE-KEY-FILE}
                     (fn [res]
                       (when-let [kf-info
                                  (on-ok
                                   res
                                   #(dispatch [:common/default-error "Error while handling the picked file " %]))]
                         (dispatch [:remote-storage-picked-private-key-file-handling-complete  kf-info]))))))))

(defn remote-storage-connection-form-data-update
  "Updates a field in the form with entered value"
  [kw-type field-name-kw value]
  (dispatch [:remote-storage-connection-form-data-update kw-type field-name-kw value]))

(defn remote-storage-new-config-connect-and-save
  "Called to connect to the remote storage service first time using the entered 
   config data and saves the new config"
  [kw-type]
  (dispatch [:remote-storage-new-config-connect-and-save kw-type]))

(defn remote-storage-listing-previous
  "Determines whether to use page stack to go the previous page or remain in the same page 
   but update the listing data by removing the current page data"
  []
  (dispatch [:remote-storage-listing-previous]))

(defn remote-storage-delete-selected-config [connection-id]
  (dispatch [:remote-storage-delete-selected-config connection-id]))

(defn remote-storage-config-view
  "Called to show the read only view of a remote storage config form"
  [connection-id]
  (dispatch [:remote-storage-config-view connection-id]))

(defn remote-storage-connection-configs
  "All connection config list for the sftp or webdav"
  [kw-type]
  (subscribe [:remote-storage-connection-configs kw-type]))

(defn remote-storage-connection-form-data
  "Data for the form when a new connection is added"
  [kw-type]
  (subscribe [:remote-storage-connection-form-data kw-type]))

(defn remote-storage-connection-form-errors
  "Data entry validation errors"
  [kw-type]
  (subscribe [:remote-storage-connection-form-errors kw-type]))

(defn remote-storage-current-rs-type
  "Determines to show the connetion form for the storage location type: sftp or webdav "
  []
  (subscribe [:remote-storage-current-rs-type]))

(defn remote-storage-listing-to-show
  "Called to show the dir entry listing data from the last one of the listings stack
   Returns a map ( from struct ConnectedStatus)
  "
  []
  (subscribe [:remote-storage-listing-to-show]))


(defn- get-current-rs-type [db]
  (get-in db [:remote-storage :current-rs-type]))

(def sftp-init-data {:connection-id const/UUID-DEFAULT
                     :name nil
                     :host nil
                     :port nil
                     :private-key-full-file-name nil
                     :private-key-file-name nil
                     :user-name nil
                     :password nil
                     :start-dir nil
                     ;; UI specific fields
                     :logon-type "password" ;; or "privateKey" from modal selector
                     :password-visible false
                     :edit true})

(def webdav-init-data {:connection-id const/UUID-DEFAULT
                       :name nil
                       :root-url nil
                       :user-name nil
                       :password nil
                       :allow-untrusted-cert true

                       ;; UI specific fields
                       :password-visible false
                       :edit true})

;; kw-type :sftp or :webdav
(defn- init-type-data [app-db kw-type] 
  (let [data (if (= kw-type :sftp) sftp-init-data webdav-init-data)]
    (-> app-db (assoc-in  [:remote-storage kw-type :form-data] data)
        (assoc-in  [:remote-storage kw-type :form-errors] {}))))

(defn- set-type-form-data [app-db kw-type form-data]
  (-> app-db (assoc-in  [:remote-storage kw-type :form-data] form-data)
      (assoc-in  [:remote-storage kw-type :form-errors] {})))

(defn- merge-data [db kw-type & {:as kws}]
  (let [data (get-in db [:remote-storage kw-type :form-data])
        data (merge data kws)]
    (assoc-in db [:remote-storage kw-type :form-data] data)))

(defn- merge-type-form-data [kw-type {:keys [private-key-file-name] :as form-data} edit]
  (if (= kw-type :sftp)
    (merge form-data {:edit edit  :logon-type (if-not (nil? private-key-file-name) 
                                                "privateKey" 
                                                "password")})
    (merge form-data {:edit edit})))

(defn- validate-sftp-fields
  "Validates storage specific fields and returns errors map if any"
  [app-db]
  (let [{:keys [name host port user-name password private-key-file-name logon-type]} (get-in app-db [:remote-storage :sftp :form-data])

        ;;errors {} 
        ;;errors (merge errors (if (str/blank? host) {:host "Valid value is required"} {}))

        errors (cond-> {}
                 (str/blank? name) (merge {:name "Valid value is required"}) 
                 (str/blank? host) (merge {:host "Valid value is required"})
                 (str/blank? port) (merge {:port "Valid value is required"})
                 (str/blank? user-name) (merge {:user-name "Valid value is required"})
                 (and (= logon-type "password") (str/blank? password)) (merge {:password "Valid value is required"})
                 (and (= logon-type "privateKey") (str/blank? private-key-file-name)) (merge {:private-key-file-name "Valid value is required"}))]

    errors))

(defn- validate-webdav-fields
  "Validates storage specific fields and returns errors map if any"
  [app-db]
  (let [{:keys [name root-url user-name password]} (get-in app-db [:remote-storage :webdav :form-data]) 
        errors (cond-> {}
                 (str/blank? name) (merge {:name "Valid value is required"}) 
                 (str/blank? root-url) (merge {:root-url "Valid value is required"}) 
                 (str/blank? user-name) (merge {:user-name "Valid value is required"})
                 (str/blank? password) (merge {:password "Valid value is required"}))]

    errors))

(defn- validate-fields [app-db kw-type]
  (condp = kw-type
    :sftp (validate-sftp-fields app-db)
    :webdav (validate-webdav-fields app-db)))

;; Updates individual field of a form
(reg-event-db
 :remote-storage-connection-form-data-update
 (fn [db [_event-id kw-type field-name-kw value]]
   (-> db
       (merge-data kw-type field-name-kw value)
       ;; Just remove the error for this field
       (assoc-in [:remote-storage kw-type :form-errors field-name-kw] nil))))

;; Called when a list of connection configs for either :sftp or :webdav are loaded in 
;; a backend call
;; Calls the next page displaying the connection names for this type
(reg-event-fx
 :remote-storage-connection-configs-loaded
 (fn [{:keys [db]} [_query-id {:keys [type content] :as _info}]]
   {:db (-> db (assoc-in [:remote-storage :configs (csk/->kebab-case-keyword type)] content))
    :fx [[:dispatch [:common/next-page const/RS_CONNECTIONS_LIST_PAGE_ID "connections"]]]}))

;; Called from the configs listing page to add a new config for the previously set remote type
(reg-event-fx
 :remote-storage-rs-type-new-form-page-show
 (fn [{:keys [db]} [_query-id]]
   (let [;; The remote type is already set to :sftp or :webdav
         curr-kw-type (get-current-rs-type db) #_(-> db (get-in [:remote-storage :current-rs-type]))
         name (kw-type-to-enum-tag curr-kw-type)]
     {:db (-> db (init-type-data curr-kw-type))
      :fx [[:dispatch [:common/next-page const/RS_CONNECTION_CONFIG_PAGE_ID name]]]})))

(reg-event-fx
 :remote-storage-config-view
 (fn [{:keys [db]} [_query-id connection-id]]
   (let [curr-kw-type (get-current-rs-type db)
         configs-v (get-in db [:remote-storage :configs curr-kw-type])
         {:keys [] :as form-data} (first (filter (fn [m] (= (:connection-id m) connection-id)) configs-v))
         form-data (merge-type-form-data curr-kw-type form-data false)  #_(merge form-data {:edit false :logon-type (if-not (nil? "") "privateKey" "password")})
         name (kw-type-to-enum-tag curr-kw-type)]

     {:db (-> db (set-type-form-data curr-kw-type form-data))
      :fx [[:dispatch [:common/next-page const/RS_CONNECTION_CONFIG_PAGE_ID name]]]})))

;; Dispatched in bg/handle-picked-file after user picks the private key file and that file is 
;; copied to internal location. Only the file name is used in config
;; Sftp only
(reg-event-fx
 :remote-storage-picked-private-key-file-handling-complete
 (fn [{:keys [db]} [_query-id  {:keys [full-file-name file-name]}]]
   {:db (-> db (merge-data :sftp :private-key-file-name file-name :private-key-full-file-name full-file-name)
            ;; Need to remove any prior error msg
            (assoc-in [:remote-storage :sftp :form-errors :private-key-file-name] nil))}))

;; Connects to the remote storage using the config entered
(reg-event-fx
 :remote-storage-new-config-connect-and-save
 (fn [{:keys [db]} [_query-id kw-type]]
   (let [errors (validate-fields db kw-type)]
     (if (empty? errors)
       ;; No errors found and backend api is called
       (let [connection-info (get-in db [:remote-storage kw-type :form-data])]
         {:fx [[:dispatch [:common/message-modal-show nil "Connecting..."]]
               [:bg-rs-connect-and-retrieve-root-dir [kw-type connection-info]]]})
       ;; Errors in data entry
       {:db (-> db (assoc-in [:remote-storage kw-type :form-errors] errors))}))))

(reg-fx
 :bg-rs-connect-and-retrieve-root-dir
 (fn [[kw-type connection-info]]
   (bg-rs/connect-and-retrieve-root-dir
    kw-type connection-info
    (fn [api-response]
      (when-let [connected-status (on-ok api-response)]
        (dispatch [:remote-storage-connect-save-complete kw-type connected-status]))))))


(reg-event-fx
 :remote-storage-connect-save-complete
 (fn [{:keys [_db]} [_query-id kw-type _connected-status]]
   ;; Returned 'connected-status' is not used. To support a proper navigation flow
   ;; we remain on the connections page itself with updated new connections list
   {:fx [;; Need to reload for newly added configs 
         [:bg-rs-remote-storage-configs-for-type [kw-type]]
         [:dispatch [:common/previous-page]]
         [:dispatch [:common/message-modal-hide]]
         [:dispatch [:common/message-snackbar-open "New config added"]]]}))

;; Appends to a vec (listings) where an item is a map ( struct ConnectStatus)
(reg-event-fx
 :remote-storage-dir-listing-loaded
 (fn [{:keys [db]} [_query-id kw-type connected-status]]
   ;; connected-status is a map e.g {:connection-id "" :dir-entries {} } ( struct ConnectStatus)
   ;; (println "dir-listing-loaded as " connected-status)
   (let [listings (get-in db [:remote-storage kw-type :listings])
         listings (if (vector? listings) listings (vec listings))
         listings (conj listings connected-status)]
     {:db (-> db (assoc-in [:remote-storage kw-type :listings] listings))})))

(reg-event-fx
 :remote-storage-connect-by-id-start
 (fn [{:keys [_db]} [_query-id kw-type connection-id]]
   {:fx [[:dispatch [:common/message-modal-show nil "Connecting..."]]
         [:bg-rs-connect-by-id-and-retrieve-root-dir [kw-type connection-id]]]}))

(reg-fx
 :bg-rs-connect-by-id-and-retrieve-root-dir
 (fn [[kw-type connection-id]]
   (bg-rs/connect-by-id-and-retrieve-root-dir
    kw-type
    connection-id
    (fn [api-response]
      (when-let [connected-status (on-ok api-response)]
        (dispatch [:remote-storage-connect-by-id-complete kw-type connected-status]))))))

(reg-event-fx
 :remote-storage-connect-by-id-complete
 (fn [{:keys [_db]} [_query-id kw-type connected-status]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:dispatch [:remote-storage-dir-listing-loaded kw-type connected-status]]
         [:dispatch [:common/next-page const/RS_FILES_FOLDERS_PAGE_ID "Dir Entries"]]]}))

(reg-event-fx
 :remote-storage-sub-dir-listing-start
 (fn [{:keys [db]} [_query-id connection-id parent-dir sub-dir]]
   (let [kw-type (get-current-rs-type db)]
     {:fx [[:bg-rs-list-sub-dir [kw-type connection-id parent-dir sub-dir]]]})))

(reg-fx
 :bg-rs-list-sub-dir
 (fn [[kw-type connection-id parent-dir sub-dir]]
   (bg-rs/list-sub-dir kw-type connection-id parent-dir sub-dir
                       (fn [api-response]
                         (when-let [dir-entries (on-ok api-response)]
                               ;; Need to form a map equivalent to struct ConnectedStatus
                               ;; as used in 'bg-rs-connect-and-retrieve-root-dir'
                           (dispatch [:remote-storage-dir-listing-loaded
                                      kw-type
                                      {:connection-id connection-id
                                       :dir-entries dir-entries}]))))))

;; Called when user presses the back button
(reg-event-fx
 :remote-storage-listing-previous
 (fn [{:keys [db]} [_query-id]]
   (let [kw-type (get-current-rs-type db)
         listings (get-in db [:remote-storage kw-type :listings])
         ;;_ (println "before (count listings) " (count listings))
         ;; Drop the current listing showing
         listings (-> listings drop-last vec)
         ;; Page stack previous page is called if no more listing is found
         cnt (count listings)]
     #_(println "cnt is " cnt)
     {:db (-> db (assoc-in [:remote-storage kw-type :listings] listings))
      :fx [(when (= cnt 0)
             [:dispatch [:common/previous-page]])]})))

;; Sets the type selected during the Open Database or New Database call
;; and loads the list of configs for the type
(reg-event-fx
 :remote-storage-type-selected
 (fn [{:keys [db]} [_query-id kw-type]]
   {;; should we dispatch :remote-storage-current-rs-type-set instead of db update?
    :db (-> db (assoc-in [:remote-storage :current-rs-type] kw-type))
    :fx [[:bg-rs-remote-storage-configs-for-type [kw-type]]]}))

(reg-fx
 :bg-rs-remote-storage-configs-for-type
 (fn [[kw-type]]
   (bg-rs/remote-storage-configs {:type (kw-type kw-type-to-enum-tag)}
                                 (fn [api-response]
                                   (when-let [info (on-ok api-response)]
                                     (dispatch [:remote-storage-connection-configs-loaded info]))))))
(reg-event-fx
 :remote-storage-delete-selected-config
 (fn [{:keys [db]} [_query-id connection-id]]
   (let [kw-type (get-current-rs-type db)]
     ;; Side effect call
     (bg-rs/delete-config kw-type connection-id
                          (fn [api-response]
                            (when-not (on-error api-response)
                              (dispatch [:remote-storage-delete-selected-config-complete kw-type]))))
     {})))

(reg-event-fx
 :remote-storage-delete-selected-config-complete
 (fn [{:keys [_db]} [_query-id kw-type]]
   {:fx [[:bg-rs-remote-storage-configs-for-type [kw-type]]]}))

;; Gets the last dir entries data to show on the page
(reg-sub
 :remote-storage-listing-to-show
 (fn [db [_query-id]]
   (let [kw-type (get-current-rs-type db)]
     (-> db (get-in [:remote-storage kw-type :listings]) last))))

(reg-sub
 :remote-storage-listings
 (fn [db [_query-id kw-type]]
   (-> db (get-in [:remote-storage kw-type :listings]))))

;; This is set to show the config form for :sftp or :webdav
#_(reg-sub
   :remote-storage-current-form-type
   (fn [db [_query-id]]
     (-> db (get-in [:remote-storage :current-form-type]))))

;; TODO Use this for current-form-type
;; Valid values are :sftp or :webdav for now
(reg-sub
 :remote-storage-current-rs-type
 (fn [db [_query-id]]
   (-> db (get-in [:remote-storage :current-rs-type]))))

(reg-sub
 :remote-storage-connection-configs
 (fn [db [_query-id kw-type]]
   (let [configs (-> db (get-in [:remote-storage :configs kw-type]))]
     configs)))

(reg-sub
 :remote-storage-connection-form-data
 (fn [db [_query-id kw-type]]
   (get-in db [:remote-storage kw-type :form-data])))

(reg-sub
 :remote-storage-connection-form-errors
 (fn [db [_query-id kw-type]]
   (get-in db [:remote-storage kw-type :form-errors])))

(comment
  (in-ns 'onekeepass.mobile.events.remote-storage)
  (-> @re-frame.db/app-db :remote-storage)
  (def db-key (-> @re-frame.db/app-db :current-db-file-name)))



#_(bg-rs/connect-by-id-and-retrieve-root-dir
   kw-type
   connection-id
   (fn [api-response]
     (when-let [connected-status (on-ok api-response)]
       (dispatch [:remote-storage-connect-by-id-complete kw-type connected-status]))))
;; TODO Repalce the use of current-form-type with current-rs-type 
#_(reg-event-db
   :remote-storage-current-rs-type-set
   (fn [db [_event-id kw-type]]
     (-> db (assoc-in [:remote-storage :current-rs-type] kw-type))))

#_(reg-fx
   :bg-rs-connect-by-id-and-retrieve-root-dir
   (fn [[kw-type connection-id]]
     (bg-rs/connect-by-id-and-retrieve-root-dir kw-type  connection-id
                                                (fn [api-response]
                                                  (when-let [connected-status (on-ok api-response)]
                                                    (dispatch [:remote-storage-connect-by-id-complete kw-type connected-status]))))))


#_(defn connect-by-id-and-retrieve-root-dir [kw-type connection-id]
    (bg-rs/connect-by-id-and-retrieve-root-dir kw-type  connection-id
                                               (fn [api-response]
                                                 (when-let [connected-status (on-ok api-response)]
                                                   (dispatch [:remote-storage-dir-listing-loaded kw-type connected-status])))))


#_(defn remote-storage-current-form-set
    "Called to show the connection config form for :sftp or for :webdav connection"
    [kw-type]
    (dispatch [:remote-storage-current-form-set kw-type]))
;; Should be called when user presses "Add"
#_(reg-event-fx
   :remote-storage-current-form-set
   (fn [{:keys [db]} [_query-id kw-type]]
     {:db (-> db (assoc-in [:remote-storage :current-form-type] kw-type)
              (init-type-data kw-type))
      :fx [[:dispatch [:common/next-page const/RS_CONNECTION_CONFIG_PAGE_ID "sftp"]]]}))

#_(defn load-sub-dir-listing
    "Loads the dir listing under a given parent-dir/sub-dir
   using the connection identified by the connection id"
    [kw-type connection-id parent-dir sub-dir]
    (bg-rs/list-sub-dir connection-id parent-dir sub-dir
                        (fn [api-response]
                          (when-let [dir-entries (on-ok api-response)]
                          ;; Need to form a map equivalent to struct ConnectedStatus
                          ;; as used in 'bg-rs-connect-and-retrieve-root-dir'
                            (dispatch [:remote-storage-dir-listing-loaded
                                       kw-type
                                       {:connection-id connection-id
                                        :dir-entries dir-entries}])))))

;; Do we need to have this fn?
#_(defn remote-storage-current-rs-type-set
    "Called to set the currently selected remote type. Valid values are :sftp or :webdav for now
   This fn is called when user presses the Sftp or WebDav option
   See the corresponding 'subscribe' 
   Most of remote operations depend upon this type information
   "
    [kw-type]
    (dispatch [:remote-storage-current-rs-type-set kw-type]))

#_(defn remote-storage-current-form-type
    "Determines to show the connetion form for the storage location type: sftp or webdav "
    []
    (subscribe [:remote-storage-current-form-type]))

#_(defn remote-storage-listings
    [kw-type]
    (subscribe [:remote-storage-listings kw-type]))

#_(defn load-remote-connection-configs
    "Called to get the list of Sftp or Webdav connection configs"
    [kw-type]
    (bg-rs/remote-storage-configs {:type (kw-type kw-type-to-enum-tag)}
                                  (fn [api-response]
                                    (when-let [info (on-ok api-response)]
                                      (dispatch [:remote-storage-connection-configs-loaded info])))))

#_(reg-event-fx
   :remote-storage-list-sub-dir
   (fn [{:keys [db]} [_query-id kw-type connection-id parent-dir sub-dir]]))
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

(defn load-remote-connection-configs
  "Called to get the list of Sftp or Webdav connection configs"
  [kw-type]
  (bg-rs/remote-storage-configs {:type (kw-type kw-type-to-enum-tag)}
                                (fn [api-response]
                                  (when-let [info (on-ok api-response)]
                                    (dispatch [:remote-storage-connection-configs-loaded info])))))

;; Used only for Sftp form
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

(defn remote-storage-connection-form-data-update [kw-type field-name-kw value]
  (dispatch [:remote-storage-connection-form-data-update kw-type field-name-kw value]))

(defn remote-storage-current-form-set [kw-type]
  (dispatch [:remote-storage-current-form-set kw-type]))

(defn remote-storage-new-config-connect-and-save
  "Called to connect to the remote storage service using the entered config data and saves the new config"
  [kw-type]
  (dispatch [:remote-storage-new-config-connect-and-save kw-type]))

(defn remote-storage-connection-configs [kw-type]
  (subscribe [:remote-storage-connection-configs kw-type]))

(defn remote-storage-connection-form-data [kw-type]
  (subscribe [:remote-storage-connection-form-data kw-type]))

(defn remote-storage-connection-form-errors [kw-type]
  (subscribe [:remote-storage-connection-form-errors kw-type]))

(defn remote-storage-current-form-type []
  (subscribe [:remote-storage-current-form-type]))

(def sftp-init-data {:connection-id const/UUID-DEFAULT
                     :name nil
                     :host nil
                     :port nil
                     :logon-type "password" ;; or "privateKey" from modal selector
                     :private-key-full-file-name nil
                     :private-key-file-name nil
                     :user-name nil
                     :password nil
                     :password-visible false
                     :start-dir nil})

(def webdav-init-data {:connection-id const/UUID-DEFAULT
                       :name nil
                       :root-url nil
                       :user-name nil
                       :password nil
                       :allow-untrusted-cert false})

;; kw-type :sftp or :webdav
(defn- init-type-data [app-db kw-type]
  (let [data (if (= kw-type :sftp) sftp-init-data webdav-init-data)]
    (-> app-db (assoc-in  [:remote-storage kw-type :form-data] data)
        (assoc-in  [:remote-storage kw-type :form-errors] {}))))

(defn- merge-data [db kw-type & {:as kws}]
  (let [data (get-in db [:remote-storage kw-type :form-data])
        data (merge data kws)]
    (assoc-in db [:remote-storage kw-type :form-data] data)))

(defn- validate-sftp-fields
  "Validates storage specific fields and returns errors map if any"
  [app-db]
  (let [{:keys [host port user-name password private-key-file-name logon-type]} (get-in app-db [:remote-storage :sftp :form-data])

        ;;errors {} 
        ;;errors (merge errors (if (str/blank? host) {:host "Valid value is required"} {}))

        errors (cond-> {}
                 (str/blank? host) (merge {:host "Valid value is required"})
                 (str/blank? port) (merge {:port "Valid value is required"})
                 (str/blank? user-name) (merge {:user-name "Valid value is required"})
                 (and (= logon-type "password") (str/blank? password)) (merge {:password "Valid value is required"})
                 (and (= logon-type "privateKey") (str/blank? private-key-file-name)) (merge {:private-key-file-name "Valid value is required"}))]

    errors))

(defn- validate-fields [app-db kw-type]
  (condp = kw-type
    :sftp (validate-sftp-fields app-db)
    :webdav #()))

;; Updates individual field of a form
(reg-event-db
 :remote-storage-connection-form-data-update
 (fn [db [_event-id kw-type field-name-kw value]]
   (-> db
       (merge-data kw-type field-name-kw value)
       ;; Just remove the error for this field
       (assoc-in [:remote-storage kw-type :form-errors field-name-kw] nil))))

;; Called when a list of connection configs for either :sftp or :webdav are loaded
(reg-event-fx
 :remote-storage-connection-configs-loaded
 (fn [{:keys [db]} [_query-id {:keys [type content] :as _info}]]
   {:db (-> db (assoc-in [:remote-storage :configs (csk/->kebab-case-keyword type)] content))}))

;; Should be called when user presses "Add"
(reg-event-fx
 :remote-storage-current-form-set
 (fn [{:keys [db]} [_query-id kw-type]]
   {:db (-> db (assoc-in [:remote-storage :current-form-type] kw-type)
            (init-type-data kw-type))
    :fx [[:dispatch [:common/next-page const/RS_CONNECTION_CONFIG_PAGE_ID "sftp"]]]}))

;; Dispatched in bg/handle-picked-file  
;; Sftp only
(reg-event-fx
 :remote-storage-picked-private-key-file-handling-complete
 (fn [{:keys [db]} [_query-id  {:keys [full-file-name file-name]}]]
   {:db (-> db (merge-data :sftp
                           :private-key-file-name file-name
                           :private-key-full-file-name full-file-name)
            ;; Need to remove any prior error msg
            (assoc-in [:remote-storage :sftp :form-errors :private-key-file-name] nil))}))

(reg-event-fx
 :remote-storage-new-config-connect-and-save
 (fn [{:keys [db]} [_query-id kw-type]]
   (let [errors (validate-fields db kw-type)]
     (if (empty? errors)
       (let [data (get-in db [:remote-storage kw-type :form-data])
             request {:type (kw-type-to-enum-tag kw-type)
                      :connection_info data}]
         {:fx [[:bg-rs-connect-and-retrieve-root-dir [request]]]})

       {:db (-> db (assoc-in [:remote-storage kw-type :form-errors] errors))}))))

(reg-fx
 :bg-rs-connect-and-retrieve-root-dir
 (fn [[connect-request]]
   (bg-rs/connect-and-retrieve-root-dir connect-request
                                        (fn [api-response]
                                          (when-let [listing  (on-ok api-response)]
                                            (println "Listing is " listing))))))

(reg-sub
 :remote-storage-current-form-type
 (fn [db [_query-id]]
   (-> db (get-in [:remote-storage :current-form-type]))))

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
  (def db-key (-> @re-frame.db/app-db :current-db-file-name)))

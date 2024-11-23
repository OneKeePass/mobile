(ns onekeepass.mobile.events.remote-storage
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [camel-snake-kebab.core :as csk]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.events.common :as cmn-events :refer [on-ok on-error]]
   [onekeepass.mobile.background-remote-server :as bg-rs]
   [onekeepass.mobile.background :as bg]))


(defn load-all-remote-connection-configs
  "Called onetime during launch of the app to load previously stored configs"
  []
  (bg-rs/read-configs (fn [api-response]
                        (on-error api-response
                                  #(dispatch [:common/default-error
                                              "Error in loading remote storage all connection configs " %])))))

(defn load-remote-connection-configs
  "Called to get the list of Sftp or Webdav connection configs"
  [type]
  (bg-rs/remote-storage-configs {:type type}
                                (fn [api-response]
                                  (when-let [info (on-ok api-response)]
                                    (dispatch [:remote-storage-connection-configs-loaded info])))))


(defn pick-private-key-file
  "Called when user clicks the browse button"
  []
  ;; We make first backend api call 'bg/pick-file' followed 
  ;; by the second backend api call 'bg-rs/copy-private-key-file' based on the response from first
  (bg/pick-file (fn [api-response]
                              (when-let [full-file-name
                                         (on-ok
                                          api-response)]
                                ;; User picked a key file and needs to be copied
                                (println "full-file-name is picked " full-file-name)
                                (bg/handle-picked-file
                                   full-file-name {:handler "SftpPrivateKeyFile"}
                                   (fn [res]
                                     (println "handler resp " res)
                                     #_(when-let [kf-info (on-ok
                                                           res
                                                           #(dispatch [:common/default-error "Key File Error" %]))]
                                         (dispatch [:key-file-copied kf-info]))))))))


(defn remote-storage-connection-form-data-update [kw-type field-name-kw value]
  (dispatch [:remote-storage-connection-form-data-update kw-type field-name-kw value]))

(defn remote-storage-current-form-set [kw-type]
  (dispatch [:remote-storage-current-form-set kw-type]))

(defn remote-storage-connection-configs [kw-type]
  (subscribe [:remote-storage-connection-configs kw-type]))

(defn remote-storage-connection-form-data [kw-type]
  (subscribe [:remote-storage-connection-form-data kw-type]))

(defn remote-storage-current-form-type []
  (subscribe [:remote-storage-current-form-type]))

(def sftp-init-data {:connection-id ""
                     :name nil
                     :host nil
                     :port nil
                     :logon-type "password"
                     :private-key nil
                     :user-name nil
                     :password nil
                     :password-visible false
                     :start-dir nil})

(def webdav-init-data {:connection-id ""
                       :name nil
                       :root-url nil
                       :user-name nil
                       :password nil
                       :allow-untrusted-cert false})
;; kw-type :sftp or :webdav
(defn- init-type-data [app-db kw-type]
  (let [data (if (= kw-type :sftp) sftp-init-data webdav-init-data)]
    (assoc-in app-db [:remote-storage kw-type :form-data] data)))

(defn- merge-data [db kw-type & {:as kws}]
  (let [data (get-in db [:remote-storage kw-type :form-data])
        data (merge data kws)]
    (assoc-in db [:remote-storage kw-type :form-data] data)))

(reg-event-db
 :remote-storage-connection-form-data-update
 (fn [db [_event-id kw-type field-name-kw value]]
   (-> db
       (merge-data kw-type field-name-kw value))))

(reg-event-fx
 :remote-storage-connection-configs-loaded
 (fn [{:keys [db]} [_query-id {:keys [type content] :as info}]]
   (println "Loaded confs info are " info)
   {:db (-> db (assoc-in [:remote-storage :configs (csk/->kebab-case-keyword type)] content))}))

;; Should be called when user presses "Add"
(reg-event-fx
 :remote-storage-current-form-set
 (fn [{:keys [db]} [_query-id kw-type]]
   {:db (-> db (assoc-in [:remote-storage :current-form-type] kw-type)
            (init-type-data kw-type))
    :fx [[:dispatch [:common/next-page const/RS_CONNECTION_CONFIG_PAGE_ID "sftp"]]]}))

;;pick-file-to-copy



(reg-sub
 :remote-storage-current-form-type
 (fn [db [_query-id]]
   (-> db (get-in [:remote-storage :current-form-type]))))

(reg-sub
 :remote-storage-connection-configs
 (fn [db [_query-id kw-type]]
   (let [configs (-> db (get-in [:remote-storage :configs kw-type #_(csk/->kebab-case-keyword type)]))]
     configs)
   #_[{:name "Connection1" :user-name "Name1"} {:name "Connection2" :user-name "Name2"}]))

(reg-sub
 :remote-storage-connection-form-data
 (fn [db [_query-id kw-type]]
   (get-in db [:remote-storage kw-type :form-data])))


;; (reg-event-fx
;;  :remote-storage/read-configs
;;  (fn [{:keys [_db]} [_event-id]]
;;    {:fx [[:bg-read-configs]]}))

;; (reg-fx
;;  :bg-read-configs
;;  (fn []
;;    (bg-rs/read-configs (fn [api-response]
;;                          (on-error api-response
;;                                    #(dispatch [:common/default-error
;;                                                "Error in loading remote storage all connection configs " %]))))))


#_(reg-fx
   :bg-rs
   (fn [type]
     (bg-rs/remote-storage-configs {:type type}
                                   (fn [api-response]
                                     (when-let [info (on-ok api-response)]
                                       (dispatch [:remote-storage-connection-configs-loaded info]))))))

(comment
  (in-ns 'onekeepass.mobile.events.remote-storage))

(ns onekeepass.mobile.events.common
  "All common events that are used across many pages"
  (:require [cljs.core.async :refer [<! go timeout]]
            [clojure.string :as str]
            [onekeepass.mobile.background-remote-server :as bg-rs]
            [onekeepass.mobile.background :as bg :refer [is-Android]]
            [onekeepass.mobile.utils :as u :refer [str->int tags->vec]]
            [re-frame.core :refer [dispatch dispatch-sync reg-event-db
                                   reg-event-fx reg-fx reg-sub subscribe]]))

(def home-page-title "home")

(defn sync-initialize
  "Called just before rendering to set all requied values in re-frame db"
  []
  ;; For now load-app-preference also gets any uri of kdbx database in case user pressed .kdbx file 
  (dispatch-sync [:load-app-preference]))

(defn- default-error-fn [error]
  (println "API returned error: " error)
  (dispatch [:common/error-box-show 'apiError error])
  (dispatch [:common/message-modal-hide]))

(defn on-error
  "A common error handler for the background API call.
  logs the error and returns true in case of error or false
  "
  ([{:keys [error]} error-fn]
   (if-not (nil? error)
     (do
       (if  (nil? error-fn)
         (default-error-fn error)
         (error-fn error))
       true)
     false))
  ([api-response]
   (on-error api-response nil)))

(defn on-ok
  "Receives a map with keys result and error or either one.
  Returns the value of result in case there is no error or returns nil if there is an error and 
  calls the error-fn with error value
  "
  ([{:keys [ok error]} error-fn]
   (if-not (nil? error)
     (do
       (if (nil? error-fn)
         (default-error-fn error)
         (error-fn error))
       nil)
     ok))
  ([api-response]
   (on-ok api-response nil)))

(defn active-db-key
  "Returns the current database key 'db-key'"
  ;; To be called only in react components as it used 'subscribe' (i.e in React context)
  ([]
   (subscribe [:active-db-key]))
  ;; Used in reg-event-db , reg-event-fx by passing the main re-frame global 'app-db' 
  ([app-db]
   (:current-db-file-name app-db)))

(defn assoc-in-key-db
  "Called to associate the value in 'v' to the keys 'ks' location 
  in the db for the currently selected db key and then updates the main db 
  with this new key db
  Returns the main db 
  "
  [app-db ks v]
  ;; Get current db key and update that map with v in ks
  ;; Then update the main db
  (let [kdbx-db-key (active-db-key app-db)
        kdb (get app-db kdbx-db-key)]
    ;; kdb is the db info map found for the current db key 'kdbx-db-key'
    (assoc app-db kdbx-db-key (assoc-in kdb ks v))))

(defn assoc-in-selected-db
  "Called to associate the value in 'v' to the keys 'ks' location 
  in the db info map found by using the passed db-key and then updates the main db 
  with this new key db
  Returns the main db 
  "
  [app-db db-key ks v]
  ;; Get db info using the db-key arg and update that map with v in ks
  ;; Then update the main db
  (let [kdb (get app-db db-key)]
    ;; kdb is the db info map found for the db-key
    (assoc app-db db-key (assoc-in kdb ks v))))

(defn get-in-key-db
  "Gets the value for the key lists from an active kdbx content"
  [app-db ks]
  ;; First we get the kdbx content map and then supplied keys 'ks' used to get the actual value
  (get-in app-db (into [(active-db-key app-db)] ks)))

(defn get-in-selected-db
  "Gets the value for the key lists from database info using the passed database key 'db-key'"
  [app-db db-key ks]
  ;; First we get the kdbx content map and then supplied keys 'ks' used to get the actual value
  (get-in app-db (into [db-key] ks)))

(defn db-opened
  "Updates the db list and current active db key when a new kdbx database is loaded
  The args are the re-frame 'app-db' and KdbxLoaded struct returned by backend API.
  Returns the updated app-db
  "
  [app-db {:keys [db-key database-name file-name key-file-name]}] ;;kdbx-loaded 
  (let [app-db  (if (nil? (:opened-db-list app-db)) (assoc app-db :opened-db-list []) app-db)
        ;; Existing db-key is removed first to maintain unique db-key
        dbs (filterv (fn [m] (not= db-key (:db-key m))) (:opened-db-list app-db))
        app-db (assoc app-db :opened-db-list dbs)]
    (-> app-db
        (assoc :current-db-file-name db-key)
         ;; opened-db-list is a vec of map with keys [db-key database-name file-name user-action-time]
         ;; :database-name is different from :file-name found in the the map Preference -> RecentlyUsed
         ;; See ':recently-used' subscription 
         ;; The latest opened db is last in the vector
        (update-in [:opened-db-list] conj {:db-key db-key
                                           :database-name database-name
                                           :file-name file-name
                                           ;; user-action-time is used for db timeout
                                           ;; See onekeepass.mobile.events.app-settings
                                           :user-action-time (js/Date.now)
                                           ;;:database-name (:database-name meta)
                                           }))))

#_(defn is-db-in-opened-dbs? [app-db database-file-name]
  ;; not-any? Returns false if (pred x) is logical true - 'database-file-name' is found in db-list - else returns true
  ;; need to do the complement (not fn) to return the expected true or false as the name of this fn indicates
    (not (not-any? (fn [{:keys [db-key]}] (= db-key database-file-name)) (:opened-db-list app-db))))

(defn current-database-name
  ([]
   (subscribe [:current-database-name]))
  ([app-db]
   (let [curr-dbkey  (:current-db-file-name app-db)]
     (-> (filter (fn [m] (= curr-dbkey (:db-key m))) (:opened-db-list app-db))
         first :database-name))))

(defn current-database-file-name
  "Gets the database kdbx file name"
  [app-db]
  (let [curr-dbkey  (:current-db-file-name app-db)
        recent-dbs-info (-> app-db :app-preference :data :recent-dbs-info)]
    (-> (filter (fn [{:keys [db-file-path]}] (= curr-dbkey db-file-path)) recent-dbs-info) first :file-name)))

;;;;;;;;;;;;;;;;;;;;  Common Events ;;;;;;;;;;;;;;;;;;;;;

(defn close-kdbx [db-key]
  (dispatch [:close-kdbx-db db-key]))

(defn close-current-kdbx-db []
  (dispatch [:common/close-current-kdbx-db]))

(defn remove-from-recent-list [full-file-name-uri]
  (dispatch [:remove-from-recent-list full-file-name-uri]))

#_(defn load-language-translation-completed []
    (dispatch [:common/load-language-translation-complete]))

(defn opened-database-file-names
  "Gets db info map with keys [db-key database-name file-name key-file-name] from the opened database list"
  []
  (subscribe [:common/opened-database-file-names]))

(defn language-translation-loading-completed []
  (subscribe [:language-translation-loading-completed]))

;; Put an initial value into app-db.
;; Using the sync version of dispatch means that value is in
;; place before we go onto the next step.
;; Need to add the following dispatch-sync in 'core.cljs' top 
;; During development we can call this in the REPL to initialize the app-db
;; (re-frame.core/dispatch-sync [:initialise-db]) 
(reg-event-db
 :initialise-db
 (fn [_db _event]              ;; Ignore both params (db and event)
   {:opened-db-list []
    :previous-page {}
    :page-info {:page :home
                :title home-page-title}}))

(reg-event-fx
 :common/kdbx-database-opened
 (fn [{:keys [db]} [_event-id {:keys [database-name] :as kdbx-loaded}]]
   {:db (db-opened db kdbx-loaded) ;; current-db-file-name is set in db-opened
    :fx [[:dispatch [:entry-category/load-categories-to-show]]
         [:dispatch [:common/next-page :entry-category database-name]]
         [:dispatch [:load-all-tags]]
         [:dispatch [:groups/load]]
         [:dispatch [:common/load-entry-type-headers]]
         ;; Loads the updated recent dbs info
         [:bg-app-preference]]}))

(reg-event-fx
 :close-kdbx-db
 (fn [{:keys [_db]}  [_event-id db-key]]
   {:fx [[:bg-close-kdbx [db-key]]]}))

(reg-fx
 :bg-close-kdbx
 (fn [[db-key]]
   (bg/close-kdbx db-key
                  (fn [api-response]
                    (when-not (on-error api-response)
                      (dispatch [:common/message-snackbar-open 'databaseClosed])
                      (dispatch [:close-kdbx-completed db-key]))))))

(reg-event-fx
 :common/close-current-kdbx-db
 (fn [{:keys [db]}  [_event-id]]
   {:fx [[:bg-close-kdbx [(active-db-key db)]]
         [:dispatch [:to-home-page]]]}))

;; A common refresh all forms after an entry form changes - delete, put back , delete permanent
(reg-event-fx
 :common/refresh-forms
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:entry-category/load-categories-to-show]]
         [:dispatch [:groups/load]]
         [:dispatch [:entry-list/reload-selected-entry-items]]]}))

;; Called to set a previously opened database as current active one
(reg-event-fx
 :common/set-active-db-key
 (fn [{:keys [db]} [_event-id full-file-name-uri]]
   (let [db (assoc db :current-db-file-name full-file-name-uri)
         database-name (current-database-name db)]
     ;; TODO: Need to ensure that db-key 'full-file-name-uri' is in the opened-db-list
     {:db (assoc db :current-db-file-name full-file-name-uri)
      ;; For now, the category page of the chosen db is shown irrespective of the previous active page
      :fx [[:dispatch [:entry-category/load-categories-to-show]]
           [:dispatch [:common/next-page :entry-category database-name]]]})))

(defn notify-android-af
  "Wrapps android autofill specific events if required from main app events

  The arg 'main-events-vec' is a vector of vector of main app's events 
     e.g [[:dispatch [:load-app-preference]]]
  The arg 'args' is an optional args that is passed to the autofill event

  Returns the combined events which is set to :fx key
    e.g If the android autofill activity is opened and then we may see  
        [[:dispatch [:load-app-preference]] 
         [:dispatch [:android-af/main-app-event-happened {:main-event-id :close-kdbx-completed :db-key \"some val\"}]
        ]
       For iOS, it returns the original event vec 
       [[:dispatch [:load-app-preference]]]
  "
  [db main-events-vec args]
  (if (is-Android)
    (let [;; If the android autofill activity is already launched, then 
          ;; we may have non nil value in [:android-af :main-event-handler]
          event-name-kw (get-in db [:android-af :main-event-handler])
          main-events-vec (if-not (nil? event-name-kw)
                            (into main-events-vec [[:dispatch [event-name-kw args]]])
                            main-events-vec)]
      main-events-vec)
    main-events-vec))

(reg-event-fx
 :close-kdbx-completed
 (fn [{:keys [db]} [event-id db-key]]
   (let [;; Remove the closed db-key summary map from list
         dbs (filterv (fn [m] (not= (:db-key m) db-key)) (:opened-db-list db))
         ;; For now make the last db if any as the active one  
         next-active-db-key (if (empty? dbs) nil (:db-key (last dbs)))]

     {:db (-> db
              (assoc :opened-db-list dbs)
              (assoc :current-db-file-name next-active-db-key)
              ;; clears all entry-form, group-form, categories etc for this db-key
              (dissoc db-key))
      :fx (notify-android-af db
                             [[:dispatch [:load-app-preference]]]
                             {:main-event-id event-id
                              :db-key db-key})  #_[[:dispatch [:load-app-preference]]]})))

(reg-event-fx
 :remove-from-recent-list
 (fn [{:keys [_db]} [_event-id full-file-name-uri]]
   ;; full-file-name-uri is passed as db-key 
   ;; As db is not used, this call may be moved to a top level function
   ;; This backend call not only removes the recent use file info but also closes the db if it is openned 
   (bg/remove-from-recently-used full-file-name-uri (fn [api-reponse]
                                                      (when-not (on-error api-reponse)
                                                        (dispatch [:common/message-snackbar-open 'databaseRemovedFromList])
                                                        ;; As db is closed when this response is received, we call :close-kdbx-completed 
                                                        (dispatch [:close-kdbx-completed full-file-name-uri]))))
   {}))

(reg-event-fx
 :common/default-error
 (fn [{:keys [_db]} [_event-id message-title error]]
   {:fx [[:dispatch [:common/message-modal-hide]]
         [:dispatch [:common/error-box-show message-title error]]]}))

;; Called whenever the db name is changed in settings
(reg-event-fx
 :common/database-name-update
 (fn [{:keys [db]} [_event-id new-db-name]]
   (let [curr-dbkey (:current-db-file-name db)
         new-list (mapv (fn [m] (if (= curr-dbkey (:db-key m)) (assoc m :database-name new-db-name) m))
                        (:opened-db-list db))]
     ;;(println "new-list " new-list (keys db))
     {:db (assoc db :opened-db-list new-list)})))

(reg-event-fx
 :common/load-language-translation-complete
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [:background-loading-statuses :load-language-translation] true)}))

;; Called before reloading language translation on change
#_(reg-event-db
   :common/reset-load-language-translation-status
   (fn [db [_event-id]]
     (assoc-in db [:background-loading-statuses :load-language-translation] false)))

(reg-event-fx
 :common/reset-load-language-translation-status
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [:background-loading-statuses :load-language-translation] false)
    ;; When language translations are loaded in settings page, we could not update the page with new language
    ;; As a workaround, this page is shown and allows user to reresh  with the newly loaded translation data
    :fx [[:dispatch [:common/next-page :blank nil]]]}))

(reg-sub
 :language-translation-loading-completed
 (fn [db _query-vec]
   (get-in db [:background-loading-statuses :load-language-translation] false)))

(reg-sub
 :active-db-key
 (fn [db _query-vec]
   (:current-db-file-name db)))

(reg-sub
 :current-database-name
 (fn [db _query-vec]
   (current-database-name db)))

;; Gets all db keys / full database file names
(reg-sub
 :common/opened-database-file-names
 (fn [db _query-vec]
   (mapv (fn [m] (:db-key m)) (:opened-db-list db))))

;;;;;;;;;;;;;;;;;;  App preference, Recent dbs etc ;;;;;;;;;;;;;;;;;;;;

(defn app-preference-status-loaded []
  (subscribe [:app-preference-status-loaded]))

(defn recently-used
  "Returns a vec of maps (from struct RecentlyUsed) with keys :file-name and :db-file-path 
   The kdbx file name is found here for each db-key
 "
  []
  (subscribe [:recently-used]))

(defn biometric-available []
  (subscribe [:biometric-available]))

(reg-event-fx
 :load-app-preference
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in [:app-preference :status] :loading))
    :fx [[:bg-app-preference]
         ;; Check for any open uri availability if the app is launched when the user presses a kdbx file
         ;; TODO: Need to call only first time load-app-preference is called
         [:bg-kdbx-uri-to-open-on-create]]}))

(reg-fx
 :bg-app-preference
 (fn []
   (bg/app-preference (fn [api-response]
                        (when-let [r (on-ok api-response)]
                          (dispatch [:app-preference-loaded r]))))))

(reg-fx
 :bg-kdbx-uri-to-open-on-create
 (fn []
   (bg/kdbx-uri-to-open-on-create (fn [api-response]
                                    ;; api-response may be {} and in that case 
                                    ;; (:ok {}) is nil and nothing is done
                                    (when-let [m (on-ok api-response)]
                                      ;; The app was started by the user pressing a .kdbx file
                                      ;; m is a map with keys - file-name full-file-name-uri
                                      (dispatch [:open-database/database-file-picked m]))))))

(reg-event-fx
 :app-preference-loaded
 (fn [{:keys [db]} [_event-id pref]]
   ;; pref is a map - {:version \"0.0.1\" :recent-dbs-info [{},{}..]}
   ;; based on Preference struct 
   {:db (-> db
            ;; Set the biometric availablity info in db so that we can use it in a subscription
            (assoc :biometric-available (bg/is-biometric-available))
            (assoc-in [:app-preference :status] :loaded)
            (assoc-in [:app-preference :data] pref))
    :fx [[:dispatch [:app-settings/app-preference-loaded]]]}))

(reg-sub
 :recently-used
 (fn [db [_event-id]]
   (let [r (get-in db [:app-preference :data :recent-dbs-info])]
     (if (nil? r) [] r))))

(reg-sub
 :biometric-available
 (fn [db [_event-id]]
   (get db :biometric-available)))

(reg-sub
 :app-preference-status-loaded
 (fn [db [_event-id]]
   (let [s (get-in db [:app-preference :status])]
     (cond
       (nil? s)
       false

       (= s :loaded)
       true

       :else
       false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  DB Lock ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; All DB unlock call events are in open-databse ns

(defn locked? [db-key]
  (if (nil? db-key)
    (subscribe [:current-db-locked])
    (subscribe [:selected-db-locked db-key])))

(defn lock-kdbx
  "Called to lock a database. The arg is nil if we want to lock the current database"
  [db-key]
  ;; Lock database call from entry category or entry list page is meant for the 
  ;; current database and hence db-key passed is nil
  (if (nil? db-key)
    (dispatch  [:lock-current-kdbx])
    (dispatch [:lock-selected-kdbx db-key])))


;; Similiar to :common/close-current-kdbx-db 
;; Locks db instead of closing 
;; Called to lock the current active db - typically used from entry cat page menu 
(reg-event-fx
 :lock-current-kdbx
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in-key-db db [:locked] true)
    :fx [[:dispatch [:to-home-page]]
         [:dispatch [:common/message-snackbar-open 'databaseLocked]]]}))

;; Called to lock any opened database using the passed db-key - used from home page
(reg-event-fx
 :lock-selected-kdbx
 (fn [{:keys [db]} [_event-id db-key]]
   {:db (assoc-in-selected-db db db-key [:locked] true)
    :fx [#_[:bg-lock-kdbx [(active-db-key db)]]
         [:dispatch [:to-home-page]]
         [:dispatch [:common/message-snackbar-open 'databaseLocked]]]}))

(reg-event-fx
 :lock-on-session-timeout
 (fn [{:keys [db]} [_event-id db-key]]
   (let [curr-dbkey  (:current-db-file-name db)]
     {:db (assoc-in-selected-db db db-key [:locked] true)
      :fx [(when (= curr-dbkey db-key)
             [:dispatch [:to-home-page]])]})))

;; Need to make use of API call in case we want to do something for lock call
;; Currently nothing is done on the backend
#_(reg-fx
   :bg-lock-kdbx
   (fn [[db-key]]
     #_(bg/lock-kdbx db-key (fn [api-response]
                              (when-not (on-error api-response)
                            ;; Add any relevant dispatch calls here
                            ;;(println "Database is locked")
                                #())))))

;;:open-database/unlock-dialog-show
(reg-event-fx
 :common/unlock-selected-db
 (fn [{:keys [db]} [_event-id db-key]]
   {:db (assoc-in-selected-db db db-key [:locked] false)}))

(reg-sub
 :current-db-locked
 (fn [db _query-vec]
   (boolean (get-in-key-db db [:locked]))))

(reg-sub
 :selected-db-locked
 (fn [db [_query-id db-key]]
   (boolean (get-in-selected-db db db-key [:locked]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Page Navigation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Each valid pages have a keyword indentifier as id and a title
;; Valid page ids are [:home :about ...]  See appbar.cljs
;; Need to be added to constants.cljs

(defn to-home-page
  "Called to navigate to the home page"
  []
  (dispatch [:to-home-page]))

(defn to-about-page
  "Called to navigate to the about page"
  []
  (dispatch [:to-about-page]))

(defn to-privacy-policy-page []
  (dispatch [:to-privacy-policy-page]))

(defn to-previous-page
  "Called to navigate to the previous page"
  []
  (dispatch [:common/previous-page]))

(defn page-info []
  (subscribe [:page-info]))

(defn current-page
  "Returns the current page that is being shown. 
  The arg db is re-frame.db/app-db. Mostly used in other events
  "
  [db]
  (let [page-data (first (get-in db [:pages-stack]))]
    (-> page-data  :page)))

(reg-event-fx
 :to-home-page
 (fn [{:keys [db]} [_event-id]]
   {;;pages-stack is a list and need to be set to empty when navigated to home page
    :db (assoc-in db [:pages-stack] nil)
    :fx [;; load-app-preference loads preference asynchronously and it may complte
         ;; before or after the next-page is displayed
         [:dispatch [:load-app-preference]]
         [:dispatch [:common/next-page :home home-page-title]]]}))

(reg-event-fx
 :to-about-page
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:common/next-page :about "about"]]]}))

(reg-event-fx
 :to-privacy-policy-page
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:common/next-page :privacy-policy "privacyPolicy"]]]}))

;; Called when user navigates to the next page
;; For most of the cases, the title is expected to be a key to get the translated page title text
;; See positioned-title component in appbar.cljs 
(reg-event-db
 :common/next-page
 (fn [db [_event-id page title]]
   (let [pages-stack (get-in db [:pages-stack])
         page-data (first pages-stack)
         page-info {:page page :title title}
         ;; pages-stack is a list
         ;; If the incoming page is the same as the one on the top (first item) of the list, then the list is returned 
         ;; otherwise we add the page to the begining of the list - conj adds to the front of list
         pages-stack (if (= (:page page-data) page) pages-stack  (conj pages-stack page-info))]
     (assoc-in db [:pages-stack] pages-stack))))

;; Called when user navigates to the previous page
(reg-event-db
 :common/previous-page
 (fn [db [_event-id]]
   (let [pages-stack (get-in db [:pages-stack])
         ;; The following rest call removes the first item and returns the remaining pages
         pages-stack (rest pages-stack)]
     (assoc-in db [:pages-stack] pages-stack))))

;; Gets the current page info 
;; This is the first item in the pages-stack list
(reg-sub
 :page-info
 (fn [db _query-vec]
   (let [info (first (get-in db [:pages-stack]))]
     (if (empty? info)
       {:page :home
        :title home-page-title}
       info))))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Icons ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn show-icons-to-select [on-icon-selection]
  (dispatch [:common/show-icons-to-select on-icon-selection]))

(defn icon-selected [icon-name index]
  (dispatch [:common/icon-selected icon-name index]))

(reg-event-fx
 :common/show-icons-to-select
 (fn [{:keys [db]} [_event-id on-icon-selection]]
   {:db (assoc db :on-icon-selection on-icon-selection)
    :fx [[:dispatch [:common/next-page :icons-list "icons"]]]}))

(reg-event-fx
 :common/icon-selected
 (fn [{:keys [db]} [_event-id icon-name index]]
   (let [on-icon-selection-fn (:on-icon-selection db)]
     (when-not (nil? on-icon-selection-fn)
       (on-icon-selection-fn icon-name index)) ;; side effect ?
     {:db (assoc db :on-icon-selection nil)
      :fx [[:dispatch [:common/previous-page]]]})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;; Entry types with uuid ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all-entry-type-headers
  ;; Returns an atom that holds list of all entry headers map and meant to be used in UI 
  ([]
   (subscribe [:all-entry-type-headers]))
  ([app-db]
   (let [{:keys [standard custom]} (get-in-key-db app-db [:entry-type-headers])]
     (vec (concat standard custom)))))

;; Future use
#_(defn is-custom-entry-type [entry-type-uuiid]
    (subscribe [:is-custom-entry-type-uuid entry-type-uuiid]))

;; Needs to be called during initial loading and also whenever new custom type is added.
(reg-event-db
 :common/load-entry-type-headers
 (fn [db [_event-id]]
   ;; entry-type-headers call gets a map formed by struct EntryTypeHeaders
   (bg/entry-type-headers
    (active-db-key db)
    (fn [api-response]
      (when-let [et-headers-m (on-ok api-response)]
        (dispatch [:load-entry-type-headers-completed et-headers-m]))))
   db))

(reg-event-db
 :load-entry-type-headers-completed
 (fn [db [_event-id et-headers-m]]
   (assoc-in-key-db db [:entry-type-headers] et-headers-m)))

;; Gets a map formed by struct EntryTypeHeaders
(reg-sub
 :entry-type-headers
 (fn [db _query-vec]
   (get-in-key-db db [:entry-type-headers])))

(reg-sub
 :all-entry-type-headers
 :<- [:entry-type-headers]
 (fn [{:keys [standard custom]} _query-vec]
   (vec (concat standard custom))))

;; Future use
#_(reg-sub
   :is-custom-entry-type-uuid
   :<- [:entry-type-headers]
   (fn [{:keys [custom]} [_query-id entry-type-uuid]]
   ;; custom field is a vector with a list of  maps (EntryTypeHeader struct)
   ;; One matching member in custom vector need to be found to return true
   ;; We can use (boolean seq coll) to determine true or false or alternative we can use 
   ;; (->> custom (filter (fn [m] (= entry-type-uuid (:uuid m)))) count (= 1))
     (->> custom (filter (fn [m] (= entry-type-uuid (:uuid m)))) seq boolean)))


;;;;;; Tags Related Begin ;;;;;;;;;;

(defn tags-dialog-done []
  (dispatch [:tags-dialog-done]))

(defn tags-dialog-init-selected-tags
  "Shows the tags dialog after initializing the selected-tags"
  [selected-tags]
  (dispatch [:tags-dialog-init-selected-tags selected-tags]))

(defn tags-dialog-tag-selected [selected-tag]
  (dispatch [:tags-dialog-tag-selected selected-tag]))

(defn tags-dialog-update-new-tags-str [new-tags-str]
  (dispatch [:tags-dialog-update-new-tags-str new-tags-str]))

(defn tags-dialog-add-tags []
  (dispatch [:tags-dialog-add-tags]))

(defn tags-dialog-data []
  (subscribe [:tags-dialog-data]))

(reg-event-db
 :tags-dialog-init-selected-tags
 (fn [db [_event-id selected-tags]]
   (-> db (assoc-in-key-db [:tags :dialog-data :selected-tags] selected-tags)
       (assoc-in-key-db [:tags :dialog-data :show] true))))

;; Toggles the add/removal of the selected-tag from selected-tags list
(reg-event-db
 :tags-dialog-tag-selected
 (fn [db [_event-id selected-tag]]
   (let [selected-tags (get-in-key-db db [:tags :dialog-data :selected-tags])
         selected-tags (if (u/contains-val? selected-tags selected-tag)
                         (filterv #(not= selected-tag %) selected-tags)
                         (conj selected-tags selected-tag))]
     (assoc-in-key-db db [:tags :dialog-data :selected-tags] selected-tags))))

(reg-event-db
 :tags-dialog-update-new-tags-str
 (fn [db [_event-id new-tags-str]]
   (assoc-in-key-db db [:tags :dialog-data :new-tags-str] new-tags-str)))

;; Called when user adds new tags in the tags dialog
(reg-event-db
 :tags-dialog-add-tags
 (fn [db [_event-id]]
   (let [;; Todo: ensure unique tags are added and also any existing tag names should be ignored
         ;; and should not be added to :tags :all
         new-tags-str (get-in-key-db db [:tags :dialog-data :new-tags-str])
         new-tags (-> (tags->vec new-tags-str) distinct) ;; new-tags is a sequence
         selected-tags (get-in-key-db db [:tags :dialog-data :selected-tags])
         tags (get-in-key-db db [:tags :all])
         ;; Joining two vectors or joining a sequence to a vector
         ;; all-tags (into [] (flatten (conj tags new-tags)) ) ;; or (reduce  #(conj %1 %2) tags new-tags)
         all-tags (->> new-tags (conj tags) flatten distinct vec)
         selected-tags (->> new-tags (conj selected-tags) flatten distinct vec)]
     (-> db
         (assoc-in-key-db [:tags :all] all-tags)
         (assoc-in-key-db  [:tags :dialog-data :new-tags-str] nil)
         (assoc-in-key-db  [:tags :dialog-data :selected-tags] selected-tags)))))

(reg-event-fx
 :tags-dialog-done
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in-key-db [:tags :dialog-data :show] false)
            (assoc-in-key-db  [:tags :dialog-data :new-tags-str] nil)
            (assoc-in-key-db [:tags :dialog-data :selected-tags] []))}))

(reg-event-db
 :load-all-tags
 (fn [db [_event-id]]
   (bg/collect-entry-group-tags
    (active-db-key db)
    (fn [api-response]
      (when-let [result (on-ok api-response)]
        (dispatch [:load-all-tags-completed result]))))
   db))

(reg-event-db
 :load-all-tags-completed
 (fn [db [_event-id result]]
   (assoc-in-key-db db
                    [:tags :all]
                    (into []
                          (concat (:entry-tags result)
                                  (:group-tags result))))))

;; Should we exclude "Favourites" tag from showing in all-tags ?
(reg-sub
 :all-tags
 (fn [db _query-vec]
   (get-in-key-db db [:tags :all])))

(reg-sub
 :tags-dialog-data
 (fn [db [_query-id]]
   (let [dialog-data (get-in-key-db db [:tags :dialog-data])
         tags (get-in-key-db db [:tags :all])]
     (assoc dialog-data :all-tags tags))))


;;;;;;;;;;;;;;; Error dialog ;;;;;;;;;;;;;

(defn close-message-dialog []
  (dispatch [:message-box-hide]))

(defn message-dialog-data []
  (subscribe [:message-box]))

(reg-event-db
 :common/message-box-show
 (fn [db [_event-id title message]]
   ;; Incoming 'message' may be a map with key :message or string or an object
   ;; We need to convert message as '(str message)' to ensure it can be used 
   ;; in UI component
   ;; The message may be a symbol meaning that it is to be used as a key to get the translated text
   (let [msg (if (symbol? message) message
                 (get message :message (str message)))]
     (-> db
         (assoc-in [:message-box :dialog-show] true)
         (assoc-in [:message-box :title] title)
         (assoc-in [:message-box :category] :message)
         (assoc-in [:message-box :message] msg)))))

(reg-event-db
 :common/error-box-show
 (fn [db [_event-id title message]]
   ;; Incoming 'message' may be a map with key :message or string or an object
   ;; We need to convert message as '(str message)' to ensure it can be used 
   ;; in UI component
   ;; The message may be a symbol meaning that it is to be used as a key to get the translated text
   (let [msg (if (symbol? message) message
                 (get message :message (str message)))]
     (-> db
         (assoc-in [:message-box :dialog-show] true)
         (assoc-in [:message-box :title] (if (str/blank? title) "Error" title))
         (assoc-in [:message-box :category] :error)
         (assoc-in [:message-box :message] msg)))))

(reg-event-db
 :message-box-hide
 (fn [db [_event-id]]
   (-> db
       (assoc-in [:message-box :dialog-show] false)
       (assoc-in [:message-box :title] nil)
       (assoc-in [:message-box :category] nil)
       (assoc-in [:message-box :message] nil))))

(reg-sub
 :message-box
 (fn [db _query-vec]
   (-> db :message-box)))

;;;;;;;;;;;;;;;;;;;;; Common snackbar ;;;;;;;;;;;;;;;;

(defn show-snackbar
  [message]
  (dispatch [:common/message-snackbar-open message]))

(defn close-message-snackbar []
  (dispatch [:message-snackbar-close]))

(defn message-snackbar-data []
  (subscribe [:message-snackbar-data]))

(reg-event-db
 :common/message-snackbar-open
 (fn [db [_event-id message]]
   (-> db
       (assoc-in [:message-snackbar-data :open] true)
       ;; lstr is used in 'common-components/message-snackbar' to provide a language specific message
       (assoc-in [:message-snackbar-data :message] message))))

(reg-event-db
 :message-snackbar-close
 (fn [db [_event-id]]
   (-> db
       (assoc-in [:message-snackbar-data :open] false))))

(reg-sub
 :message-snackbar-data
 (fn [db _query-vec]
   (-> db :message-snackbar-data)))

;;;;;;;;;;;;;;;;;;;; Message Modal ;;;;;;;;;;;;;;;;

(defn message-modal-show [message]
  (dispatch [:common/message-modal-show nil message]))

(defn message-modal-data []
  (subscribe [:message-modal]))

;; TODO: Need to swap the args order to 'message title' as title is used optional field
;; It seems we do not use 'title' in the 'message-modal' dialog anymore (see common_components.cljs).
;; Only the message is used
;; Need to remove passing the arg 'title' while dispatching this event
(reg-event-db
 :common/message-modal-show
 (fn [db [_event-id title message]]
   (-> db
       (assoc-in [:message-modal :dialog-show] true)
       (assoc-in [:message-modal :title] title)
       (assoc-in [:message-modal :message] message))))

(reg-event-db
 :common/message-modal-hide
 (fn [db [_event-id]]
   (-> db
       (assoc-in [:message-modal :dialog-show] false)
       (assoc-in [:message-modal :title] nil)
       (assoc-in [:message-modal :message] nil))))

(reg-sub
 :message-modal
 (fn [db _query-vec]
   (-> db :message-modal)))

;;;;;;;;;;;;;;;  Get File Info ;;;;;;;;;;;;;;;;;;;;;

(defn load-file-info [full-file-name-uri]
  (dispatch [:load-file-info full-file-name-uri]))

(defn close-file-info-dialog []
  (dispatch [:close-file-info-dialog]))

(defn file-info-dialog-data []
  (subscribe [:file-info-dialog-data]))

(reg-event-fx
 :load-file-info
 (fn [{:keys [_db]} [_event-id full-file-name-uri]]
   {:fx [[:bg-get-file-info [full-file-name-uri]]]}))

(reg-fx
 :bg-get-file-info
 (fn [[full-file-name-uri]]
   (bg/get-file-info full-file-name-uri (fn [api-response]
                                          (when-let [file-info (on-ok api-response)]
                                            (dispatch [:load-file-info-complete file-info]))))))

(reg-event-fx
 :load-file-info-complete
 (fn [{:keys [db]} [_event-id file-info]]
   {:db (assoc db :file-info-dialog-data (merge {:dialog-show true} file-info))}))

(reg-event-db
 :close-file-info-dialog
 (fn [db [_event-id]]
   (assoc-in  db [:file-info-dialog-data :dialog-show] false)))

(reg-sub
 :file-info-dialog-data
 (fn [db]
   (get db :file-info-dialog-data {:dialog-show false})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def clipboard-session-timeout "Timeout in milliseconds" (atom 10000))

(defn set-clipboard-session-timeout
  "Called to set a new timeout to use"
  [time-in-milli-seconds]
  (let [in-timeout (str->int time-in-milli-seconds)
        in-timeout (if (nil? in-timeout) 10000 in-timeout)]
    (reset! clipboard-session-timeout in-timeout)))

(def field-in-clip (atom false))

(defn clear-clipboard
  "Clears out the last protected field after 10 sec assuming user copies one field and pastes
   "
  [protected]
  (reset! field-in-clip protected)
  (when-not (= @clipboard-session-timeout -1)
    (go
      (<! (timeout @clipboard-session-timeout))
      (when @field-in-clip (bg/write-string-to-clipboard nil)))))

(defn write-string-to-clipboard 
  "Calls the backend api to copy the passsed field value to the ios or android clipboard"
  [{:keys [field-name value protected]}]
  ;;(println "write-string-to-clipboard called field-name value... " field-name value)
  #_(bg/write-string-to-clipboard value)
  #_(clear-clipboard protected)
  ;;clipboard-session-timeout is in milliseconds
  (let [cb-timeout_secs (if-not (= @clipboard-session-timeout -1) (/ @clipboard-session-timeout 1000) 0)] 
    (bg/copy-to-clipboard {:field-name field-name
                           :field-value value
                           :protected protected
                           :cleanup-after cb-timeout_secs}
                          #())
    )
  
  (when field-name
    (dispatch [:common/message-snackbar-open (str field-name " " "copied")])))


;;;;;;;;;;;;;;;;;;;; Open URL ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn open-https-url [https-url]
  (bg/open-https-url https-url  (fn [api-response] (on-error api-response))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (in-ns 'onekeepass.mobile.events.common)

  ;; Sometime subscrition changes are reflected after save. In that case the following clears old subs
  (re-frame.core/clear-subscription-cache!)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys))


#_(defn create-api-handler-fn
    "
  ok-response-handler is a fn to handle the value found in :ok
  error-response-handler is a fn to handle the value found in :error
  Returns a fn wrapping the individual ok and error fns that can be used 
  in any backend api call as dispatch-fn
  "
    [ok-response-handler error-response-handler]
    (cond

      (and ok-response-handler error-response-handler)
      (fn [api-response]
        (when-let [ok-response (on-ok api-response error-response-handler)]
          (ok-response-handler ok-response)))

      ok-response-handler
      (fn [api-response]
        (when-let [ok-response (on-ok api-response)]
          (ok-response-handler ok-response)))

      error-response-handler
      (fn [api-response]
        (on-error api-response error-response-handler))))
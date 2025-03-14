(ns onekeepass.mobile.android.autofill.events.common
  "Only the Android Autofill specific common events. All events should be prefixed with :android-af"
  (:require [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.constants :refer [CATEGORY_ALL_ENTRIES]]
            [onekeepass.mobile.events.common :refer [on-ok]]
            [onekeepass.mobile.events.open-database :refer [blank-open-db]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                                   reg-sub subscribe]]))

;; IMPORTANT
;; All event names and map keys are expected to have prefix :adnroid-af to avoid name and data 
;; conflicts with the main app's events and data. This is required as both android app and android autofill
;; will be running in the same process and use the same JS global space

;;;;;;;;;;;;;;;;;; Conmmon functions specific to android autofill ;;;;;;;;;;;;;;

;; Based on the fn 'db-opened' from the main common 
;; We use the main ':opened-db-list' to maintain the list of the recent db list
;; which is shared here in autofill events and in the main app' side 

(defn android-af-db-opened
  "Updates the db list and current active db key when a new kdbx database is loaded
  The args are the re-frame 'app-db' and KdbxLoaded struct returned by backend API.
  Returns the updated app-db
  "
  [app-db {:keys [db-key database-name file-name _key-file-name]}] ;;kdbx-loaded 
  (let [app-db  (if (nil? (:opened-db-list app-db)) (assoc app-db :opened-db-list []) app-db)
        ;; Existing db-key is removed first to maintain unique db-key
        dbs (filterv (fn [m] (not= db-key (:db-key m))) (:opened-db-list app-db))
        app-db (assoc app-db :opened-db-list dbs)]
    (-> app-db
        (assoc-in [:android-af :current-db-file-name] db-key)
        #_(assoc :current-db-file-name db-key)
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

(defn android-af-active-db-key
  "Returns the current database key 'db-key'"
  ;; To be called only in react components (i.e in React context) as it uses 'subscribe' 
  ([]
   (subscribe [:android-af-active-db-key]))
  ;; Used in reg-event-db , reg-event-fx by passing the main re-frame global 'app-db' 
  ([app-db]
   (get-in app-db [:android-af :current-db-file-name])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;  All page navigation related ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def home-page-title "Home")

(def HOME_PAGE_ID :home)
(def LOGIN_PAGE_ID :login)
(def ENTRY_LIST_PAGE_ID :entry-list)
(def ENTRY_FORM_PAGE_ID :entry-form)

(defn to-home []
  (dispatch [:android-af-common/next-page HOME_PAGE_ID "Home"]))

(defn to-page [page-id title]
  (dispatch [:android-af-common/next-page page-id title]))

(defn to-entry-form-page []
  (dispatch [:android-af-common/next-page ENTRY_FORM_PAGE_ID  "Entry"]))

(defn to-previous-page
  "Called to navigate to the previous page"
  []
  (dispatch [:android-af-common/previous-page]))

(defn page-info []
  (subscribe [:android-af-page-info]))

(reg-event-db
 :android-af-common/next-page
 (fn [db [_event-id page title]]
   (let [pages-stack (get-in db [:android-af :pages-stack])
         page-data (first pages-stack)
         page-info {:page page :title title}
         ;; pages-stack is a list
         ;; If the incoming page is the same as the one on the top (first item) of the list, then the list is returned 
         ;; otherwise we add the page to the begining of the list - conj adds to the front of list
         pages-stack (if (= (:page page-data) page) pages-stack  (conj pages-stack page-info))]
     (assoc-in db [:android-af :pages-stack] pages-stack))))

;; Called when user navigates to the previous page
(reg-event-db
 :android-af-common/previous-page
 (fn [db [_event-id]]
   (let [pages-stack (get-in db [:android-af :pages-stack])
         ;; The following rest call removes the first item and returns the remaining pages
         pages-stack (rest pages-stack)]
     (assoc-in db [:android-af :pages-stack] pages-stack))))


;; Gets the current page info 
;; This is the first item in the pages-stack list
(reg-sub
 :android-af-page-info
 (fn [db _query-vec]
   (let [info (first (get-in db [:android-af :pages-stack]))]
     (if (empty? info)
       {:page HOME_PAGE_ID
        :title home-page-title }
       info))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Open db related ;;;;;;;;;;;;;;;;;;;;;;;;

(defn open-selected-database
  "Called to open a database when user presses a row item of the datanases list on the home page"
  [file-name full-file-name-uri already-opened?]
  #_(dispatch [:android-af/database-file-picked {:file-name file-name :full-file-name-uri full-file-name-uri}])
  (if already-opened?
    (dispatch [:android-af/set-active-db-key full-file-name-uri])
    (dispatch [:android-af/database-file-picked {:file-name file-name :full-file-name-uri full-file-name-uri}])))

(defn cancel-on-press []
  (dispatch [:android-af/open-database-dialog-hide]))

#_(defn show-key-file-form []
  ;; kw :open-database-key-file-selected is used in events in ns onekeepass.mobile.events.key-file-form
  ;; to send back the selected key file 
  #_(dispatch [:key-file-form/show :open-database-key-file-selected]))

(defn database-field-update [kw-field-name value]
  (dispatch [:android-af/open-database-field-update kw-field-name value]))

;; See main app common
(defn opened-database-file-names
  "Gets a vec of all opened db-keys from the opened database list"
  []
  (subscribe [:common/opened-database-file-names]))

(defn open-database-read-db-file
  "Called when user clicks the continue button. By this time user would have picked a file to open in the 
  previous pick file call
  "
  []
  (dispatch [:android-af/open-database-read-db-file]))

(defn open-database-on-press []
  (dispatch [:android-af/pick-database-file]))

(defn dialog-data []
  (subscribe [:android-af/open-database-dialog-data]))

(defn- init-open-database-data
  "Initializes all open db related values in the incoming main app db 
  and returns the updated main app db
  "
  [db]
  (assoc-in db [:android-af :open-database] blank-open-db))

(reg-event-db
 :android-af/open-database-dialog-hide
 (fn [db [_event-id]]
   (assoc-in  db [:android-af :open-database :dialog-show] false)))

;; An event to be called (from key file related page) after user selects a key file 
#_(reg-event-fx
 :android-af/open-database-key-file-selected
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name] :as m}]]
   {:db (-> db (assoc-in [:android-af :open-database :key-file-name-part] file-name)
            (assoc-in [:android-af :open-database :key-file-name] full-file-name))}))

(reg-event-db
 :android-af/open-database-field-update
 (fn [db [_event-id kw-field-name value]] ;; kw-field-name is single kw or a vec of kws
   (-> db
       (assoc-in (into [:android-af :open-database]
                       (if (vector? kw-field-name)
                         kw-field-name
                         [kw-field-name])) value))))

;; This will make dialog open status true
(reg-event-fx
 :android-af/database-file-picked
 (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
   #_(println "android-af/database-file-picked is called")
   {:db (-> db init-open-database-data
            ;; database-file-name is just the 'file name' part derived from full uri 'database-full-file-name'
            ;; to show in the dialog
            (assoc-in [:android-af :open-database :database-file-name] file-name)
            (assoc-in [:android-af :open-database :database-full-file-name] full-file-name-uri)
            (assoc-in [:android-af :open-database :dialog-show] true))}))

;; Called to set a previously opened database as current active one
;; Based on :common/set-active-db-key
(reg-event-fx
 :android-af/set-active-db-key
 (fn [{:keys [db]} [_event-id full-file-name-uri]]
   (let [db (-> db
                (assoc-in  [:android-af :current-db-file-name] full-file-name-uri)
                (assoc-in  [:android-af :main-event-handler] :android-af/main-app-event-happened))]
     {:db db
      :fx [[:android-af/bg-entry-summary-data [full-file-name-uri CATEGORY_ALL_ENTRIES]]]})))

(reg-event-fx
 :android-af/pick-database-file
 (fn [{:keys [_db]} [_event-id]] 
   {:fx [[:android-af/bg-pick-database-file]]}))

(reg-fx
 :android-af/bg-pick-database-file
 (fn [] 
   (bg/pick-database-to-read-write
    (fn [api-response] 
      (when-let [picked-response (on-ok
                                  api-response
                                  ;; main app's :database-file-pick-error reused 
                                  (fn [error] 
                                    (dispatch [:database-file-pick-error error])))]
        (dispatch [:android-af/database-file-picked picked-response]))))))

(reg-sub
 :android-af/open-database-dialog-data
 (fn [db _query-vec]
   (get-in db [:android-af :open-database])))

;;;;;;;;;;;;;;;;;;;;;;;; Load kdbx ;;;;;;;;;;;;;;;

;; Android af specific ':open-database' map (in :android-af )
;; [:android-af :open-database ] should have valid values by this time as
;; user has picked a database,entered credentials and pressed "Continue" button
(reg-event-fx
 :android-af/open-database-read-db-file
 (fn [{:keys [db]} [_event-id]]
   (let [error-fields {}
         errors-found (boolean (seq error-fields))]
     (if errors-found
       {:db (assoc-in db [:android-af :open-database :error-fields] error-fields)}
       {:db (-> db (assoc-in [:android-af :open-database :status] :in-progress))
        :fx [[:android-af/bg-load-kdbx [(get-in db [:android-af  :open-database :database-full-file-name])
                                        (get-in db [:android-af :open-database :password])
                                        (get-in db [:android-af :open-database :key-file-name])
                                        false
                                        ]]]}))))

(reg-fx
 :android-af/bg-load-kdbx
 (fn [[db-file-name password key-file-name biometric-auth-used]]
   (bg/load-kdbx db-file-name password key-file-name biometric-auth-used
                 (fn [api-response]
                   (when-let [kdbx-loaded
                              (on-ok
                               api-response
                               (fn [error]
                                 (dispatch [:android-af/database-open-error error])))]
                     ;; Need to use autofill specific event
                     (dispatch [:android-af/database-open-complete kdbx-loaded]))))))

(reg-event-fx
 :android-af/database-open-complete
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   {:db (-> db (assoc-in [:android-af :open-database :error-fields] {})
            (assoc-in [:android-af :open-database :status] :completed))
    :fx [[:dispatch [:android-af/open-database-dialog-hide]]
         [:dispatch [:android-af/kdbx-database-opened kdbx-loaded]]]}))

;; Needs to be enhanced based on the original :open-database-read-kdbx-error
(reg-event-fx
 :android-af/database-open-error
 (fn [{:keys [db]} [_event-id error]]
   {:db  (-> db (assoc-in [:android-af :open-database :error-fields] {})
             (assoc-in [:android-af :open-database :status] :completed))
    :fx [[:dispatch [:common/error-box-show "Database Open Error" error]]]}))

;; Based on :common/kdbx-database-opened but dispatches autofill specific events
(reg-event-fx
 :android-af/kdbx-database-opened
 (fn [{:keys [db]} [_event-id {:keys [db-key _database-name] :as kdbx-loaded}]]
   ;; TODO: We need to add this opened db to the list, but Main app's current-db-file-name
   ;; is not be set and instead [:android-af :current-db-file-name] is set
   ;; The amin app current-db-file-name is not set in db-opened
   {:db (-> db (android-af-db-opened  kdbx-loaded)
            ;; Here we store a callback event handler which is called 
            ;; from main app event. For example, ':close-kdbx-completed' will dispatch to
            ;; the event ':android-af/main-app-event-happened'
            (assoc-in [:android-af :main-event-handler] :android-af/main-app-event-happened))
    :fx [;; Loads the updated recent dbs info in app preferece
         [:bg-app-preference]
         ;; Loads the allEntries based entry sumarry list for android autofill 
         [:android-af/bg-entry-summary-data [db-key CATEGORY_ALL_ENTRIES]]]}))

(reg-fx
 :android-af/bg-entry-summary-data
 (fn [[db-key category]]
   (bg/entry-summary-data db-key category
                          (fn [api-response]
                            (when-let [entry-summaries (on-ok api-response)]
                              (dispatch [:android-af-all-entries-loaded db-key entry-summaries])
                              #_(dispatch [:android-af/entry-list-load-complete entry-summaries]))))))

;; Called after retreiving all entries for the opened database
(reg-event-fx
 :android-af-all-entries-loaded
 (fn [{:keys [_db]} [_event-id db-key entry-summaries]]
   {:fx [[:dispatch [:android-af/entry-list-load-complete entry-summaries]]
         [:bg-android-af-autofill-filtered-entries [db-key]]]}))

;; Called to load any matching entries based on ios autofill credential identifiers 
;; This is called after loading all entries summary - see the above event
(reg-fx
 :bg-android-af-autofill-filtered-entries
 (fn [[db-key]]
   (bg/android-autofill-filtered-entries
    db-key
    (fn [api-response]
      (when-let [result (on-ok api-response)]
        (dispatch [:android-af-search-term-completed result]))))))

;; This event name is stored in [:android-af :main-event-handler] and is called 
;; by any main app event
;; At this time, the vent name is stored in the event ':android-af/kdbx-database-opened'

;; For now, the main app event ':close-kdbx-completed' triggers this event 
(reg-event-fx
 :android-af/main-app-event-happened
 (fn [{:keys [_db]} [_event_id _args]] 
   {:fx [[:dispatch [:android-af-common/next-page HOME_PAGE_ID "Home"]]]}))

(reg-sub
 :android-af-active-db-key
 (fn [db _query-vec]
   (get-in db [:android-af :current-db-file-name])))

;;;;;;;;;;;;;;;;;;;; Search  ;;;;;;;;;;;;;;

(defn search-term-update [term]
  (dispatch [:android-af-search-term-update term]))

(defn search-result-entry-items
  "Returns an atom that has a list of all search term matched entry items "
  []
  (subscribe [:android-af-search-result-entry-items]))

(defn search-term 
  "Gets the search term"
  []
  (subscribe [:android-af-search-term]))

(reg-event-fx
 :android-af-search-term-update
 (fn [{:keys [db]} [_event-id term]]
   {:db (assoc-in db [:android-af :search :term] term)
    :fx [[:bg-android-af-start-term-search [(android-af-active-db-key db) term]]]}))

(reg-event-fx
 :android-af-search-term-completed
 (fn [{:keys [db]} [_event-id result]]
   ;; result is a map {:entry-items [map of entry summary]} as defined in struct EntrySearchResult
   (let [not-matched (empty? (:entry-items result))]
     {:db (-> db
              (assoc-in  [:android-af :search :result] (:entry-items result))
              (assoc-in  [:android-af :search :term] (:term result))
              (assoc-in  [:android-af :search :selected-entry-id] nil)
              (assoc-in  [:android-af :search :error-text] nil)
              (assoc-in  [:android-af :search :not-matched] not-matched))})))


#_(reg-event-db
 :android-af-search-term-clear
 (fn [db [_event-id]]
   (-> db (assoc-in [:android-af :search :term] nil)
       (assoc-in  [:android-af :search :error-text] nil)
       (assoc-in [:android-af :search :selected-entry-id] nil)
       (assoc-in  [:android-af :search :result] []))))

(reg-event-db
 :android-af-search-error-text
 (fn [db [_event-id error-text]]
   (-> db (assoc-in  [:android-af :search :error-text] error-text)
          (assoc-in  [:android-af :search :selected-entry-id] nil)
          (assoc-in  [:android-af :search :result] []))))


;; Backend API call 
(reg-fx
 :bg-android-af-start-term-search
 ;; fn in 'reg-fx' accepts only single argument
 (fn [[db-key term]]
   (bg/search-term db-key term
                   (fn [api-response]
                     (when-let [result (on-ok api-response #(dispatch [:android-af-search-error-text %]))]
                       (dispatch [:android-af-search-term-completed result]))))))


;; Gets the matched entry items if any
(reg-sub
 :android-af-search-result-entry-items
 (fn [db _query-vec]
   (let [r (get-in db [:android-af :search :result])]
     ;; Note: if the ':search' key is not present in app-db, the r will be nil
     (if (nil? r)
       []
       r))))

#_(reg-sub
 :android-af-search-selected-entry-id
 (fn [db _query-vec]
   (get-in db [:android-af :search :selected-entry-id])))

(reg-sub
 :android-af-search-term
 (fn [db _query-vec]
   (get-in db [:android-af :search :term])))

(comment
  (in-ns 'onekeepass.mobile.android.autofill.events.common)

  (-> @re-frame.db/app-db keys)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))

  (def db-key-af (-> @re-frame.db/app-db :android-af :current-db-file-name)))
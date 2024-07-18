(ns onekeepass.mobile.android.autofill.events.common
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
;; which is shared here in autofill events and the main app' side 

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
  ;; To be called only in react components as it used 'subscribe' (i.e in React context)
  ([]
   (subscribe [:android-af-active-db-key]))
  ;; Used in reg-event-db , reg-event-fx by passing the main re-frame global 'app-db' 
  ([app-db]
   (get-in app-db [:android-af :current-db-file-name])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;  All page navigation related ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def home-page-title "home")

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
        :title home-page-title}
       info))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Open db related ;;;;;;;;;;;;;;;;;;;;;;;;

(defn open-selected-database
  "Called to open a datanase when user presses a row item of the datanases list on the home page"
  [file-name full-file-name-uri already-opened?]
  #_(dispatch [:android-af/database-file-picked {:file-name file-name :full-file-name-uri full-file-name-uri}])
  (if already-opened?
    (dispatch [:android-af/set-active-db-key full-file-name-uri])
    (dispatch [:android-af/database-file-picked {:file-name file-name :full-file-name-uri full-file-name-uri}])))

(defn cancel-on-press []
  (dispatch [:android-af/open-database-dialog-hide]))

(defn show-key-file-form []
  ;; kw :open-database-key-file-selected is used in events in ns onekeepass.mobile.events.key-file-form
  ;; to send back the selected key file 
  #_(dispatch [:key-file-form/show :open-database-key-file-selected]))

(defn database-field-update [kw-field-name value]
  (dispatch [:android-af/open-database-field-update kw-field-name value]))

;; See main app common
(defn opened-database-file-names
  "Gets db info map with keys [db-key database-name file-name key-file-name] from the opened database list"
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
(reg-event-fx
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

#_(reg-event-fx
   :common/set-active-db-key
   (fn [{:keys [db]} [_event-id full-file-name-uri]]
     (let [db (assoc db :current-db-file-name full-file-name-uri)
           database-name (current-database-name db)]
     ;; TODO: Need to ensure that db-key 'full-file-name-uri' is in the opened-db-list
       {:db (assoc db :current-db-file-name full-file-name-uri)
      ;; For now, the category page of the chosen db is shown irrespective of the previous active page
        :fx [[:dispatch [:entry-category/load-categories-to-show]]
             [:dispatch [:common/next-page :entry-category database-name]]]})))


(reg-event-fx
 :android-af/pick-database-file
 (fn [{:keys [_db]} [_event-id]]
   #_(println "android-af/pick-database-file is called ")
   {:fx [[:android-af/bg-pick-database-file]]}))

(reg-fx
 :android-af/bg-pick-database-file
 (fn []
   #_(println "android-af/bg-pick-database-file is called")
   (bg/pick-database-to-read-write
    (fn [api-response]
      #_(println " pick-database-to-read-write response " api-response)
      (when-let [picked-response (on-ok
                                  api-response
                                  ;; main app's :database-file-pick-error reused 
                                  (fn [error]
                                    #_(println "Error is " error)
                                    (dispatch [:database-file-pick-error error])))]
        (dispatch [:android-af/database-file-picked picked-response]))))))

;; This will make dialog open status true
#_(reg-event-fx
   :android-af/database-file-picked
   (fn [{:keys [db]} [_event-id {:keys [file-name full-file-name-uri]}]]
     {:db (-> db init-open-database-data
            ;; database-file-name is just the 'file name' part derived from full uri 'database-full-file-name'
            ;; to show in the dialog
              (assoc-in [:android-af  :open-database :database-file-name] file-name)
              (assoc-in [:android-af :open-database :database-full-file-name] full-file-name-uri)
              (assoc-in [:android-af :open-database :dialog-show] true))}))

#_(reg-event-fx
   :database-file-pick-error
   (fn [{:keys [_db]} [_event-id error]]
   ;; If the user cancels any file selection, 
   ;; the RN response is a error due to the use of promise rejecton in Native Module. And we can ignore that error 
     {:fx [(when-not (= "DOCUMENT_PICKER_CANCELED" (:code error))
             [:dispatch [:common/error-box-show "File Pick Error" error]])]}))

(reg-sub
 :android-af/open-database-dialog-data
 (fn [db _query-vec]
   (get-in db [:android-af :open-database])))


;;;;;;;;;;;;;;;;;;;;;;;; Load kdbx ;;;;;;;;;;;;;;;

;; Android af specific ':open-database' map (in :android-af )
;; [:android-af :open-database ]should have valid values by this time as
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
                                        (get-in db [:android-af :open-database :key-file-name])]]]}))))

(reg-fx
 :android-af/bg-load-kdbx
 (fn [[db-file-name password key-file-name]]
   (bg/load-kdbx db-file-name password key-file-name
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


#_(reg-event-fx
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
                            (when-let [result (on-ok api-response)]
                              (dispatch [:android-af/entry-list-load-complete result]))))))

;; This event name is stored in [:android-af :main-event-handler] and is called 
;; by any main app event
;; At this time, the vent name is stored in the event ':android-af/kdbx-database-opened'

;; For now, the main app event ':close-kdbx-completed' triggers this event 
(reg-event-fx
 :android-af/main-app-event-happened
 (fn [{:keys [db]} [_event_id args]]
   #_(println ":android-af/main-app-event-happened is called with args" args)
   {:fx [[:dispatch [:android-af-common/next-page HOME_PAGE_ID "Home"]]]}))

(reg-sub
 :android-af-active-db-key
 (fn [db _query-vec]
   (get-in db [:android-af :current-db-file-name])))


;;;;;;;;;;;

(comment
  (in-ns 'onekeepass.mobile.android.autofill.events.common)

  (-> @re-frame.db/app-db keys)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))

  (def db-key-af (-> @re-frame.db/app-db :android-af :current-db-file-name)))




#_(reg-event-fx
   :android-af/entry-list-load-complete
   (fn [{:keys [db]} [_event-id entry-summaries]]
     (println "In android-af/entry-list-load-complete...")
     {:db (-> db
              (assoc-in-key-db  [:android-af :entry-list :selected-entry-items] entry-summaries))
      :fx [#_[:dispatch [:android-af/next-page ENTRY_LIST_PAGE_ID "Entries"]]]}))

#_(reg-fx
   :load-bg-entry-summary-data
 ;; reg-fx accepts only single argument. So the calleer needs to use map or vector to pass multiple values
   (fn [[db-key category-detail category reloaded?]]
     (bg/entry-summary-data db-key category
                            (fn [api-response]
                            ;;(println "ENTRYLIST: In entry-summary-data callback with api-response " api-response)
                              (when-let [result (on-ok api-response)]
                                (dispatch [:entry-list-load-complete result category-detail reloaded?]))))))

;; When a list of all entry summary data is loaded successfully, this is called 
#_(reg-event-fx
   :entry-list-load-complete
   (fn [{:keys [db]} [_event-id result {:keys [title display-title] :as _category-detail} reloaded?]]
     (let [page-title (if (nil? display-title) title display-title)]
       {:db db
        :fx [[:dispatch [:update-selected-entry-items result]]
             (when-not reloaded?
               [:dispatch [:common/next-page :entry-list page-title]])]})))

#_(reg-event-fx
   :android-af/open-database-read-db-file
   (fn [{:keys [db]} [_event-id]]
     {:db (-> db (assoc-in [:android-af :open-database :status] :in-progress))
      :fx [[:android-af/bg-all-entries-on-db-open [(get-in db [:android-af :open-database :database-full-file-name])
                                                   (get-in db [:android-af :open-database :password])
                                                   (get-in db [:android-af :open-database :key-file-name])]]]}))

#_(reg-fx
   :android-af/bg-all-entries-on-db-open
   (fn [[db-key password key-file-name]]
     #_(bg/all-entries-on-db-open db-key password key-file-name
                                  (fn [api-response]
                                    (when-let [entry-summaries (on-ok api-response #(dispatch [:open-database-read-kdbx-error %]))]
                                      #_(dispatch [:entry-list/update-selected-entry-items db-key entry-summaries])
                                      (dispatch [:all-entries-loaded db-key entry-summaries]))))))

;; On successful loading of entries, we also set current-db-file-name to db-key so that
;; we can use 'active-db-key' though only one db is opened at a time
#_(reg-event-fx
   :entry-list/update-selected-entry-items
   (fn [{:keys [db]} [_event-id db-key entry-summaries]]
     {:db (-> db
              (assoc-in [:current-db-file-name] db-key)
              (assoc-in  [:entry-list :selected-entry-items] entry-summaries))
      :fx [[:dispatch [:common/next-page ENTRY_LIST_PAGE_ID "Entries"]]]}))

#_(reg-sub
   :selected-entry-items
   (fn [db _query-vec]
     (get-in db [:entry-list :selected-entry-items])))

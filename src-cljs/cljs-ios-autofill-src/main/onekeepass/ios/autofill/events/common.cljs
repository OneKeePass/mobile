(ns onekeepass.ios.autofill.events.common
  "All common events that are used across many pages"
  (:require [clojure.string :as str]
            [onekeepass.ios.autofill.background :as bg]
            [onekeepass.ios.autofill.constants :as const :refer [HOME_PAGE_ID ENTRY_FORM_PAGE_ID]]
            [onekeepass.ios.autofill.events.common-dialogs]
            [re-frame.core :refer [dispatch dispatch-sync reg-event-db
                                   reg-event-fx reg-fx reg-sub subscribe]]))

(defn sync-initialize
  "Called just before rendering to set all requied values in re-frame db"
  []
  (dispatch-sync [:load-autofill-init-data]
                 #_[:load-autofill-db-files-info]))

;;;;;;;;;;;;;;;;

(defn- default-error-fn [error]
  ;;(println "API returned error: " error)
  #_(dispatch [:common/error-box-show 'apiError error])
  (dispatch [:common/error-box-show "Api Error" error])
  #_(dispatch [:common/message-modal-hide]))

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

;; :current-db-file-name is set in :entry-list/update-selected-entry-items
(defn active-db-key
  "Returns the current database key 'db-key'"
  ;; To be called only in react components as it used 'subscribe' (i.e in React context)
  ([]
   (subscribe [:active-db-key]))
  ;; Used in reg-event-db , reg-event-fx by passing the main re-frame global 'app-db' 
  ([app-db]
   (:current-db-file-name app-db)))

(defn all-autofill-db-files-info
  "Returns a vec of maps  (the map is from struct CopiedDbFileInfo) or an empty vec
 "
  ([app-db]
   (let [r (get-in app-db [:autofill-db-files-info])]
     (if (nil? r) [] r))))

(defn org-db-file-path
  "Gets the full original db key for the selected db's db-key (which comes from :current-db-file-name)"
  [app-db db-key]
  (let [infos (all-autofill-db-files-info app-db)
        info (first (filter (fn [info] (= (:db-file-path info) db-key)) infos))]
    (:org-db-file-path info)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;; Init data ;;;;;;;;;;;;;;


(defn autofill-db-files-info []
  (subscribe [:autofill-db-files-info]))

(defn key-files-info []
  (subscribe [:key-files-info]))

#_(reg-event-fx
   :load-autofill-db-files-info
   (fn [{:keys [db]} [_event-id]]
     {:fx [[:bg-list-app-group-db-files]]}))

#_(reg-fx
   :bg-list-app-group-db-files
   (fn []
     (bg/list-app-group-db-files (fn [api-response]
                                   (when-let [files-info (on-ok api-response)]
                                     (dispatch [:autofill-db-files-info-loaded files-info]))))))

#_(reg-event-fx
   :autofill-db-files-info-loaded
   (fn [{:keys [db]} [_event-id files-info]]
     {:db (-> db (assoc-in [:autofill-db-files-info] files-info))
      :fx [[:bg-load-database-preferences]
           [:bg-list-key-files]]}))

(defn app-preference-data
  "Gets the app pref data"
  ([app-db]
   (get-in app-db [:app-preference :data])))

(defn app-lock-preference
  "Gets the app lock preference map (struct AppLockPreference)"
  ([app-db]
   (get-in app-db [:app-preference :data :app-lock-preference]))
  ([]
   (subscribe [:app-lock-preference])))

;;;
(reg-event-fx
 :load-autofill-init-data
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:bg-load-autofill-init-data]]}))

(reg-fx
 :bg-load-autofill-init-data
 (fn []
   (bg/load-autofill-init-data (fn [api-response]
                                 (when-let [af-init-data (on-ok api-response)]
                                   (dispatch [:autofill-init-data-loaded af-init-data]))))))

(reg-event-fx
 :autofill-init-data-loaded
 (fn [{:keys [db]} [_event-id {:keys [copied-dbs-info database-preferences app-lock-preference last-pin-auth-success-time] :as af-init-data}]]
   {:db (-> db (assoc-in [:app-preference :data]
                         {:database-preferences database-preferences
                          :app-lock-preference app-lock-preference
                          :last-pin-auth-success-time last-pin-auth-success-time})
            (assoc-in [:autofill-db-files-info] copied-dbs-info))
    :fx [[:dispatch [:app-lock/app-launched]]
         [:bg-list-key-files]]}))

(reg-sub
 :app-lock-preference
 (fn [db [_event-id]]
   (app-lock-preference db)))

;;;


(reg-fx
 :bg-list-key-files
 (fn []
   (bg/list-key-files (fn [api-response]
                        (when-let [key-files (on-ok api-response)]
                          (dispatch [:autofill-key-files-info-loaded key-files]))))))
(reg-event-fx
 :autofill-key-files-info-loaded
 (fn [{:keys [db]} [_event-id key-files]]
   ;; key-files is a Vec<String> - full key file path url and file name is extracted 
   (let [key-files-info (map (fn [name]
                               {:full-key-file-path  name
                                :file-name (last (str/split name #"/"))}) key-files)]
     {:db (-> db (assoc-in [:key-files-info] key-files-info))})))

(reg-sub
 :autofill-db-files-info
 (fn [db _query-vec]
   (let [r (:autofill-db-files-info db)]
     (if-not (nil? r) r  []))))

(reg-sub
 :key-files-info
 (fn [db _query-vec]
   (let [r (:key-files-info db)]
     (if-not (nil? r) r  []))))

(reg-sub
 :active-db-key
 (fn [db _query-vec]
   (:current-db-file-name db)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 

(defn database-preferences
  "Returns a vec of maps  (the map is from struct DatabasePreference) or an empty vec
 "
  ([app-db]
   (let [r (get-in app-db [:app-preference :data :database-preferences])]
     (if (nil? r) [] r))))


(defn database-preference-by-db-key
  "Gets the database preference ( a map ) for a given db-key from the vec or the default DatabasePreference"
  [app-db db-key]
  (let [org-db-key (org-db-file-path app-db db-key)
        db-prefs (database-preferences app-db)
        db-p (first (filter (fn [db-pref] (= (:db-key db-pref) org-db-key)) db-prefs))]
    (if (empty? db-p) {:db-key org-db-key
                       :db-open-biometric-enabled false
                       :db-unlock-biometric-enabled true}  db-p)))

#_(reg-fx
   :bg-load-database-preferences
   (fn []
     (bg/database-preferences (fn [api-response]
                                (when-let [db-prefs (on-ok api-response #())]
                                  (dispatch [:database-preferences-loaded db-prefs]))))))

#_(reg-event-fx
   :database-preferences-loaded
   (fn [{:keys [db]} [_event-id db-prefs]]
     {:db (-> db (assoc-in [:app-preference :data :database-preferences] db-prefs))}))
;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn cancel-extension []
  (bg/cancel-extension #(println %)))

(def home-page-title "home")

;; (def HOME_PAGE_ID :home)
;; (def LOGIN_PAGE_ID :login)
;; (def ENTRY_LIST_PAGE_ID :entry-list)
;; (def ENTRY_FORM_PAGE_ID :entry-form)

(defn to-home []
  (dispatch [:common/next-page HOME_PAGE_ID "Home"]))

(defn to-page [page-id title]
  (dispatch [:common/next-page page-id title]))

(defn to-entry-form-page []
  (dispatch [:common/next-page ENTRY_FORM_PAGE_ID  "Entry"]))

(defn to-previous-page
  "Called to navigate to the previous page"
  []
  (dispatch [:common/previous-page]))

(defn page-info []
  (subscribe [:page-info]))

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
       {:page HOME_PAGE_ID
        :title home-page-title}
       info))))

;;;;;;;;;;;;;;;;;;;; Search  ;;;;;;;;;;;;;;

#_(defn show-selected-entry [entry-id]
    (dispatch [:entry-form/find-entry-by-id entry-id]))

(defn search-term-update [term]
  (dispatch [:search-term-update term]))

(defn search-term-clear []
  (dispatch [:search-term-clear]))

(defn search-result-entry-items
  "Returns an atom that has a list of all search term matched entry items "
  []
  (subscribe [:search-result-entry-items]))

(defn search-term
  "Gets the search term"
  []
  (subscribe [:search-term]))

(reg-event-fx
 :search-term-update
 (fn [{:keys [db]} [_event-id term]]
   {:db (assoc-in db [:search :term] term)
    :fx [[:bg-start-term-search [(active-db-key db) term]]]}))

(reg-event-fx
 :search-term-completed
 (fn [{:keys [db]} [_event-id result]]
   ;; result is a map {:entry-items [map of entry summary]} as defined in struct EntrySearchResult
   (let [not-matched (empty? (:entry-items result))]
     {:db (-> db
              (assoc-in  [:search :result] (:entry-items result))
              (assoc-in  [:search :term] (:term result))
              (assoc-in  [:search :selected-entry-id] nil)
              (assoc-in  [:search :error-text] nil)
              (assoc-in  [:search :not-matched] not-matched))})))

(reg-event-db
 :search-term-clear
 (fn [db [_event-id]]
   (-> db (assoc-in [:search :term] nil)
       (assoc-in  [:search :error-text] nil)
       (assoc-in [:search :selected-entry-id] nil)
       (assoc-in  [:search :result] []))))

;; Backend API call 
(reg-fx
 :bg-start-term-search
 ;; fn in 'reg-fx' accepts only single argument
 (fn [[db-key term]]
   (bg/search-term db-key term
                   (fn [api-response]
                     (when-let [result (on-ok api-response #(dispatch [:search-error-text %]))]
                       (dispatch [:search-term-completed result]))))))

;; Gets the matched entry items if any
(reg-sub
 :search-result-entry-items
 (fn [db _query-vec]
   (let [r (get-in db [:search :result])]
     ;; Note: if the ':search' key is not present in app-db, the r will be nil
     (if (nil? r)
       []
       r))))

(reg-sub
 :search-selected-entry-id
 (fn [db _query-vec]
   (get-in db [:search :selected-entry-id])))

(reg-sub
 :search-term
 (fn [db _query-vec]
   (get-in db [:search :term])))

;;;;;;;;;;;;;;; Error dialog ;;;;;;;;;;;;;

;; Events are defined in common_dialogs.cljs

(defn close-message-dialog []
  (dispatch [:message-box-hide]))

(defn message-dialog-data []
  (subscribe [:message-box]))

;;;;;;;;;;;;;;;;;;;;; Common snackbar ;;;;;;;;;;;;;;;;

(defn show-snackbar
  [message]
  (dispatch [:common/message-snackbar-open message]))

(defn close-message-snackbar []
  (dispatch [:message-snackbar-close]))

(defn message-snackbar-data []
  (subscribe [:message-snackbar-data]))

;;;;;;;;;;;;;;;;;;; Loading translation data related ;;;;;;;;;;;;

(defn language-translation-loading-completed []
  (subscribe [:language-translation-loading-completed]))

(reg-event-fx
 :common/load-language-translation-complete
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [:background-loading-statuses :load-language-translation] true)}))

(reg-sub
 :language-translation-loading-completed
 (fn [db _query-vec]
   (get-in db [:background-loading-statuses :load-language-translation] false)))

(comment
  (in-ns 'onekeepass.ios.autofill.events.common)

  (def db-key (-> @re-frame.db/app-db :current-db-file-name))
  (-> @re-frame.db/app-db (get db-key) keys))



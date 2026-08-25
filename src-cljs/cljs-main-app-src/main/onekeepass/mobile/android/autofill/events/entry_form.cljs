(ns onekeepass.mobile.android.autofill.events.entry-form
  "Only the Android Autofill specific entry form events. All events should be prefixed with :android-af"
  (:require [clojure.string :as str]
            [onekeepass.mobile.android.autofill.events.common :refer [android-af-active-db-key
                                                                      native-app-request?
                                                                      totp-field-present?
                                                                      totp-only-request?]]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.constants :refer [ADDITIONAL_URLS PASSWORD USERNAME]]
            [onekeepass.mobile.events.common :refer [on-error on-ok]]
            [onekeepass.mobile.events.entry-form-common :refer [entry-form-key
                                                                extract-form-otp-fields]]
            [onekeepass.mobile.events.native-events :as native-events]
            [onekeepass.mobile.utils :as u :refer [contains-val?]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                                   reg-sub subscribe]]))

;; Seconds after which a code put on the clipboard is cleared again. Matches the iOS autofill
;; extension's fallback copy
(def CLIPBOARD_CLEAR_AFTER 20)

;; How long a copied code is left on the clipboard.
;; A copied token is a snapshot and dies with its own time step whatever the clipboard does,
;; so keeping it beyond that only leaves behind a code no verifier will take - which reads as
;; a wrong code rather than as an empty clipboard. 'ttl' is what is left of the step the token
;; belongs to, and a verifier that allows a step of drift takes it until the end of the step
;; after that, so one more period is the longest it can still be of any use
(defn- otp-clipboard-timeout [ttl period]
  (if (and (number? ttl) (number? period))
    (+ ttl period)
    CLIPBOARD_CLEAR_AFTER))

#_(defn entry-form-field-visibility-toggle
    "Called with the field name as key that is toggled between show/hide"
    [key]
    (dispatch [:android-af-entry-form-field-visibility-toggle key]))

(defn entry-form-data-fields
  " 
  Called to get value of one more of form top level fields. 
  The arg is a single field name or  fields in a vector of two more field 
  names (Keywords) like [:title :icon-id]
  Returns an atom which resolves to a single value  or a map when derefenced
  e.g {:title 'value'} or {:tags 'value} or {:title 'value' :icon-id 'value}
   "
  [fields]
  (subscribe [:android-af-entry-form-data-fields fields]))

#_(defn entry-form-uuid []
    (subscribe [:android-af-entry-form-data-fields :uuid]))

(defn entry-form
  "Returns an atom that has the map entry-form"
  []
  (subscribe [:android-af-entry-form]))

(defn entry-form-field
  "Gets the value of any field at the top level in entry-form itself. See other subs to get the field
  values from :data or [:data :section-fields] 
  "
  [file-name-kw]
  (subscribe [:android-af-entry-form-field file-name-kw]))

(defn visible? [key]
  (subscribe [:android-af-entry-form-field-in-visibile-list key]))

(defn- on-entry-find [api-response]
  ;; :entry-form-data-load-error from main entry-form event is reused
  (when-let [entry (on-ok api-response #(dispatch [:entry-form-data-load-error %]))]
    (dispatch [:android-af-entry-form-data-load-completed entry])
    (dispatch [:android-af-entry-list/form-loaded])))

;; Called when user selects an entry uuid by long press on entry list page
(reg-event-fx
 :android-af-entry-form/find-entry-by-id
 (fn [{:keys [db]} [_event-id entry-uuid]]
   {:fx [[:bg-android-af-find-entry-by-id [(android-af-active-db-key db) entry-uuid on-entry-find]]]}))

(reg-fx
 :bg-android-af-find-entry-by-id
 (fn [[db-key entry-uuid dispatch-fn]]
   (bg/find-entry-by-id db-key entry-uuid dispatch-fn)))

;; IMPORTANT: Valid values for :showing are [:selected :new :history-form]
(defn- set-on-entry-load [app-db entry-form-data]
  (let [otp-fields (extract-form-otp-fields entry-form-data)]
    (-> app-db
        (assoc-in [:android-af entry-form-key :data] entry-form-data)
        (assoc-in [:android-af entry-form-key :undo-data] entry-form-data)
        (assoc-in [:android-af entry-form-key :otp-fields] otp-fields)
        (assoc-in [:android-af entry-form-key :showing] :selected)
        (assoc-in [:android-af entry-form-key :edit] false))))

(reg-event-fx
 :android-af-entry-form-data-load-completed
 (fn [{:keys [db]} [_event-id entry-form-data]]
   {:db  (set-on-entry-load db entry-form-data)}))

;;;;;
;; Toggles the a field's membership in a list of visibility fields
(reg-event-db
 :android-af-entry-form-field-visibility-toggle
 (fn [db [_event-id key]]
   (let [vl (get-in db [:android-af entry-form-key :visibility-list])]
     (if (contains-val? vl key)
       (assoc-in db [:android-af entry-form-key :visibility-list] (filterv #(not= % key) vl))
       (assoc-in db [:android-af entry-form-key :visibility-list] (conj vl key))))))

;; Checks whether a form field is visible or not
(reg-sub
 :android-af-entry-form-field-in-visibile-list
 (fn [db [_query-id key]]
   (contains-val? (get-in db [:android-af entry-form-key :visibility-list]) key)))

(reg-sub
 :android-af-entry-form
 (fn [db _query-vec]
   (get-in db [:android-af entry-form-key])))

(reg-sub
 :android-af-entry-form-data
 (fn [db _query-vec]
   (get-in db [:android-af entry-form-key :data])))

;; Gets a :data level field value
(reg-sub
 :android-af-entry-form-data-fields
 :<- [:android-af-entry-form-data]
 (fn [data [_query-id fields]]
   (if-not (vector? fields)
     ;; fields is a single field name
     (get data fields)
     ;; a vector field names
     (select-keys data fields))))


;; Gets the value of a field at top level 'entry-form' itself
(reg-sub
 :android-af-entry-form-field
 :<- [:android-af-entry-form]
 (fn [form [_query-id field]]
   ;;(println "form-db called... " form)
   (get form field)))

;; (reg-sub
;;  :entry-form-edit
;;  (fn [db _query-vec]
;;    (get-in db [entry-form-key :edit])))


;;;;;;;;;;;;;;;;;;; Complete Autofill activity ;;;;;;;;;;;;;;;;;;;;;;;;

;; This is copied from iOS extension cljs. Move to a common place ?
(defn- find-field
  "Finds the KV data map (KeyValueData struct) for a given field name from an entries data map
   The arg 'form-data' is a map based on struct EntryFormData
   The field name is a string 
   "
  [form-data field-name]
  (let [kvds (flatten (vals (:section-fields form-data)))]
    (first (filter (fn [m] (= field-name (:key m))) kvds))))

;; The autofill request can come from a native app with no web domain, in which
;; case the calling-app uri is our synthesized "android://<packageName>" token
;; (see ParseResultData.buildUri). Such a token never matches stored https URLs
;; until it is added to an entry, so on fill we offer to remember the app
;; (capture-on-fill). KeePassDX stores the same identity as "androidapp://<pkg>".
(def ANDROID_APP_URI_PREFIX "android://")
(def ANDROIDAPP_URI_PREFIX "androidapp://")

(defn- native-app-uri?
  "True when the autofill request came from a native app with no web domain."
  [app-uri]
  (and (string? app-uri) (str/starts-with? app-uri ANDROID_APP_URI_PREFIX)))

(defn- app-already-associated?
  "True when the entry's Additional URLs already holds this app token for the
   same package (either android:// or KeePassDX's androidapp:// scheme)."
  [form-data app-uri]
  (let [pkg (subs app-uri (count ANDROID_APP_URI_PREFIX))
        additional (or (:value (find-field form-data ADDITIONAL_URLS)) "")
        tokens (set (str/split additional #"\s+"))]
    (or (contains? tokens (str ANDROID_APP_URI_PREFIX pkg))
        (contains? tokens (str ANDROIDAPP_URI_PREFIX pkg)))))

;; Called when user selectes the "Autofill" menu option
;; By this time, the entry form should have been loaded as entry-list single press or long press
;; would have loaded the entry form before the menu selection.
;; The calling-app uri was fetched and stored at autofill load time
;; (:android-af :client-app-uri). If it is a native app not yet associated with
;; this entry we offer capture-on-fill; otherwise (web request, app already
;; associated, or uri unknown) we fill directly as before.
(reg-event-fx
 :android-af-entry-form/complete-login-autofill
 (fn [{:keys [db]} [_event-id]]
   (let [app-uri (get-in db [:android-af :client-app-uri])
         form-data (get-in db [:android-af entry-form-key :data])]
     ;; A code only screen is the second page of a login whose first page already offered
     ;; the association, so asking again there would be a second prompt for one login
     (if (and (not (totp-only-request? db))
              (native-app-uri? app-uri)
              (not (app-already-associated? form-data app-uri)))
       {:db (assoc-in db [:android-af :app-capture]
                      {:show true
                       :app-uri app-uri
                       :entry-uuid (:uuid form-data)
                       :entry-title (:title form-data)})}
       {:fx [[:dispatch [:android-af-entry-form/do-complete-login-autofill]]]}))))

;; User declined to remember the app - just complete the fill.
(reg-event-fx
 :android-af-entry-form/autofill-capture-skip
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [:android-af :app-capture :show] false)
    :fx [[:dispatch [:android-af-entry-form/do-complete-login-autofill]]]}))

;; associate → (await) → save → (await) → fill → close
;; User chose to remember the app - associate it with the entry, then fill. The
;; fill proceeds even if the association call fails (best-effort enhancement).
(reg-event-fx
 :android-af-entry-form/autofill-capture-confirm
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [app-uri entry-uuid]} (get-in db [:android-af :app-capture])]
     {:db (assoc-in db [:android-af :app-capture :show] false)
      :fx [[:bg-android-af-associate-app [(android-af-active-db-key db) entry-uuid app-uri]]]})))

(reg-fx
 :bg-android-af-associate-app
 (fn [[db-key entry-uuid app-uri]]
   (bg/android-autofill-associate-app-to-entry
    db-key entry-uuid app-uri
    (fn [api-response]
      ;; associate_app_to_entry only mutates the in-memory db; persist it before
      ;; filling, otherwise the close that follows the fill drops the new token.
      ;; Save only when it actually modified the entry (returns true).
      (let [associated (on-ok api-response
                              (fn [error]
                                (js/console.warn "App association failed:" error)))]
        (if associated
          (dispatch [:android-af-entry-form/save-then-complete-fill db-key])
          (dispatch [:android-af-entry-form/do-complete-login-autofill])))))))

;; Saves the db (so the just-added app token is persisted) and then completes the
;; fill. A save failure is surfaced but still proceeds to fill (best-effort).
(reg-event-fx
 :android-af-entry-form/save-then-complete-fill
 (fn [{:keys [_db]} [_event-id db-key]]
   {:fx [[:bg-android-af-save-kdbx db-key]]}))

(reg-fx
 :bg-android-af-save-kdbx
 (fn [db-key]
   (bg/save-kdbx db-key false
                 (fn [api-response]
                   (on-error api-response)
                   (dispatch [:android-af-entry-form/do-complete-login-autofill])))))

;; The actual fill: copies the selected credentials to the app that initiated the
;; AF service and then closes the opened database (consistent with passkey flows).
;; When the requesting screen has a 2FA code field, the token is generated now instead of
;; reusing the one the form's polling happens to hold - that one may be a second from
;; expiring by the time the target app validates it.
(reg-event-fx
 :android-af-entry-form/do-complete-login-autofill
 (fn [{:keys [db]} [_event-id]]
   (let [form-data (get-in db [:android-af entry-form-key :data])
         ;; Only whether there is any otp field to ask about. Which of them the entry is
         ;; represented by is left to the backend, so that what reaches the clipboard is
         ;; always the code the entry list row was showing
         entry-has-otp? (seq (extract-form-otp-fields form-data))]
     (cond
       (totp-field-present? db)
       {:fx [[:bg-android-af-current-otp [(android-af-active-db-key db) (:uuid form-data)]]]}

       ;; No code field was recognised on a native app screen. That may mean the screen has
       ;; no 2FA at all, or that the field was there and the id/hint heuristic did not see it
       ;; - the two are indistinguishable here. The code goes to the clipboard so the user can
       ;; paste it in the second case. A browser request is left alone: a web code field is
       ;; found from its html attributes, so a miss there is not the likely explanation and
       ;; copying on every ordinary web login would not be worth the exposure
       (and (native-app-request? db) entry-has-otp?)
       {:fx [[:bg-android-af-copy-otp-then-fill [(android-af-active-db-key db) (:uuid form-data)]]]}

       :else
       {:fx [[:bg-android-af-complete-fill [form-data nil]]]}))))

;; Generates the current token and puts it on the clipboard (auto cleared) before completing
;; the fill. The fill must come after the copy, since it closes the autofill activity. A
;; failure to generate is not fatal - the credentials are still filled
(reg-fx
 :bg-android-af-copy-otp-then-fill
 (fn [[db-key entry-uuid]]
   (bg/entry-list-current-otps
    db-key [entry-uuid]
    (fn [api-response]
      (let [{:keys [otp-field-name token ttl period]}
            (first (on-ok api-response
                          (fn [error]
                            (js/console.warn "Could not generate the otp token:" error))))]
        (if (str/blank? token)
          (dispatch [:android-af-entry-form/complete-fill-with-otp nil])
          (bg/android-copy-to-clipboard
           {:field-name otp-field-name
            :field-value token
            :protected true
            :cleanup-after (otp-clipboard-timeout ttl period)}
           (fn [copy-response]
             (on-error copy-response)
             (dispatch [:android-af-entry-form/complete-fill-with-otp nil])))))))))

;; Generates the current token for the entry's standard otp field. A failure is not fatal:
;; the credentials are still filled and only the code field is left empty
(reg-fx
 :bg-android-af-current-otp
 (fn [[db-key entry-uuid]]
   (bg/entry-list-current-otps
    db-key [entry-uuid]
    (fn [api-response]
      (let [token (:token (first (on-ok api-response
                                        (fn [error]
                                          (js/console.warn "Could not generate the otp token:" error)))))]
        (dispatch [:android-af-entry-form/complete-fill-with-otp token]))))))

(reg-event-fx
 :android-af-entry-form/complete-fill-with-otp
 (fn [{:keys [db]} [_event-id token]]
   {:fx [[:bg-android-af-complete-fill [(get-in db [:android-af entry-form-key :data]) token]]]}))

(reg-fx
 :bg-android-af-complete-fill
 (fn [[form-data otp]]
   (let [username (-> (find-field form-data USERNAME) :value)
         password (-> (find-field form-data PASSWORD) :value)]
     (bg/android-complete-login-autofill username
                                         password
                                         otp
                                         (fn [api-response]
                                           (when-not (on-error api-response)
                                             (dispatch [:android-af/close-current-db])))))))

;; UI accessors for the capture-on-fill confirm dialog
(defn app-capture-data []
  (subscribe [:android-af-app-capture-data]))

(defn autofill-capture-skip []
  (dispatch [:android-af-entry-form/autofill-capture-skip]))

(defn autofill-capture-confirm []
  (dispatch [:android-af-entry-form/autofill-capture-confirm]))

(reg-sub
 :android-af-app-capture-data
 (fn [db _query-vec]
   (get-in db [:android-af :app-capture])))

;;;;;;;;;;;;;;;;;;;;; OTP ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn entry-form-otp-start-polling
  "Called to start polling from use effect"
  []
  (dispatch [:android-af/entry-form-otp-start-polling]))

(defn entry-form-otp-stop-polling
  "Called to stop polling from use effect"
  []
  (dispatch [:android-af/entry-form-otp-stop-polling]))

(defn otp-currrent-token [opt-field-name]
  (subscribe [:android-af/otp-currrent-token opt-field-name]))

;; Called to start polling from use effect
(reg-event-fx
 :android-af/entry-form-otp-start-polling
 (fn [{:keys [db]} [_event-id]]
   (let [form-status (get-in db [:android-af entry-form-key :showing])
         entry-uuid (get-in db [:android-af entry-form-key :data :uuid])
         otp-fields (extract-form-otp-fields (get-in db [:android-af entry-form-key :data]))]
     (if (= form-status :selected)
       {:fx [[:otp/start-polling-otp-fields [(android-af-active-db-key db)
                                             entry-uuid
                                             otp-fields]]]}
       {}))))

;; Called to stop polling from use effect
(reg-event-fx
 :android-af/entry-form-otp-stop-polling
 (fn [{:keys [db]} [_event-id]]
   (let [form-status (get-in db [:android-af entry-form-key :showing])]
     (if (= form-status :selected)
       {:fx [[:otp/stop-all-entry-form-polling [(android-af-active-db-key db) nil]]]}
       {}))))

;; Called from backend event handler (see native_events.cljs) to update with the current tokens 
(reg-event-fx
 :android-af/entry-form-update-otp-tokens
 (fn [{:keys [db]} [_event-id entry-uuid current-opt-tokens-by-field]]
   ;; First we need to ensure that the incoming entry id is the same one showing
   (if (= entry-uuid (get-in db [:android-af entry-form-key :data :uuid]))

     ;; otp-fields is a map with otp field name as key and its token info with time ttl 
     (let [db (reduce (fn [db [otp-field-name {:keys [token ttl]}]]
                        (let [otp-field-name (name otp-field-name) ;; make sure field name is string
                              otp-field-m (get-in db [:android-af entry-form-key :otp-fields otp-field-name])
                              otp-field-m (if (nil? token)
                                            (assoc otp-field-m :ttl ttl)
                                            (assoc otp-field-m :token token :ttl ttl))]
                          (assoc-in db [:android-af entry-form-key :otp-fields otp-field-name] otp-field-m)))
                      db current-opt-tokens-by-field)]

       ;;(println "After otp-fields " (get-in db [entry-form-key :otp-fields]))

       {:db db})
     {})))

;; Returns a map with otp fileds as key and its token info as value
;; e.g {"My Git OTP Code" {:token "576331", :ttl 9, :period 30}, "otp" {:token "145214", :ttl 9, :period 30}}
(reg-sub
 :android-af/otp-currrent-token
 (fn [db [_query-id otp-field-name]]
   (get-in db [:android-af entry-form-key :otp-fields otp-field-name])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Native event listener ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn register-entry-otp-update-handler
  "The register-event-listener is called to register android 
  autofill specific listener to handle OTP filed update. This event is registered 
  in addition to the main app's event handler. Note the use of ':android-af' as first arg to 
  'register-event-listener'
  "
  []
  (bg/register-event-listener :android-af  native-events/EVENT_ENTRY_OTP_UPDATE
                              (fn [event-message]
                                (let [converted (bg/transform-api-response event-message
                                                                           {:convert-response-fn native-events/token-response-converter})]
                                  (when-let [{:keys [entry-uuid reply-field-tokens]} (on-ok converted)]
                                    (dispatch [:android-af/entry-form-update-otp-tokens entry-uuid reply-field-tokens]))))))


(register-entry-otp-update-handler)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment
  (in-ns 'onekeepass.mobile.android.autofill.events.entry-form)

  (def db-key-af (-> @re-frame.db/app-db :android-af :current-db-file-name)))

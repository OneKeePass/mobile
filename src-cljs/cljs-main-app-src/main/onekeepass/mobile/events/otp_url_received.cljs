(ns onekeepass.mobile.events.otp-url-received
  "Handles an 'otpauth://' url that another app sends to us - typically the device Camera app
   after the user scans a 2FA QR code and presses the shown link

   Android only at this time. See the intent-filter in AndroidManifest.xml and the routing
   done in EventEmitter.kt

   The url holds the TOTP shared secret and any app on the device can send it. So it is
   validated in the backend as the in app scanner does and the user has to confirm where it
   goes before anything is written to the database. The url itself is kept in the app db
   only till it is used or dismissed
  "
  (:require [clojure.string :as str]
            [onekeepass.mobile.constants :refer [OTP_URL_PREFIX
                                                 SEARCH_PAGE_ID
                                                 UUID_OF_ENTRY_TYPE_LOGIN]]
            [onekeepass.mobile.events.app-lock :refer [app-locked?]]
            [onekeepass.mobile.events.common :refer [active-db-key
                                                     get-in-key-db
                                                     is-db-locked
                                                     on-ok]]
            [onekeepass.mobile.events.entry-form-common :refer [otp-field-target]]
            [re-frame.core :refer [dispatch reg-event-fx reg-sub subscribe]]))

(def ^:private otp-url-key :otp-url-received)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Url parsing  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- decode-url-part [s]
  (try
    (js/decodeURIComponent s)
    (catch :default _e s)))

(defn- issuer-param
  "Gets the value of the 'issuer' query param of an otp url if any"
  [query]
  (some (fn [param]
          (when (str/starts-with? param "issuer=")
            (decode-url-part (subs param (count "issuer=")))))
        (str/split (str query) #"&")))

(defn- otp-url-info
  "Parses the label part and the issuer param of an otp url so that we can show what the
   user is about to save and prefill a new entry with it
   Returns a map with keys :issuer and :account-name either of which may be nil

   This parsing is only for showing and prefilling. The url itself is always validated in
   the backend before it is used
  "
  [otp-url]
  (let [after-prefix (-> (subs otp-url (count OTP_URL_PREFIX))
                         (str/replace-first #"^/" ""))
        [label query] (str/split after-prefix #"\?" 2)
        label (decode-url-part (str label))
        ;; The label is either 'issuer:account name' or just the 'account name'
        [label-issuer account-name] (if (str/includes? label ":")
                                      (str/split label #":" 2)
                                      [nil label])]
    {:issuer (-> (or (issuer-param query) label-issuer) str str/trim not-empty)
     :account-name (-> (str account-name) str/trim not-empty)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- otp-url-valid? [otp-url]
  (str/starts-with? (str/lower-case (str otp-url)) OTP_URL_PREFIX))

(defn- pending-url [app-db]
  (get-in app-db [otp-url-key :otp-url]))

(defn- db-usable?
  "True when the url can be dealt with right away - the app lock screen is not showing and
   there is an open database that is not locked

   The app lock is checked because the dialogs here are shown through the portal and they
   would otherwise come up over the app lock screen and the user would be asked to make a
   change to a database before getting past that lock"
  [app-db]
  (let [db-key (active-db-key app-db)]
    (and (not (app-locked? app-db))
         (not (nil? db-key))
         (not (is-db-locked app-db db-key)))))

(defn picking-entry-mode?
  "True when the search page is showing so that the user picks the entry the pending url is
   to be added to. Used in the search page events"
  [app-db]
  (and (some? (pending-url app-db))
       (boolean (get-in app-db [otp-url-key :picking-entry]))))

(defn- entry-title
  "The title of a new entry created for this url. The issuer is what the user recognises
   and the account name is used when the url carries no issuer"
  [{:keys [issuer account-name]}]
  (or issuer account-name))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  UI calls  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn destination-dialog-data []
  (subscribe [:generic-dialog-data :otp-url-destination-dialog]))

(defn create-new-entry []
  (dispatch [:otp-url-received-create-new-entry]))

(defn pick-existing-entry []
  (dispatch [:otp-url-received-pick-existing-entry]))

(defn cancel []
  (dispatch [:otp-url-received-cancel]))

(defn overwrite-confirm-data []
  (subscribe [:generic-dialog-data :otp-url-overwrite-confirm-dialog]))

(defn overwrite-confirmed []
  (dispatch [:otp-url-received-overwrite-confirmed]))

(defn picking-entry?
  "True while the search page is used to pick the entry the url is to be added to"
  []
  (subscribe [:otp-url-received/picking-entry]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Events  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- on-form-otp-url
  "The backend returns the url in its normalised form or an error for an url that it cannot
   make an otp code out of"
  [api-response]
  (when-let [otp-url (on-ok api-response #(dispatch [:otp-url-received-invalid %]))]
    (dispatch [:otp-url-received-validated otp-url])))

;; Called from the native event handler and from the launch time pull call
;; See onekeepass.mobile.events.native-events and the fx :bg-otp-auth-url-on-create
;;
;; The url comes from another app and is never used as it is. The prefix check here only
;; keeps an obviously wrong one from going any further and the backend does the real
;; validation as it does for the scanned and the manually entered ones
(reg-event-fx
 :otp-url-received/url-received
 (fn [{:keys [_db]} [_event-id otp-url]]
   (if-not (otp-url-valid? otp-url)
     {:fx [[:dispatch [:common/error-box-show 'scanError 'scannedUrlNotOtpUri]]]}
     {:fx [[:entry-form/bg-form-otp-url [{:secret-or-url otp-url} on-form-otp-url]]]})))

(reg-event-fx
 :otp-url-received-invalid
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [[:dispatch [:common/error-box-show 'scanError error]]]}))

(reg-event-fx
 :otp-url-received-validated
 (fn [{:keys [db]} [_event-id otp-url]]
   {:db (assoc db otp-url-key (merge {:otp-url otp-url} (otp-url-info otp-url)))
    :fx [[:dispatch [:otp-url-received-route-pending]]]}))

;; Called after the app lock is passed and after a database is opened or unlocked so that
;; any url that arrived before that is dealt with now
(reg-event-fx
 :otp-url-received/check-pending
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:otp-url-received-route-pending]]]}))

;; Decides what is to be done with the url that is waiting. It stays where it is till the
;; user is in a position to say where it goes
(reg-event-fx
 :otp-url-received-route-pending
 (fn [{:keys [db]} [_event-id]]
   (cond
     (nil? (pending-url db))
     {}

     ;; Nothing is put in front of the user over the app lock screen
     (app-locked? db)
     {}

     (db-usable? db)
     {:fx [[:dispatch [:otp-url-received-destination-dialog-show]]]}

     ;; No database is open or the open one is locked. The message is shown in the home
     ;; page and so we come back to it first
     :else
     {:fx [[:dispatch [:common/to-home-page]]
           [:dispatch [:common/message-box-show 'otpUrlReceived 'otpUrlNoDatabaseOpen]]]})))

(reg-event-fx
 :otp-url-received-destination-dialog-show
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [issuer account-name]} (get db otp-url-key)]
     {:fx [[:dispatch [:generic-dialog-show-with-state :otp-url-destination-dialog
                       {:data {:issuer issuer :account-name account-name}}]]]})))

(reg-event-fx
 :otp-url-received-cancel
 (fn [{:keys [db]} [_event-id]]
   {:db (dissoc db otp-url-key)
    :fx [[:dispatch [:generic-dialog-close :otp-url-destination-dialog]]
         [:dispatch [:generic-dialog-close :otp-url-overwrite-confirm-dialog]]]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  New entry  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; The root group is preselected and the user can change it before saving the entry
(defn- root-group-info [app-db]
  (let [root-uuid (get-in-key-db app-db [:groups :data :root-uuid])
        groups (get-in-key-db app-db [:groups :data :groups])]
    (when root-uuid
      {:uuid root-uuid :name (get-in groups [root-uuid :name])})))

(reg-event-fx
 :otp-url-received-create-new-entry
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:generic-dialog-close :otp-url-destination-dialog]]
         ;; The group tree may not be loaded yet and then no group is preselected. The
         ;; required field validation of the form makes the user pick one before saving
         [:dispatch [:entry-form/add-new-entry
                     (root-group-info db)
                     UUID_OF_ENTRY_TYPE_LOGIN
                     [:otp-url-received-new-entry-form-created]]]]}))

;; Called once the blank new entry form is ready. The entry is not saved here and the user
;; reviews it and presses Save as for any other new entry
(reg-event-fx
 :otp-url-received-new-entry-form-created
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [otp-url account-name] :as url-info} (get db otp-url-key)]
     {:db (dissoc db otp-url-key)
      :fx [[:dispatch [:entry-form/otp-url-received-set {:otp-url otp-url
                                                         :title (entry-title url-info)
                                                         :user-name account-name
                                                         :save? false}]]]})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Existing entry  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(reg-event-fx
 :otp-url-received-pick-existing-entry
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [otp-url-key :picking-entry] true)
    :fx [[:dispatch [:generic-dialog-close :otp-url-destination-dialog]]
         [:dispatch [:search/term-clear]]
         [:dispatch [:common/next-page SEARCH_PAGE_ID "selectEntry"]]]}))

;; Called when the user opens the search page in the normal way. Leaving the search page
;; with the back action does not go through any event of ours and the pick mode would
;; otherwise still be on when the user comes back to search later
(reg-event-fx
 :otp-url-received/picking-cancel
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [otp-url-key :picking-entry] false)}))

;; Called when the user presses an entry row in the search page while picking
;; See onekeepass.mobile.events.search
(reg-event-fx
 :otp-url-received/entry-picked
 (fn [{:keys [db]} [_event-id entry-uuid]]
   {:db (assoc-in db [otp-url-key :picking-entry] false)
    :fx [[:dispatch [:entry-form/find-entry-by-id entry-uuid [:otp-url-received-entry-loaded]]]]}))

;; The picked entry is now loaded in the entry form and the user can see what changes
(reg-event-fx
 :otp-url-received-entry-loaded
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [value]} (otp-field-target db)]
     (if (str/blank? value)
       {:fx [[:dispatch [:otp-url-received-set-on-loaded-entry]]]}
       ;; The entry has an otp code already and replacing it makes the earlier one unusable
       {:fx [[:dispatch [:generic-dialog-show-with-state :otp-url-overwrite-confirm-dialog {}]]]}))))

(reg-event-fx
 :otp-url-received-overwrite-confirmed
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:generic-dialog-close :otp-url-overwrite-confirm-dialog]]
         [:dispatch [:otp-url-received-set-on-loaded-entry]]]}))

(reg-event-fx
 :otp-url-received-set-on-loaded-entry
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [otp-url]} (get db otp-url-key)]
     {:db (dissoc db otp-url-key)
      ;; Only the otp field is set here. The title and the user name of an entry that the
      ;; user already has are left alone
      :fx [[:dispatch [:entry-form/otp-url-received-set {:otp-url otp-url :save? true}]]
           [:dispatch [:common/message-snackbar-open 'otpUrlAdded]]]})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Subscriptions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(reg-sub
 :otp-url-received/picking-entry
 (fn [db _query-vec]
   (boolean (get-in db [otp-url-key :picking-entry]))))

(comment
  (in-ns 'onekeepass.mobile.events.otp-url-received))

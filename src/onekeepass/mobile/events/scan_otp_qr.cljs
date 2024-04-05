(ns onekeepass.mobile.events.scan-otp-qr
  (:require-macros [onekeepass.mobile.okp-macros
                    :refer  [as-api-response-handler]])
  (:require [clojure.string :as str]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.constants :refer [CAMERA_PERMISSION_DENIED
                                                 CAMERA_PERMISSION_GRANTED
                                                 CAMERA_PERMISSION_NOT_DETERMINED
                                                 CAMERA_PERMISSION_RESTRICTED
                                                 CAMERA_SCANNER_PAGE_ID
                                                 OTP_URL_PREFIX]]
            [onekeepass.mobile.events.common :refer [on-ok]]
            [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub
                                   subscribe]]))

(defn initiate-scan-qr [{:keys [section-name field-name standard-field] :as field-info-m}]
  ;;(println "initiate-scan-qr field-info-m... " field-info-m)
  (let [permission-status (bg/camera-permission-status)]
    (cond
      ;; (empty? (bg/available-cameras))
      ;; (dispatch [:common/message-box-show
      ;;            "No Camera" "No camera is found. Please enter the secret code manually"])

      (= permission-status CAMERA_PERMISSION_GRANTED)
      (dispatch [:scan-qr-camera-show (assoc field-info-m :camera-permission CAMERA_PERMISSION_GRANTED)])

      (= permission-status CAMERA_PERMISSION_DENIED)
      (dispatch [:scan-qr-camera-show (assoc field-info-m :camera-permission CAMERA_PERMISSION_DENIED)])

      (= permission-status CAMERA_PERMISSION_NOT_DETERMINED)
      (dispatch [:scan-qr-request-permission (assoc field-info-m :camera-permission CAMERA_PERMISSION_NOT_DETERMINED)])

      ;;:common/error-box-show or :common/message-box-show with title message
      ;; Ask user to use manual code entering
      (= permission-status CAMERA_PERMISSION_RESTRICTED)
      (dispatch [:common/error-box-show
                 "Restricted" "Please enter the secret code instead of scanning QR code"])

      ;; Ask user to use manual code entering
      :else
      (dispatch [:common/error-box-show
                 "No scanning" "Please enter the secret code instead of scanning QR code"]))))

(defn scan-qr-data []
  (subscribe [:scan-qr-data]))

;;;;;;;;;;;;;; Events ;;;;;;

;; Keys of field-info-m are [section-name field-name standard-field camera-permission]

(reg-event-fx
 :scan-qr-camera-show
 (fn [{:keys [db]} [_event-id field-info-m]]
   {:db (-> db (assoc-in [:scan-otp-qr] field-info-m))
    :fx [[:dispatch [:common/next-page CAMERA_SCANNER_PAGE_ID "Scan QR Code"]]]}))

(reg-fx
 :scan-qr/initiate-scan-qr
 (fn [field-info-m]
   (initiate-scan-qr field-info-m)))

;; called when the current status is CAMERA_PERMISSION_NOT_DETERMINED
(reg-event-fx
 :scan-qr-request-permission
 (fn [{:keys [db]} [_event-id field-info-m]]
   {:db (-> db (assoc-in [:scan-otp-qr] field-info-m))
    :fx [[:bg-camera-request-permission]]}))

;; called when the current status is CAMERA_PERMISSION_NOT_DETERMINED
;; Typically this is the first time the app is used for scanning
(reg-fx
 :bg-camera-request-permission
 (fn []
   (bg/request-camera-permission (fn [api-response]
                                   (when-let [status (on-ok api-response)]
                                     (dispatch [:scan-qr-ask-camera-permission-response status])
                                     #_(if (= CAMERA_PERMISSION_GRANTED p)
                                         #()
                                       ;; Ask user to use manual code entering
                                         #()))))))

(reg-event-fx
 :scan-qr-ask-camera-permission-response
 (fn [{:keys [db]} [_event-id permission-status]]
   (if (= permission-status CAMERA_PERMISSION_RESTRICTED)

     {:db (-> db (assoc-in [:scan-otp-qr :camera-permission] permission-status))
      :fx [[:dispatch [:common/error-box-show "Restricted" "Please enter the secret code instead of scanning QR code"]]]}

     {:db (-> db (assoc-in [:scan-otp-qr :camera-permission] permission-status))
      :fx [[:dispatch [:common/next-page CAMERA_SCANNER_PAGE_ID "Scan QR Code"]]]})))


#_(defn callback-on-form-otp-url [api-response]
    (when-let [opt-url (on-ok
                        api-response
                        #(dispatch [:scan-qr-form-url-error %]))]
      (dispatch [:scan-qr-form-url-success opt-url])))

(def callback-on-form-otp-url (as-api-response-handler
                               #(dispatch [:scan-qr-form-url-success %])
                               #(dispatch [:scan-qr-form-url-error %])))

(reg-event-fx
 :scan-qr-scanned
 (fn [{:keys [db]} [_event-id url]]
   (println "Going to call bg with url and check " url (= (str/lower-case url) OTP_URL_PREFIX))
   (if (str/starts-with? (str/lower-case url) OTP_URL_PREFIX)
     {:fx [[:entry-form/bg-form-otp-url [{:secret-or-url url} callback-on-form-otp-url]]]}
     {:fx [[:dispatch [:common/error-box-show "Error" "The scanned url is not an otp type url"]]]})))


(reg-event-fx
 :scan-qr-form-url-success
 (fn [{:keys [db]} [_event-id url]]
   (println "verified url returned is  " url)
   {}))

(reg-event-fx
 :scan-qr-form-url-error
 (fn [{:keys [db]} [_event-id error]]
   (println "verified url error returned is " error)
   {}))

;; Called to show the page with link for openSettings as CAMERA_PERMISSION_DENIED  
#_(reg-event-fx
   :scan-qr-camera-access-settings-show
   (fn [{:keys [db]} [_event-id field-info-m]]
     {:db (-> db (assoc-in [:scan-otp-qr] field-info-m))}))

(reg-sub
 :scan-qr-data
 (fn [db]
   (get-in db [:scan-otp-qr])))

(def test1 (clojure.core/fn [G__6787]
             (clojure.core/when-let [G__6788 (onekeepass.mobile.events.common/on-ok G__6787
                                                                                    (fn* [p1__6784#] (dispatch [:scan-qr-form-url-error p1__6784#])))]
               ((fn* [p1__6783#] (dispatch [:scan-qr-form-url-success p1__6783#])) G__6788))))

(comment
  (in-ns 'onekeepass.mobile.events.scan-otp-qr))
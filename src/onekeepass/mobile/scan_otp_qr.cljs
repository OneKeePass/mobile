(ns onekeepass.mobile.scan-otp-qr
  (:require [onekeepass.mobile.rn-components
             :as rnc :refer [rn-view
                             rnp-button
                             rn-safe-area-view
                             rnp-text]]
            [onekeepass.mobile.constants :refer [CAMERA_PERMISSION_GRANTED]]
            [onekeepass.mobile.events.entry-form :as form-events]
            [onekeepass.mobile.events.scan-otp-qr :as scan-qr-events]))

(def scanned (atom false))

(defn on-code-scanned [codes]
  (when-not @scanned
    (let [codes (js->clj codes :keywordize-keys true)]
      ;; Disable code scanning to prevent rapid scans
      (when (> (count codes) 0) 
        (scan-qr-events/scan-qr-scanned (-> codes (nth 0) :value))
        (reset! scanned true)))))

(def code-scanner-arg (clj->js {:codeTypes ["qr"]
                                :onCodeScanned on-code-scanned}))

(defn camera-view []
  (let [device (rnc/use-camera-device "back")]

    (if-not (nil? device)
      [rn-view {:style {:flex 1}}
       [rn-view {:style {:flex .15 :background-color @rnc/primary-container-color}}]
       [rn-view {:style {:flex .8}}
        [rnc/camera {:style {:position "absolute" :left 0 :right 0 :top 0 :bottom 0}
                     :device device
                     :isActive true
                     :codeScanner code-scanner-arg}]]
       [rn-view {:style {:flex .15 :background-color @rnc/primary-container-color}}]]

      [rn-view {:style {:flex 1}}
       [rn-view {:style {:flex .3}}]
       [rn-view {:style {:flex .7}}
        [rnp-text {:style {:color @rnc/error-color :margin 20}
                   :variant "titleSmall"} "Back camera is not found. Please enter the secret code manually"]]])))

(defn settings-link []
  (let [form-changed @(form-events/form-modified)]
    [rn-view {:style {:flex 1}}
     [rn-view {:style {:flex .3}}]
     (if-not form-changed
       [rn-view {:style {:flex .4} :justify-content "center"}
        [rn-view {:flexDirection "column"  :align-self "center"  :align-items "center"}
                ;;[rn-view { :style {:margin 10} :flexDirection "row"  :align-self "center"  :align-items "center"}
         [rnp-text {:style {:color @rnc/primary-color :margin 20}
                    :variant "titleSmall"} "Camera permission is required for OneKeePass to scan a QR code. Please enable it"]
         [rnp-button {:style {:width "70%"}
                      :labelStyle {:fontWeight "bold"}
                      :mode "outlined"
                      :on-press #(.openSettings rnc/rn-native-linking)} "Settings"]]]

       [rn-view {:style {:flex .4} :justify-content "center"}
        [rn-view {:flexDirection "column"  :align-self "center"  :align-items "center"}

         [rnp-text {:style {:color @rnc/primary-color :margin 20}
                    :variant "titleSmall"} "Camera permission is required for OneKeePass to scan a QR code"]

         [rnp-text {:style {:color @rnc/error-color :margin 20}
                    :variant "titleSmall"}
          (str "There are unsaved changes in the entry form. "
               "Please go back and save the changes first if required. "
               "Then go to the device settings to enable the camera permission")]]])

     [rn-view {:style {:flex .3}}]]))

(defn main-content []
  (let [{:keys [camera-permission]} @(scan-qr-events/scan-qr-data)]
    (reset! scanned false)
    (if (= camera-permission CAMERA_PERMISSION_GRANTED)
      [:f> camera-view]
      [settings-link])))

(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
   [main-content]])

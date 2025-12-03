(ns onekeepass.mobile.scan-otp-qr
  (:require [onekeepass.mobile.constants :refer [CAMERA_PERMISSION_GRANTED]]
            [onekeepass.mobile.events.entry-form :as form-events]
            [onekeepass.mobile.events.scan-otp-qr :as scan-qr-events]
            [onekeepass.mobile.rn-components
             :as rnc :refer [rn-safe-area-view rn-view rnp-button rnp-text]]
            [onekeepass.mobile.translation :refer [lstr-mt]]))

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
       [rn-view {:style {:flex 0.15 :background-color @rnc/primary-container-color}}]
       [rn-view {:style {:flex 0.8}}
        [rnc/camera {:style {:position "absolute" :left 0 :right 0 :top 0 :bottom 0}
                     :device device
                     :isActive true
                     :codeScanner code-scanner-arg}]]
       [rn-view {:style {:flex 0.15 :background-color @rnc/primary-container-color}}]]

      [rn-view {:style {:flex 1}}
       [rn-view {:style {:flex 0.3}}]
       [rn-view {:style {:flex 0.7}}
        [rnp-text {:style {:color @rnc/error-color :margin 20}
                   :variant "titleSmall"} 
         (lstr-mt 'scanQr 'backCameraNotFound)]]])))

(defn settings-link []
  (let [form-changed @(form-events/form-modified)]
    [rn-view {:style {:flex 1}}
     [rn-view {:style {:flex 0.3}}]
     (if-not form-changed
       [rn-view {:style {:flex 0.4} :justify-content "center"}
        [rn-view {:flexDirection "column"  :align-self "center"  :align-items "center"}
                ;;[rn-view { :style {:margin 10} :flexDirection "row"  :align-self "center"  :align-items "center"}
         [rnp-text {:style {:color @rnc/primary-color :margin 20}
                    :variant "titleSmall"} 
          (lstr-mt 'scanQr 'cameraPermissionEnable)]
         
         [rnp-button {:style {:width "70%"}
                      :labelStyle {:fontWeight "bold"}
                      :mode "outlined"
                      :on-press #(.openSettings rnc/rn-native-linking)} "Settings"]]]

       [rn-view {:style {:flex 0.4} :justify-content "center"}
        [rn-view {:flexDirection "column"  :align-self "center"  :align-items "center"}

         [rnp-text {:style {:color @rnc/primary-color :margin 20}
                    :variant "titleSmall"} 
          (lstr-mt 'scanQr 'cameraPermissionRequired)]

         [rnp-text {:style {:color @rnc/error-color :margin 20}
                    :variant "titleSmall"}
          (str "There are unsaved changes in the entry form. "
               "Please go back and save the changes first if required. "
               "Then go to the device settings to enable the camera permission")]]])

     [rn-view {:style {:flex 0.3}}]]))

(defn main-content []
  (let [{:keys [camera-permission]} @(scan-qr-events/scan-qr-data)]
    (reset! scanned false)
    (if (= camera-permission CAMERA_PERMISSION_GRANTED)
      [:f> camera-view]
      [settings-link])))

(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
   [main-content]])

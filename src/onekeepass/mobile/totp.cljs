(ns
 onekeepass.mobile.totp
  (:require [reagent.core :as r]
            [onekeepass.mobile.rn-components
             :as rnc :refer [rn-view
                             rn-safe-area-view
                             rnp-text]]
            [onekeepass.mobile.background :as bg]))

(def scanned (atom false))

(defn on-code-scanned [codes]
  (when-not @scanned
    (let [codes (js->clj codes :keywordize-keys true) ]
      (println "codes " codes)
      
      ;; Disable code scanning to prevent rapid scans
      (when (> (count codes) 0)
        (println "value is " (-> codes (nth 0) :value))
        (reset! scanned true)))
    
    )
  
  )

(def code-scanner-arg (clj->js {:codeTypes ["qr"]
                                :onCodeScanned on-code-scanned}))

(defn camera-view []
  (fn []
    (let [{:keys [hasPermission requestPermission]} (js->clj (rnc/use-camera-permission) :keywordize-keys true)
          device (rnc/use-camera-device "back")]
      (println "hasPermission is " hasPermission)
      (println "requestPermission is " requestPermission)
      ;;(println "Device is " device)
      ;;(requestPermission)
      #_(when (= hasPermission false)
          (requestPermission)
          [rn-view
           [rnp-text "Scanner comes here"]])

      (if (= hasPermission false)
        (do
          (requestPermission)
          [rn-view
           [rnp-text "Camera permission is required..."]
           [rnp-text {:style {:textDecorationLine "underline"}
                      :on-press #(.openSettings rnc/rn-native-linking)}  "Seetings"]])
        (let [device1 (if (nil? device) (rnc/use-camera-device "back") device )]
          ;;(println "device " device)
          [rn-view {:style {:flex 1}}
           [rnc/camera {:style {:position "absolute" :left 0 :right 0 :top 0 :bottom 0}
                        :device device1
                        :isActive true
                        :codeScanner code-scanner-arg}]
           [rnp-text "Scanner comes here"]]))


      ;;(.requestPermission permission)
      )))

(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
   [:f> camera-view]])
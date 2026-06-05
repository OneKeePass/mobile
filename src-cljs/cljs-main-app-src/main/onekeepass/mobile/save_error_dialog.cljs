(ns onekeepass.mobile.save-error-dialog
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.rn-components :refer [tertiary-color
                                            rnp-button
                                            rnp-divider
                                            rnp-modal
                                            rn-scroll-view
                                            rnp-portal
                                            rnp-text
                                            rn-view]]
   [onekeepass.mobile.common-components :refer [confirm-dialog-factory]]
   [onekeepass.mobile.translation :refer [lstr-bl lstr-modal-dlg-text
                                          lstr-modal-dlg-title]]
   [onekeepass.mobile.events.save :as save-events]))

;;;;;;;;;;;;;;; confirm dialog ;;;;;;;;;;;;;;
(def ovewrite-confirm-dialog-data (r/atom {:dialog-show false
                                           :title nil
                                           :confirm-text nil
                                           :call-on-ok-fn #(println %)}))

;; overwrite-confirm-dialog-info is a map with keys [:dialog :show]
;; value of key :show is a fn
(def overwrite-confirm-dialog-info (confirm-dialog-factory ovewrite-confirm-dialog-data))

;; When we use 'lstr' fn, we need to define inside a component as it requires react context
(defn overwrite-on-press []
  (swap! ovewrite-confirm-dialog-data assoc
         :title (lstr-modal-dlg-title 'overwriting)
         :confirm-text (lstr-modal-dlg-text 'overwriteConfirm)
         :call-on-ok-fn save-events/overwrite-on-save-error)

  ((:show overwrite-confirm-dialog-info)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-error-modal [{:keys [dialog-show file-name error-type error-message merge-save-called remote-db?]}]
  [rnp-modal {:style {:margin-right 25
                      :margin-left 25}
              :visible dialog-show
              :dismissable false
              :dismissableBackButton false
              ;;:onDismiss #() 
              :contentContainerStyle {:borderRadius 15
                                      :height "75%"
                                      :backgroundColor
                                      "white"
                                      :padding 10}}

   [rn-scroll-view {:centerContent "true" :style {:backgroundColor "white"}}
    [rn-view {:style {:height "100%" :backgroundColor "white"}}
     [rn-view {:style {:flex 0.1  :justify-content "center" :align-items "center"}}
      [rnp-text {:style {:color @tertiary-color} :variant "titleLarge"} (lstr-modal-dlg-title 'saveError)]
      [rnp-text {:style {:color @tertiary-color} :variant "titleSmall"} file-name]]

     [rn-view {:style {:flex 0.2  :min-height 50 :justify-content "center" :align-items "center"}}

      (condp =  error-type
        :content-change-detected
        [rnp-text {:style {:textAlign "justify"}} (lstr-modal-dlg-text 'contentChangedSinceLoad)]

        :no-remote-storage-connection
        [rnp-text {:style {:textAlign "justify"}} (lstr-modal-dlg-text 'noRemoteServerConnection)]

        [rnp-text {:style {:textAlign "justify"}} error-message])

      #_(if (= error-type :content-change-detected)
          [rnp-text {:style {:textAlign "justify"}} "The database content has changed since you have loaded"]
          [rnp-text {:style {:textAlign "justify"}} error-message])]

     [rnp-divider]
     [rn-view {:style {:flex 00.70}}
      (when (and (= error-type :content-change-detected) remote-db?)
        [:<>
         [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
          [rnp-button {:style {:width "50%"}
                       :labelStyle {:fontWeight "bold"}
                       :mode "text"
                       :on-press save-events/merge-on-save-error} (lstr-bl "merge")]
          [rnp-text {:style {:textAlign "justify"}}
           (lstr-modal-dlg-text 'mergeExternalChangesDesc)]]
         [rnp-divider]])

      [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
       [rnp-button {:style {:width "50%"}
                    :labelStyle {:fontWeight "bold"}
                    :mode "text"
                    :on-press save-events/save-as-on-error} (lstr-bl "saveAs")]
       [rnp-text {:style {:textAlign "justify"}}
        (lstr-modal-dlg-text 'saveAsConflictDesc)]]

      [rnp-divider]
      [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
       [rnp-button {:style {:width "70%"}
                    :labelStyle {:fontWeight "bold"}
                    :mode "text"
                    :on-press save-events/discard-on-save-error} (lstr-bl "discardAndCloseDb")]
       [rnp-text {:style {:textAlign "justify"}}
        (lstr-modal-dlg-text 'discardChangesDesc)]]

      (when (= error-type :content-change-detected)
        [:<>
         [rnp-divider]
         [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
          [rnp-button {:style {:width "70%"}
                       :labelStyle {:fontWeight "bold"}
                       :textColor "red"
                       :mode "text"
                       :on-press overwrite-on-press} (lstr-bl "overwrite")]
          [rnp-text {:style {:textAlign "justify"}}
           (lstr-modal-dlg-text 'overwriteDbDesc)]]])

      [rnp-divider]
      ;; Hide the cancel button for any save error happened during merge save call
      (when-not merge-save-called
        [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
         [rnp-button {:style {:width "70%"}
                      :labelStyle {:fontWeight "bold"}
                      :mode "text"
                      :on-press save-events/save-error-modal-cancel} (lstr-bl "cancel")]
         [rnp-text {:style {:textAlign "justify"}} ""]])]]]

   ;; Anchoring the overwrite confirm dialog to this modal 
   [rnp-portal
    (:dialog overwrite-confirm-dialog-info)]])

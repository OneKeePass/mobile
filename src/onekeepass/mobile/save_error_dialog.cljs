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
   [onekeepass.mobile.events.save :as save-events]))

;;;;;;;;;;;;;;; confirm dialog ;;;;;;;;;;;;;;
(def ovewrite-confirm-dialog-data (r/atom {:dialog-show false
                                           :title nil
                                           :confirm-text nil
                                           :call-on-ok-fn #(println %)}))

;; overwrite-confirm-dialog-info is a map with :dialog and :show
;; value of key :show is a fn
(def overwrite-confirm-dialog-info (confirm-dialog-factory ovewrite-confirm-dialog-data))

;; When we use 'lstr' fn, we need to define inside a component as it requires react context
(defn overwrite-on-press []
  (swap! ovewrite-confirm-dialog-data assoc
         :title "Overwriting"
         :confirm-text   "This will overwrite the target databses. Are you sure?"
         :call-on-ok-fn save-events/overwrite-on-save-error)

  ((:show overwrite-confirm-dialog-info)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn save-error-modal [{:keys [dialog-show file-name error-type error-message]}]
  [rnp-modal {:style {:margin-right 25
                      :margin-left 25}
              :visible dialog-show
              :dismissable false
              :dismissableBackButton false
              ;;:onDismiss #() 
              :contentContainerStyle {:borderRadius 15
                                      :height "60%"
                                      :backgroundColor
                                      "white"
                                      :padding 10}}

   [rn-scroll-view {:centerContent "true" :style {:backgroundColor "white"}}
    [rn-view {:style {:height "100%" :backgroundColor "white"}}
     [rn-view {:style {:flex .1  :justify-content "center" :align-items "center"}}
      [rnp-text {:style {:color tertiary-color} :variant "titleLarge"} "Database Save Error"]
      [rnp-text {:style {:color tertiary-color} :variant "titleSmall"} file-name]]

     [rn-view {:style {:flex .2  :min-height 50 :justify-content "center" :align-items "center"}}
      (if (= error-type :content-change-detected)
        [rnp-text {:style {:textAlign "justify"}} "The database content has changed since you have loaded"]
        [rnp-text {:style {:textAlign "justify"}} error-message])]

     [rnp-divider]
     [rn-view {:style {:flex .7}}
      [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
       [rnp-button {:style {:width "50%"}
                    :labelStyle {:fontWeight "bold"}
                    :mode "text"
                    :on-press save-events/save-as-on-error} "Save as .."]
       [rnp-text {:style {:textAlign "justify"}}
        "You can save the database file with all your changes to another file and later manually resolve the conflicts"]]

      [rnp-divider]
      [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
       [rnp-button {:style {:width "70%"}
                    :labelStyle {:fontWeight "bold"}
                    :mode "text"
                    :on-press save-events/discard-on-save-error} "Discard & Close database"]
       [rnp-text {:style {:textAlign "justify"}}
        "Ignore all changes made here"]]

      (when (= error-type :content-change-detected)
        [:<>
         [rnp-divider]
         [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
          [rnp-button {:style {:width "70%"}
                       :labelStyle {:fontWeight "bold"}
                       :textColor "red"
                       :mode "text"
                       :on-press overwrite-on-press} "Overwrite"]
          [rnp-text {:style {:textAlign "justify"}}
           "This database will overwrite the target database with your changes"]]])

      [rnp-divider]
      [rn-view {:style {:margin-top 10 :margin-bottom 10 :align-items "center"}}
       [rnp-button {:style {:width "70%"}
                    :labelStyle {:fontWeight "bold"}
                    :mode "text"
                    :on-press save-events/save-error-modal-hide} "Cancel"]
       [rnp-text {:style {:textAlign "justify"}} ""]]]]]

   [rnp-portal
    (:dialog overwrite-confirm-dialog-info)]])

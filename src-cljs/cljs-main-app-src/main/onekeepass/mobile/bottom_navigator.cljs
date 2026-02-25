(ns onekeepass.mobile.bottom-navigator
  (:require
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.events.app-settings :as as-events]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.password-generator :as pg-events]
   [onekeepass.mobile.events.settings :as stgs-events]
   [onekeepass.mobile.rn-components :as rnc :refer [page-background-color
                                                    rn-view rnp-icon-button
                                                    rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-ml]]))

(defn home-icon-action-item []
  {:icon const/ICON-HOME :label (lstr-ml "home") :action #(cmn-events/to-home-page)})

(defn close-db-icon-action-item []
  {:icon const/ICON-DB-EYE-OFF-OUTLINE :label (lstr-ml "closedb") :action #(cmn-events/close-current-kdbx-db)})

(defn settings-icon-action-item []
  {:icon const/ICON-COG-OUTLINE :label (lstr-ml "settings") :action #(stgs-events/load-db-settings)})

(defn bottom-nav-bar-gen
  "Creates a bottom bar
   The arg 'items' is a vec of maps where eacg map has keys [icon label action]
  "
  [items]
  [rn-view {:style {:width "100%"
                    ;; Need to use the same background-color as the entry list content to make it opaque
                    :background-color @page-background-color
                    :padding-left 25
                    :padding-right 25
                    :borderTopWidth 1
                    :borderTopColor  @rnc/outline-variant
                    :min-height 50

                    :bottom 0}}

   [rn-view {:flexDirection "row" :justifyContent "space-between"}
    (doall
     (for [{:keys [icon label action]} items]
       ^{:key label}  [rn-view {:align-items "center"}
                       [rnp-icon-button {:size 24
                                         :icon icon
                                         :iconColor @rnc/on-error-container-color
                                         :onPress action}]
                       [rnp-text {:style {:margin-top -5}
                                  :text-align "center"}
                        label]]))]])

(defn bottom-common-nav-bar []
  (fn []
    (let [items [(home-icon-action-item)
                 (close-db-icon-action-item)
                 (settings-icon-action-item)]]

      [bottom-nav-bar-gen items])))

(defn home-page-bottom-bar []
  (fn []
    (let [items [{:icon const/ICON-DOTS-SQUARE :label (lstr-ml "pwdGenerator") :action #(pg-events/generate-password)}
                 {:icon const/ICON-HOME :label (lstr-ml "appSettings") :action #(as-events/to-app-settings-page)}]]

      [bottom-nav-bar-gen items])))

;; This is the orginal custom bottom bar used in ns 'onekeepass.mobile.entry-list'
;; Leaving it here for any reference if required
#_(defn- bottom-nav-bar
    "A functional reagent componnent that returns the custom bottom bar"
    []
    (fn []
      (let [selected-category-key @(elist-events/selected-category-key)
            selected-category-detail @(elist-events/selected-category-detail)
            sort-criteria @(elist-events/entry-list-sort-criteria)]

        [rn-view {:style {:width "100%"
                          ;; Need to use the same background-color as the entry list content to make it opaque
                          :background-color @page-background-color
                          :padding-left 25
                          :padding-right 25
                          :borderTopWidth 1
                          :borderTopColor  @rnc/outline-variant
                          :min-height 50

                          ;;:position "absolute"

                          ;; In adndroid when we use absolute position, this bottom bar hides
                          ;; the entries list content - particularly when the list has more entries
                          ;; and even using the scroll does not work and it scrolls behind this component 

                          ;; Not using absolute position works for both android in iOS

                          ;; After Android 'compileSdkVersion = 35 introduction, adding insets hides the entries list content
                          ;; Also see comments in js/components/KeyboardAvoidingDialog.js

                          ;; Instead of setting bottom value from inset, we are using a dummy view 'adjust-inset-view'

                          :bottom 0}}

         [rn-view {:flexDirection "row" :justifyContent "space-between"}
          [rn-view {:align-items "center"}
           [rnp-icon-button {:size 24
                             :icon const/ICON-SORT
                             :iconColor @rnc/on-error-container-color
                             :onPress (fn [e]
                                        (show-sort-menu e sort-criteria))}]
           [rnp-text {:style {:margin-top -5}
                      :text-align "center"}
            (lstr-l 'sort)]]

          [rn-view {:align-items "center"}
           [rnp-icon-button {:size 24
                             :icon const/ICON-PLUS
                             :iconColor @rnc/on-error-container-color
                             :disabled @(cmn-events/current-db-disable-edit)
                             :onPress (fn [e]
                                        (show-fab-action-menu e selected-category-key selected-category-detail))}]
           [rnp-text {:style {:margin-top -5}
                      :text-align "center"}
            (lstr-l 'add)]]]])))

;;;;;;;;;;; Few other ideas of using bottom bar in entry-list ;;;;;;;;;;;;;;;;;;;;;;;;

#_(def idx (r/atom -1))

#_(defn- bottom-nav-bar1 []
    (let [routes [{:key "sort" :title "Sort" :focusedIcon "heart" :unfocusedIcon "heart-outline"}
                  {:key "settings" :title "Settings" :focusedIcon "bell" :unfocusedIcon "bell-outline"}]

          states {:index @idx
                  :routes routes}]

      [rnp-bottom-navigation-bar {:safeAreaInsets {:bottom 0}
                                  :navigationState (clj->js states :keywordize-keys true)
                                  :onTabPress (fn [props]
                                                (let [{:keys [route] :as p} (js->clj props :keywordize-keys true)
                                                      _ (println "route is " route)
                                                      {:keys [key preventDefault]} route]
                                                  (println "key is " key)
                                                  (println "p is " p)

                                                  (if (= key "sort")
                                                    (reset! idx 0)
                                                    (reset! idx 1)))
                                                #_(println props))}]))

#_(defn- bottom-nav-bar2 []
    (let [routes [{:key "sort" :title "Sort" :focusedIcon "heart" :unfocusedIcon "heart-outline"}
                  {:key "settings" :title "Settings" :focusedIcon "bell" :unfocusedIcon "bell-outline"}]

          states {:index @idx
                  :routes routes}]

      [rnp-bottom-navigation-bar {:safeAreaInsets {:bottom 0}
                                  :navigationState (clj->js states :keywordize-keys true)
                                  :onTabPress (fn [props]
                                                (let [{:keys [route] :as p} (js->clj props :keywordize-keys true)
                                                      _ (println "route is " route)
                                                      {:keys [key preventDefault]} route]
                                                  (println "key is " key)
                                                  (println "p is " p)

                                                  (if (= key "sort")
                                                    (reset! idx 0)
                                                    (reset! idx 1)))
                                                #_(println props))}]))

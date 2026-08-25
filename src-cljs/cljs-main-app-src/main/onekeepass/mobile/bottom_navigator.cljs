(ns onekeepass.mobile.bottom-navigator
  (:require
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.events.app-settings :as as-events]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.password-generator :as pg-events]
   [onekeepass.mobile.events.settings :as stgs-events]
   [onekeepass.mobile.background :refer [is-iOS]]
   [onekeepass.mobile.rn-components :as rnc :refer [rn-touchable-opacity
                                                    rn-view rnp-icon rnp-text
                                                    rnp-touchable-ripple]]
   [onekeepass.mobile.translation :refer [lstr-ml]]))

;; Gap left between the bar and either side of the screen. Same as the side margin of the cards
;; of the grouped lists, so that the bar lines up with whatever is listed above it
(def ^:private BAR-SIDE-MARGIN 12)

;; The page already keeps the bar clear of the home indicator through its safe area view, so no
;; further gap is added below. A negative value here would pull the bar down into that inset
(def ^:private BAR-BOTTOM-MARGIN -8)

;; Close to half of the height the bar settles at, so that its ends read as nearly round
(def ^:private BAR-CORNER-RADIUS 24)

;; Outline drawn around the floating iOS bar so that its edge is still read where the bar and the
;; page ground are close in color. Kept to a hairline - the same weight the read mode form fields
;; draw their underline at - as anything heavier turns into a frame around the bar
(def ^:private BAR-BORDER-WIDTH 0.5)

(defn home-icon-action-item []
  {:icon const/ICON-HOME-OUTLINE :label (lstr-ml "home") :action #(cmn-events/to-home-page)})

(defn close-db-icon-action-item []
  {:icon const/ICON-DB-EYE-OFF-OUTLINE :label (lstr-ml "closedb") :action #(cmn-events/close-current-kdbx-db)})

(defn settings-icon-action-item []
  {:icon const/ICON-COG-OUTLINE :label (lstr-ml "settings") :action #(stgs-events/load-db-settings)})

(defn- bar-style
  "iOS floats its bottom bars over the page as a rounded card inset from the edges. Android
   keeps the band that spans the full width and is attached to the bottom edge, which is what
   its own navigation bars do"
  []
  (if (is-iOS)
    {:background-color @rnc/grouped-list-card-color
     :margin-left BAR-SIDE-MARGIN
     :margin-right BAR-SIDE-MARGIN
     :margin-bottom BAR-BOTTOM-MARGIN
     :borderRadius BAR-CORNER-RADIUS
     :borderWidth BAR-BORDER-WIDTH
     :borderColor @rnc/outline-variant
     ;;:min-height 50
     :overflow "hidden"}

    {:width "100%"
     ;; The same background as the page content, so that the band is opaque
     :background-color @rnc/page-background-color
     :borderTopWidth BAR-BORDER-WIDTH
     :min-height 50
     :padding-top 8
     :padding-bottom 16
     :borderTopColor @rnc/outline-variant}))

(defn- bar-item-content
  "The icon of an item with its label beneath. The icon carries the primary color to read as
   something that can be pressed, the label the muted color secondary text uses"
  [icon label]
  [rn-view {:style {:align-items "center" :padding-top 5 :padding-bottom 5}}
   [rnp-icon {:source icon :size 22 :color @rnc/primary-color}]
   [rnp-text {:variant "labelMedium"
              :numberOfLines 1
              :style {:margin-top 1 :textAlign "center" :color @rnc/on-surface-variant}}
    label]])

(defn- bar-item
  "One pressable item of the bar. The whole cell is what responds to the press, not just the
   icon, which is both a larger target and what a bottom bar does on either platform"
  [{:keys [icon label action]}]
  (if (is-iOS)
    ;; iOS dims what is being pressed. It has no ripple
    [rn-touchable-opacity {:style {:flex 1} :activeOpacity 0.4 :onPress action}
     [bar-item-content icon label]]

    [rnp-touchable-ripple {:style {:flex 1} :onPress action}
     [bar-item-content icon label]]))

(defn bottom-nav-bar-gen
  "Creates a bottom bar
   The arg 'items' is a vec of maps where eacg map has keys [icon label action]
  "
  [items]
  [rn-view {:style (bar-style)}
   [rn-view {:style {:flexDirection "row" :align-items "flex-start"}}
    (doall
     (for [{:keys [label] :as item} items]
       ;; Each item takes an equal share of the bar so that the items stay evenly spread
       ;; whether the page's bar has two of them or four
       ^{:key label} [bar-item item]))]])

(defn bottom-common-nav-bar []
  (fn []
    (let [items [(home-icon-action-item)
                 (close-db-icon-action-item)
                 (settings-icon-action-item)]]

      [bottom-nav-bar-gen items])))

(defn home-page-bottom-bar []
  (fn []
    (let [items [{:icon const/ICON-DOTS-SQUARE :label (lstr-ml "pwdGenerator") :action #(pg-events/generate-password)}
                 {:icon const/ICON-COG-OUTLINE :label (lstr-ml "appSettings") :action #(as-events/to-app-settings-page)}]]

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

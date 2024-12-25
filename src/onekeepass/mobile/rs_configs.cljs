(ns onekeepass.mobile.rs-configs
  "Remote storage configs page showing list of all for sftp or webdav conenctions"
  (:require
   [onekeepass.mobile.common-components :refer [confirm-dialog
                                                list-section-header
                                                menu-action-factory]]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.remote-storage :as rs-events]
   [onekeepass.mobile.rn-components
    :as rnc
    :refer [appbar-text-color dots-icon-name page-title-text-variant
            rn-safe-area-view rn-section-list rn-view rnp-button
            rnp-divider rnp-icon-button rnp-list-icon rnp-list-item
            rnp-menu rnp-menu-item rnp-portal rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-bl lstr-l lstr-ml lstr-pt]]
   [reagent.core :as r]))


(defn appbar-title []
  [rn-view {:flexDirection "row"
            :style {:alignItems "center"
                    :justify-content "space-between"}}
   [rnp-button {:style {}
                :textColor @appbar-text-color
                :mode "text"
                :onPress cmn-events/to-previous-page} (lstr-bl 'cancel)]
   [rnp-text {:style {:color @appbar-text-color
                      :max-width 200
                      :margin-right 10 :margin-left 10}
              :ellipsizeMode "tail"
              :numberOfLines 1
              :variant page-title-text-variant} (lstr-pt 'selectConnection)]
   [rnp-button {:style {}
                :textColor @appbar-text-color

                :mode "text"
                :onPress rs-events/remote-storage-rs-type-new-form-page-show} (lstr-bl 'add)]])


;;;;;;;;;;;;;;;;;;;;;;;;  Confirm delete ;;;;;;;;;;;;;;;;;;;;;;

(def ^:private conn-del-confirm-dialog-data (r/atom {:dialog-show false :connection-id nil}))

(defn- hide-conf-dlg []
  (reset! conn-del-confirm-dialog-data {:dialog-show false :connection-id nil}))

(defn- show-conf-dlg [connection-id]
  (reset! conn-del-confirm-dialog-data {:dialog-show true :connection-id connection-id}))

(defn delete-config-confirm-dialog []
  (let [{:keys [dialog-show connection-id]} @conn-del-confirm-dialog-data]
    [confirm-dialog {:dialog-show dialog-show
                     :title "Delete remote connection"
                     :confirm-text "Do you want to delete this connection permanently?"
                     :actions [{:label (lstr-bl "yes")
                                :on-press (fn []
                                            (rs-events/remote-storage-delete-selected-config connection-id)
                                            (hide-conf-dlg))}
                               {:label (lstr-bl "no")
                                :on-press (fn []
                                            (hide-conf-dlg))}]}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Menus ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private list-menu-action-data (r/atom {:show false :x 0 :y 0
                                              :connection-id nil}))

(defn hide-list-action-menu []
  (swap! list-menu-action-data assoc :show false))

(defn show-list-menu
  "Pops the menu popup for the selected row item"
  [^js/PEvent event connection-id]
  (swap! list-menu-action-data
         assoc :show true
         :connection-id connection-id
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

;;menu-action-factory returns a factory which returns a fn that is used in 'onPress' 
(def list-menu-action-factory-fn (menu-action-factory hide-list-action-menu))

(defn list-action-menu [{:keys [show x y connection-id]}]
  [rnp-menu {:visible show :onDismiss hide-list-action-menu :anchor (clj->js {:x x :y y})}

   [rnp-menu-item {:title (lstr-ml "view")
                   :onPress (list-menu-action-factory-fn rs-events/remote-storage-config-view connection-id)}]

   [rnp-menu-item {:title (lstr-ml "delete")
                   :onPress (list-menu-action-factory-fn show-conf-dlg connection-id)}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 

(defn row-item []
  (fn [{:keys [name connection-id]} data]
    (let [selected-type-kw @(rs-events/remote-storage-current-rs-type)
          [icon-name color] [const/ICON-DATABASE-ARROW-LEFT @rnc/tertiary-color]]
      [rnp-list-item {:style {}
                      :onLongPress (fn [e]
                                     (show-list-menu e connection-id))
                      :onPress (fn [] (rs-events/connect-by-id-and-retrieve-root-dir selected-type-kw connection-id))
                      :title (r/as-element
                              [rnp-text {:style {:color color}
                                         :variant "titleMedium"} name])
                      :left (fn [_props]
                              (r/as-element
                               [rnp-list-icon {:style {:height 24}
                                               :icon icon-name
                                               :color color}]))
                      :right (fn [_props] (r/as-element
                                           [:<> ;; We can add another icon here if required
                                            [rnp-icon-button
                                             {:size 24
                                              :style {:margin -10}
                                              :icon dots-icon-name
                                              :onPress #(show-list-menu % connection-id)}]]))}])))


(defn connections-list-content []
  (fn [connections]
    (let [sections  [{:title "databases"
                      :key "Databases"
                      ;; Connetions info forms the data for this list
                      :data (if (nil? connections) [] connections)}]]
      [rn-section-list
       {:style {}
        :sections (clj->js sections)
        :renderItem  (fn [props] ;; keys are (:item :index :section :separators)
                       (let [props (js->clj props :keywordize-keys true)]
                         (r/as-element [row-item (-> props :item)])))
        :ItemSeparatorComponent (fn [_p]
                                  (r/as-element [rnp-divider]))
        :renderSectionHeader (fn [props] ;; key is :section
                               (let [props (js->clj props :keywordize-keys true)
                                     {:keys [title]} (-> props :section)]
                                 (r/as-element [list-section-header title])))}])))


(defn remote-connections-list-page-content []
  (let [selected-type-kw @(rs-events/remote-storage-current-rs-type)
        connections @(rs-events/remote-storage-connection-configs selected-type-kw)]
    [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
     [rn-view {:style {:flex 1 :justify-content "center" :align-items "center" :margin-top "10%"}}

      [rn-view {:style {:flex 1 :width "100%"}}
       [connections-list-content connections]]]

     [rnp-portal
      [delete-config-confirm-dialog]
      [list-action-menu @list-menu-action-data]]]))
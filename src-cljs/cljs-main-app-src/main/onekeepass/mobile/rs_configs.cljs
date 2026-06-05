(ns onekeepass.mobile.rs-configs
  "Remote storage configs page showing list of all for sftp or webdav conenctions"
  (:require
   [clojure.string :as str]
   [onekeepass.mobile.common-components :refer [confirm-dialog
                                                list-section-header
                                                menu-action-factory]]
   [onekeepass.mobile.constants :as const]
   [onekeepass.mobile.events.dialogs :as dlg-events]
   [onekeepass.mobile.events.common :as cmn-events]
   [onekeepass.mobile.events.remote-storage :as rs-events]
   [onekeepass.mobile.rn-components
    :as rnc
    :refer [appbar-text-color dots-icon-name page-title-text-variant
            rn-safe-area-view rn-section-list rn-view rnp-button rnp-divider
            rnp-icon-button rnp-list-icon rnp-list-item rnp-menu rnp-menu-item
            rnp-portal rnp-text]]
   [onekeepass.mobile.translation :refer [lstr-bl lstr-dlg-text lstr-dlg-title
                                          lstr-ml lstr-pt]]
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
                ;; :onPress rs-events/remote-storage-rs-type-new-form-page-show} (lstr-bl 'add)
                :onPress dlg-events/confirm-adding-rs-config-in-secure-store-dialog-show} (lstr-bl 'add)]])

;;;;;;;;;;;;;;;;;;;;;;;; Confirm app secure store ;;;;;;;;;;;;

(defn  confirm-adding-rs-config-in-secure-store-dialog []
  (let [{:keys [dialog-show]} @(dlg-events/confirm-adding-rs-config-in-secure-store-dialog-data)]
    [confirm-dialog {:dialog-show dialog-show
                     :title (lstr-dlg-title 'addRemoteConnectionConfig)
                     :confirm-text (lstr-dlg-text 'addRemoteConnectionConfig)
                     :actions [{:label (lstr-bl "cancel")
                                :on-press (fn []
                                            (dlg-events/confirm-adding-rs-config-in-secure-store-dialog-close))}

                               {:label (lstr-bl "continue")
                                :on-press (fn []
                                            (dlg-events/confirm-adding-rs-config-in-secure-store-dialog-close)
                                            (rs-events/remote-storage-rs-type-new-form-page-show))}]}]))

;;;;;;;;;;;;;;;;;;;;;;;;  Confirm delete ;;;;;;;;;;;;;;;;;;;;;;

(def ^:private conn-del-confirm-dialog-data (r/atom {:dialog-show false :connection-id nil}))

(defn- hide-conf-dlg []
  (reset! conn-del-confirm-dialog-data {:dialog-show false :connection-id nil}))

(defn- show-conf-dlg [connection-id]
  (reset! conn-del-confirm-dialog-data {:dialog-show true :connection-id connection-id}))

(defn delete-config-confirm-dialog []
  (let [{:keys [dialog-show connection-id]} @conn-del-confirm-dialog-data]
    [confirm-dialog {:dialog-show dialog-show
                     :title (lstr-dlg-title 'deleteRemoteConnection)
                     :confirm-text (lstr-dlg-text 'deleteRemoteConnection)
                     :actions [{:label (lstr-bl "yes")
                                :on-press (fn []
                                            (rs-events/remote-storage-delete-selected-config connection-id)
                                            (hide-conf-dlg))}
                               {:label (lstr-bl "no")
                                :on-press (fn []
                                            (hide-conf-dlg))}]}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Menus ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private list-menu-action-data (r/atom {:show false :x 0 :y 0
                                              :connection-id nil
                                              :source :blob}))

(defn hide-list-action-menu []
  (swap! list-menu-action-data assoc :show false))

(defn show-list-menu
  "Pops the menu popup for the selected row item. source is :blob (legacy
   app secure store) or :kdbx (entry inside an open database) — controls
   which menu items render."
  [^js/PEvent event connection-id source]
  (swap! list-menu-action-data
         assoc :show true
         :connection-id connection-id
         :source source
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

;;menu-action-factory returns a factory which returns a fn that is used in 'onPress'
(def list-menu-action-factory-fn (menu-action-factory hide-list-action-menu))

(defn list-action-menu [{:keys [show x y connection-id source]}]
  [rnp-menu {:visible show :key (str show) :onDismiss hide-list-action-menu :anchor (clj->js {:x x :y y})}
   (if (= source :kdbx)
     ;; Database-entry connections: read-only inspection; manage from the source database.
     [rnp-menu-item {:title (lstr-ml "view")
                     :onPress (list-menu-action-factory-fn
                               rs-events/remote-storage-kdbx-source-config-view connection-id)}]
     [:<>
      [rnp-menu-item {:title (lstr-ml "view")
                      :onPress (list-menu-action-factory-fn rs-events/remote-storage-config-view connection-id)}]
      [rnp-menu-item {:title (lstr-ml "edit")
                      :onPress (list-menu-action-factory-fn rs-events/remote-storage-config-edit connection-id)}]
      [rnp-divider]
      [rnp-menu-item {:title (lstr-ml "delete")
                      :onPress (list-menu-action-factory-fn show-conf-dlg connection-id)}]])])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 

(defn row-item []
  (fn [{:keys [name connection-id source]} _data]
    (let [selected-type-kw @(rs-events/remote-storage-current-rs-type)
          row-source (or (some-> source keyword) :blob)
          [icon-name color] [const/ICON-DATABASE-ARROW-LEFT @rnc/tertiary-color]]
      [rnp-list-item {:style {}
                      :onLongPress (fn [e]
                                     (show-list-menu e connection-id row-source))
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
                                           [rnp-icon-button
                                            {:size 24
                                             :style {:margin -10}
                                             :icon dots-icon-name
                                             :onPress #(show-list-menu % connection-id row-source)}]))}])))

(defn- split-by-source
  "Returns {:blob [...] :kdbx [...]}. The :kdbx list contains only summaries
   whose :connection-id isn't already in the blob list (blob wins on
   collision so a migrated user doesn't see duplicates), mapped to the row
   shape ({:name :connection-id :source}). Each row carries its :source so
   row-item can route the menu to the right action set."
  [blob-connections kdbx-source-summaries]
  (let [blob-list (or blob-connections [])
        blob-tagged (mapv #(assoc % :source :blob) blob-list)
        blob-ids (into #{} (map :connection-id blob-list))
        kdbx-only (->> kdbx-source-summaries
                       (remove #(contains? blob-ids (:connection-id %)))
                       (mapv (fn [{:keys [title connection-id]}]
                               {:name (if (str/blank? title)
                                        (lstr-ml "untitledConnection")
                                        title)
                                :connection-id connection-id
                                :source :kdbx})))]
    {:blob blob-tagged
     :kdbx kdbx-only}))

(defn connections-list-content []
  (fn [{:keys [blob kdbx]}]
    (let [sections (cond-> []
                     (seq blob) (conj {:title "appSecureStoreConnections"
                                       :key "blob"
                                       :data blob})
                     (seq kdbx) (conj {:title "databaseEntryConnections"
                                       :key "kdbx"
                                       :data kdbx}))]
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
        blob-connections @(rs-events/remote-storage-connection-configs selected-type-kw)
        kdbx-source-connections @(rs-events/remote-storage-kdbx-source-connections selected-type-kw)
        split (split-by-source blob-connections kdbx-source-connections)]
    [rn-safe-area-view {:style {:flex 1 :background-color @rnc/page-background-color}}
     [rn-view {:style {:flex 1 :justify-content "center" :align-items "center" :margin-top "10%"}}

      [rn-view {:style {:flex 1 :width "100%"}}
       [connections-list-content split]]]

     [rnp-portal
      [delete-config-confirm-dialog]
      [list-action-menu @list-menu-action-data]
      [confirm-adding-rs-config-in-secure-store-dialog]]]))
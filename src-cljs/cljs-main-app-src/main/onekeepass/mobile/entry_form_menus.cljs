(ns
 onekeepass.mobile.entry-form-menus
  (:require [reagent.core :as r]
            [onekeepass.mobile.entry-form-dialogs :refer
             [show-delete-attachment-dialog show-rename-attachment-name-dialog]]
            [onekeepass.mobile.rn-components
             :as rnc :refer [rnp-divider
                             rnp-menu
                             rnp-menu-item]]
            [onekeepass.mobile.translation :refer [lstr-ml]]
            [onekeepass.mobile.events.entry-form :as form-events]))

(def section-menu-dialog-data (r/atom {:section-name nil
                                                 :is-standard-section false
                                                 :show false
                                                 :x 0 :y 0}))

(defn section-menu-dialog-show [{:keys [section-name is-standard-section ^js/Event event ]}]
  (swap! section-menu-dialog-data assoc
         :section-name section-name
         :is-standard-section is-standard-section
         :show true
         :x  (-> event .-nativeEvent .-pageX)
         :y (-> event .-nativeEvent .-pageY)))

(defn section-menu [{:keys [section-name is-standard-section show x y]}]
  ;; anchor coordinates can be obtained in the icon button's onPrees. This is called with 'PressEvent Object Type'
  ;; (-> event .-nativeEvent .-pageY) and (-> event .-nativeEvent .-pageX) and use that 
  ;; in achor's coordinate 
  [rnp-menu {:visible show :key (str show) :onDismiss #(swap! section-menu-dialog-data assoc :show false)
             :anchor (clj->js {:x x :y y})} ;;:contentStyle {:backgroundColor  "red"}
   [rnp-menu-item {:title (lstr-ml "changeName") :disabled is-standard-section
                   :onPress (fn []
                              (form-events/open-section-name-modify-dialog section-name)
                              (swap! section-menu-dialog-data assoc :show false))}]
   [rnp-menu-item {:title (lstr-ml "addCustomField")
                   :onPress (fn []
                              (swap! section-menu-dialog-data assoc :show false)
                              (form-events/open-section-field-dialog section-name))}]])

(def custom-field-menu-data (r/atom {:section-name nil
                                               :field-name nil
                                               :show false
                                               :x 0 :y 0}))

(defn custom-field-menu-show [^js/PEvent event section-name key protected required]
  (swap! custom-field-menu-data assoc
         :section-name section-name
         ;; need to use field-name instead of 'key' as key 
         ;; in the custom-field-menu-data for the menu popup work properly
         :field-name key
         :protected protected
         :required required
         :show true
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(defn custom-field-menu-action-on-dismiss []
  (swap! custom-field-menu-data assoc :show false))

(defn custom-field-menu [{:keys [show x y section-name field-name protected required]}]
  [rnp-menu {:visible show :key (str show)
             :onDismiss custom-field-menu-action-on-dismiss
             :anchor (clj->js {:x x :y y})}
   [rnp-menu-item {:title (lstr-ml "modifyCustomField")
                   :onPress (fn [_e]
                              (form-events/open-section-field-modify-dialog
                               {:key field-name
                                :protected protected
                                :required required
                                :section-name section-name})
                              (custom-field-menu-action-on-dismiss))}]
   [rnp-menu-item {:title (lstr-ml "deleteField")
                   :onPress (fn [_e]
                              (form-events/field-delete section-name field-name)
                              (custom-field-menu-action-on-dismiss))}]])

(def attachment-long-press-menu-data (r/atom {:show false
                                                        :edit false
                                                        :name nil
                                                        :data-hash nil
                                                        :x 0 :y 0}))

(defn show-attachment-long-press-menu [^js/PEvent event edit name data-hash]
  (swap! attachment-long-press-menu-data assoc
         :edit edit
         :name name
         :data-hash data-hash
         :show true
         :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

(defn dismiss-attachment-long-press-menu []
  (swap! attachment-long-press-menu-data assoc
         :show false
         :edit false
         :name nil
         :data-hash nil
         :x 0 :y 0))

(defn attachment-long-press-menu 
  [{:keys [show x y edit name data-hash]}]
  [rnp-menu {:visible show :key (str show)
             :onDismiss dismiss-attachment-long-press-menu
             :anchor (clj->js {:x x :y y})}

   [rnp-menu-item {:title "View"
                   :onPress (fn [_e]
                              (form-events/view-attachment name data-hash)
                              (dismiss-attachment-long-press-menu))}]

   [rnp-menu-item {:title (lstr-ml "saveAs")
                   :onPress (fn [_e]
                              (form-events/save-attachment name data-hash)
                              (dismiss-attachment-long-press-menu))}]

   [rnp-divider]
   [rnp-menu-item {:title (lstr-ml "rename")
                   :disabled (not edit)
                   :onPress (fn [_e]
                              (show-rename-attachment-name-dialog name data-hash)
                              (dismiss-attachment-long-press-menu))}]
   [rnp-menu-item {:title (lstr-ml "delete")
                   :disabled (not edit)
                   :onPress (fn [_e]
                              (show-delete-attachment-dialog
                               (lstr-ml "delete") "Do you want to delete this attachment?"
                               #(form-events/delete-attachment data-hash))
                              (dismiss-attachment-long-press-menu))}]])

;; Following menu may be used if we addd Menu action to to attachment header 
#_(def ^:private attachment-menu-data (r/atom {:show false :x 0 :y 0}))

#_(defn show-attachment-menu [^js/PEvent event]
    (swap! attachment-menu-data assoc
           :show true
           :x (-> event .-nativeEvent .-pageX) :y (-> event .-nativeEvent .-pageY)))

#_(defn dismiss-attachment-menu []
    (swap! attachment-menu-data assoc :show false))

#_(defn attachment-menu [{:keys [show x y]}]
    [rnp-menu {:visible show
               :onDismiss dismiss-attachment-menu
               :anchor (clj->js {:x x :y y})}
     [rnp-menu-item {:title "Upload"
                     :onPress (fn [_e]
                                (dismiss-attachment-menu))}]])
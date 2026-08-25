(ns onekeepass.mobile.search
  (:require [onekeepass.mobile.events.entry-list-otp :as otp-events]
            [onekeepass.mobile.events.otp-url-received :as otp-url-events]
            [onekeepass.mobile.events.search :as search-events :refer [entry-row-pressed]]
            [onekeepass.mobile.icons-list :refer [icon-id->name]]
            [onekeepass.mobile.otp-badge :as otp-badge]
            [onekeepass.mobile.rn-components :as rnc :refer [background-color
                                                             icon-color
                                                             no-assist-text-props
                                                             rn-safe-area-view
                                                             rn-section-list
                                                             rn-view
                                                             rnp-divider
                                                             rnp-list-icon
                                                             rnp-list-item
                                                             rnp-searchbar
                                                             rnp-text]]
            [onekeepass.mobile.translation :refer [lstr-l]]
            [reagent.core :as r]))

(defn- otp-right-element
  "The entry's current 2FA code on the right of the row. An entry without one renders
   nothing, so those rows are unchanged"
  [uuid]
  (let [token-data @(otp-events/otp-token-data uuid)]
    (otp-events/ensure-otp-token uuid token-data)
    [rn-view {:style {:justify-content "center" :margin-right 5}}
     [otp-badge/otp-badge token-data {:code-color @rnc/on-surface-variant
                                      :bar-color @rnc/circular-progress-color
                                      :bar-track-color @rnc/outline-variant}]]))

(defn row-item [_entry-m]
  (fn [{:keys [title secondary-title icon-id uuid] :as _entry-summary}]
    (let [icon-name (icon-id->name icon-id)]
      [rnp-list-item {:onPress #(entry-row-pressed uuid)
                      :title (r/as-element
                              [rnp-text {:variant "titleMedium"} title])
                      :description secondary-title
                      :left (fn [_props] (r/as-element 
                                          [rnp-list-icon
                                           {:style {:margin-left 5 :align-self "center"} 
                                            :color @icon-color
                                            :icon icon-name }]))
                      :right (fn [_props] (r/as-element
                                           [otp-right-element uuid]))}])))
(defn search-list []
  (let [data @(search-events/search-result-entry-items)
        sections  [{:title "Entries"
                    :key "Entries"
                    :data data}]]
    (if (empty? data) 
      [rn-view  {:style {:margin-top 40 :flexDirection "column" :align-items "center"}} 
       [rnp-text {:variant "titleMedium"} (lstr-l 'noResult)]]
      [rn-section-list {:sections (clj->js sections)
                            ;; keys in propps are (:item :index :section :separators)
                        :renderItem (fn [props]
                                      (let [props (js->clj props :keywordize-keys true)]
                                        (r/as-element [row-item (-> props :item)])))
                        :ItemSeparatorComponent (fn [_p]
                                                  (r/as-element [rnp-divider]))
                        :stickySectionHeadersEnabled false
                        :renderSectionHeader nil}]
      )
    ))

(defn main-content []
  (let [term @(search-events/search-term)
        ;; The page is also used to pick the entry an otp url received from another app is
        ;; added to and the user needs to be told what a row press does now
        picking-entry? @(otp-url-events/picking-entry?)]
    [rn-view {:style {:flexDirection "column"}}
     (when picking-entry?
       [rn-view {:style {:padding 10}}
        [rnp-text {:variant "bodyMedium"} (lstr-l 'selectEntryForOtpUrl)]])
     [rnp-searchbar (merge no-assist-text-props
                           {:style {:margin-left 1 :margin-right 1 :borderWidth 0}
                            :placeholder (lstr-l 'search)
                            :onChangeText #(search-events/search-term-update %) :value term})]
     [search-list]]))

(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @background-color}}
   [main-content]])
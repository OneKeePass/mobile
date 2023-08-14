(ns onekeepass.mobile.search
  (:require
   [reagent.core :as r]
   [onekeepass.mobile.rn-components :as rnc :refer [
                                                    background-color
                                                    icon-color
                                                    
                                                    rn-view
                                                    rn-safe-area-view
                                                    rn-section-list
                                                    rnp-searchbar
                                                    rnp-list-item
                                                    rnp-divider
                                                    rnp-list-icon
                                                    rnp-text]]
   [onekeepass.mobile.icons-list :refer [icon-id->name]]
   [onekeepass.mobile.events.search :as search-events :refer [show-selected-entry]]))

(defn row-item [_entry-m]
  (fn [{:keys [title secondary-title icon-id uuid] :as _entry-summary}]
    (let [icon-name (icon-id->name icon-id)]
      [rnp-list-item {:onPress #(show-selected-entry uuid)
                      :title (r/as-element
                              [rnp-text {:variant "titleMedium"} title])
                      :description secondary-title
                      :left (fn [_props] (r/as-element 
                                          [rnp-list-icon
                                           {:style {:margin-left 5 :align-self "center"} 
                                            :color @icon-color
                                            :icon icon-name }]))}])))

#_(defn search-list []
  (let [sections  [{:title "Entries"
                    :key "Entries"
                    :data @(search-events/search-result-entry-items)}]] 
    [rn-section-list {:sections (clj->js sections)
                      ;; keys in propps are (:item :index :section :separators)
                      :renderItem (fn [props] 
                                    (let [props (js->clj props :keywordize-keys true)]
                                      (r/as-element [row-item (-> props :item)])))
                      :ItemSeparatorComponent (fn [_p]
                                                (r/as-element [rnp-divider]))
                      :stickySectionHeadersEnabled false
                      :renderSectionHeader nil}]))

(defn search-list []
  (let [data @(search-events/search-result-entry-items)
        sections  [{:title "Entries"
                    :key "Entries"
                    :data data}]]
    (if (empty? data) 
      [rn-view  {:style {:margin-top 40 :flexDirection "column" :align-items "center"}} [rnp-text {:variant "titleMedium"} "No result"]]
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
  (let [term @(search-events/search-term)] 
    [rn-view {:style {:flexDirection "column"}}
     [rnp-searchbar {:style {:margin-left 1 :margin-right 1 :borderWidth 0} 
                     :placeholder "Search"  
                     :onChangeText #(search-events/search-term-update %) :value term}]
     [search-list]]))

(defn content []
  [rn-safe-area-view {:style {:flex 1 :background-color @background-color}}
   [main-content]])
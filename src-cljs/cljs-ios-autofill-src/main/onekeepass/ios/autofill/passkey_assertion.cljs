(ns onekeepass.ios.autofill.passkey-assertion
  "UI for the passkey assertion page shown in the iOS autofill extension."
  (:require [onekeepass.ios.autofill.events.passkey-assertion :as pk-events]
            [onekeepass.ios.autofill.rn-components :as rnc :refer [primary-container-color
                                                                    rn-flat-list
                                                                    rn-view
                                                                    rnp-divider
                                                                    rnp-list-item
                                                                    rnp-text]]
            [reagent.core :as r]))

(defn passkey-item [{:keys [entry-uuid db-key rp-id username]}]
  [rnp-list-item
   {:title (r/as-element [rnp-text {:variant "titleMedium"} (or (not-empty username) rp-id)])
    :description rp-id
    :onPress #(pk-events/passkey-assertion-select {:entry-uuid entry-uuid :db-key db-key})}])

(defn content []
  (let [items (pk-events/passkey-assertion-items) ]
    [rn-view {:style {:flex 1 :width "100%"}}
     [rn-view {:style {:background-color @primary-container-color
                       :padding 10
                       :align-items "center"}}
      [rnp-text {:variant "titleSmall"} "Select a passkey to sign in"]]
     (if (empty? @items)
       [rn-view {:style {:flex 1 :justify-content "center" :align-items "center"}}
        [rnp-text {:variant "titleSmall"} "No matching passkeys found"]]
       [rn-flat-list
        {:data (clj->js @items)
         :keyExtractor (fn [item] (aget item "entry-uuid"))
         :ItemSeparatorComponent (fn [_] (r/as-element [rnp-divider]))
         :renderItem (fn [props]
                       (let [item (js->clj (.-item props) :keywordize-keys true)]
                         (r/as-element [passkey-item item])))}])]
    #_(fn []
      [rn-view {:style {:flex 1 :width "100%"}}
       [rn-view {:style {:background-color @primary-container-color
                         :padding 10
                         :align-items "center"}}
        [rnp-text {:variant "titleSmall"} "Select a passkey to sign in"]]
       (if (empty? @items)
         [rn-view {:style {:flex 1 :justify-content "center" :align-items "center"}}
          [rnp-text {:variant "titleSmall"} "No matching passkeys found"]]
         [rn-flat-list
          {:data (clj->js @items)
           :keyExtractor (fn [item] (.-entry_uuid ^js/PasskeySummary item))
           :ItemSeparatorComponent (fn [_] (r/as-element [rnp-divider]))
           :renderItem (fn [props]
                         (let [item (js->clj (.-item props) :keywordize-keys true)]
                           (r/as-element [passkey-item item])))}])])))

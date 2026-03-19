(ns onekeepass.ios.autofill.passkey-registration
  "UI for the passkey registration page shown in the iOS autofill extension.
   Two-step flow: group picker -> entry picker (select existing or create new)."
  (:require [onekeepass.ios.autofill.events.passkey-registration :as reg-events]
            [onekeepass.ios.autofill.rn-components :as rnc :refer [primary-container-color
                                                                    rn-flat-list
                                                                    rn-view
                                                                    rnp-button
                                                                    rnp-divider
                                                                    rnp-list-item
                                                                    rnp-text
                                                                    rnp-text-input]]
            [reagent.core :as r]))

(defn- group-item [{:keys [name] :as group}]
  [rnp-list-item
   {:title (r/as-element [rnp-text {:variant "titleMedium"} name])
    :onPress #(reg-events/select-group group)}])

(defn- entry-item [{:keys [title] :as entry}]
  [rnp-list-item
   {:title (r/as-element [rnp-text {:variant "titleMedium"} (or (not-empty title) "(Untitled)")])
    :onPress #(reg-events/select-existing-entry entry)}])

(defn- group-picker []
  (let [groups (reg-events/registration-groups)
        context (reg-events/registration-context)]
    (fn []
      (let [rp-id (:rp-id @context)
            user-name (:user-name @context)]
        [rn-view {:style {:flex 1 :width "100%"}}
         [rn-view {:style {:background-color @primary-container-color
                           :padding 10
                           :align-items "center"}}
          [rnp-text {:variant "titleSmall"} (str "Register passkey for " rp-id)]
          (when (not-empty user-name)
            [rnp-text {:variant "bodySmall" :style {:margin-top 4}} (str "User: " user-name)])]
         [rn-view {:style {:padding 10}}
          [rnp-text {:variant "titleSmall"} "Select a group:"]]
         (if (empty? @groups)
           [rn-view {:style {:flex 1 :justify-content "center" :align-items "center"}}
            [rnp-text {:variant "titleSmall"} "No groups found"]]
           [rn-flat-list
            {:data (clj->js @groups)
             :keyExtractor (fn [item] (aget item "group-uuid"))
             :ItemSeparatorComponent (fn [_] (r/as-element [rnp-divider]))
             :renderItem (fn [props]
                           (let [item (js->clj (.-item props) :keywordize-keys true)]
                             (r/as-element [group-item item])))}])]))))

(defn- entry-picker []
  (let [entries (reg-events/registration-entries)
        selected-group (reg-events/registration-selected-group)
        new-entry-name (reg-events/registration-new-entry-name)]
    (fn []
      (let [group-name (:name @selected-group)]
        [rn-view {:style {:flex 1 :width "100%"}}
         [rn-view {:style {:background-color @primary-container-color
                           :padding 10
                           :align-items "center"}}
          [rnp-text {:variant "titleSmall"} (str "Add to group: " group-name)]]

         ;; New entry creation section
         [rn-view {:style {:padding 10}}
          [rnp-text-input {:label "New entry name"
                           :defaultValue @new-entry-name
                           :onChangeText reg-events/update-new-entry-name}]
          [rn-view {:style {:margin-top 10 :align-items "center"}}
           [rnp-button {:mode "contained"
                        :onPress reg-events/create-new-entry}
            "Create new entry"]]]

         [rn-view {:style {:padding-horizontal 10 :padding-vertical 5}}
          [rnp-divider]
          [rn-view {:style {:align-items "center" :padding 8}}
           [rnp-text {:variant "bodySmall"} "or add to existing entry"]]]

         ;; Existing entries list
         (if (empty? @entries)
           [rn-view {:style {:flex 1 :justify-content "center" :align-items "center"}}
            [rnp-text {:variant "bodySmall"} "No entries in this group"]]
           [rn-flat-list
            {:data (clj->js @entries)
             :keyExtractor (fn [item] (aget item "entry-uuid"))
             :ItemSeparatorComponent (fn [_] (r/as-element [rnp-divider]))
             :renderItem (fn [props]
                           (let [item (js->clj (.-item props) :keywordize-keys true)]
                             (r/as-element [entry-item item])))}])]))))

(defn- error-view []
  (let [error-msg (reg-events/registration-error-message)]
    (fn []
      [rn-view {:style {:flex 1 :width "100%" :justify-content "center" :align-items "center" :padding 20}}
       [rn-view {:style {:background-color @primary-container-color
                         :padding 10
                         :width "100%"
                         :align-items "center"}}
        [rnp-text {:variant "titleSmall"} "Passkey Registration Failed"]]
       [rn-view {:style {:padding 20}}
        [rnp-text {:variant "bodyMedium" :style {:text-align "center"}} @error-msg]]
       [rnp-button {:mode "contained"
                    :onPress reg-events/close-after-error}
        "Close"]])))

(defn content []
  (let [step (reg-events/registration-step)]
    (fn []
      (condp = @step
        :group-picker [group-picker]
        :entry-picker [entry-picker]
        :error [error-view]
        [group-picker]))))

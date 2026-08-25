(ns onekeepass.mobile.events.clone-entry
  "Events to clone an entry. The clone is a simple one - all fields of the source entry
   are copied as values (no references to the source entry) to a new entry that is added
   to the same group as the source entry and the source entry's histories are not copied"
  (:require [clojure.string :as str]
            [onekeepass.mobile.background :as bg]
            [onekeepass.mobile.events.common :refer [active-db-key on-ok]]
            [re-frame.core :refer [dispatch reg-event-fx reg-fx]]))

(defn clone-entry-start
  "Called when the user presses Ok in the clone entry dialog"
  [entry-uuid new-title parent-group-uuid]
  (dispatch [:clone-entry-start entry-uuid new-title parent-group-uuid]))

(reg-event-fx
 :clone-entry-start
 (fn [{:keys [db]} [_event-id entry-uuid new-title parent-group-uuid]]
   (if (str/blank? new-title)
     {:fx [[:dispatch [:generic-dialog-update-with-map
                       :clone-entry-dialog
                       {:error-fields {:new-title "A valid title is required"}}]]]}
     {:fx [[:bg-clone-entry [(active-db-key db)
                             entry-uuid
                             ;; Corresponds to the struct 'EntryCloneOption'
                             {:new-title (str/trim new-title)
                              :parent-group-uuid parent-group-uuid
                              :keep-histories false
                              :link-by-reference false}]]]})))

(reg-fx
 :bg-clone-entry
 (fn [[db-key entry-uuid entry-clone-option]]
   (bg/clone-entry db-key entry-uuid entry-clone-option
                   (fn [api-response]
                     (when-let [cloned-entry-uuid (on-ok api-response)]
                       (dispatch [:clone-entry-completed cloned-entry-uuid]))))))

(reg-event-fx
 :clone-entry-completed
 (fn [{:keys [_db]} [_event-id _cloned-entry-uuid]]
   {:fx [[:dispatch [:generic-dialog-close :clone-entry-dialog]]
         [:dispatch [:save/save-current-kdbx
                     {:error-title "Save cloned entry"
                      :save-message "Cloning and Saving...."
                      :on-save-ok (fn []
                                    (dispatch [:common/refresh-forms])
                                    (dispatch [:common/message-snackbar-open 'entryCloned]))}]]]}))

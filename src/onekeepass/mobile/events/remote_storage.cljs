(ns onekeepass.mobile.events.remote-storage
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.mobile.events.common :as cmn-events :refer [on-ok]]
   [onekeepass.mobile.background :as bg]))


(defn remote-storage-connections [type]
  (subscribe [:remote-storage/connection-configs type]))



(reg-event-fx
 :remote-storage/read-connection-configs
 (fn [{:keys [db]} [_event-id]]
   {}))

#_(reg-fx
   :bg-)

(reg-sub
 :remote-storage/connection-configs
 (fn [db [_query-id type]]
   [{:name "Connection1" :user-name "Name1"} {:name "Connection2" :user-name "Name2"}]))
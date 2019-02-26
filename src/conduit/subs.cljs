(ns conduit.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]))

(reg-sub
 :active-page           ;; usage: (subscribe [:active-page])
 (fn [db _]             ;; db is the (map) value stored in the app-db atom
   (:active-page db)))  ;; extract a value from the application state

(reg-sub
 :profile  ;; usage: (subscribe [:profile])
 (fn [db _]
   (:profile db)))

(reg-sub
 :loading  ;; usage: (subscribe [:loading])
 (fn [db _]
   (:loading db)))

(reg-sub
 :errors  ;; usage: (subscribe [:errors])
 (fn [db _]
   (:errors db)))

(reg-sub
 :user  ;; usage: (subscribe [:user])
 (fn [db _]
   (:user db)))

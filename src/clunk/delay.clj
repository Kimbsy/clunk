(ns clunk.delay
  (:require [clunk.util :as u]))

(defn delay
  [duration-ms f & {:keys [tag] :or {tag :none}}]
  {:remaining-time-ms duration-ms
   :on-complete-fn f
   :tag tag})

(defn add-delay
  "Add a delay into the current scene."
  [{:keys [current-scene] :as state} d]
  (update-in state [:scenes current-scene :delays] conj d))

;; @TODO: implement sequential delays

(defn update-delay
  "Decrement the time remaining since the last frame."
  [dt d]
  (update d :remaining-time-ms - dt))

(def finished? (comp neg? :remaining-time-ms))

(defn apply-all
  "apply a collection of finished delays to the state."
  [state finished]
  (reduce (fn [acc-state d]
            ((:on-complete-fn d) state))
          state
          finished))

(defn update-state
  "Update all the delays in the current scene and apply the ones that
  have finished."
  [{:keys [current-scene dt] :as state}]
  (let [path [:scenes current-scene :delays]
        delays (get-in state path)]
    (if (seq delays)
      (let [updated-delays (map (partial update-delay dt) delays)
            [finished unfinished] (u/split-by finished? updated-delays)]
        (-> state
            (assoc-in path unfinished)
            (apply-all finished)))
      state)))

(ns clunk.delay
  (:require [clunk.util :as u]))

(defn delay-fn
  [duration-ms f & {:keys [tag] :or {tag :none}}]
  {:remaining-time-ms duration-ms
   :on-complete-fn f
   :tag tag})

(defn add-delay-fn
  "Add a delay-fn into the current scene."
  [{:keys [current-scene] :as state} d]
  (update-in state [:scenes current-scene :delay-fns] conj d))

(defn sequential-delay-fns
  "Creates a sequence of delay-fns from a given list of `[time-ms
  on-complete-fn]` tuples where each `time-ms` is relative to the
  ending of the previous one."
  [delay-datas &
   {:keys [initial-delay
           tag]
    :or {initial-delay 0
         tag :none}}]
  (let [times (map first delay-datas)
        on-complete-fns (map second delay-datas)
        relative-times (loop [acc []
                              curr initial-delay
                              ts times]
                         (if (seq ts)
                           (let [new (+ curr (first ts))]
                             (recur (conj acc new) new (rest ts)))
                           acc))]
    (map (fn [t f]
           (delay-fn t f :tag tag))
         relative-times
         on-complete-fns)))

(defn add-delay-fns
  "Add a collection of delay-fns into the current scene."
  [{:keys [current-scene] :as state} ds]
  (update-in state [:scenes current-scene :delay-fns] concat ds))

(defn update-delay-fn
  "Decrement the time remaining since the last frame."
  [dt d]
  (update d :remaining-time-ms - dt))

(def finished? (comp neg? :remaining-time-ms))

(defn apply-all
  "apply a collection of finished delay-fns to the state."
  [state finished]
  (reduce (fn [acc-state d]
            ((:on-complete-fn d) state))
          state
          finished))

(defn update-state
  "Update all the delay-fns in the current scene and apply the ones
  that have finished."
  [{:keys [current-scene dt] :as state}]
  (let [path [:scenes current-scene :delay-fns]
        delay-fns (get-in state path)]
    (if (seq delay-fns)
      (let [updated-delay-fns (map (partial update-delay-fn dt) delay-fns)
            [finished unfinished] (u/split-by finished? updated-delay-fns)]
        (-> state
            (assoc-in path unfinished)
            (apply-all finished)))
      state)))

(ns resizable-world-bounds.core
  (:gen-class)
  (:require [clunk.core :as c]
            [resizable-world-bounds.scenes.level-01 :as level-01]))

(defn init-scenes
  "Map of scenes in the game"
  [state]
  {:level-01 (level-01/init state)})

;; Configure the game
(def resizable-world-bounds-game
  (c/game {:title "resizable-world-bounds"
           :size [800 600]
           :init-scenes-fn init-scenes
           :current-scene :level-01
           :assets {}}))

(defn -main
  "Run the game"
  [& args]
  (c/start! resizable-world-bounds-game))

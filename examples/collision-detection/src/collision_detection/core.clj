(ns collision-detection.core
  (:gen-class)
  (:require [clunk.core :as c]
            [collision-detection.scenes.level-01 :as level-01]))

(defn init-scenes
  "Map of scenes in the game"
  [state]
  {:level-01 (level-01/init state)})

;; Configure the game
(def collision-detection-game
  (c/game {:title "Collision Detection Example"
           :init-scenes-fn init-scenes
           :current-scene :level-01}))

(defn -main
  "Run the game"
  [& args]
  (c/start! collision-detection-game))

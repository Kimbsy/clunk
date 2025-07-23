(ns delays.core
  (:gen-class)
  (:require [clunk.core :as c]
            [delays.scenes.level-01 :as level-01]))

(defn init-scenes
  "Map of scenes in the game"
  [state]
  {:level-01 (level-01/init state)})

;; Configure the game
(def delays-game
  (c/game {:title "Delays Example"
           :init-scenes-fn init-scenes
           :current-scene :level-01
           :assets {:image {:captain-spritesheet "resources/img/captain.png"}}}))

(defn -main
  "Run the game"
  [& args]
  (c/start! delays-game))

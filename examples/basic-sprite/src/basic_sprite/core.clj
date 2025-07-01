(ns basic-sprite.core
  (:gen-class)
  (:require [basic-sprite.scenes.level-01 :as level-01]
            [clunk.core :as c]))

(defn init-scenes
  "Map of scenes in the game"
  [state]
  {:level-01 (level-01/init state)})

;; Configure the game
(def basic-sprite-game
  (c/game {:title "Basic Sprite Example"
           :init-scenes-fn init-scenes
           :current-scene :level-01
           :assets {:image {:captain-spritesheet "resources/img/captain.png"}}}))

(defn -main
  "Run the game"
  [& args]
  (c/start! basic-sprite-game))

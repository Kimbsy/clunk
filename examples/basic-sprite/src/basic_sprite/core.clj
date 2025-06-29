(ns basic-sprite.core
  (:gen-class)
  (:require [clunk.core :as c]
            [basic-sprite.scenes.level-01 :as level-01]))

(defn init-scenes
  "Map of scenes in the game"
  [state]
  {:level-01 (level-01/init state)})

;; Configure the game
(def basic-sprite-game
  (c/game {:title "Basic Sprite Example"
           :init-scenes-fn init-scenes
           :current-scene :level-01}))

(defn -main
  "Run the game"
  [& args]
  (c/start! basic-sprite-game))

(ns tweens.core
  (:gen-class)
  (:require [clunk.core :as c]
            [tweens.scenes.level-01 :as level-01]))

(defn init-scenes
  "Map of scenes in the game"
  [state]
  {:level-01 (level-01/init state)})

;; Configure the game
(def tweens-game
  (c/game {:title "tweens"
           :init-scenes-fn init-scenes
           :current-scene :level-01
           :assets {:image {:captain-spritesheet "resources/img/captain.png"}}}))

(defn -main
  "Run the game"
  [& args]
  (c/start! tweens-game))

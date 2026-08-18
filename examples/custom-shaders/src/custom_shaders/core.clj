(ns custom-shaders.core
  (:gen-class)
  (:require [clunk.core :as c]
            [custom-shaders.scenes.level-01 :as level-01]))

(defn init-scenes
  "Map of scenes in the game"
  [state]
  {:level-01 (level-01/init state)})

;; Configure the game
(def custom-shaders-game
  (c/game {:title "custom-shaders"
           :size [800 600]
           :init-scenes-fn init-scenes
           :current-scene :level-01
           :assets {:image {:big-present "resources/img/big-present.png"}}}))

(defn -main
  "Run the game"
  [& args]
  (c/start! custom-shaders-game))

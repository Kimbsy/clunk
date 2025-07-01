(ns sounds.core
  (:gen-class)
  (:require [clunk.audio :as audio]
            [clunk.core :as c]
            [sounds.scenes.level-01 :as level-01]))

(defn startup
  [state]
  (assoc state :music-source
         (audio/play! :music :loop? true)))

(defn init-scenes
  "Map of scenes in the game"
  [state]
  {:level-01 (level-01/init state)})

;; Configure the game
(def sounds-game
  (c/game {:title "Music and sound effects example"
           :on-start-fn startup
           :init-scenes-fn init-scenes
           :current-scene :level-01
           :assets {:audio {:music "resources/audio/music/music.ogg"
                            :blip-1 "resources/audio/sfx/blip-1.ogg"
                            :blip-2 "resources/audio/sfx/blip-2.ogg"
                            :blip-3 "resources/audio/sfx/blip-3.ogg"}}}))

(defn -main
  "Run the game"
  [& args]
  (c/start! sounds-game))

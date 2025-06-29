(ns sounds.core
  (:gen-class)
  (:require [clunk.core :as c]
            [clunk.audio :as audio]
            [sounds.scenes.level-01 :as level-01]))

(defn startup
  [state]
  (audio/load-ogg-file! "resources/audio/music/music.ogg" :music)
  (audio/load-ogg-file! "resources/audio/sfx/blip-1.ogg" :blip-1)
  (audio/load-ogg-file! "resources/audio/sfx/blip-2.ogg" :blip-2)
  (audio/load-ogg-file! "resources/audio/sfx/blip-3.ogg" :blip-3)
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
            :current-scene :level-01}))

(defn -main
  "Run the game"
  [& args]
  (c/start! sounds-game))

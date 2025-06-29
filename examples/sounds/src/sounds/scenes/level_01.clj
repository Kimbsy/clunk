(ns sounds.scenes.level-01
  (:require [clunk.audio :as audio]
            [clunk.core :as c]
            [clunk.input :as i]
            [clunk.sprite :as sprite]
            [clunk.util :as u]))

;; Nicer than actual white and black
(def white [0.96 0.96 0.96 1])
(def black [0.23 0.23 0.23 1])

(defn handle-music!
  "Handle a key-pressed event `e`.
  When the key was `m` reset the music."
  [{:keys [music-source] :as state} e]
  (if (i/is e i/K_M i/PRESS)
    (do
      (audio/stop! music-source)
      (assoc state :music-source
             (audio/play! :music :loop? true)))
    state))

;; Available sound effects
(def blips
  [:blip-1 :blip-2 :blip-3])

(defn handle-sfx!
  "handle a key-pressed event `e`.
  When the key was `s` play a random sound effect."
  [state e]
  (if (i/is e i/K_S i/PRESS)
    (do
      (audio/play! (rand-nth blips))
      state)
    state))

(defn key-pressed-fns
  "Define the key-pressed handler functions for the scene."
  []
  [handle-music!
   handle-sfx!])

(defn sprites
  "The initial list of sprites for this scene"
  [{:keys [window] :as state}]
  [(sprite/text-sprite
    :instructions
    (u/ratio-pos window [0.5 0.4])
    "Press <m> to restart music"
    :color white)
   (sprite/text-sprite
    :instructions
    (u/ratio-pos window [0.5 0.6])
    "Press <s> to play sound effect"
    :color white)])

(defn draw-level-01!
  "Called each frame, draws the current scene to the screen"
  [state]
  (c/draw-background! black)
  (sprite/draw-scene-sprites! state))

(defn update-level-01
  "Called each frame, update the sprites in the current scene"
  [state]
  (-> state
      sprite/update-state))

(defn init
  "Initialise this scene"
  [state]
  {:sprites (sprites state)
   :draw-fn draw-level-01!
   :update-fn update-level-01
   :key-fns (key-pressed-fns)})

(ns delays.scenes.level-01
  (:require [clunk.core :as c]
            [clunk.delay :as delay]
            [clunk.sprite :as sprite]
            [clunk.tween :as tween]
            [clunk.util :as u]))

(def blue [0 0.6 1 1])

(def spin-tween
  (tween/tween
   :rotation
   360))

(defn captain
  [pos]
  (sprite/animated-sprite
   :captain
   pos
   [240 360]
   :captain-spritesheet
   [1680 1440]
   :animations {:none {:frames 1
                       :y-offset 0
                       :frame-delay 100}
                :idle {:frames 4
                       :y-offset 1
                       :frame-delay 15}
                :run  {:frames 4
                       :y-offset 2
                       :frame-delay 8}
                :jump {:frames 7
                       :y-offset 3
                       :frame-delay 8}}
   :current-animation :idle))

(defn sprites
  "The initial list of sprites for this scene"
  [state]
  [(captain [150 180])])

(defn print-current-time
  "All delayed functions should take the game state and return an
  (optionally) modified version of it."
  [state]
  (prn (str "Current time: " (System/currentTimeMillis)))
  state)

(defn add-sprites-to-scene
  "Create a delay which adds new sprites to the current scene."
  [duration-ms new-sprites]
  (delay/delay-fn
   duration-ms
   (fn [{:keys [current-scene] :as state}]
     (update-in state [:scenes current-scene :sprites]
                concat new-sprites))))

(defn add-tween-to-sprites
  "Create a delay which adds a tween to the collection of sprites which
  satisfy the `sprite-selection-fn`."
  [duration-ms tween sprite-selection-fn]
  (delay/delay-fn
   duration-ms
   (fn [{:keys [current-scene] :as state}]
     (sprite/update-sprites
      state
      sprite-selection-fn
      #(tween/add-tween % tween)))))

(defn delays
  "Define the delays which exist as the scene begins."
  [{:keys [window] :as state}]
  (let [[w h] (u/window-size window)]
    [;; We can have a simple side-effecting delayed function:
     (delay/delay-fn 100 print-current-time)

     ;; We can add new sprites into the scene:     
     (add-sprites-to-scene 200 [(captain [(rand-int w)
                                          (rand-int h)])])

     ;; We can add tweens to sprites in the scene:
     (add-tween-to-sprites 300
                           spin-tween
                           (sprite/has-group :captain))

     ;; We can even delay adding new delays to the scene:
     (delay/delay-fn
      3000
      (fn [state]
        (reduce delay/add-delay-fn
                state
                ;; add the same delays again!
                (delays state))))]))

(defn draw-level-01!
  "Called each frame, draws the current scene to the screen"
  [state]
  (c/draw-background! blue)
  (sprite/draw-scene-sprites! state))

(defn update-level-01
  "Called each frame, update the sprites in the current scene"
  [state]
  (-> state
      sprite/update-state
      tween/update-state
      delay/update-state))

(defn init
  "Initialise this scene"
  [state]
  {:sprites (sprites state)
   :draw-fn draw-level-01!
   :update-fn update-level-01
   :delay-fns (delays state)})

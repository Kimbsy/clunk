(ns collision-detection.scenes.level-01
  (:require [clunk.collision :as collision]
            [clunk.core :as c]
            [clunk.sprite :as sprite]))

(def grey [0.2 0.2 0.2 1])
(def green [0.6 0.9 0.6 1])
(def pink [1.0 0.45 0.45 1])

(defn square
  "A simple sprite"
  [group pos vel size color]
  (sprite/sprite
   group
   pos
   :vel vel
   :size [size size]
   :color color))

(defn sprites
  "The initial list of sprites for this scene"
  [_state]
  [(square :greens [400 300] [5 0] 100 green)
   (square :reds [300 300] [0 0] 100 pink)
   (square :reds [500 300] [0 0] 100 pink)])

(defn colliders
  "Returns the list of colliders for the scene"
  []
  [(collision/collider
    :greens  ;; collision group `a`
    :reds    ;; collision group `b`

    ;; collision function `a`, returns updated `a`
    (fn [g r]
      ;; reverse x direction of green
      (update-in g [:vel 0] #(* % -1)))

    ;; collision function `b`, retuns updated `b`
    (fn [r g]
      ;; bump red a little
      (let [distance (- (get-in r [:pos 0])
                        (get-in g [:pos 0]))
            direction (if (neg? distance) -1 1)]
        (update-in r [:pos 0] #(+ % (* direction 10))))))

   ;; ... other colliders
   ])

(defn draw-level-01!
  "Called each frame, draws the current scene to the screen"
  [state]
  (c/draw-background! grey)
  (sprite/draw-scene-sprites! state))

(defn update-level-01
  "Called each frame, update the sprites in the current scene"
  [state]
  (-> state
      sprite/update-state
      ;; NOTE: you must update collisions for them to work
      collision/update-state))

(defn init
  "Initialise this scene"
  [state]
  {:sprites (sprites state)
   :colliders (colliders)
   :draw-fn draw-level-01!
   :update-fn update-level-01})

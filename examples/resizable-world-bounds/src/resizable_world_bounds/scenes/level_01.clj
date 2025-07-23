(ns resizable-world-bounds.scenes.level-01
  (:require [clunk.collision :as collision]
            [clunk.core :as c]
            [clunk.input :as i]
            [clunk.palette :as p]
            [clunk.sprite :as sprite]
            [clunk.util :as u]))

(def coral-pink (p/hex->rgba "#FF9B85"))
(def transparent [0 0 0 0])

(defn border-data
  [side [w h]]
  (get {:top {:sprite-group :world-bounds-y
              :pos [0 0]
              :size [w 100]
              :offsets [:left :bottom]}
        :right {:sprite-group :world-bounds-x
                :pos [w 0]
                :size [100 h]
                :offsets [:left :top]}
        :bottom {:sprite-group :world-bounds-y
                 :pos [0 h]
                 :size [w 100]
                 :offsets [:left :top]}
        :left {:sprite-group :world-bounds-x
               :pos [0 0]
               :size [100 h]
               :offsets [:right :top]}}
       side))

(defn world-bound-border
  [side window-size]
  (let [{:keys [sprite-group pos size offsets]} (border-data side window-size)]
    (sprite/sprite sprite-group
                   pos
                   :size size
                   :offsets offsets
                   :color transparent
                   :extra {:side side})))

(defn rand-vel
  []
  [(rand-nth [-3 -2 2 3])
   (rand-nth [-3 -2 2 3])])

(defn sprites
  "The initial list of sprites for this scene"
  [{:keys [window] :as state}]
  (let [window-size (u/window-size window)]
    [(sprite/geometry-sprite :ball
                             (u/center window)
                             (u/ellipse-points [50 50] :num-points 64)
                             :vel (rand-vel)
                             :size [50 50]
                             :color p/white
                             :fill? true)
     ;; world bounds
     (world-bound-border :top window-size)
     (world-bound-border :right window-size)
     (world-bound-border :bottom window-size)
     (world-bound-border :left window-size)]))

(defn draw-level-01!
  "Called each frame, draws the current scene to the screen"
  [state]
  (c/draw-background! coral-pink)
  (sprite/draw-scene-sprites! state))

(defn update-level-01
  "Called each frame, update the sprites in the current scene"
  [state]
  (-> state
      sprite/update-state
      collision/update-state))

(defn bounce-x [s] (update-in s [:vel 0] -))
(defn bounce-y [s] (update-in s [:vel 1] -))

(defn colliders
  []
  [(collision/collider
    :ball
    :world-bounds-x
    (fn [ball _] (bounce-x ball))
    collision/identity-collide-fn)
   (collision/collider
    :ball
    :world-bounds-y
    (fn [ball _] (bounce-y ball))
    collision/identity-collide-fn)])

(defn restart
  [{:keys [window] :as state} e]
  (if (i/is e :key i/K_R :action i/PRESS)
    (sprite/update-sprites
     state
     (sprite/has-group :ball)
     #(-> %
          (assoc :pos (u/center window))
          (assoc :vel (rand-vel))))
    state))

(defn adjust-world-bounds
  [{:keys [window] :as state} {[w h] :size}]
  (let [window-size (u/window-size window)]
    (sprite/update-sprites
     state
     (sprite/has-group [:world-bounds-x :world-bounds-y])
     (fn [{:keys [side] :as s}]
       (let [{:keys [pos size offsets]} (border-data side window-size)]
         (-> s
             (assoc :pos pos)
             (assoc :size size)
             (assoc :offsets offsets)))))))

(defn init
  "Initialise this scene"
  [state]
  {:sprites (sprites state)
   :draw-fn draw-level-01!
   :update-fn update-level-01
   :colliders (colliders)
   :key-fns [restart]
   :window-resize-fns [adjust-world-bounds]})

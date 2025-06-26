(ns basic-clunk.scenes.other
  (:require [clunk.collision :as collision]
            [clunk.core :as c]
            [clunk.image :as image]
            [clunk.input :as i]
            [clunk.palette :as p]
            [clunk.scene :as scene]
            [clunk.sprite :as sprite]
            [clunk.tween :as tween]
            [clunk.util :as u]))

(defn sprites
  [{:keys [window vg] :as state}]
  (let [[window-w window-h] (u/window-size window)]
    [(sprite/sprite :example
                    [500 50]
                    :vel [3 3]
                    :color [0 1 0])
     (sprite/animated-sprite :animated-captain
                             [600 500]
                             [240 360]
                             (image/load-texture "resources/img/captain.png")
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
                             :current-animation :none
                             :vel [2 -3]
                             :debug? true
                             :debug-color p/cyan)
     ;; world bounds
     (sprite/sprite :wall-y [0 -100]
                    :size [window-w 100]
                    :update-fn identity
                    :offsets [:left :top])
     (sprite/sprite :wall-x [window-w 0]
                    :size [100 window-h]
                    :update-fn identity
                    :offsets [:left :top])
     (sprite/sprite :wall-y [0 window-h]
                    :size [window-w 100]
                    :update-fn identity
                    :offsets [:left :top])
     (sprite/sprite :wall-x [-100 0]
                    :size [100 window-h]
                    :update-fn identity
                    :offsets [:left :top])]))

(defn wall-colliders
  [sprite-group]
  [(collision/collider
    sprite-group
    :wall-x
    (fn [s _]
      (update-in s [:vel 0] * -1))
    collision/identity-collide-fn)
   (collision/collider
    sprite-group
    :wall-y
    (fn [s _]
      (update-in s [:vel 1] * -1))
    collision/identity-collide-fn)])

(defn colliders
  []
  (concat
   [(collision/collider
     :animated-captain
     :example
     (fn [{:keys [current-animation] :as animated-captain} _example-sprite]
       (-> animated-captain
           (assoc :pos [600 500])
           (sprite/set-animation (rand-nth (remove #{current-animation}
                                                   [:none :idle :run :jump])))))
     (fn [example-sprite _animated-captain]
       (-> example-sprite
           (assoc :pos [700 100])
           (assoc :color [(rand) (rand) (rand)]))))]
   (wall-colliders :animated-captain)
   (wall-colliders :example)
   (wall-colliders :example-text)))

(defn update-other
  [state]
  (-> state
      sprite/update-state
      collision/update-state
      tween/update-state))

(defn draw-other!
  [state]
  (c/draw-background! (p/hex->rgba "#559CAD"))
  (-> state
      sprite/draw-scene-sprites!))

(defn kp1
  [state e]
  ;; when we press space, transition to another scene
  (if (i/is e i/K_SPACE i/PRESS)
    (scene/transition
     state
     ((:current-scene state) {:other :demo
                              :demo :other})
     :transition-length 60)
    state))

(defn init
  [state]
  {:sprites (sprites state)
   :colliders (colliders)
   :update-fn update-other
   :draw-fn draw-other!
   :key-fns [kp1]})

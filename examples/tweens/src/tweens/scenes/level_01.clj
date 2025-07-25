(ns tweens.scenes.level-01
  (:require [clunk.core :as c]
            [clunk.image :as image]
            [clunk.sprite :as sprite]
            [clunk.tween :as tween]))

(def blue [0 0.6 1 1])

(defn captain
  [pos current-animation]
  (sprite/animated-sprite :captain
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
                          :current-animation current-animation))

(defn tween
  "A tween requires a key of the field to modify and a value to modify it by.

  For non-numerical fields (such as `:pos`, a vector of 2 values), we
  need to supply an `:update-fn` which takes the current value of the
  field and updates it by a delta `d`.

  We can specify the tween's progression curve using the `:easing-fn`
  option, a number of default easing functions are provided, but you
  can write your own if needed.

  We can make the tween yoyo with the `:yoyo?` option. This will
  return the field to it's orginal untweened value by applying the
  tween in reverse. For non-numerical fields we will also need to
  supply a `:yoyo-update-fn`.

  We can specify a positive number of times to repeat the tween with
  the `:repeat-times` option (defaults to 1). We can supply the `##Inf`
  special symbolic value for continuous repetition.

  We can alter the number of deltas generated and applied by the tween
  with the `:step-count` option. This defaults to 100 and determines
  how many updates will be applied to the sprite to achieve the
  modified field value. We can use the `quip.util/ms->frames` function
  to calculate the expected number of frames processed in a given
  millisecond time interval.

  We can specify `:on-<x>-fn` functions to modify the sprite when it
  `repeat`s, `yoyo`s or `complete`s."
  []
  (tween/tween :pos
               300
               :update-fn (fn [[x y] d]
                            [(+ x d) y])
               :easing-fn tween/ease-in-out-quart
               :repeat-times 3
               :on-repeat-fn (fn [s]
                               (prn "REPEAT!")
                               (sprite/set-animation s :run))
               :yoyo? true
               :yoyo-update-fn (fn [[x y] d]
                                 [(- x d) y])
               :on-yoyo-fn (fn [s]
                             (prn "YOYO!")
                             (sprite/set-animation s :jump))
               :on-complete-fn (fn [s]
                                 (prn "COMPLETE!")
                                 (sprite/set-animation s :idle))))

(defn random-pos-tween
  "Creates a non-repeating yoyoing tween which chooses a random axis and
  magnitude.

  When it yoyos a new `random-pos-tween` is added to the sprite."
  []
  (let [axis (rand-int 2)]
    (tween/tween :pos
                 (- (rand-int 200) 100)
                 :update-fn (if (zero? axis)
                              (fn [[x y] d]
                                [(+ x d) y])
                              (fn [[x y] d]
                                [x (+ y d)]))
                 :yoyo? true
                 :yoyo-update-fn (if (zero? axis)
                                   (fn [[x y] d]
                                     [(- x d) y])
                                   (fn [[x y] d]
                                     [x (- y d)]))
                 :on-yoyo-fn (fn [s] (tween/add-tween s (random-pos-tween))))))

(defn sprites
  "The initial list of sprites for this scene"
  [_state]
  (let [tween (tween)
        captain-sprite (captain [150 180] :run)
        tweened-captain (tween/add-tween captain-sprite tween)]
    [captain-sprite
     tweened-captain]))

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
      tween/update-state))

(defn init
  "Initialise this scene"
  [state]
  {:sprites (sprites state)
   :draw-fn draw-level-01!
   :update-fn update-level-01})

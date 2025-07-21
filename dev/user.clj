(ns user
  "Temporary example game namespace, useful as we don't need to install clunk locally to test changes.

  Eventually we should remove this and just have a bunch of games in `examples`."
  (:require [clunk.audio :as audio]
            [clunk.collision :as collision]
            [clunk.core :as c]
            [clunk.delay :as delay]
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
                    [80 500]
                    :vel [3 3]
                    :color p/green
                    ;; the default size is [20 20] so we shift the
                    ;; bounding poly points by half otherwise they
                    ;; center on the top-left corner of the sprite.
                    :points (u/ellipse-points [60 60] :num-points 8)
                    :size [60 60]
                    :debug? true
                    :debug-color p/white)

     (sprite/image-sprite :captain-sheet
                          [100 100]
                          [1680 1440]
                          :captain-spritesheet
                          :offsets [:left :top])
     (-> (sprite/animated-sprite :animated-captain
                                [600 500]
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
                                :current-animation :none
                                :vel [2 -3]
                                :debug? true
                                :debug-color p/cyan)
         (i/add-on-click (fn [state s]
                           (prn "CLICKED THE CAPTAIN!")
                           state)))
     (-> (sprite/image-sprite :example-image
                              (u/center window)
                              [322 346]
                              :big-present
                              :debug? true)
         (tween/add-tween
          (tween/tween :rotation
                       360
                       :yoyo? true
                       :repeat-times ##Inf))
         (tween/add-tween
          (tween/tween :pos
                       100
                       :update-fn tween/tween-x-fn
                       :yoyo? true
                       :yoyo-update-fn tween/tween-x-yoyo-fn
                       :repeat-times ##Inf))
         (tween/add-tween
          (tween/tween :pos
                       -200
                       :step-count 50
                       :easing-fn tween/ease-out-quad
                       :update-fn tween/tween-y-fn
                       :yoyo? true
                       :yoyo-update-fn tween/tween-y-yoyo-fn
                       :repeat-times ##Inf)))
     (-> (sprite/text-sprite :example-text
                            [250 50]
                            "hello clunk game"
                            :vel [-2 -3]
                            :update-fn sprite/update-pos
                            :color p/cyan
                            :debug? true)
         (tween/add-tween
          (tween/tween :rotation
                       -360
                       :step-count 300
                       :repeat-times ##Inf)))

     (sprite/geometry-sprite :right-side-indicator
                             [window-w 300]
                             (u/ellipse-points [120 60] :num-points 3)
                             :size [120 60]
                             :offsets [:right :center]
                             :fill? true
                             :color p/grey
                             :debug? true
                             :debug-color p/red)

     (let [start [0 0]
           c1 [(- (rand-int window-w) (/ window-w 2))
               (- (rand-int window-h) (/ window-h 2))]
           c2 [(+ 600 (- (rand-int window-w) (/ window-w 2)))
               (- (rand-int window-h) (/ window-h 2))]
           end [600 0]]
       (sprite/geometry-sprite :bezier-curve
                               (u/center window)
                               (u/bezier-points start c1 c2 end)
                               :size [600 0]
                               :closed? false
                               :color (p/hex->rgba "#FF9B85")
                               :offsets [:center]
                               :line-width 6))

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
           (update-in [:vel 0] (rand-nth [- identity]))
           (update-in [:vel 1] (rand-nth [- identity]))
           (sprite/set-animation (rand-nth (remove #{current-animation}
                                                   [:none :idle :run :jump])))))
     (fn [example-sprite _animated-captain]
       (-> example-sprite
           (assoc :pos [700 100])
           (update-in [:vel 0] (rand-nth [- identity]))
           (update-in [:vel 1] (rand-nth [- identity]))
           (assoc :color [(rand) (rand) (rand) 1]))))]
   (wall-colliders :animated-captain)
   (wall-colliders :example)
   (wall-colliders :example-text)))

(defn update-demo
  [state]
  (-> state
      sprite/update-state
      collision/update-state
      tween/update-state
      delay/update-state))

(defn update-other
  [state]
  (-> state
      sprite/update-state
      collision/update-state
      tween/update-state
      delay/update-state))

(defn draw-demo!
  [state]
  (c/draw-background! (p/hex->rgba "#3A435E"))
  (sprite/draw-scene-sprites! state))

(defn draw-other!
  [state]
  (c/draw-background! (p/hex->rgba "#559CAD"))
  (-> state
      sprite/draw-scene-sprites!))

(defn kp1
  [{:keys [restart-fn] :as state} e]
  (cond
    ;; when we press space, transition to another scene
    (i/is e :key i/K_SPACE :action i/PRESS)
    (scene/transition
     state
     ((:current-scene state) {:other :demo
                              :demo :other})
     :transition-length 60)

    ;; when we press r, reset the game
    (i/is e :key i/K_R :action i/PRESS)
    (restart-fn state)

    :else state))

(defn kp2
  [state e]
  ;;  (prn "kp2" e)
  ;; if we pres enter, enqueue a custom event
  (when (i/is e :key i/K_ENTER :action i/PRESS)
    (c/enqueue-event! {:event-type :other-event
                       :data {:a 1 :b 2}}))
  state)

(defn m1
  [state e]
  ;; (prn "m1" e)
  (audio/play! :blip-1)
  state)

(defn m2
  [state e]
  (prn "m2" e)
  (if (and (= i/M_LEFT (:button e))
           (= i/PRESS (:action e)))
    (do (prn "adding delay")
        (-> state
            (delay/add-delay-fns
             (delay/sequential-delay-fns
              [[0 (fn [state] (prn "DELAYED PRINT 1!!!") state)]
               [50 (fn [state] (prn "DELAYED PRINT 2!!!") state)]
               [1000 (fn [state] (prn "DELAYED PRINT 3!!!") state)]]
              :initial-delay 1000))))
    state))

(defn mm
  [state e]
  ;; (prn "mm" e)
  state)

(defn wr
  [state {[w _h] :size :as e}]
  (-> state
      (sprite/update-sprites
       (sprite/has-group :right-side-indicator)
       (fn [s]
         (assoc-in s [:pos 0] w)))))

;; handle custom events
(defn other
  [state e]
  (prn (:data e))
  state)

(defn init-scenes
  [state]
  {:demo {:sprites (sprites state)
          :colliders (colliders)
          :update-fn update-demo
          :draw-fn draw-demo!
          :key-fns [kp1 kp2]
          :mouse-button-fns [m1 m2]
          :mouse-movement-fns [mm]
          :window-resize-fns [wr]
          ;; define some custom event handlers
          :other-event-fns [other]}
   :other {:sprites (sprites state)
           :key-fns [kp1]
           :update-fn update-other
           :draw-fn draw-other!}})

(defn restart
  [{:keys [init-scenes-fn] :as state}]
  (prn "RESTARTING!!")
  ;; clean up any global stuff here too, set the current scene if need be etc.
  (assoc state :scenes (init-scenes-fn state)))

(def game (c/game {:title "Example Clunk Game"
                   :size [1200 800]
                   :init-scenes-fn init-scenes
                   :restart-fn restart
                   :current-scene :demo
                   :on-start-fn (fn [state]
                                  (prn "STARTING!!!!!")
                                  state)
                   :on-close-fn (fn [final-state]
                                  (prn "CLOSING!!!!!!")
                                  final-state)
                   :assets {:image {:captain-spritesheet "resources/img/captain.png"
                                    :big-present "resources/img/big-present.png"}
                            :audio {:music "resources/audio/music/music.ogg"
                                    :blip-1 "resources/audio/sfx/blip-1.ogg"}}}))

(defn -main
  []
  (c/start! game))

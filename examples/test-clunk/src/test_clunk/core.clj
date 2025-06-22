(ns test-clunk.core
  (:gen-class)
  (:require [clunk.core :as c]
            [clunk.sprite :as sprite]
            [clunk.util :as u]
            [clunk.palette :as p]
            [clunk.collision :as collision]
            [clunk.tween :as tween]
            [clunk.image :as image]))

(defn sprites
  [{:keys [window vg] :as state}]
  (let [[window-w window-h] (u/window-size window)]
    [(sprite/sprite :example
                    [500 50]
                    :vel [3 3]
                    :color [0 1 0])
     (sprite/image-sprite :captain-sheet
                          [100 100]
                          [1680 1440]
                          ;; @TODO: preload assets and get them easily
                          (image/load-texture "resources/img/captain.png")
                          :offsets [:left :top])
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
     (-> (sprite/sprite :tween-example
                        [600 250]
                        :color p/magenta)
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

     (sprite/text-sprite :example-text
                         [50 50]
                         "hello clunk game"
                         :vel [-2 -3]
                         :update-fn sprite/update-pos
                         :color p/cyan
                         :debug? true)

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

(defn update-demo
  [state]
  (-> state
      sprite/update-state
      collision/update-state
      tween/update-state))

(defn draw-demo!
  [state]
  (c/draw-background! (p/hex->rgb "#3A435E"))
  (-> state
      sprite/draw-scene-sprites!))

(defn kp1
  [state e]
  ; (prn "kp1" e)
  state)

(defn kp2
  [state e]
  ;;  (prn "kp2" e)
  ;; if we pres space, enqueue a custom event
  (when (= 32 (:k e))
    (c/enqueue-event! {:event-type :other-event
                       :data {:a 1 :b 2}}))
  state)

(defn m1
  [state e]
  ;; (prn "m1" e)
  state)

(defn m2
  [state e]
  ;; (prn "m2" e)
  state)

(defn mm
  [state e]
  ;; (prn "mm" e)
  state)

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
          ;; define some custom event handlers
          :other-event-fns [other]}})

(def game (c/game {:title "Example Clunk Game"
                   :size [1200 800]
                   :init-scenes-fn init-scenes
                   :current-scene :demo
                   :audio? false}))

(defn -main
  []
  (c/start! (c/game game)))

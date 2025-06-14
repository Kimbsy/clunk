>(ns clunk.sprite
   (:import (org.lwjgl.opengl GL11))
   (:require [clunk.image :as image]))

(defn pos-offsets
  "Determine the x and y offsets for a sprite based on it's `:w`, `:h`
  and `:offsets` configuration.

  Defaults to `[:center :center]`."
  [{[x-off y-off] :offsets
    :keys [w h]}]
  (let [dx (cond
             (= :left x-off) 0
             (= :right x-off) (- w)
             (#{:center :centre} x-off) (- (/ w 2))
             :else (- (/ w 2)))
        dy (cond
             (= :top y-off) 0
             (= :bottom y-off) (- h)
             (#{:center :centre} y-off) (- (/ h 2))
             :else (- (/ h 2)))]
    [dx dy]))

(defn update-pos
  "Update the sprite position based on its velocity."
  [{[x y] :pos
    [vx vy] :vel
    :as s}]
  (assoc s :pos [(+ x vx) (+ y vy)]))

(defn update-frame-delay
  [{:keys [current-animation] :as s}]
  (let [animation   (current-animation (:animations s))
        frame-delay (:frame-delay animation)]
    (update s :delay-count #(mod (inc %) frame-delay))))

(defn update-animation
  [{:keys [current-animation delay-count] :as s}]
  (if (zero? delay-count)
    (let [animation (current-animation (:animations s))
          max-frame (:frames animation)]
      (update s :animation-frame #(mod (inc %) max-frame)))
    s))

(defn update-animated-sprite
  [s]
  (some-> s
          update-frame-delay
          update-animation
          update-pos))

(defn draw-default-sprite!
  [{[x y] :pos
    [r g b] :color
    :keys [w h]}]
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor3f r g b)
  (GL11/glBegin GL11/GL_QUADS)
  (GL11/glVertex2f x y)
  (GL11/glVertex2f (+ x w) y)
  (GL11/glVertex2f (+ x w) (+ y h))
  (GL11/glVertex2f x (+ y h))
  (GL11/glEnd))

(defn draw-image-sprite!
  [{:keys [pos size image-texture] :as sprite}]
  (image/draw-image! image-texture pos size))

(defn draw-animated-sprite!
  [{:keys [pos rotation w h spritesheet current-animation animation-frame] :as s}]
  ;; @TODO: implement
  )

(defn set-animation
  [s animation]
  (-> s
      (assoc :current-animation animation)
      (assoc :animation-frame 0)))

(defn default-bounding-poly
  "Generates a bounding polygon based off the `w` by `h` rectangle of a
  sprite."
  [{:keys [w h]}]
  [[0 0]
   [w 0]
   [w h]
   [0 h]])

(defn sprite
  "The simplest sensible sprite.

  Takes a `sprite-group` (a label for sprites of this type) and a
  `pos` (an `[x y]` position vector).

  Can be enriched with any custom fields by providing an `:extra`
  kwarg map."
  [sprite-group pos &
   {:keys [w
           h
           vel
           color
           update-fn
           draw-fn
           points
           bounds-fn
           offsets
           extra]
    :or {w 20
         h 20
         vel [0 0]
         color [1 1 1]
         update-fn update-pos
         draw-fn draw-default-sprite!
         offsets [:center]
         extra {}}}]
  (merge
   {:sprite-group sprite-group
    :uuid (random-uuid)
    :pos pos
    :w w
    :h h
    :vel vel
    :color color
    :update-fn update-fn
    :draw-fn draw-fn
    :bounds-fn (or bounds-fn
                   (if (seq points)
                     :points
                     default-bounding-poly))
    :offsets offsets}
   extra))

;; @TODO: geometry-sprite

;; @TODO: how do we do rotation?
(defn image-sprite
  [sprite-group pos [w h :as size] image-texture &
   {:keys [vel
           update-fn
           draw-fn
           points
           bounds-fn
           offsets
           extra]
    :or   {vel [0 0]
           update-fn update-pos
           draw-fn draw-image-sprite!
           offsets [:center]
           extra {}}}]
  (merge
   (sprite sprite-group pos)
   {:w w
    :h h
    :size size
    :image-texture image-texture
    :vel vel
    :update-fn update-fn
    :draw-fn draw-fn
    :points points
    :bounds-fn (or bounds-fn
                   (if (seq points)
                     :points
                     default-bounding-poly))
    :offsets offsets}
   extra))

;; @TODO: animated sprite

;; @TODO: text sprite

(defn update-state
  "Update each sprite in the current scene using its `:update-fn`."
  [{:keys [current-scene] :as state}]
  (update-in state [:scenes current-scene :sprites]
             (fn [sprites]
               (pmap (fn [s]
                       ((:update-fn s) s))
                     sprites))))

(defn draw-scene-sprites!
  "Draw each sprite in the current scene using its `:draw-fn`."
  [{:keys [current-scene] :as state}]
  (let [sprites (get-in state [:scenes current-scene :sprites])]
    (doall
     (map (fn [s]
            ((:draw-fn s) s))
          sprites))))

(defn update-sprites
  "Update sprites in the current scene with the update function `f`.

  Optionally takes a filtering function `pred`."
  ([state f]
   (update-sprites state (constantly true) f))
  ([{:keys [current-scene] :as state} pred f]
   (update-in state [:scenes current-scene :sprites]
              (fn [sprites]
                (pmap (fn [s]
                        (if (pred s)
                          (f s)
                          s))
                      sprites)))))

(defn has-group
  "Creates a predicate function for filtering sprites based on their
  `:sprite-group.`

  Takes either a single `:sprite-group` keyword, or a collection of
  them.

  Commonly used alongside `update-sprites`:

  (sprite/update-sprites
    state
    (sprite/has-group :asteroids)
    sprite-update-fn)

  (sprite/update-sprites
    state
    (sprite/has-group [:asteroids :ships])
    sprite-update-fn)"
  [sprite-group]
  (if (coll? sprite-group)
    (fn [s]
      ((set sprite-group) (:sprite-group s)))
    (fn [s]
      (= sprite-group (:sprite-group s)))))

(defn is-sprite
  [{:keys [uuid]}]
  "Creates a predicate function for filtering sprites based on their
  `:uuid`.

  Takes a map with a `:uuid` key (such as a sprite).

  Commonly used alongside `update-sprites`:

  (sprite/update-sprites
    state
    (sprite/is-sprite player)
    sprite-update-fn)"
  (fn [s]
    (= uuid (:uuid s))))

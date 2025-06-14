>(ns clunk.sprite
   (:import (org.lwjgl.opengl GL11))
   (:require [clunk.image :as image]
             [clunk.palette :as p]))

(defn default-bounding-poly
  "Generates a bounding polygon based on the `:size` rectangle of a
  sprite."
  [{[w h] :size}]
  [[0 0]
   [w 0]
   [w h]
   [0 h]])

(defn pos-offsets
  "Determine the x and y offsets for a sprite based on it's `:w`, `:h`
  and `:offsets` configuration.

  Defaults to `[:center :center]`."
  [{[x-off y-off] :offsets
    [w h] :size}]
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
  "Increment the `:delay-count` (wrapping to zero at `:frame-delay`).

  The animation frame will change at 0."
  [{:keys [current-animation] :as s}]
  (let [animation (current-animation (:animations s))
        frame-delay (:frame-delay animation)]
    (update s :delay-count #(mod (inc %) frame-delay))))

(defn update-animation
  "If the `:delay-count` is zero, move to the next animation frame."
  [{:keys [current-animation delay-count] :as s}]
  (if (zero? delay-count)
    (let [animation (current-animation (:animations s))
          max-frame (:frames animation)]
      (update s :animation-frame #(mod (inc %) max-frame)))
    s))

(defn update-animated-sprite
  "Update the animation of a sprite in addition to it's position."
  [s]
  (some-> s
          update-frame-delay
          update-animation
          update-pos))

(defn draw-default-sprite!
  "Draw a green square as a sprite placeholder."
  [{[x y] :pos
    [r g b] :color
    [w h] :size
    :as s}]
  (let [[off-x off-y] (pos-offsets s)]
    (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
    (GL11/glColor3f r g b)
    (GL11/glBegin GL11/GL_QUADS)
    (GL11/glVertex2f (+ x off-x) (+ y off-y))
    (GL11/glVertex2f (+ x w off-x) (+ y off-y))
    (GL11/glVertex2f (+ x w off-x) (+ y h off-y))
    (GL11/glVertex2f (+ x off-x) (+ y h off-y))
    (GL11/glEnd)))

(defn draw-bounds
  [{[x y] :pos
    [r g b] :debug-color
    bounds-fn :bounds-fn
    :as s}]
  (let [[off-x off-y] (pos-offsets s)]
    (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
    (GL11/glColor3f r g b)
    (GL11/glBegin GL11/GL_LINE_LOOP)
    (doseq [[bx by] (bounds-fn s)]
      (GL11/glVertex2f (+ x bx off-x) (+ y by off-y)))
    (GL11/glEnd)))

;; @TODO: this should be refactored into a general draw-rect function
(defn draw-center
  [{:keys [pos] :as s}]
  (draw-default-sprite! {:pos pos :color [1 0 0] :size [40 2]})
  (draw-default-sprite! {:pos pos :color [1 0 0] :size [2 40]}))

(defn draw-image-sprite!
  [{:keys [pos size image-texture] :as s}]
  (let [offsets (pos-offsets s)]
    (image/draw-image! image-texture (map + pos offsets) size)))

(defn draw-animated-sprite!
  [{:keys [pos
           spritesheet-texture
           spritesheet-size
           current-animation
           animation-frame]
    [w h :as size] :size
    :as s}]
  (let [animation (current-animation (:animations s))
        sheet-x-offset  (* animation-frame w)
        sheet-y-offset  (* (:y-offset animation) h)
        offsets (pos-offsets s)]
    (image/draw-sub-image! spritesheet-texture
                           (map + pos offsets)
                           spritesheet-size
                           [sheet-x-offset
                            sheet-y-offset]
                           size)))

(defn set-animation
  [s animation]
  (-> s
      (assoc :current-animation animation)
      (assoc :animation-frame 0)))

(defn sprite
  "The simplest sensible sprite.

  Takes a `sprite-group` (a label for sprites of this type) and a
  `pos` (an `[x y]` position vector).

  Can be enriched with any custom fields by providing an `:extra`
  kwarg map."
  [sprite-group pos &
   {:keys [size
           vel
           color
           update-fn
           draw-fn
           points
           bounds-fn
           offsets
           debug?
           debug-color
           extra]
    :or {size [20 20]
         vel [0 0]
         color [1 1 1]
         update-fn update-pos
         draw-fn draw-default-sprite!
         offsets [:center]
         debug? false
         debug-color p/red
         extra {}}}]
  (merge
   {:sprite-group sprite-group
    :uuid (random-uuid)
    :pos pos
    :size size
    :vel vel
    :color color
    :update-fn update-fn
    :draw-fn draw-fn
    :bounds-fn (or bounds-fn
                   (if (seq points)
                     :points
                     default-bounding-poly))
    :debug? debug?
    :debug-color debug-color
    :offsets offsets}
   extra))

;; @TODO: geometry-sprite

;; @TODO: how do we do rotation?
(defn image-sprite
  [sprite-group pos size image-texture &
   {:keys [vel
           update-fn
           draw-fn
           points
           bounds-fn
           offsets
           debug?
           debug-color
           extra]
    :or {vel [0 0]
         update-fn update-pos
         draw-fn draw-image-sprite!
         offsets [:center]
         debug? false
         debug-color p/red
         extra {}}}]
  (merge
   (sprite sprite-group pos)
   {:size size
    :image-texture image-texture
    :vel vel
    :update-fn update-fn
    :draw-fn draw-fn
    :points points
    :bounds-fn (or bounds-fn
                   (if (seq points)
                     :points
                     default-bounding-poly))
    :debug? debug?
    :debug-color debug-color
    :offsets offsets}
   extra))

(defn animated-sprite
  [sprite-group pos size spritesheet-texture spritesheet-size &
   {:keys [vel
           update-fn
           draw-fn
           points
           bounds-fn
           offsets
           animations
           current-animation
           debug?
           debug-color
           extra]
    :or {rotation 0
         vel [0 0]
         update-fn update-animated-sprite
         draw-fn draw-animated-sprite!
         offsets [:center]
         animations {:none {:frames 1
                            :y-offset 0
                            :frame-delay 100}}
         current-animation :none
         debug? false
         debug-color p/red
         extra {}}}]
  (merge
   (sprite sprite-group pos)
   {:size size
    :spritesheet-texture spritesheet-texture
    :spritesheet-size spritesheet-size
    :vel vel
    :animated? true
    :update-fn update-fn
    :draw-fn draw-fn
    :points points
    :bounds-fn (or bounds-fn
                   (if (seq points)
                     :points
                     default-bounding-poly))
    :offsets offsets
    :animations animations
    :current-animation current-animation
    :delay-count 0
    :animation-frame 0
    :debug? debug?
    :debug-color debug-color}
   extra))

;; @TODO: text sprite

(defn update-state
  "Update each sprite in the current scene using its `:update-fn`."
  [{:keys [current-scene] :as st}]
  (update-in st [:scenes current-scene :sprites]
             (fn [sprites]
               (pmap (fn [s]
                       ((:update-fn s) s))
                     sprites))))

(defn draw-scene-sprites!
  "Draw each sprite in the current scene using its `:draw-fn`."
  [{:keys [current-scene]
    global-debug? :debug?
    :as st}]
  (let [sprites (get-in st [:scenes current-scene :sprites])]
    (doall
     (map (fn [{:keys [draw-fn debug?] :as s}]
            (draw-fn s)
            (when (or global-debug? debug?)
              (draw-bounds s)
              (draw-center s)))
          sprites))))

(defn update-sprites
  "Update sprites in the current scene with the update function `f`.

  Optionally takes a filtering function `pred`."
  ([st f]
   (update-sprites st (constantly true) f))
  ([{:keys [current-scene] :as st} pred f]
   (update-in st [:scenes current-scene :sprites]
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

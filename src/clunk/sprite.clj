(ns clunk.sprite
  (:require [clojure.math :as math]
            [clunk.image :as image]
            [clunk.palette :as p]
            [clunk.shape :as shape]
            [clunk.util :as u])
  (:import (org.lwjgl.nanovg NanoVG)
           (org.lwjgl.opengl GL11
                             GL14
                             GL30)))

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
  [{:keys [pos size color] :as s}]
  (let [offsets (pos-offsets s)]
    (shape/fill-rect! (map + pos offsets) size color)))

(defn draw-bounds
  [{:keys [pos debug-color bounds-fn] :as s}]
  (let [offsets (pos-offsets s)
        points (bounds-fn s)]
    (shape/draw-poly! (map + pos offsets) points debug-color)))

(defn draw-center
  [{[x y] :pos
    color :debug-color}]
  (shape/draw-rect! [(- x 20) y] [40 2] color)
  (shape/draw-rect! [x (- y 20)] [2 40] color))

(defn draw-geometry-sprite!
  [{:keys [pos size points color fill? line-width] :as s}]
  (let [offsets (pos-offsets s)]
    (if fill?
      (shape/fill-concave-poly! (map + pos offsets) points color)
      (shape/draw-poly! (map + pos offsets) points color :line-width line-width))))

(defn draw-image-sprite!
  [{:keys [pos size image-texture rotation] :as s}]
  (let [offsets (pos-offsets s)]
    (image/draw-image! image-texture (map + pos offsets) size rotation)))

(defn draw-animated-sprite!
  [{:keys [pos
           spritesheet-texture
           spritesheet-size
           current-animation
           animation-frame
           rotation]
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
                           size
                           rotation)))

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
         color p/white
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
    :points points
    :bounds-fn (or bounds-fn
                   (if (seq points)
                     :points
                     default-bounding-poly))
    :debug? debug?
    :debug-color debug-color
    :offsets offsets}
   extra))

(defn geometry-sprite
  [sprite-group pos points &
   {:keys [rotation
           size
           vel
           update-fn
           draw-fn
           bounds-fn
           offsets
           color
           fill?
           line-width
           debug?
           debug-color
           extra]
    :or  {rotation 0
          size (let [xs (sort (map first points))
                     ys (sort (map second points))]
                 [(- (last xs) (first ys))
                  (- (last xs) (first ys))])
          vel [0 0]
          update-fn update-pos
          draw-fn draw-geometry-sprite!
          bounds-fn :points
          offsets [:center]
          color p/white
          fill? false
          line-width 2
          debug? false
          debug-color p/red
          extra {}}}]
  (merge
   (sprite sprite-group pos)
   {:size size
    :rotation rotation
    :vel vel
    :update-fn update-fn
    :draw-fn draw-fn
    :points points
    :bounds-fn bounds-fn
    :color color
    :fill? fill?
    :line-width line-width
    :debug? debug?
    :debug-color debug-color
    :offsets offsets}
   extra))

(defn image-sprite
  [sprite-group pos size image-texture-key &
   {:keys [rotation
           vel
           update-fn
           draw-fn
           points
           bounds-fn
           offsets
           debug?
           debug-color
           extra]
    :or {rotation 0
         vel [0 0]
         update-fn update-pos
         draw-fn draw-image-sprite!
         offsets [:center]
         debug? false
         debug-color p/red
         extra {}}}]
  (merge
   (sprite sprite-group pos)
   {:size size
    :image-texture (get @image/textures image-texture-key)
    :rotation rotation
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
  [sprite-group pos size spritesheet-texture-key spritesheet-size &
   {:keys [rotation
           vel
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
    :spritesheet-texture (get @image/textures spritesheet-texture-key)
    :spritesheet-size spritesheet-size
    :rotation rotation
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

(defn capture-gl-state
  "Capture the current blending state of GL so we can draw NanoVG
  text (which blats the config) before restoring the original state
  for drawing images/shapes."
  []
  {:src-rgb (GL11/glGetInteger GL30/GL_BLEND_SRC_RGB)
   :src-alpha (GL11/glGetInteger GL30/GL_BLEND_SRC_ALPHA)
   :dst-rgb (GL11/glGetInteger GL14/GL_BLEND_DST_RGB)
   :dst-alpha (GL11/glGetInteger GL11/GL_DST_ALPHA)
   :blend-enabled? (GL11/glIsEnabled GL11/GL_BLEND)})

(defn restore-gl-state
  "Restore the original blending state of GL for drawing images/shapes."
  [{:keys [src-rgb src-alpha dst-rgb dst-alpha blend-enabled?]}]
  (if blend-enabled?
    (GL11/glEnable GL11/GL_BLEND)
    (GL11/glDisable GL11/GL_BLEND))
  (GL30/glBlendFuncSeparate src-rgb dst-rgb src-alpha dst-alpha))

(defn draw-text-sprite!
  [{:keys [window vg vg-color default-font] :as state}
   {:keys [content font font-size color rotation]
    [x0 y0 :as pos] :pos
    [w h] :size
    :as s}]
  (let [[off-x _] (pos-offsets s)
        ;; text is drawn with different defaults than other things, so
        ;; we need to modify the pos-offsets rules for the y axis.
        off (second (:offsets s))
        off-y (cond
                (= :top off) h
                (= :bottom off) 0
                (#{:center :centre} off) (/ h 2)
                :else (/ h 2))
        [x y] [(+ x0 off-x) (+ y0 off-y)]
        [r g b a] (map float color)
        a (or a (float 1)) ;; default alpha
        font (or font default-font)
        [window-w window-h] (u/window-size window)
        old-state (capture-gl-state)]
    (NanoVG/nvgBeginFrame vg window-w window-h 1)
    (NanoVG/nvgFontSize vg font-size)
    (NanoVG/nvgFontFace vg font)
    (NanoVG/nvgFillColor vg (NanoVG/nvgRGBAf r g b a vg-color))

    (NanoVG/nvgSave vg)
    (NanoVG/nvgTranslate vg (float x) (float y))
    (when (and rotation
               (not (zero? (mod rotation 360))))
      (NanoVG/nvgRotate vg (math/to-radians rotation)))
    (NanoVG/nvgText vg (float 0) (float 0) content)
    (NanoVG/nvgRestore vg)

    (NanoVG/nvgEndFrame vg)
    (restore-gl-state old-state)))

(defn text-sprite
  [sprite-group pos content &
   {:keys [rotation
           vel
           update-fn
           draw-fn
           points
           bounds-fn
           offsets
           font
           font-size
           color
           debug?
           debug-color
           extra]
    :or {rotation 0
         vel [0 0]
         update-fn identity
         draw-fn draw-text-sprite!
         offsets [:center]
         font "UbuntuMono-Regular"
         font-size 32
         color p/white
         debug? false
         debug-color p/red
         extra {}}}]
  (merge
   (sprite sprite-group pos)
   {:draw-requires-state? true
    :content content
    :font font
    :font-size font-size
    ;; @TODO: collision bounds for text are more complex than
    ;; this (look at nvgtextbounds)
    :size [(* (count content) font-size 0.5) font-size]
    :color color
    :rotation rotation
    :vel vel
    :update-fn update-fn
    :draw-fn draw-fn
    :points points
    :bounds-fn (or bounds-fn
                   (if (seq points)
                     :points
                     default-bounding-poly))
    :offsets offsets
    :debug? debug?
    :debug-color debug-color}
   extra))

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
  [{:keys [current-scene]
    global-debug? :debug?
    :as state}]
  (let [sprites (get-in state [:scenes current-scene :sprites])]
    (doall
     (map (fn [{:keys [draw-fn debug? draw-requires-state?] :as s}]
            (if draw-requires-state?
              (draw-fn state s)
              (draw-fn s))
            (when (or global-debug? debug?)
              (draw-bounds s)
              (draw-center s)))
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

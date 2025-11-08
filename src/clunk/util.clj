(ns clunk.util
  (:require [clojure.math :as math]
            [clojure.java.io :as io])
  (:import (java.io ByteArrayOutputStream)
           (org.lwjgl.glfw GLFW)
           (org.lwjgl.system MemoryStack
                             MemoryUtil)))

;;;; General helpers

(defn split-by
  "Split a collection using a predicate.

  Returns `[things-that-were-true things-that-were-false]`."
  [pred coll]
  (reduce (fn [[yes no] x]
            (if (pred x)
              [(conj yes x) no]
              [yes (conj no x)]))
          [[] []]
          coll))

(defn remove-nth
  "Remove the nth element from a collection."
  [n xs]
  (keep-indexed
   #(when (not= n %1) %2)
   xs))

;;;; LWJGL helpers

(defn window-size
  [window]
  ;; MemoryStack implements java.io.Closeable, so with-open will pop for us
  (with-open [stack (MemoryStack/stackPush)]
    ;; allocate two IntBuffers of size 1 each
    (let [p-width (.mallocInt stack 1)
          p-height (.mallocInt stack 1)]
      ;; get the frameBuffer size info
      (GLFW/glfwGetFramebufferSize ^long window p-width p-height)
      ;; extract the values from the IntBuffers
      [(.get p-width 0)
       (.get p-height 0)])))

(defn window-pos
  "Create an `[x y]` position vector at the specified ratio
  coordinates."
  [window [x-factor y-factor]]
  (let [[w h] (window-size window)]
    [(* w x-factor) (* h y-factor)]))

(defn center
  "The `[x y]` position vector at the center of the screen."
  [window]
  (window-pos window [0.5 0.5]))

(def centre center)

(defn mouse-pos
  "Get the current `[x y]` position of the mouse cursor."
  [window]
  (with-open [stack (MemoryStack/stackPush)]
    ;; allocate two IntBuffers of size 1 each
    (let [p-x (.mallocDouble stack 1)
          p-y (.mallocDouble stack 1)]
      ;; get the cursorPos info
      (GLFW/glfwGetCursorPos window p-x p-y)
      ;; extract the values from the IntBuffers
      [(.get p-x 0)
       (.get p-y 0)])))

(defn resource->direct-bytebuffer [resource-path]
  (with-open [in (io/input-stream (io/resource resource-path))
              bout (ByteArrayOutputStream.)]
    (let [buf (byte-array 8192)]
      (loop []
        (let [r (.read in buf)]
          (when (pos? r)
            (.write bout buf 0 r)
            (recur))))
      (let [bytes (.toByteArray bout)
            bb (MemoryUtil/memAlloc (alength bytes))]
        (.put bb bytes)
        (.flip bb)
        bb)))) ;; caller must MemoryUtil/memFree


;;; Vector helpers

(defn zero-vector?
  "Predicate to check if a vector has length 0."
  [v]
  (every? zero? v))

(defn squared-magnitude
  "Sum the squares of the components of a vector.

  The `if` check on `z` is a lot faster than doing an `apply` or
  `reduce` across the vector."
  [[x y z]]
  (+ (* x x)
     (* y y)
     (if z (* z z) 0)))

(defn magnitude
  "Calculate the length of a vector."
  [v]
  (math/sqrt (squared-magnitude v)))

(defn v<
  "Determine if the magnitude of a vector `a` is less than the magnitude
  of vector `b`.

  We can just compare the component squares to avoid the costly `sqrt`
  operations."
  [a b]
  (< (squared-magnitude a) (squared-magnitude b)))

(defn v<=
  "Determine if the magnitude of a vector `a` is less than or equal to
  the magnitude of vector `b`.

  We can just compare the component squares to avoid the costly `sqrt`
  operations."
  [a b]
  (<= (squared-magnitude a) (squared-magnitude b)))

(defn normalize
  "Calculate the unit vector of a given vector.

  We calculate the reciprocal of the magnitude of the vector and
  multiply the components by this factor to avoid multiple division
  operations."
  [v]
  (when-not (zero-vector? v)
    (let [factor (/ 1 (magnitude v))]
      (map #(* factor %) v))))

(defn unit-vector
  "Calculate the unit vector of a given vector."
  [v]
  (normalize v))

(defn invert
  "Multiply each component of the vector by -1.

  Represents a rotation of 180 degrees."
  [v]
  (map (partial * -1) v))

(defn rotate-vector
  "Rotate a vector about the origin by `r` degrees.

  Checks first for `r` representing an integer number of rotations, in
  with case the vector will be unchanged."
  [[x y :as v] r]
  (cond
    ;; 360*n degree rotations
    (or (zero? (mod (or r 0) 360))
        (zero-vector? v))
    v

    ;; 180+(360*n) degree rotations
    (= 180 (mod (or r 0) 360))
    (invert v)

    :else
    (let [radians (math/to-radians r)
          cr (math/cos radians)
          sr (math/sin radians)]
      [(- (* x cr)
          (* y sr))
       (+ (* x sr)
          (* y cr))])))

(defn orthogonals
  "Calculate the two orthogonal vectors to a given 2D vector.

  Y axis is inverted so this returns [90-degrees-right-vector
                                      90-degrees-left-vector]"
  [[x y]]
  [[(- y) x]
   [y (- x)]])

(defn direction-vector
  "Calculate the unit direction vector based on the rotation angle."
  [r]
  (let [radians (math/to-radians r)]
    [(math/sin radians)
     (- (math/cos radians))]))

(defn rotation-angle
  "Calculate the rotation angle of a vector."
  [[x y]]
  (math/to-degrees (math/atan2 x y)))

(defn cross
  "Calculate the cross product of two vectors."
  [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])

;;;; Geometry helpers

(defn poly-lines
  "Construct the lines that make up a polygon from its points."
  [poly]
  (partition 2 1 (take (inc (count poly))
                       (cycle poly))))

(defn left-turn?
  "Is the corner from A->B->C a left turn?"
  [[ax ay] [bx by] [cx cy]]
  (let [v1 [(- bx ax) (- by ay) 0]
        v2 [(- cx bx) (- cy by) 0]]
    (>= 0 (last (cross v1 v2)))))

(defn convex?
  "Are all the corners of the polygon turning in the same direction?"
  [poly]
  (->> (cycle poly)
       (take (+ 2 (count poly)))
       (partition 3 1)
       (map (fn [[a b c]]
              (left-turn? a b c)))
       (apply =)))

(defn pos-in-tri?
  "Is the position `pos` inside the triangle ABC?"
  [pos [a b c]]
  (every? true?
          (map (partial apply left-turn?)
               [[a b pos]
                [b c pos]
                [c a pos]])))

(defn other-points
  "Return the points which are not in the ear starting at index `i`"
  [i points]
  (let [n (count points)
        repeating (cycle points)]
    (cond
      (= i 0) (drop 3 points)
      (= i (- n 1)) (drop 2 (butlast points))
      (= i (- n 2)) (rest (drop-last 2 points))
      (= i (- n 3)) (drop-last 3 points)
      :else (concat (take i points)
                    (drop (+ i 3) points)))))

(defn all-ears
  "Return all the ears of a polygon"
  [points]
  (keep-indexed
   (fn [i [a b c]]
     (when (and (left-turn? a b c)
                (not (some #(pos-in-tri? % [a b c])
                           (other-points i points))))
       [i [a b c]]))
   (->> points
        cycle
        (take (+ 2 (count points)))
        (partition 3 1))))

(defn triangulate
  "Split a concave polygon with no holes or overlapping edges into a
  collection of triangles which can be drawn."
  [poly]
  (loop [p poly
         tris []]
    (if-not (= 3 (count p))
      (if-let [[i tri] (first (all-ears p))]
        (recur (remove-nth (mod (inc i) (count p)) p)
               (conj tris tri))
        (do (prn "FOUND NO EARS????")
            (prn p)
            tris))
      (conj tris (vec p)))))

(defn ellipse-points
  "Generate a collection of `[x y]` points in CCW order around an
  ellipse with dimensions `[w h]`.

  The points are all in the `[+x +y]` quadrant so the
  `clunk.sprite/pos-offsets` function works by default."
  [[w h] &
   {:keys [num-points]
    :or {num-points 32}}]
  (let [rx (/ w 2)
        ry (/ h 2)
        ;; negative dr gives us points in CCW order
        dr (- (/ (* 2 math/PI) num-points))]
    (for [i (range num-points)]
      (let [r (* i dr)]
        [(+ rx (* rx (math/cos r)))
         (+ ry (* ry (math/sin r)))]))))

(defn rounded-rect-points
  "Generate a collection of `[x y]` points in CCW order tracing a
  rectangle with rounded corners.

  The points are all in the `[+x +y]` quadrant so the
  `clunk.sprite/pos-offsets` function works by default."
  [[w h] &
   {:keys [radius]
    :or {radius 60}}]
  ;; if the radius is larger than either dimension it all gets wonky
  (let [radius (min w h radius)
        circle (ellipse-points [radius radius])
        n (count circle)
        sector-size (inc (int (/ n 4)))
        ;; split the circle into sectors with common edge points
        [tr tl bl br] (partition sector-size
                                 (dec sector-size)
                                 (take (inc n) (cycle circle)))
        ;; offset 3 of the sectors by the rect size (adjusting for radius)
        br (map (partial map + [(- w radius) (- h radius)]) br)
        bl (map (partial map + [0 (- h radius)]) bl)
        tr (map (partial map + [(- w radius) 0]) tr)]
    ;; reconnect the sectors
    (concat tr tl bl br)))

(defn- lerp
  "Calculate a linear interpolation between two points where `t` is
  between 0 and 1."
  [[p1x p1y] [p2x p2y] t]
  [(+ (* p1x (- 1 t)) (* p2x t))
   (+ (* p1y (- 1 t)) (* p2y t))])

(defn bezier-point
  "Calculate a point on a Bezier curve using De Casteljau's algorithm
  via successive lerps.

  `start` and `end` are the beginning and end of the line, `c1` and
  `c2` are the control points."
  [start c1 c2 end t]
  (let [t1 (lerp start c1 t)
        t2 (lerp c1 c2 t)
        t3 (lerp c2 end t)
        t4 (lerp t1 t2 t)
        t5 (lerp t2 t3 t)]
    (lerp t4 t5 t)))

(defn bezier-curve
  "Generate a collection of `[x y]` points which describe a Bezier
  curve."
  [start c1 c2 end &
   {:keys [samples]
    :or {samples 32}}]
  (let [step (/ 1 samples)]
    (map (partial bezier-point start c1 c2 end)
         (range 0 (+ 1 step) step))))

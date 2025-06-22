(ns clunk.util
  (:import (org.lwjgl.glfw GLFW)
           (org.lwjgl.system MemoryStack))
  (:require [clojure.math :as math]))

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

(defn center
  [window]
  (map #(/ % 2)
       (window-size window)))

;;;; Geometry helpers

(defn poly-lines
  "Construct the lines that make up a polygon from its points."
  [poly]
  (partition 2 1 (take (inc (count poly))
                       (cycle poly))))

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

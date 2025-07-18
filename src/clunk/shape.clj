(ns clunk.shape
  (:require [clojure.math :as math]
            [clunk.util :as u])
  (:import (org.lwjgl.opengl GL11)))

(def default-line-width 1)

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
     (when (and (u/left-turn? a b c)
                (not (some #(u/pos-in-tri? % [a b c])
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
    (if (seq p)
      (if-let [[i tri] (first (all-ears p))]
        (recur (u/remove-nth (mod (inc i) (count p)) p)
               (conj tris tri))
        (do (prn "FOUND NO EARS????")
            (prn p)
            tris))
      tris)))

(defn draw-line!
  [[x1 y1] [x2 y2] [r g b a] &
   {:keys [line-width]
    :or {line-width default-line-width}}]
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor4f r g b a)
  (GL11/glLineWidth line-width)
  (GL11/glBegin GL11/GL_LINES)
  (GL11/glVertex2f x1 y1)
  (GL11/glVertex2f x2 y2)
  (GL11/glEnd))

(defn draw-poly!
  [[x y] points [r g b a] &
   {:keys [line-width]
    :or {line-width default-line-width}}]
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor4f r g b a)
  (GL11/glLineWidth line-width)
  (GL11/glBegin GL11/GL_LINE_LOOP)
  (doseq [[bx by] points]
    (GL11/glVertex2f (+ x bx) (+ y by)))
  (GL11/glEnd))

(defn fill-poly!
  [[x y] points [r g b a]]
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor4f r g b a)
  (GL11/glBegin GL11/GL_POLYGON)
  (doseq [[bx by] points]
    (GL11/glVertex2f (+ x bx) (+ y by)))
  (GL11/glEnd))

(defn fill-concave-poly!
  [pos points color]
  (let [poly points]
    (doseq [t (triangulate poly)]
      (fill-poly! pos t color))))

(defn draw-rect!
  [pos [w h] color & opts]
  (draw-poly! pos
              [[0 0]
               [w 0]
               [w h]
               [0 h]]
              color
              opts))

(defn fill-rect!
  [[x y] [w h] [r g b a]]
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor4f r g b a)
  (GL11/glBegin GL11/GL_QUADS)
  (GL11/glVertex2f x y)
  (GL11/glVertex2f (+ x w) y)
  (GL11/glVertex2f (+ x w) (+ y h))
  (GL11/glVertex2f x (+ y h))
  (GL11/glEnd))

(defn ellipse-points
  [[w h] &
   {:keys [segments]
    :or {segments 32}}]
  (let [rx (/ w 2)
        ry (/ h 2)
        dr (/ (* 2 math/PI) segments)]
    (for [i (range segments)]
      (let [r (* i dr)]
        [(* rx (math/cos r))
         (* ry (math/sin r))]))))

(defn draw-ellipse!
  [pos size color & opts]
  (draw-poly! pos (ellipse-points size) color opts))

(defn fill-ellipse!
  [pos size color]
  (fill-poly! pos (ellipse-points size) color))

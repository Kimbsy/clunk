(ns clunk.shape
  (:require [clojure.math :as math]
            [clunk.util :as u])
  (:import (org.lwjgl.opengl GL11)))

(defn left-turn?
  [[ax ay] [bx by] [cx cy]]
  (let [v1 [(- bx ax) (- by ay) 0]
        v2 [(- cx bx) (- cy by) 0]]
    (>= 0 (last (u/cross v1 v2)))))

(defn pos-in-tri?
  [pos [a b c]]
  (every? true?
          (map (partial apply left-turn?)
               [[a b pos]
                [b c pos]
                [c a pos]])))

(defn draw-line!
  [[x1 y1] [x2 y2] [r g b a]]
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor4f r g b a)
  ;; @TODO: support line-width for all draw-<shape>! functions
  ;; (GL11/glLineWidth 2.0)
  (GL11/glBegin GL11/GL_LINES)
  (GL11/glVertex2f x1 y1)
  (GL11/glVertex2f x2 y2)
  (GL11/glEnd))

(defn draw-poly!
  [[x y] points [r g b a]]
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor4f r g b a)
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

(defn draw-rect!
  [pos [w h] color]
  (draw-poly! pos
              [[0 0]
               [w 0]
               [w h]
               [0 h]]
              color))

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
  [[w h] & {:keys [segments] :or {segments 32}}]
  (let [rx (/ w 2)
        ry (/ h 2)
        dr (/ (* 2 math/PI) segments)]
    (for [i (range segments)]
      (let [r (* i dr)]
        [(* rx (math/cos r))
         (* ry (math/sin r))]))))

(defn draw-ellipse!
  [pos size color]
  (draw-poly! pos (ellipse-points size) color))

(defn fill-ellipse!
  [pos size color]
  (fill-poly! pos (ellipse-points size) color))

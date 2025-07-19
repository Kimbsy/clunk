(ns clunk.shape
  (:require [clojure.math :as math]
            [clunk.util :as u])
  (:import (org.lwjgl.opengl GL11)))

(def default-line-width 1)

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
    (doseq [t (u/triangulate poly)]
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

(defn draw-ellipse!
  [pos size color & opts]
  (draw-poly! pos (u/ellipse-points size) color opts))

(defn fill-ellipse!
  [pos size color]
  (fill-poly! pos (u/ellipse-points size) color))

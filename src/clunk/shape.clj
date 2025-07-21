(ns clunk.shape
  "These side-effecting functions draw various shapes to the game
  window.

  Positions (`pos`) are `[x y]` vectors, so are
  dimensions (`size`).

  Polygons (`poly`) are a vector of `[x y]` point vectors which are
  relative to the `pos`.

  Colors are `[r g b a]` vectors."
  (:require [clojure.math :as math]
            [clunk.util :as u])
  (:import (org.lwjgl.opengl GL11)))

(def default-line-width 1)

(defn draw-lines!
  [lines [r g b a] &
   {:keys [line-width]
    :or {line-width default-line-width}}]
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor4f r g b a)
  (GL11/glLineWidth line-width)
  (GL11/glBegin GL11/GL_LINES)
  (doseq [[[p1x p1y] [p2x p2y]] lines]
    (GL11/glVertex2f p1x p1y)
    (GL11/glVertex2f p2x p2y))
  (GL11/glEnd))

(defn draw-line!
  [p1 p2 color & opts]
  (draw-lines! [[p1 p2]] color opts))

(defn draw-poly!
  [[x y] poly [r g b a] &
   {:keys [line-width]
    :or {line-width default-line-width}}]
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor4f r g b a)
  (GL11/glLineWidth line-width)
  (GL11/glBegin GL11/GL_LINE_LOOP)
  (doseq [[bx by] poly]
    (GL11/glVertex2f (+ x bx) (+ y by)))
  (GL11/glEnd))

(defn fill-convex-poly!
  "Draw a convex polygon filled with the specified colour."
  [[x y] poly [r g b a]]
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor4f r g b a)
  (GL11/glBegin GL11/GL_POLYGON)
  (doseq [[bx by] poly]
    (GL11/glVertex2f (+ x bx) (+ y by)))
  (GL11/glEnd))

(defn fill-concave-poly!
  "Draw a concave polygon filled with the specified colour."
  [pos poly color]
  (doseq [t (u/triangulate poly)]
    (fill-convex-poly! pos t color)))

(defn fill-poly!
  "Draw a polygon filled with the specified colour."
  [pos poly color]
  (if (u/convex? poly)
    (fill-convex-poly! pos poly color)
    (fill-concave-poly! pos poly color)))

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

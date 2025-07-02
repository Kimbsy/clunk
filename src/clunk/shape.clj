(ns clunk.shape
  (:import (org.lwjgl.opengl GL11)))

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

(ns clunk.graphics
  (:require [clunk.util :as u])
  (:import (org.lwjgl.nanovg NanoVG
                             NVGColor)
           (org.lwjgl.opengl GL11)))

(defn nvg-color
  "Convert RGBA floats to an NVGColor."
  [[r g b a]]
  (doto (NVGColor/create)
    (.r r)
    (.g g)
    (.b b)
    (.a a)))

(defn begin-frame!
  "Call once per frame before drawing anything."
  [{:keys [vg window]}]
  (let [[width height] (u/window-size window)]
    (NanoVG/nvgBeginFrame vg width height 1)))

(defn end-frame!
  "Call once per frame after drawing everything."
  [{:keys [vg]}]
  (NanoVG/nvgEndFrame vg))

(defn draw-background!
  "Clear the scene to an RGBA colour."
  [[r g b a]]
  (GL11/glClearColor r g b a)
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT))

(ns clunk.shape
  (:require [clojure.math :as math]
            [clunk.graphics :as g])
  (:import (org.lwjgl.nanovg NanoVG)))

(defn fill-rect!
  "Fill a rectangle."
  [vg [x y] [w h] color]
  (let [c (g/nvg-color color)]
    (NanoVG/nvgBeginPath vg)
    (NanoVG/nvgRect vg x y w h)
    (NanoVG/nvgFillColor vg c)
    (NanoVG/nvgFill vg)))

(defn draw-rect!
  "Stroke a rectangle."
  [vg [x y] [w h] color & {:keys [lw] :or {lw 1}}]
  (let [c (g/nvg-color color)]
    (NanoVG/nvgBeginPath vg)
    (NanoVG/nvgRect vg x y w h)
    (NanoVG/nvgStrokeColor vg c)
    (NanoVG/nvgStrokeWidth vg lw)
    (NanoVG/nvgStroke vg)))

(defn fill-poly!
  "Fill an arbitrary polygon where `points` are relative to `pos`."
  [vg [pos-x pos-y] points color]
  (let [c (g/nvg-color color)]
    (NanoVG/nvgBeginPath vg)
    (let [[x0 y0] (first points)]
      (NanoVG/nvgMoveTo vg (+ pos-x x0) (+ pos-y y0)))
    (doseq [[x y] (rest points)]
      (NanoVG/nvgLineTo vg (+ pos-x x) (+ pos-y y)))
    (NanoVG/nvgClosePath vg)
    (NanoVG/nvgFillColor vg c)
    (NanoVG/nvgFill vg)))

(defn draw-poly!
  "Stroke an arbitrary polygon where `points` are relative to `pos`."
  [vg [pos-x pos-y] points color & {:keys [lw] :or {lw 1}}]
  (let [c (g/nvg-color color)]
    (NanoVG/nvgBeginPath vg)
    (let [[x0 y0] (first points)]
      (NanoVG/nvgMoveTo vg (+ pos-x x0) (+ pos-y y0)))
    (doseq [[x y] (rest points)]
      (NanoVG/nvgLineTo vg (+ pos-x x) (+ pos-y y)))
    (NanoVG/nvgClosePath vg)
    (NanoVG/nvgStrokeColor vg c)
    (NanoVG/nvgStrokeWidth vg lw)
    (NanoVG/nvgStroke vg)))

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
  [vg pos size color]
  (draw-poly! vg pos (ellipse-points size) color))

(defn fill-ellipse!
  [vg pos size color]
  (fill-poly! vg pos (ellipse-points size) color))

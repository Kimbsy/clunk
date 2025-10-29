(ns clunk.scene
  (:require [clunk.core :as c]
            [clunk.shape :as shape]
            [clunk.util :as u])
  (:import (org.lwjgl.glfw GLFW)))

(defn fade-to-black
  [{:keys [window current-scene scenes] :as state}
   target
   i
   transition-length]
  (if (< i (/ transition-length 2))
    (do
      (c/draw-game! (assoc state :current-scene current-scene))
      (shape/fill-rect!
       state
       [0 0]
       (u/window-size window)
       [0 0 0 (float (/ (* 2 i) transition-length))])
      (GLFW/glfwSwapBuffers window))
    (do
      (c/draw-game! (assoc state :current-scene target))
      (shape/fill-rect!
       state
       [0 0]
       (u/window-size window)
       [0 0 0 (float (/ (* 2 (- transition-length i)) transition-length))])
      (GLFW/glfwSwapBuffers window))))

(defn transition
  [{:keys [transitioning?] :as state}
   target
   & {:keys [transition-fn
             transition-length
             init-fn]
      :or {transition-fn fade-to-black
           transition-length 20
           init-fn identity}}]
  (if transitioning?
    state
    (let [steps (range transition-length)]
      (doseq [i steps]
        (transition-fn state target i transition-length))
      (-> state
          (assoc :current-scene target)
          (assoc :transitioning? false)
          init-fn))))

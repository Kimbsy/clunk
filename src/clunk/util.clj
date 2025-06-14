(ns clunk.util
  (:import (org.lwjgl.glfw GLFW)
           (org.lwjgl.system MemoryStack)))

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

(defn hex->rgb
  ([hex-string]
   (hex->rgb hex-string 0))
  ([hex-string alpha]
   (let [s (if (= \# (first hex-string))
             (apply str (rest hex-string))
             hex-string)]
     (->> s
          (partition 2)
          (map (partial apply str "0x"))
          (map read-string)
          (map #(float (/ % 255)))
          vec
          (#(conj % alpha))))))

(ns minimal.text
  (:require [clojure.java.io :as io]
            [clojure.math :as math])
  (:import (org.lwjgl BufferUtils)
           (org.lwjgl.glfw GLFW)
           (org.lwjgl.nanovg NanoVG)
           (org.lwjgl.opengl GL11
                             GL14
                             GL15
                             GL30)
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

;; hack to stop font memory buffers from being freed
(defonce ^:private font-buffers (atom {}))

(defn create-font
  "Load a .ttf from a classpath resource and register it as `name` in
  the NanoVG context `vg`."
  [vg name classpath-resource]
  (with-open [is (io/input-stream (io/resource classpath-resource))]
    ;; read all bytes
    (let [ba (byte-array (.available is))]
      (.read is ba)
      ;; wrap into a direct ByteBuffer
      (let [buf (doto (BufferUtils/createByteBuffer (alength ba))
                  (.put ba)
                  (.flip))]
        ;; keep a reference so GC doesn't free it
        (swap! font-buffers assoc name buf)
        (NanoVG/nvgCreateFontMem vg name buf false)))))

(defn capture-gl-state
  "Capture the current blending state of GL so we can draw NanoVG
  text (which blats the config) before restoring the original state
  for drawing images/shapes."
  []
  {:src-rgb (GL11/glGetInteger GL30/GL_BLEND_SRC_RGB)
   :src-alpha (GL11/glGetInteger GL30/GL_BLEND_SRC_ALPHA)
   :dst-rgb (GL11/glGetInteger GL14/GL_BLEND_DST_RGB)
   :dst-alpha (GL11/glGetInteger GL11/GL_DST_ALPHA)
   :blend-enabled? (GL11/glIsEnabled GL11/GL_BLEND)
   :vao (GL30/glGetInteger GL30/GL_VERTEX_ARRAY_BINDING)
   :array-buffer (GL11/glGetInteger GL15/GL_ARRAY_BUFFER_BINDING)})

(defn restore-gl-state
  "Restore the original blending state of GL for drawing images/shapes."
  [{:keys [src-rgb src-alpha dst-rgb dst-alpha blend-enabled? vao array-buffer]}]
  (if blend-enabled?
    (GL11/glEnable GL11/GL_BLEND)
    (GL11/glDisable GL11/GL_BLEND))
  (GL30/glBlendFuncSeparate src-rgb dst-rgb src-alpha dst-alpha)
  (GL30/glBindVertexArray vao)
  (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER array-buffer))

(defn draw-text!
  [{:keys [window vg vg-color default-font] :as state}
   [x y]
   content
   & {:keys [font
             font-size
             color
             rotation]
      :or {font default-font
           font-size 32
           color [1 1 1 1]
           rotation 0}}]
  (let [[r g b a] (map float color)
        a (or a (float 1)) ;; default alpha
        [window-w window-h] (window-size window)
        old-state (capture-gl-state)]
    (NanoVG/nvgBeginFrame vg window-w window-h 1)
    (NanoVG/nvgFontSize vg font-size)
    (NanoVG/nvgFontFace vg font)
    (NanoVG/nvgFillColor vg (NanoVG/nvgRGBAf r g b a vg-color))

    (NanoVG/nvgSave vg)
    (NanoVG/nvgTranslate vg (float x) (float y))
    (when (and rotation
               (not (zero? (mod rotation 360))))
      (NanoVG/nvgRotate vg (math/to-radians rotation)))
    (NanoVG/nvgText vg (float 0) (float 0) content)
    (NanoVG/nvgRestore vg)

    (NanoVG/nvgEndFrame vg)
    (restore-gl-state old-state)))

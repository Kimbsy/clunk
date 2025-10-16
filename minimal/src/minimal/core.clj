(ns minimal.core
  (:require [clojure.java.io :as io])
  (:import (org.lwjgl BufferUtils)
           (org.lwjgl.glfw Callbacks
                           GLFW
                           GLFWCursorPosCallbackI
                           GLFWErrorCallback
                           GLFWFramebufferSizeCallbackI
                           GLFWKeyCallbackI
                           GLFWMouseButtonCallbackI)
           (org.lwjgl.nanovg NanoVG
                             NanoVGGL3
                             NVGColor)
           (org.lwjgl.opengl GL
                             GL11
                             GL14
                             GL30)
           (org.lwjgl.system MemoryStack)))

(defn capture-gl-state
  "Capture the current blending state of GL so we can draw NanoVG
  text (which blats the config) before restoring the original state
  for drawing images/shapes."
  []
  {:src-rgb (GL11/glGetInteger GL30/GL_BLEND_SRC_RGB)
   :src-alpha (GL11/glGetInteger GL30/GL_BLEND_SRC_ALPHA)
   :dst-rgb (GL11/glGetInteger GL14/GL_BLEND_DST_RGB)
   :dst-alpha (GL11/glGetInteger GL11/GL_DST_ALPHA)
   :blend-enabled? (GL11/glIsEnabled GL11/GL_BLEND)})

(defn restore-gl-state
  "Restore the original blending state of GL for drawing images/shapes."
  [{:keys [src-rgb src-alpha dst-rgb dst-alpha blend-enabled?]}]
  (if blend-enabled?
    (GL11/glEnable GL11/GL_BLEND)
    (GL11/glDisable GL11/GL_BLEND))
  (GL30/glBlendFuncSeparate src-rgb dst-rgb src-alpha dst-alpha))

(defn draw-text!
  [vg content font vg-color]
  (let [[r g b a] (map float [1 1 1 1])
        [window-w window-h] [800 600]
        old-state (capture-gl-state)]
    (NanoVG/nvgBeginFrame vg window-w window-h 1)
    (NanoVG/nvgFontSize vg 32)
    (NanoVG/nvgFontFace vg font)
    (NanoVG/nvgFillColor vg (NanoVG/nvgRGBAf r g b a vg-color))

    (NanoVG/nvgSave vg)
    (NanoVG/nvgTranslate vg (float 100) (float 300))
    (NanoVG/nvgText vg (float 0) (float 0) content)
    (NanoVG/nvgRestore vg)

    (NanoVG/nvgEndFrame vg)
    (restore-gl-state old-state)))

(defn create-font
  "Load a .ttf from a classpath resource and register it as `name` in
  the NanoVG context `vg`."
  [vg name path]
  (prn path)
  (prn (io/resource path))
  (with-open [is (io/input-stream (io/resource path))]
    ;; read all bytes
    (let [ba (byte-array (.available is))]
      (.read is ba)
      ;; wrap into a direct ByteBuffer
      (let [buf (doto (BufferUtils/createByteBuffer (alength ba))
                  (.put ba)
                  (.flip))]
        (NanoVG/nvgCreateFontMem vg name buf false)))))

(defn -main
  []
  ;; init error
  (.set (GLFWErrorCallback/createPrint System/err))

  (when (not (GLFW/glfwInit))
    (throw (IllegalStateException. "Unable to initialise GLFW")))

  ;; optional, the current window hints are already the default
  (GLFW/glfwDefaultWindowHints)

  ;; the window will stay hidden after creation
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  ;; the window will be resizable
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)

  (let [window (GLFW/glfwCreateWindow 800 600 "Hello, World!" 0 0)]
    (when (zero? window)
      (throw (IllegalStateException. "Unable to create the GLFW window")))

    ;; make the OpenGL context current
    (GLFW/glfwMakeContextCurrent window)
    ;; enable v-sync (set this to 0 to uncap framerate and run fast as possible)
    (GLFW/glfwSwapInterval 1)
    ;; make the window visible
    (GLFW/glfwShowWindow window)

    ;; this line is critical for LWJGL's interoperation with GLFW's
    ;; OpenGL context, or any context that is managed externally. LWJGL
    ;; detects the context that is current in the current thread,
    ;; creates the GLCapabilities instance and makes the OpenGL bindings
    ;; available for use
    (GL/createCapabilities)

    ;; reset orthographic projection
    (GL11/glMatrixMode GL11/GL_PROJECTION)
    (GL11/glLoadIdentity)
    ;; left, right, top, bottom in pixel units
    (GL11/glOrtho 0 800 600 0 -1 1)
    ;; go back to model view
    (GL11/glMatrixMode GL11/GL_MODELVIEW)
    (GL11/glLoadIdentity)

    ;; @TODO: testing text
    ;; create NanoVG context
    (let [vg (NanoVGGL3/nvgCreate (bit-or NanoVGGL3/NVG_ANTIALIAS
                                          NanoVGGL3/NVG_STENCIL_STROKES))
          ;; load a font
          font-name "UbuntuMono-Regular"
          font (create-font vg font-name "font/UbuntuMono-Regular.ttf")
          vg-color (NVGColor/create)]

      ;; enable transparency
      (GL11/glEnable GL11/GL_BLEND)
      (GL30/glBlendFuncSeparate GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA GL11/GL_ONE GL11/GL_ONE_MINUS_SRC_ALPHA)
      
      ;; loop
      (while (not (GLFW/glfwWindowShouldClose window))

        ;; poll for events
        (GLFW/glfwPollEvents)

        ;; draw background
        ;; set the clear colour
        (GL11/glClearColor 1.0 0.6 0.5 1)
        ;; clear the frameBuffer
        (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))

        ;; @TODO: testing draw text
        (draw-text! vg "Drawing text seems to work" font-name vg-color)

        ;; swap buffers
        (GLFW/glfwSwapBuffers window)))

    ;; free window callbacks and destroy the window
    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwDestroyWindow window))

  ;; terminate GLFW and terminate the error callback
  (GLFW/glfwTerminate)
  (-> (GLFW/glfwSetErrorCallback nil)
      .free))

(ns minimal.core
  (:require [minimal.image :as image])
  (:import (org.lwjgl.glfw Callbacks GLFW GLFWErrorCallback)
           (org.lwjgl.opengl GL GL11 GL15 GL30)))

(defn -main
  []
  ;; init error
  (.set (GLFWErrorCallback/createPrint System/err))

  (when (not (GLFW/glfwInit))
    (throw (IllegalStateException. "Unable to initialise GLFW")))

  ;; request OpenGL 3.2 core profile
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
  ;; required on macOS
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GLFW/GLFW_TRUE)

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

    ;; set viewport to the window size so NDC maps to pixels
    (GL11/glViewport 0 0 800 600)

    ;; enable transparency for image drawing
    (GL11/glEnable GL11/GL_BLEND)
    (GL30/glBlendFuncSeparate GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA GL11/GL_ONE GL11/GL_ONE_MINUS_SRC_ALPHA)

    ;; setting up primitive drawing
    (while (not (GLFW/glfwWindowShouldClose window))

      ;; poll for events
      (GLFW/glfwPollEvents)

      ;; draw background
      ;; set the clear colour
      (GL11/glClearColor 1.0 0.6 0.5 1)
      ;; clear the frameBuffer
      (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)

      ;; draw the image
      (let [texture (image/load-texture! nil "resources/img/captain.png")]
        (image/draw-sub-image! texture
                               [100 100] ; pos
                               [1680 1440] ; spritesheet dims
                               [0 0] ; offsets inside the spritesheet
                               [240 360]
                               (* 20 (GLFW/glfwGetTime))   ; rotation
                               [800 600] ; screen dims
                               )

        (image/draw-sub-image! texture
                               [400 100] ; pos
                               [1680 1440] ; spritesheet dims
                               [0 0] ; offsets inside the spritesheet
                               [240 360]
                               0 ; rotation
                               [800 600] ; screen dims
                               )
        )       

      ;; swap buffers to draw everything
      (GLFW/glfwSwapBuffers window))

    ;; free window callbacks and destroy the window
    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwDestroyWindow window))

  ;; terminate GLFW and terminate the error callback
  (GLFW/glfwTerminate)
  (-> (GLFW/glfwSetErrorCallback nil)
      .free))

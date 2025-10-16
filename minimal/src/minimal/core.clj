(ns minimal.core
  (:import (org.lwjgl.glfw Callbacks
                           GLFW
                           GLFWCursorPosCallbackI
                           GLFWErrorCallback
                           GLFWFramebufferSizeCallbackI
                           GLFWKeyCallbackI
                           GLFWMouseButtonCallbackI)
           (org.lwjgl.nanovg NanoVGGL3
                             NVGColor)
           (org.lwjgl.opengl GL
                             GL11
                             GL30)
           (org.lwjgl.system MemoryStack)))

(defn -main
  []
  ;; init error
  (.set (GLFWErrorCallback/createPrint System/err))

  (when (not (GLFW/glfwInit))
    (throw (IllegalStateException. "Unable to initialise GLFW")))

  ;; optional, the current window hints are already the default
  (GLFW/glfwDefaultWindowHints)

  ;; @TODO: testing
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

      ;; swap buffers
      (GLFW/glfwSwapBuffers window))

    ;; free window callbacks and destroy the window
    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwDestroyWindow window))

  ;; terminate GLFW and terminate the error callback
  (GLFW/glfwTerminate)
  (-> (GLFW/glfwSetErrorCallback nil)
      .free))

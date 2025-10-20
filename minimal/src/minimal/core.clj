(ns minimal.core
  (:require [clojure.java.io :as io]
            [clojure.math :as math]
            [minimal.shader :as shader]
            [minimal.shape :as shape])
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
                             GL15
                             GL20
                             GL30
                             GL40)
           (org.lwjgl.system MemoryStack)))

(defn hex->rgba
  ([hex-string]
   (hex->rgba hex-string 1))
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

    ;; setting up primitive drawing
    (let [position-size 3
          color-size 3
          vertex-size 6 ;; x,y,z,r,g,b
          ;; a Vertex Buffer Object (VBO) for holding the vertex data
          vbo (GL15/glGenBuffers)
          ;; a Vertex Array Object (VAO) for holding the attributes for the vbo
          vao (GL30/glGenVertexArrays)
          ;; an Element Array Buffer (EBO) for storing the indices of our vertices
          ebo (GL15/glGenBuffers)

          ;; define a rectangle using 4 vertices and 2 triangles
          vertices (float-array [;; top right
                                 0.5 0.5 0    1 0 0
                                 ;; bottom right
                                 0.5 -0.5 0   0 1 0
                                 ;; bottom left
                                 -0.5 -0.5 0  0 0 1
                                 ;; top left
                                 -0.5 0.5 0   1 1 0
                                 ])
          indices (int-array [0 1 3    ;; first tri
                              1 2 3])] ;; second tri

      ;; bind the vao, now everything following should be inside it
      (GL30/glBindVertexArray vao)

      ;; copy the vertex data into the vbo
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
      (GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices GL15/GL_STATIC_DRAW)

      ;; put the index array in the ebo for opengl to use
      (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER ebo)
      (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER indices GL15/GL_STATIC_DRAW)

      ;; set vertex attribute pointers
      (GL30/glVertexAttribPointer 0 ;; attribute at location 0 in the shader is position
                                  position-size ;; position is 3 bytes (xyz)
                                  GL15/GL_FLOAT
                                  false
                                  (* vertex-size (Float/BYTES))
                                  0) ;; offset 0 since xyz is at the start of each vertex section
      ;; enable the vertex attribute
      (GL30/glEnableVertexAttribArray 0) ;; location 0

      (GL30/glVertexAttribPointer 1 ;; attribute at location 1 in the shader is color
                                  color-size ;; color is 3 bytes (rgb)
                                  GL15/GL_FLOAT
                                  false
                                  (* vertex-size (Float/BYTES))
                                  (* position-size (Float/BYTES))) ;; offset 3 since rgb comes after xyz
      ;; enable the vertex attribute
      (GL30/glEnableVertexAttribArray 1) ;; location 1

      ;; set up a shader program (a vertex shader and a fragment shader)
      (let [shader-program (shader/program
                            "shader/basic.vert"
                            "shader/basic.frag")]
        ;; LOOP ;;
        (while (not (GLFW/glfwWindowShouldClose window))

          ;; poll for events
          (GLFW/glfwPollEvents)

          ;; draw background
          ;; set the clear colour
          (GL11/glClearColor 1.0 0.6 0.5 1)
          ;; clear the frameBuffer
          (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)



          ;; draw the shape ;;

          ;; everything after this will use our shaders
          (shader/use-program shader-program)

          ;; bind our VAO
          (GL30/glBindVertexArray vao)

          ;; uncomment to draw wireframe rather than fill
          ;; (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)

          ;; draw the triangles
          (GL40/glDrawElements GL40/GL_TRIANGLES 6 GL11/GL_UNSIGNED_INT 0)

          ;; unbind the VAO
          (GL30/glBindVertexArray 0)


          (shape/fill-poly [0 0]
                           [[0.475 0.155]
                            [0.295 -0.405]
                            [-0.295 -0.405]
                            [-0.475 0.155]
                            [0.0 0.5]]
                           [0.1254902 0.5411765 0.68235296 1])


          

          ;; swap buffers to draw everything
          (GLFW/glfwSwapBuffers window))))

    ;; free window callbacks and destroy the window
    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwDestroyWindow window))

  ;; terminate GLFW and terminate the error callback
  (GLFW/glfwTerminate)
  (-> (GLFW/glfwSetErrorCallback nil)
      .free))

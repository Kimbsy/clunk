(ns minimal.core
  (:require [clojure.java.io :as io]
            [clojure.math :as math])
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
    (let [vertex-size 3 ;; x,y,z (even though z will always be 0)
          ;; a Vertex Buffer Object (VBO) for holding the vertex data
          vbo (GL15/glGenBuffers)
          ;; a Vertex Array Object (VAO) for holding the attributes for the vbo
          vao (GL30/glGenVertexArrays)
          ;; an Element Array Buffer (EBO) for storing the indices of our vertices
          ebo (GL15/glGenBuffers)

          ;; define a rectangle using 4 vertices and 2 triangles
          vertices (float-array [ 0.5  0.5 0   ;; top right
                                  0.5 -0.5 0   ;; bottom right
                                 -0.5 -0.5 0   ;; bottom left
                                 -0.5  0.5 0]) ;; top left
          indices (int-array [0 1 3    ;; first tri
                              1 2 3])] ;; second tri

      ;; bind the vao, now everything following should be inside it
      (GL30/glBindVertexArray vao)

      ;; copy the vertex data into the vbo
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
      (GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices GL15/GL_STATIC_DRAW)

      ;; put the index aray in an element buffer for opengl to use
      (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER ebo)
      (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER indices GL15/GL_STATIC_DRAW)

      ;; set vertex attribute pointers
      (GL30/glVertexAttribPointer 0 ;; attribute at location 0 in the shader
                                  vertex-size
                                  GL15/GL_FLOAT
                                  false
                                  (* vertex-size (Float/BYTES))
                                  0)
      ;; enable the vertex attribute
      (GL30/glEnableVertexAttribArray 0) ;; location 0

      ;; set up a shader program (a vertex shader and a fragment shader)
      (let [vert-shader-source (slurp (io/resource "shader/basic.vert.glsl"))
            frag-shader-source (slurp (io/resource "shader/basic.frag.glsl"))
            vert-shader (GL20/glCreateShader GL20/GL_VERTEX_SHADER)
            frag-shader (GL20/glCreateShader GL20/GL_FRAGMENT_SHADER)]

        ;; set the source for the vertex shader and compile it
        (GL20/glShaderSource vert-shader vert-shader-source)
        (GL20/glCompileShader vert-shader)

        ;; set the source for the fragment shader and compile it
        (GL20/glShaderSource frag-shader frag-shader-source)
        (GL20/glCompileShader frag-shader)


        ;; @TODO: check that the shader compilation has worked?


        

        ;; create a shader program
        (let [shader-program (GL20/glCreateProgram)]

          ;; attach the shaders and then link the program
          (GL20/glAttachShader shader-program vert-shader)
          (GL20/glAttachShader shader-program frag-shader)
          (GL20/glLinkProgram shader-program)

          ;; we don't need the shaders anymore, they're linked in the program now
          (GL20/glDeleteShader vert-shader)
          (GL20/glDeleteShader frag-shader)

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
            (GL20/glUseProgram shader-program)

            ;; set the uniform for the shape colour
            (let [time-value (GLFW/glfwGetTime)
                  g (+ 0.5 (/ (math/sin time-value)
                              2))
                  ;; grab the locatoin of the uniform attribute from the linked shader program
                  vertex-color-location (GL20/glGetUniformLocation shader-program "color")]
              ;; set the uniform value
              (GL20/glUniform4f vertex-color-location 0 g 0 1))

            ;; bind our VAO
            (GL30/glBindVertexArray vao)

            ;; uncomment to draw wireframe rather than fill
            ;; (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)

            ;; draw the triangles
            (GL40/glDrawElements GL40/GL_TRIANGLES 6 GL11/GL_UNSIGNED_INT 0)

            ;; unbind the VAO
            (GL30/glBindVertexArray 0)



            

            ;; swap buffers to draw everything
            (GLFW/glfwSwapBuffers window)))))

    ;; free window callbacks and destroy the window
    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwDestroyWindow window))

  ;; terminate GLFW and terminate the error callback
  (GLFW/glfwTerminate)
  (-> (GLFW/glfwSetErrorCallback nil)
      .free))

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
           (org.lwjgl.stb STBImage)
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

(defn load-texture!
  ;; @TODO: texture key not needed
  [#_texture-key path]
  ;; generate a texture id and bind it to the 2d texture target
  (let [tex-id (GL11/glGenTextures)]
    (GL11/glBindTexture GL11/GL_TEXTURE_2D tex-id)

    ;; set filtering
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST_MIPMAP_NEAREST)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST)
    ;; clamp edges
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL30/GL_CLAMP_TO_EDGE)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL30/GL_CLAMP_TO_EDGE)
    
    ;; prepare buffers for width and height info
    (with-open [stack (MemoryStack/stackPush)]
      (let [p-w (.mallocInt stack 1)
            p-h (.mallocInt stack 1)
            cmp (.mallocInt stack 1)]
        ;; tell STB to flip images on load if png origin differs
        (STBImage/stbi_set_flip_vertically_on_load false)

        ;; load the image (force 4 channel RGBA), we're not using
        ;; `cmp` (normally called `comp`) it grabs the number of
        ;; channels (components) actually found in the original image.
        (let [image (STBImage/stbi_load path p-w p-h cmp 4)]
          (when-not image
            (throw (RuntimeException.
                    (str "Failed to load image '" path "': "
                         (STBImage/stbi_failure_reason)))))          
          ;; upload to GPU
          (GL11/glTexImage2D GL11/GL_TEXTURE_2D
                             0
                             GL11/GL_RGBA8
                             (.get p-w 0)
                             (.get p-h 0)
                             0
                             GL11/GL_RGBA
                             GL11/GL_UNSIGNED_BYTE
                             image)
          ;; generate mipmaps
          (GL30/glGenerateMipmap GL11/GL_TEXTURE_2D)
          
          ;; cleanup
          (STBImage/stbi_image_free image)

          ;; @TODO: clunk uses an atom to store textures, will we still need this?
          ;; ;; Add the texture id to the textures atom
          ;; (swap! textures assoc texture-key tex-id)

          tex-id)))))

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

    ;; enable transparency for image drawing
    (GL11/glEnable GL11/GL_BLEND)
    (GL30/glBlendFuncSeparate GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA GL11/GL_ONE GL11/GL_ONE_MINUS_SRC_ALPHA)

    ;; setting up primitive drawing
    (let [position-size 3
          color-size 3
          tex-coord-size 2
          vertex-size 8 ;; x,y,z,r,g,b,tx,ty
          ;; a Vertex Buffer Object (VBO) for holding the vertex data
          vbo (GL15/glGenBuffers)
          ;; a Vertex Array Object (VAO) for holding the attributes for the vbo
          vao (GL30/glGenVertexArrays)
          ;; an Element Array Buffer (EBO) for storing the indices of our vertices
          ebo (GL15/glGenBuffers)

          ;; @TODO: we only need a 2 byte attribute for position since we'll always be 2d
          ;; define a rectangle using 4 vertices and 2 triangles
          ;; each vertex has a position, a colour, and a texture coordinate
          vertices (float-array [;; top right
                                 0.5 1 0 ,, 1 0 0 ,, 1/7 0.25
                                 ;; bottom right
                                 0.5 -0.5 0 ,, 0 1 0 ,, 1/7 0.5
                                 ;; bottom left
                                 -0.5 -0.5 0 ,, 0 0 1 ,, 0 0.5
                                 ;; top left
                                 -0.5 1 0 ,, 1 1 0 ,, 0 0.25
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
      (GL30/glEnableVertexAttribArray 0)

      (GL30/glVertexAttribPointer 1 ;; attribute at location 1 in the shader is color
                                  color-size ;; color is 3 bytes (rgb)
                                  GL15/GL_FLOAT
                                  false
                                  (* vertex-size (Float/BYTES))
                                  (* position-size (Float/BYTES))) ;; offset 3 since rgb comes after xyz
      (GL30/glEnableVertexAttribArray 1)

      (GL30/glVertexAttribPointer 2 ;; attribute at location 2 in the shader is tex-coord
                                  tex-coord-size
                                  GL15/GL_FLOAT
                                  false
                                  (* vertex-size (Float/BYTES))
                                  (* (+ position-size color-size) (Float/BYTES))) ;; offset 6 since tx,ty comes after xyz,rgb
      (GL30/glEnableVertexAttribArray 2)

      ;; set up a shader program (a vertex shader and a fragment shader)
      (let [shader-program (shader/program
                            "shader/texture.vert"
                            "shader/texture.frag")
            texture (load-texture! "resources/img/captain.png")]

        ;; @TODO: we should specify the animation x and y as uniforms
        
        ;; LOOP ;;
        (while (not (GLFW/glfwWindowShouldClose window))

          ;; poll for events
          (GLFW/glfwPollEvents)

          ;; draw background
          ;; set the clear colour
          (GL11/glClearColor 1.0 0.6 0.5 1)
          ;; clear the frameBuffer
          (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)



          ;; now we can draw using our drop-in fill-poly replacement
          (shape/fill-poly! [0 0]
                            [[0 -0.345]
                             [-0.2 -0.905]
                             [-0.8 -0.905]
                             [-0.975 -0.345]
                             [-0.5 0.0]]
                            [0.13 0.54 0.68 1])
          

          ;; draw the shape ;;

          ;; everything after this will use our shaders
          (shader/use-program shader-program)

          ;; bind the texture to draw
          (GL30/glBindTexture GL30/GL_TEXTURE_2D texture)

          ;; bind our VAO
          (GL30/glBindVertexArray vao)

          ;; uncomment to draw wireframe rather than fill
          ;; (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)

          ;; draw the triangles
          (GL40/glDrawElements GL40/GL_TRIANGLES 6 GL11/GL_UNSIGNED_INT 0)

          ;; unbind the VAO
          (GL30/glBindVertexArray 0)


          


          


          

          ;; swap buffers to draw everything
          (GLFW/glfwSwapBuffers window))))

    ;; free window callbacks and destroy the window
    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwDestroyWindow window))

  ;; terminate GLFW and terminate the error callback
  (GLFW/glfwTerminate)
  (-> (GLFW/glfwSetErrorCallback nil)
      .free))

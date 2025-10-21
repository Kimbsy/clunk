(ns minimal.image
  (:require [minimal.shader :as shader])
  (:import (org.joml Matrix4f)
           (org.lwjgl.opengl GL11 GL15 GL20 GL30 GL40)
           (org.lwjgl.stb STBImage)
           (org.lwjgl.system MemoryStack)))

(defn load-texture!
  ;; @TODO: texture key not needed 
  [texture-key path]
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




(defn draw-bound-texture-quad
  ;; draw the whole image
  ([pos parent-dims rotation screen-dims]
   (draw-bound-texture-quad pos parent-dims [0 0] parent-dims rotation screen-dims))
  ;; draw a subsection of the image
  ([[pos-x pos-y] [parent-w parent-h] [off-x off-y] [draw-w draw-h] rotation screen-dims]

   (let [position-size 3
         tex-coord-size 2
         vertex-size 5 ;; x,y,z,tx,ty
         ;; a Vertex Buffer Object (VBO) for holding the vertex data
         vbo (GL15/glGenBuffers)
         ;; a Vertex Array Object (VAO) for holding the attributes for the vbo
         vao (GL30/glGenVertexArrays)
         ;; an Element Array Buffer (EBO) for storing the indices of our vertices
         ebo (GL15/glGenBuffers)

         ;; @TODO: calculate the vertices properly, surely we need to know the window size to do this?

         ;; @TODO: do we only need a 2 byte attribute for position since we'll always be 2d
         ;; define a rectangle using 4 vertices and 2 triangles
         ;; each vertex has a position and a texture coordinate
         x0 pos-x
         x1 (+ pos-x draw-w)
         y0 pos-y
         y1 (+ pos-y draw-h)
         tx0 (/ off-x parent-w)
         ty0 (/ off-y parent-h)
         tx1 (/ (+ off-x draw-w) parent-w)
         ty1 (/ (+ off-y draw-h) parent-h)
         vertices (float-array [;; top right
                                x1 y1 0 ,, tx1 ty1
                                ;; bottom right
                                x1 y0 0 ,, tx1 ty0
                                ;; bottom left
                                x0 y0 0 ,, tx0 ty0
                                ;; top left
                                x0 y1 0 ,, tx0 ty1
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
                                 tex-coord-size ;; tex-coord is 2 bytes (tx ty)
                                 GL15/GL_FLOAT
                                 false
                                 (* vertex-size (Float/BYTES))
                                 (* position-size (Float/BYTES))) ;; offset 3 since this comes after xyz
     (GL30/glEnableVertexAttribArray 1)

     ;; @TODO: we should compile common shaders when the game starts
     ;; set up a shader program (a vertex shader and a fragment shader)
     (let [shader-program (shader/program
                           "shader/texture.vert"
                           "shader/texture.frag")]

       ;; draw the image ;;

       ;; everything after this will use our shaders
       (shader/use-program shader-program)

       ;; @TODO: figure out how/where to inject the ortho projection
       ;; attach the orthographic projection matrix as a uniform
       ;; compute & upload an orthographic projection matrix
       (let [[screen-w screen-h] screen-dims
             proj (doto (Matrix4f.) (.setOrtho2D 0 screen-w screen-h 0))]
         (with-open [stack (MemoryStack/stackPush)]
           (let [buf (.mallocFloat stack 16)
                 loc (GL20/glGetUniformLocation shader-program "uOrthoProjection")]
             (.get proj buf)
             (GL20/glUniformMatrix4fv loc false buf))))


       ;; draw the triangles
       (GL40/glDrawElements GL40/GL_TRIANGLES 6 GL11/GL_UNSIGNED_INT 0)

       ;; unbind the VAO
       (GL30/glBindVertexArray 0)))))




(defn draw-image!
  [texture pos image-dims rotation screen-dims]
  ;; @TODO: if we need to draw the same image multiple times we should
  ;; only bind the texture once.
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
  (draw-bound-texture-quad pos image-dims rotation screen-dims))

(defn draw-sub-image!
  [texture pos parent-dims offsets draw-dims rotation screen-dims]
  ;; @TODO: if we need to draw the same image multiple times we should
  ;; only bind the texture once.
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
  (draw-bound-texture-quad pos parent-dims offsets draw-dims rotation screen-dims))

(ns custom-shaders.scenes.level-01
  (:require [clojure.math :as math]
            [clunk.core :as c]
            [clunk.palette :as p]
            [clunk.shader :as shader]
            [clunk.sprite :as sprite]
            [clunk.util :as u])
  (:import (org.joml Matrix4f)
           (org.lwjgl.opengl GL11 GL13 GL15 GL20 GL30 GL40)
           (org.lwjgl.system MemoryStack)))

(def coral-pink (p/hex->rgba "#FF9B85"))

(defn present
  [pos]
  (sprite/image-sprite
   :present 
   pos
   [322 346]
   :big-present))

;; Let's write a shader which makes an image shiny (rainbow holographic effect thingy)

;; 1. create a shader program (do it during init, and stick it in the state)
;; 2. use the program
;; 3. bind the image texture
;; 4. create the vertices
;; 5. create the indices
;; 6. set the vertex attribute pointers
;; 7. draw the image

(defn draw-shiny-image!
  [{:keys [ortho-projection] :as state}
   {:keys [pos size image-texture rotation scale]
    [w h :as size] :size
    :as s}]

  (let [[draw-w draw-h] size
        offsets (sprite/pos-offsets s)

        ;; compile the shader program (don't do this every frame!)
        texture-program (shader/program "shader/texture.vert" "shader/texture.frag")]

    ;; use the compiled shader program
    (shader/use-program texture-program)

    ;; set any custom uniforms here (none for our colour though, it's vertex data)
    ;; for a single static colour you could:

    ;; (GL20/glUniform4fv
    ;;  (GL20/glGetUniformLocation p "uColor")
    ;;  (float-array color))
       
    ;; bind the image texture
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D image-texture)

    ;; define the vertices and indices.
    (let [position-size 3
          tex-coord-size 2
          colour-size 3
          vertex-size 8 ;; x,y,z,tx,ty,r,g,b

          ;; define a rectangle using 4 vertices, each vertex has a
          ;; position a texture coordinate and a colour
          vertices (float-array [;; bottom right
                                 1 1 0 ,, 1 1 ,, 0 1 0
                                 ;; top right
                                 1 0 0 ,, 1 0 ,, 1 0 0
                                 ;; top left
                                 0 0 0 ,, 0 0 ,, 1 1 0
                                 ;; bottom left
                                 0 1 0 ,, 0 1 ,, 0 0 1
                                 ])

          ;; we're drawing a rectangle using two triangles, so instead
          ;; of specifying 6 vertices (with two duplicates) we define
          ;; 4 vertices and 6 indices (the order to use/reuse the
          ;; vertices)
          indices (int-array [0 1 3    ;; first tri
                              1 2 3])] ;; second tri

      ;; bind the vao, now everything following should be inside it
      (GL30/glBindVertexArray (GL30/glGenVertexArrays))

      ;; copy the vertex data into the vbo
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER (GL15/glGenBuffers))
      (GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices GL15/GL_STATIC_DRAW)

      ;; put the index array in the ebo for opengl to use
      (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER (GL15/glGenBuffers))
      (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER indices GL15/GL_STATIC_DRAW)

      ;; set vertex attribute pointers
      (GL30/glVertexAttribPointer 0 ;; attribute at location 0 in the shader is position
                                  position-size ;; position is 3 bytes (x, y, z)
                                  GL15/GL_FLOAT
                                  false
                                  (* vertex-size (Float/BYTES))
                                  0) ;; offset 0 since xyz is at the start of each vertex section
      (GL30/glEnableVertexAttribArray 0)

      (GL30/glVertexAttribPointer 1 ;; attribute at location 1 in the shader is the texture coordinate
                                  tex-coord-size ;; tex-coord is 2 bytes (tx, ty)
                                  GL15/GL_FLOAT
                                  false
                                  (* vertex-size (Float/BYTES))
                                  (* position-size (Float/BYTES))) ;; this comes after x,y,z
      (GL30/glEnableVertexAttribArray 1)

      (GL30/glVertexAttribPointer 2 ;; attribute at location 2 in the shader is the shiny colour
                                  colour-size ;; colour is 3 bytes (r, g, b)
                                  GL15/GL_FLOAT
                                  false
                                  (* vertex-size (Float/BYTES))
                                  (* (+ position-size tex-coord-size) (Float/BYTES))) ;; this comes after x,y,z,tx,ty
      (GL30/glEnableVertexAttribArray 2)

      ;; draw the image ;;

      (let [[x y] (map + pos offsets)
            [x-scale y-scale] scale
            ;; the model transformation matrix handles translation,
            ;; rotation and scaling
            model (doto (Matrix4f.)
                    (.identity)
                    (.translate x y 0)
                    (.translate (/ draw-w 2) (/ draw-h 2) 0)
                    (.rotate (math/to-radians rotation) 0 0 1)
                    (.translate (- (/ draw-w 2)) (- (/ draw-h 2)) 0)
                    (.scale (* draw-w x-scale) (* draw-h y-scale) 1))]
        (with-open [stack (MemoryStack/stackPush)]
          (let [proj-buf (.mallocFloat stack 16)
                model-buf (.mallocFloat stack 16)
                proj-loc (GL20/glGetUniformLocation texture-program "uOrthoProjection")
                model-loc (GL20/glGetUniformLocation texture-program "uModel")]
            (.get ortho-projection proj-buf)
            (.get model model-buf)
            ;; attach the orthographic projection matrix and the model matrix as uniforms
            (GL20/glUniformMatrix4fv proj-loc false proj-buf)
            (GL20/glUniformMatrix4fv model-loc false model-buf))))

      ;; render the triangles with the texture
      (GL40/glDrawElements GL40/GL_TRIANGLES 6 GL11/GL_UNSIGNED_INT 0)

      ;; unbind the VAO
      (GL30/glBindVertexArray 0))))

(defn shiny-present
  [pos]
  (assoc (present pos)
         :draw-fn draw-shiny-image!))

(defn draw-level-01!
  "Called each frame, draws the current scene to the screen"
  [state]
  (c/draw-background! coral-pink)
  (sprite/draw-scene-sprites! state))

(defn update-level-01
  "Called each frame, update the sprites in the current scene"
  [state]
  (-> state
      sprite/update-state))

(defn init
  "Initialise this scene"
  [state]
  {:sprites [(present (u/window-pos (:window state) [1/4 0.5]))
             (shiny-present (u/window-pos (:window state) [3/4 0.5]))]
   :draw-fn draw-level-01!
   :update-fn update-level-01})

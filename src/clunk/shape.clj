(ns clunk.shape
  "These side-effecting functions draw various shapes to the game
  window.

  Positions (`pos`) are `[x y]` vectors, so are
  dimensions (`size`).

  Polygons (`poly`) are a vector of `[x y]` point vectors which are
  relative to the `pos`.

  Colors are `[r g b a]` vectors."
  (:require [clunk.util :as u]
            [clunk.shader :as shader]
            [clojure.math :as math])
  (:import (org.joml Matrix4f)
           (org.lwjgl.opengl GL11
                             GL15
                             GL20
                             GL30
                             GL40)
           (org.lwjgl.system MemoryStack)))

(def default-line-width 1)

;; @TODO: would be nice to use an EBO to store the triangle indices and save a bunch of duplicated shared vertices
(defn render-vertices!
  [{:keys [ortho-projection shader-programs]
    {solid-color-program :solid-color} :shader-programs}
   [x y] vertices color primitive-mode]
  (let [position-size 3
        vertex-size 3 ;; x,y,z
        ;; a Vertex Buffer Object (VBO) for holding the vertex data
        vbo (GL15/glGenBuffers)
        ;; a Vertex Array Object (VAO) for holding the attributes for the vbo
        vao (GL30/glGenVertexArrays)]

    ;; @TODO: maybe eventually the vao will be passed in (or grabbed form the sprite)
    ;; bind the vao, now everything following should be inside it
    (GL30/glBindVertexArray vao)

    ;; copy the vertex data into the vbo
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
    (GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices GL15/GL_STATIC_DRAW)

    ;; set vertex attribute pointers
    (GL30/glVertexAttribPointer 0 ;; attribute at location 0 in the shader is position
                                position-size ;; position is 3 bytes (xyz)
                                GL15/GL_FLOAT
                                false
                                (* vertex-size (Float/BYTES))
                                0) ;; offset 0 since xyz is at the start of each vertex section
    ;; enable the vertex attribute
    (GL30/glEnableVertexAttribArray 0) ;; location 0

    ;; draw the shape ;;

    ;; everything after this will use our solid color shader
    (shader/use-program solid-color-program)
    (GL20/glUniform4fv
     (GL20/glGetUniformLocation solid-color-program "color")
     (float-array color))

    ;; the model transformation matrix handles translation
    (let [model (doto (Matrix4f.)
                  (.identity)
                  (.translate x y 0))]
      (with-open [stack (MemoryStack/stackPush)]
        (let [proj-buf (.mallocFloat stack 16)
              model-buf (.mallocFloat stack 16)
              proj-loc (GL20/glGetUniformLocation solid-color-program "uOrthoProjection")
              model-loc (GL20/glGetUniformLocation solid-color-program "uModel")]
          (.get ortho-projection proj-buf)
          (.get model model-buf)
          ;; attach the orthographic projection matrix and the model matrix as uniforms
          (GL20/glUniformMatrix4fv proj-loc false proj-buf)
          (GL20/glUniformMatrix4fv model-loc false model-buf))))

    ;; draw the primitives
    (GL40/glDrawArrays primitive-mode 0 (count vertices))

    ;; unbind the VAO
    (GL30/glBindVertexArray 0)))

;; @TODO: supporting line width is not trivial, might need a geometry shader, or to render each line as a quad
(defn draw-lines!
  [state lines color &
   {:keys [line-width]
    :or {line-width default-line-width}}]
  ;; add z=0 to the lines and stick all points in a flat float array
  (let [vertices (->> lines
                      (apply concat)
                      (map #(conj % 0))
                      (apply concat)
                      float-array)]
    ;; lines are defined with absolute coords, so our model uniform translation should be the identity transform
    (render-vertices! state [0 0] vertices color GL40/GL_LINES)))

(defn draw-line!
  [state p1 p2 color & opts]
  (apply draw-lines! state [[p1 p2]] color opts))

(defn draw-curve!
  "Takes a sequence of points and connects them with lines.

  See the `clunk.util/bezier-curve` function."
  [state points color & opts]
  (apply draw-lines! state (partition 2 1 points) color opts))

(defn draw-poly!
  [state pos poly color & opts]
  ;; convert the poly points into lines describing the perimeter
  (let [lines (->> poly
                   ;; make all positions absolute
                   (mapv #(mapv + pos %))
                   cycle
                   (take (inc (count poly)))
                   (partition 2 1))]
    (apply draw-lines! state lines color opts)))

(defn fill-poly!
  "Draw a polygon filled with the specified colour."
  [state pos poly color]
  ;; triangulate the polygon and stick all points in a flat float array
  (let [tris (u/triangulate poly)
        vertices (->> tris
                      (apply concat)
                      (map #(conj % 0))
                      (apply concat)
                      float-array)]
    (render-vertices! state pos vertices color GL40/GL_TRIANGLES)))

(defn draw-rect!
  [state pos [w h] color & opts]
  (apply draw-poly!
         state
         pos
         [[0 0]
          [0 h]
          [w h]
          [w 0]]
         color
         opts))

(defn fill-rect!
  [state pos [w h] color]
  (fill-poly! state
              pos
              [[0 0]
               [0 h]
               [w h]
               [w 0]]
              color))

(defn draw-ellipse!
  [state pos size color & opts]
  (apply draw-poly! state pos (u/ellipse-points size) color opts))

(defn fill-ellipse!
  [state pos size color]
  (fill-poly! state pos (u/ellipse-points size) color))

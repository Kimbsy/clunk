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

;; @TODO: pass in a shader program to the render function, that way we can upload uniforms beforehand?

;; write a shader for drawing lines with thickness

;; @TODO: would be nice to use an EBO to store the triangle indices and save a bunch of duplicated shared vertices
(defn render-vertices!
  [{:keys [ortho-projection]}
   [x y] vertices color primitive-mode shader-program]
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

    ;; @TODO: support rotation and scale
    ;; the model transformation matrix handles translation
    (let [model (doto (Matrix4f.)
                  (.identity)
                  (.translate x y 0))]
      (with-open [stack (MemoryStack/stackPush)]
        ;; check if our shader program needs a ortho projection uniform
        (let [proj-loc (GL20/glGetUniformLocation shader-program "uOrthoProjection")]
          (when (not= proj-loc -1)
            (let [proj-buf (.mallocFloat stack 16)]
              ;; load the projection matrix into the buffer
              (.get ortho-projection proj-buf)
              ;; upload to the shader program
              (GL20/glUniformMatrix4fv proj-loc false proj-buf))))
        ;; check if our shader program needs a model uniform
        (let [model-loc (GL20/glGetUniformLocation shader-program "uModel")]
          (when (not= model-loc -1)
            (let [model-buf (.mallocFloat stack 16)]
              ;; load the model matrix into the buffer
              (.get model model-buf)
              ;; upload to the shader program
              (GL20/glUniformMatrix4fv model-loc false model-buf))))))

    ;; draw the primitives
    (GL40/glDrawArrays primitive-mode 0 (count vertices))

    ;; unbind the VAO
    (GL30/glBindVertexArray 0)))

;; @TODO: THIS ONE NEXT
;; @TODO: supporting line width is not trivial, might need a geometry shader, or to render each line as a quad
(defn draw-lines!
  [{:keys [shader-programs] :as state}
   lines color &
   {:keys [line-width]
    :or {line-width default-line-width}}]
  ;; add z=0 to the lines and stick all points in a flat float array
  (let [vertices (->> lines
                      (apply concat)
                      (map #(conj % 0))
                      (apply concat)
                      float-array)
        ;; activate shader, upload colour and line-width uniforms
        line-shader (shader/use-line-shader state color line-width)]
    ;; lines are defined with absolute coords, so our model uniform translation should be the identity transform
    (render-vertices! state [0 0] vertices color GL11/GL_LINES line-shader)))

(defn draw-line!
  [state p1 p2 color & opts]
  (apply draw-lines! state [[p1 p2]] color opts))

(defn draw-curve!
  "Takes a sequence of points and connects them with lines.

  See the `clunk.util/bezier-curve` function."
  [state points color & opts]
  (apply draw-lines! state (partition 2 1 points) color opts))

(defn draw-curves!
  [state curves color & opts]
  (let [lines (mapcat #(partition 2 1 %) curves)]
    (apply draw-lines! state lines color opts)))

(defn draw-poly!
  [state pos poly color & opts]
  ;; convert the poly points into lines describing the perimeter
  (let [lines (->> poly
                   ;; make all positions absolute
                   (mapv (partial mapv + pos))
                   u/poly-lines)]
    (apply draw-lines! state lines color opts)))

(defn draw-polys!
  [state poly-data color & opts]
  (let [lines (mapcat (fn [[pos poly]]
                        (->> poly
                             ;; make all positions absolute
                             (mapv (partial mapv + pos))
                             u/poly-lines))
                      poly-data)]
    (apply draw-lines! state lines color opts)))

(defn fill-poly!
  "Draw a polygon filled with the specified colour."
  [{:keys [shader-programs] :as state}
   pos poly color]
  ;; triangulate the polygon and stick all points in a flat float array
  (let [tris (u/triangulate poly)
        vertices (->> tris
                      (apply concat)
                      (map #(conj % 0))
                      (apply concat)
                      float-array)
        ;; activate shader, upload color uniform
        solid-poly-shader (shader/use-solid-poly-shader state color)]
    (render-vertices! state pos vertices color GL40/GL_TRIANGLES solid-poly-shader)))

(defn fill-polys!
  "Draw a collection of polygons filled with the same color."
  [{:keys [shader-programs] :as state}
   poly-data color]
  (let [tris (mapcat (fn [[pos poly]]
                       (u/triangulate (mapv (partial mapv + pos) poly)))
                     poly-data)
        vertices (->> tris
                      (apply concat)
                      (map #(conj % 0))
                      (apply concat)
                      float-array)
        ;; activate shader, upload color uniform
        solid-poly-shader (shader/use-solid-poly-shader state color)]
    (render-vertices! state [0 0] vertices color GL40/GL_TRIANGLES solid-poly-shader)))

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

(defn draw-rects!
  [state rect-data color & opts]
  (let [poly-data (mapv (fn [[pos [w h]]]
                          [pos
                           [[0 0]
                            [0 h]
                            [w h]
                            [w 0]]])
                        rect-data)]
    (draw-polys! state poly-data color)))

(defn fill-rect!
  [state pos [w h] color]
  (fill-poly! state
              pos
              [[0 0]
               [0 h]
               [w h]
               [w 0]]
              color))

(defn fill-rects!
  [state rect-data color]
  (let [poly-data (mapv (fn [[pos [w h]]]
                          [pos
                           [[0 0]
                            [0 h]
                            [w h]
                            [w 0]]])
                        rect-data)]
    (fill-polys! state poly-data color)))

(defn draw-ellipse!
  [state pos size color & opts]
  (apply draw-poly! state pos (u/ellipse-points size) color opts))

(defn draw-ellipses!
  [state ellipse-data color & opts]
  (let [poly-data (mapv (fn [[pos size]]
                          [pos (u/ellipse-points size)])
                        ellipse-data)]
    (apply draw-polys! state poly-data color opts)))

(defn fill-ellipse!
  [state pos size color]
  (fill-poly! state pos (u/ellipse-points size) color))

(defn fill-ellipses!
  [state ellipse-data color]
  (let [poly-data (mapv (fn [[pos size]]
                          [pos (u/ellipse-points size)])
                        ellipse-data)]
    (fill-polys! state poly-data color)))

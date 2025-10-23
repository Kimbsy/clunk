(ns minimal.shape
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
           (org.lwjgl.system MemoryStack))
  (:require [minimal.shader :as shader]))

(defn cross
  "Calculate the cross product of two vectors."
  [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])

(defn left-turn?
  "Is the corner from A->B->C a left turn?"
  [[ax ay] [bx by] [cx cy]]
  (let [v1 [(- bx ax) (- by ay) 0]
        v2 [(- cx bx) (- cy by) 0]]
    (>= 0 (last (cross v1 v2)))))

(defn pos-in-tri?
  "Is the position `pos` inside the triangle ABC?"
  [pos [a b c]]
  (every? true?
          (map (partial apply left-turn?)
               [[a b pos]
                [b c pos]
                [c a pos]])))

(defn- other-points
  "Return the points which are not in the ear starting at index `i`"
  [i points]
  (let [n (count points)
        repeating (cycle points)]
    (cond
      (= i 0) (drop 3 points)
      (= i (- n 1)) (drop 2 (butlast points))
      (= i (- n 2)) (rest (drop-last 2 points))
      (= i (- n 3)) (drop-last 3 points)
      :else (concat (take i points)
                    (drop (+ i 3) points)))))

(defn- all-ears
  "Return all the ears of a polygon"
  [points]
  (keep-indexed
   (fn [i [a b c]]
     (when (and (left-turn? a b c)
                (not (some #(pos-in-tri? % [a b c])
                           (other-points i points))))
       [i [a b c]]))
   (->> points
        cycle
        (take (+ 2 (count points)))
        (partition 3 1))))

(defn remove-nth
  "Remove the nth element from a collection."
  [n xs]
  (keep-indexed
   #(when (not= n %1) %2)
   xs))

(defn triangulate
  "Split a concave polygon with no holes or overlapping edges into a
  collection of triangles which can be drawn."
  [poly]
  (loop [p poly
         tris []]
    (if (seq p)
      (if-let [[i tri] (first (all-ears p))]
        (recur (remove-nth (mod (inc i) (count p)) p)
               (conj tris tri))
        (do (prn "FOUND NO EARS????")
            (prn p)
            tris))
      tris)))

;; @TODO: would be nice to use an EBO to store the triangle indices and save a bunch of duplicated shared vertices
(defn fill-poly!
  "Draw a polygon filled with the specified colour."
  ;; @TODO: send [x y] pos into the shader
  ;; @TODO: normalize the poly points somewhere @NOTE: can we do this via the attribute definition? it has a normalize flag.
  [[x y] poly color]
  (let [position-size 3
        vertex-size 3 ;; x,y,z
        ;; a Vertex Buffer Object (VBO) for holding the vertex data
        vbo (GL15/glGenBuffers)
        ;; a Vertex Array Object (VAO) for holding the attributes for the vbo
        vao (GL30/glGenVertexArrays)
        ;; triangulate the polygon and stick all points in a flat float array
        tris (triangulate poly)
        vertices (->> tris
                      (apply concat)
                      (map #(conj % 0))
                      (apply concat)
                      float-array)]

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

    ;; everything after this will use our shaders
    (shader/solid-color color)

    ;; @TODO: eventually the vao will be passed in (or grabbed form the sprite)
    ;; bind our VAO 
    (GL30/glBindVertexArray vao)

    ;; uncomment to draw wireframe rather than fill
    ;; (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)

    ;; draw the triangles
    (GL40/glDrawArrays GL40/GL_TRIANGLES 0 (count vertices))

    ;; unbind the VAO
    (GL30/glBindVertexArray 0)))

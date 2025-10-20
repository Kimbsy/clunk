(ns minimal.shader
  (:require [clojure.java.io :as io])
  (:import (org.lwjgl.opengl GL20)
           (org.lwjgl.system MemoryStack)))

(defn- report-compilation-errors
  [shader shader-path]
  (with-open [stack (MemoryStack/stackPush)]
    (let [success (.ints stack 1)]
      (GL20/glGetShaderiv shader GL20/GL_COMPILE_STATUS success)
      (when (zero? (.get success 0))
        (println (str "[ERROR] compiling shader: " shader-path))
        (println (GL20/glGetShaderInfoLog shader))
        ;; @TODO: exit?
        ))))

(defn- report-linking-errors
  [shader-program]
  (with-open [stack (MemoryStack/stackPush)]
    (let [success (.ints stack 1)]
      (GL20/glGetProgramiv shader-program GL20/GL_LINK_STATUS success)
      (when (zero? (.get success 0))
        (println (str "[ERROR] linking shader program"))
        (println (GL20/glGetProgramInfoLog shader-program))
        ;; @TODO: exit?
        ))))

(defn program
  "Create a shader program from a vertex shader filepath and fragment shader filepath."
  [vertex-shader-path fragment-shader-path]
  (let [vertex-source (slurp (io/resource vertex-shader-path))
        fragment-source (slurp (io/resource fragment-shader-path))
        vertex-shader (GL20/glCreateShader GL20/GL_VERTEX_SHADER)
        fragment-shader (GL20/glCreateShader GL20/GL_FRAGMENT_SHADER)
        shader-program (GL20/glCreateProgram)]

    ;; set the source for the vertex shader and compile it
    (GL20/glShaderSource vertex-shader vertex-source)
    (GL20/glCompileShader vertex-shader)

    ;; report any compilation issues
    (report-compilation-errors vertex-shader vertex-shader-path)

    ;; set the source for the fragment shader and compile it
    (GL20/glShaderSource fragment-shader fragment-source)
    (GL20/glCompileShader fragment-shader)

    ;; report any compilation issues
    (report-compilation-errors fragment-shader fragment-shader-path)

    ;; attach the shaders to the program and then link them
    (GL20/glAttachShader shader-program vertex-shader)
    (GL20/glAttachShader shader-program fragment-shader)
    (GL20/glLinkProgram shader-program)

    ;; report any linking issues
    (report-linking-errors shader-program)

    ;; delete the shaders now they're in the program
    (GL20/glDeleteShader vertex-shader)
    (GL20/glDeleteShader fragment-shader)

    ;;return the program
    shader-program))

;; @TODO: this could be more robust, handle errors and support non-scalar uniforms
;; (defn set-uniform
;;   [shader-program uniform-name value]
;;   (case (type value)
;;     (java.lang.Long java.lang.Integer)
;;     (GL20/glUniform1i
;;      (GL20/glGetUniformLocation shader-program uniform-name)
;;      (int value))

;;     java.lang.Boolean
;;     (GL20/glUniform1i
;;      (GL20/glGetUniformLocation shader-program uniform-name)
;;      (if value (int 1) (int 0)))

;;     (java.lang.Double java.lang.Float)
;;     (GL20/glUniform1f
;;      (GL20/glGetUniformLocation shader-program uniform-name)
;;      (float value))))


(defn use-program
  [shader-program]
  (GL20/glUseProgram shader-program))

;; @TODO: we should create these default shaders when the game inits, this will compile the shader every frame
(defn solid-color
  [color]
  (let [solid-color-program (program "shader/solid-color.vert" "shader/solid-color.frag")]
    ;; need to use the program first for our uniform setting to apply to it
    (use-program solid-color-program)
    (GL20/glUniform4fv
     (GL20/glGetUniformLocation solid-color-program "color")
     (float-array color))))

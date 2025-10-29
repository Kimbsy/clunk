(ns clunk.shader
  (:require [clojure.java.io :as io]
            [clunk.util :as u])
  (:import (org.lwjgl.opengl GL20 GL32)
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
  [vertex-shader-path fragment-shader-path
   & {:keys [geometry-shader-path]}]
  (let [vertex-source (slurp (io/resource vertex-shader-path))
        fragment-source (slurp (io/resource fragment-shader-path))
        vertex-shader (GL20/glCreateShader GL20/GL_VERTEX_SHADER)
        fragment-shader (GL20/glCreateShader GL20/GL_FRAGMENT_SHADER)
        geometry-source (when geometry-shader-path (slurp (io/resource geometry-shader-path)))
        geometry-shader (when geometry-shader-path (GL20/glCreateShader GL32/GL_GEOMETRY_SHADER))
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

    (when geometry-shader-path
      ;; set the source for the geometry shader and compile it
      (GL20/glShaderSource geometry-shader geometry-source)
      (GL20/glCompileShader geometry-shader)

      ;; report any compilation issues
      (report-compilation-errors geometry-shader geometry-shader-path))

    ;; attach the shaders to the program and then link them
    (GL20/glAttachShader shader-program vertex-shader)
    (GL20/glAttachShader shader-program fragment-shader)
    (when geometry-shader-path
      (GL20/glAttachShader shader-program geometry-shader))
    (GL20/glLinkProgram shader-program)

    ;; report any linking issues
    (report-linking-errors shader-program)

    ;; delete the shaders now they're in the program
    (GL20/glDeleteShader vertex-shader)
    (GL20/glDeleteShader fragment-shader)
    (when geometry-shader-path
      (GL20/glDeleteShader geometry-shader))

    ;; return the program
    shader-program))

(defn use-program
  [shader-program]
  (GL20/glUseProgram shader-program))

(defn default-shader-programs
  "These shader programs need an orthographic projection matrix and a
  model matrix supplied as uniforms."
  []
  {::line (program "shader/line.vert" "shader/line.frag" :geometry-shader-path "shader/line.geom")
   ::solid-poly (program "shader/solid-poly.vert" "shader/solid-poly.frag")
   ::texture (program "shader/texture.vert" "shader/texture.frag")})

(defn use-line-shader
  [{:keys [window] :as state} color line-width]
  (let [p (get-in state [:shader-programs ::line])
        window-size (u/window-size window)]
    (use-program p)
    ;; upload shader-specific uniforms
    (GL20/glUniform4fv
     (GL20/glGetUniformLocation p "uColor")
     (float-array color))
    (GL20/glUniform2fv
     (GL20/glGetUniformLocation p "uViewport")
     (float-array window-size))
    (GL20/glUniform1f
     (GL20/glGetUniformLocation p "uThickness")
     (float line-width))
    ;; return the shader program
    p))

(defn use-solid-poly-shader
  [state color]
  (let [p (get-in state [:shader-programs ::solid-poly])]
    (use-program p)
    ;; upload shader-specific uniforms
    (GL20/glUniform4fv
     (GL20/glGetUniformLocation p "uColor")
     (float-array color))
    ;; return the shader program
    p))

(defn use-texture-shader
  [state]
  (let [p (get-in state [:shader-programs ::texture])]
    (use-program p)
    ;; return the shader program
    p))

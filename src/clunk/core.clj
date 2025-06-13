(ns clunk.core
  (:gen-class)
  (:import (java.nio IntBuffer)
           (org.lwjgl Version)
           (org.lwjgl.glfw GLFW
                           GLFWErrorCallback
                           GLFWKeyCallbackI
                           GLFWFramebufferSizeCallbackI
                           Callbacks)
           ;; @NOTE ok bafflingly you need to know which version of
           ;; OpenGL a feature comes from in order to use it, java
           ;; would just import GLXX.* but we need to ns-qualify our
           ;; interop calls :sigh:
           (org.lwjgl.opengl GL GL11 GL30)
           (org.lwjgl.system MemoryStack)
           (org.lwjgl.stb STBImage)))

;; @TODO: need to be able to handle exceptions a bit better, currently
;; it kills the repl

;; @TODO: need to look into drawing text using FreeType lib https://learnopengl.com/In-Practice/Text-Rendering

;; @TODO: need to look into mouse events

;; @TODO: need to look into audio

;; @TODO: need to look into timers



(def initial-window-width 600)
(def initial-window-height 400)

(def initial-state
  {:pos [100 100]
   :vel [(rand-nth [-1 1]) (rand-nth [-1 1])]
   :w 100
   :h 100
   :color [1 1 1 1]})

(def state (atom initial-state))

(declare window)

(declare init main-loop update-state draw)

(defn hex->rgb
  ([hex-string]
   (hex->rgb hex-string 0))
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

(defn window-size
  []
  ;; MemoryStack implements java.io.Closeable, so with-open will pop for us
  (with-open [stack (MemoryStack/stackPush)]
    ;; allocate two IntBuffers of size 1 each
    (let [p-width (.mallocInt stack 1)
          p-height (.mallocInt stack 1)]
      ;; get the frameBuffer size info
      (GLFW/glfwGetFramebufferSize ^long window p-width p-height)
      ;; extract the values from the IntBuffers
      [(.get p-width 0)
       (.get p-height 0)])))

(defn load-texture
  [path]
  ;; prepare buffers for width and height info
  (with-open [stack (MemoryStack/stackPush)]
    (let [w (.mallocInt stack 1)
          h (.mallocInt stack 1)
          cmp (.mallocInt stack 1)]
      ;; tell STB to flip images on load if png origin differs
      (STBImage/stbi_set_flip_vertically_on_load false)

      ;; load the image (forge 4 channel RGBA), we're not using
      ;; `cmp` (normally called `comp`) it grabs the number of
      ;; channels (components) actually found in the original image.
      (let [image (STBImage/stbi_load path w h cmp 4)]
        (when-not image
          (throw (RuntimeException.
                  (str "Failed to load image '"
                       path
                       "': "
                       (STBImage/stbi_failure_reason)))))
        (let [tex-id (GL11/glGenTextures)]
          (GL11/glBindTexture GL11/GL_TEXTURE_2D tex-id)
          ;; upload to GPU
          (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA8 (.get w 0) (.get h 0) 0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE image)

          ;; set filtering
          (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR_MIPMAP_LINEAR)
          (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
          ;; generate mipmaps
          (GL30/glGenerateMipmap GL11/GL_TEXTURE_2D)
          
          ;; cleanup
          (STBImage/stbi_image_free image)
          tex-id)))))

(defn draw-bound-texture-quad
  "draw the currently bound texture, used by `draw-texture-quad`, but
  can also be used to manually bind a single texture, then draw it
  multiple times as the binding is expensive."
  [x y w h]
  (GL11/glEnable GL11/GL_TEXTURE_2D)

  ;; @NOTE we need to know the width and height of the spritesheet so
  ;; we can draw a subsection. we need to specify the `glTexCoord2f`
  ;; vlalues as floats in the range 0.0 - 1.0 wo we deivide desired
  ;; pixel values by image dimensions.

  ;; @TODO: currently ignoring params, hard-coding position, sizes,
  ;; offsets etc.
  (let [[x y :as pos] [100 100]
        [w h :as draw-dims] [240 360]
        [iw ih :as sprite-sheet-dims] [1680 1440]
        ;; no offsets for now, start cutting at top left
        [ox oy :as offsets] [0 0]
        ;; left is x-offset / image-width
        u0 (/ ox iw)
        ;; top is y-offset / image-height
        v0 (/ oy ih)
        ;; right is (x-offset + window-width) / image-width
        u1 (/ (+ ox w) iw)
        ;; bottom is (y-offset + window-height) / image-height
        v1 (/ (+ oy h) ih)]

    ;; @TODO: need to make sure we're not using the colour from the
    ;; bouncing rectangle
    

    (GL11/glBegin GL11/GL_QUADS)
    ;; top-left
    (GL11/glTexCoord2f u0 v0)
    (GL11/glVertex2f x y)
    ;; top-right
    (GL11/glTexCoord2f u1 v0)
    (GL11/glVertex2f (+ x w) y)
    ;; bottom-right
    (GL11/glTexCoord2f u1 v1)
    (GL11/glVertex2f (+ x w) (+ y h))
    ;; bottom-left
    (GL11/glTexCoord2f u0 v1)
    (GL11/glVertex2f x (+ y h))
    (GL11/glEnd)))

(defn draw-texture-quad
  "bind a texture, then draw it"
  [tex-id x y w h]
  (GL11/glBindTexture GL11/GL_TEXTURE_2D tex-id)
  (draw-bound-texture-quad x y w h))

(defn -main
  [& args]
  (println "Hello LWJGL! Version:" (Version/getVersion))

  (init)
  (main-loop)

  ;; free window callbacks and destroy the window
  (Callbacks/glfwFreeCallbacks window)
  (GLFW/glfwDestroyWindow window)

  ;; terminate GLFW and terminate the error callback
  (GLFW/glfwTerminate)
  (-> (GLFW/glfwSetErrorCallback nil)
      .free))

(defn init
  []
  ;; set up an error callback, the default implementation will print
  ;; the error message in System.err
  (-> (GLFWErrorCallback/createPrint System/err)
      .set)

  ;; initialise GLFW, most GLFW functions will not work before doing this
  (when (not (GLFW/glfwInit))
    (throw (IllegalStateException. "Unable to initialise GLFW")))

  ;;;; configure GLFW  
  ;; optional, the current window hints are already the default
  (GLFW/glfwDefaultWindowHints)
  ;; the window will stay hidden after creation
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  ;; the window will be resizable
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)

  ;; create the window
  (def window (GLFW/glfwCreateWindow initial-window-width initial-window-height "Hello, World!" 0 0))
  (when (zero? window)
    (throw (IllegalStateException. "Unable to create the GLFW window")))

  ;; set up a key callback, it will be called every time a key is
  ;; pressed, repeated or released
  (GLFW/glfwSetKeyCallback
   window
   (reify GLFWKeyCallbackI
     (invoke [this window k scancode action mods]
       ;; quit on ESC
       (when (and (= k GLFW/GLFW_KEY_ESCAPE)
                  (= action GLFW/GLFW_RELEASE))
         ;; we will detect this in the window loop
         (GLFW/glfwSetWindowShouldClose window true)))))

  ;; @TODO: could refactor to use `window-size`
  ;; get the thread stack and push a new frame
  (let [stack (MemoryStack/stackPush)
        p-width (.mallocInt stack 1)
        p-height (.mallocInt stack 1)]
    ;; get the window size passed to `glfwCreateWindow`
    (GLFW/glfwGetWindowSize ^long window p-width p-height)
    ;; get the resolution of the primary monitor
    (let [vidmode (-> (GLFW/glfwGetPrimaryMonitor)
                      (GLFW/glfwGetVideoMode))
          x-pos (/ (- (.width vidmode)
                      (.get p-width 0))
                   2)
          y-pos (/ (- (.height vidmode)
                      (.get p-height 0))
                   2)]
      ;; centre the window
      (GLFW/glfwSetWindowPos window x-pos y-pos))
    ;; pop the stack frame
    (MemoryStack/stackPop))

  ;; make the OpenGL context current
  (GLFW/glfwMakeContextCurrent window)
  ;; enable v-sync (set this to 0 to uncap framerate and run fast as possible)
  (GLFW/glfwSwapInterval 1)
  ;; make the window visible
  (GLFW/glfwShowWindow window))

(defn reset-otho-projection
  [w h]
  (GL11/glMatrixMode GL11/GL_PROJECTION)
  (GL11/glLoadIdentity)
  ;; left, right, top, bottom in pixel units
  (GL11/glOrtho 0 w h 0 -1 1)
  ;; go back to model view
  (GL11/glMatrixMode GL11/GL_MODELVIEW)
  (GL11/glLoadIdentity))

(defn main-loop
  []
  ;; this line is critical for LWJGL's interoperation with GLFW's
  ;; OpenGL context, or any context that is managed externally. LWJGL
  ;; detects the context that is current in the current thread,
  ;; creates the GLCapabilities instance and makes the OpenGL bindings
  ;; available for use
  (GL/createCapabilities)


  ;; @NOTE test setting orthographic mode so we can use pixel positions for vertices
  (reset-otho-projection initial-window-width initial-window-height)
  ;; set resize callback
  (GLFW/glfwSetFramebufferSizeCallback
   window
   (reify GLFWFramebufferSizeCallbackI
     (invoke [this window w h]
       ;; update the GL viewport to cover the new window
       (GL11/glViewport 0 0 w h)
       ;; re-build the ortho projection matrix to match the new size
       (reset-otho-projection w h))))





  ;; load the captain image as a texture
  (let [captain (load-texture "resources/img/captain.png")]
    (swap! state #(assoc % :captain captain)))


  ;; enable transparency when drawing images
  (GL11/glEnable GL11/GL_BLEND)
  (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)
  

  
  ;;@TODO: can we make this more functional?
  
  ;; run the rendering loop until the user has attempted to close the
  ;; window or has pressed the ESC key.
  (while (not (GLFW/glfwWindowShouldClose window))
    (swap! state update-state)
    (draw @state)))

(defn randomize-color
  [state]
  (assoc state :color [(rand) (rand) (rand)]))

(defn bounce-x
  [{:keys [w]
    [x _] :pos
    :as state}]
  (let [[max-w _] (window-size)]
    (if (or (< x 0)
            (< max-w (+ x w)))
      (-> state
          (update-in [:vel 0] * -1)
          randomize-color)
      state)))

(defn bounce-y
  [{:keys [h]
    [_ y] :pos
    :as state}]
  (let [[_ max-h] (window-size)]
    (if (or (< y 0)
            (< max-h (+ y h)))
      (-> state
          (update-in [:vel 1] * -1)
          randomize-color)
      state)))

(defn update-state
  [{:keys [vel] :as state}]
  (-> state
      (update :pos #(map + % vel))
      bounce-x
      bounce-y))

(defn draw
  [{:keys [w h captain]
    [x y] :pos
    [vx vy] :vel
    [r g b] :color
    :as state}]

  (let [[bgr bgg bgb bga] (hex->rgb "#A499B3")]
    ;; set the clear colour
    (GL11/glClearColor bgr bgg bgb bga)
    ;; clear the frameBuffer
    (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT)))



  ;; draw a rectangle based on the current state
  (GL11/glDisable GL11/GL_TEXTURE_2D) ;; we dont want the texture drawing config
  (GL11/glColor3f r g b)
  (GL11/glBegin GL11/GL_QUADS)
  (GL11/glVertex2f x y)
  (GL11/glVertex2f (+ x w) y)
  (GL11/glVertex2f (+ x w) (+ y h))
  (GL11/glVertex2f x (+ y h))
  (GL11/glEnd)


  ;; draw the captain texture (args ignored for now)
  (GL11/glColor4f 1 1 1 1)
  (draw-texture-quad captain 0 0 0 0)
  

  ;; swap the colour buffers
  (GLFW/glfwSwapBuffers window)

  ;; poll for window events, the key callback above will only be
  ;; invoked during this call
  (GLFW/glfwPollEvents))

(ns clunk.core
  (:gen-class)
  (:import (java.nio ByteBuffer IntBuffer ShortBuffer)
           (org.lwjgl Version)
           (org.lwjgl.glfw Callbacks
                           GLFW
                           GLFWCursorPosCallbackI
                           GLFWErrorCallback
                           GLFWFramebufferSizeCallbackI
                           GLFWKeyCallbackI
                           GLFWMouseButtonCallbackI)
           (org.lwjgl.nanovg NanoVG NanoVGGL3 NVGColor)
           (org.lwjgl.openal AL AL10 ALCCapabilities ALC10 ALC)
           ;; @NOTE ok bafflingly you need to know which version of
           ;; OpenGL a feature comes from in order to use it, java
           ;; would just import GLXX.* but we need to ns-qualify our
           ;; interop calls :sigh:
           (org.lwjgl.opengl GL
                             GL11
                             GL14
                             GL30)
           (org.lwjgl.stb STBImage STBVorbis)
           (org.lwjgl.system MemoryStack MemoryUtil))
  (:require [clunk.sprite :as sprite]
            [clunk.image :as image]
            [clunk.util :as u]
            [clunk.palette :as p]))

;; FEATURES

;; @TODO: need to look into timers

;; BUGS

;; @TODO: need to be able to handle exceptions a bit better, currently
;; it kills the repl


(def initial-window-width 1200)
(def initial-window-height 800)

(def audio? false)

(def initial-state
  {:scenes {:demo {:sprites []}}
   :current-scene :demo
   :debug? false})

(def state (atom initial-state))


;; @TODO: remove declares and global vars
(declare window)
(declare init main-loop)

;; capture and restore the blending state of GL so we can draw NanoVG
;; text (which blats the config) and then get it back for drawing
;; images/shapes.
(defn capture-gl-state
  []
  {:src-rgb (GL11/glGetInteger GL30/GL_BLEND_SRC_RGB)
   :src-alpha (GL11/glGetInteger GL30/GL_BLEND_SRC_ALPHA)
   :dst-rgb (GL11/glGetInteger GL14/GL_BLEND_DST_RGB)
   :dst-alpha (GL11/glGetInteger GL11/GL_DST_ALPHA)
   :blend-enabled? (GL11/glIsEnabled GL11/GL_BLEND)})
(defn restore-gl-state
  [{:keys [src-rgb src-alpha dst-rgb dst-alpha blend-enabled?]}]
  (if blend-enabled?
    (GL11/glEnable GL11/GL_BLEND)
    (GL11/glDisable GL11/GL_BLEND))
  (GL30/glBlendFuncSeparate src-rgb dst-rgb src-alpha dst-alpha))

(defn cleanup-audio
  [{{:keys [source al-buffer context device]} :audio}]
  (AL10/alSourceStop source)
  (AL10/alDeleteSources source)
  (AL10/alDeleteBuffers al-buffer)
  (ALC10/alcDestroyContext context)
  (ALC10/alcCloseDevice device))

(defn -main
  [& args]
  (println "Hello LWJGL! Version:" (Version/getVersion))

  (init)
  (try
    (main-loop)
    (catch Exception e
      (prn e)))

  ;; clean up audio stuff on close
  (when audio?
    (cleanup-audio @state))

  ;; free window callbacks and destroy the window
  (Callbacks/glfwFreeCallbacks window)
  (GLFW/glfwDestroyWindow window)

  ;; terminate GLFW and terminate the error callback
  (GLFW/glfwTerminate)
  (-> (GLFW/glfwSetErrorCallback nil)
      .free))

(defn reset-ortho-projection
  [w h]
  (GL11/glMatrixMode GL11/GL_PROJECTION)
  (GL11/glLoadIdentity)
  ;; left, right, top, bottom in pixel units
  (GL11/glOrtho 0 w h 0 -1 1)
  ;; go back to model view
  (GL11/glMatrixMode GL11/GL_MODELVIEW)
  (GL11/glLoadIdentity))

(defn start-event-polling
  [window]
  ;; putting event polling in a separate thread (didn't do much for
  ;; mouse event flood though)
  (future
    (while (not (GLFW/glfwWindowShouldClose window))
      ;; poll for window events, the key callback above will only be
      ;; invoked during this call
      (GLFW/glfwPollEvents)
      (Thread/sleep 1))))

(defn init-audio
  []
  ;; init openAL
  (let [device (ALC10/alcOpenDevice (cast ByteBuffer nil))
        device-capabilities (ALC/createCapabilities device)
        context (ALC10/alcCreateContext device (cast IntBuffer nil))]
    (ALC10/alcMakeContextCurrent context)
    (AL/createCapabilities device-capabilities)

    ;; decode .ogg -> AL buffer
    (with-open [stack (MemoryStack/stackPush)]
      (let [channels (.mallocInt stack 1)
            sample-rate (.mallocInt stack 1)
            raw-audio (STBVorbis/stb_vorbis_decode_filename
                       "resources/audio/music/music.ogg"
                       channels
                       sample-rate)]
        (when-not raw-audio
          (throw (RuntimeException. (str "Failed to load OGG: " (STBVorbis/stb_vorbis_get_error nil)))))

        ;; choose format based on channels
        (let [fmt (if (= 1 (.get channels 0))
                    AL10/AL_FORMAT_MONO16
                    AL10/AL_FORMAT_STEREO16)
              al-buffer (AL10/alGenBuffers)]
          (AL10/alBufferData al-buffer fmt raw-audio (.get sample-rate 0))

          ;; create a source, hook up the buffer, loop and play
          (let [source (AL10/alGenSources)]
            (AL10/alSourcei source AL10/AL_BUFFER al-buffer)
            (AL10/alSourcei source AL10/AL_LOOPING AL10/AL_TRUE)
            (AL10/alSourcePlay source)

            ;; @TODO: stop and clean up (later?)
            {:source source
             :al-buffer al-buffer
             :context context
             :device device}))))))

(defn bounce-x
  [{[w _] :size
    [x _] :pos
    :as s}]
  (let [[max-w _] (u/window-size window)]
    (if (or (< x 0)
            (< max-w (+ x w)))
      (update-in s [:vel 0] * -1)
      s)))

(defn bounce-y
  [{[_ h] :size
    [_ y] :pos
    :as s}]
  (let [[_ max-h] (u/window-size window)]
    (if (or (< y 0)
            (< max-h (+ y h)))
      (update-in s [:vel 1] * -1)
      s)))

(defn init-sprites
  []
  [(sprite/sprite :example
                  [300 50]
                  :vel [3 3]
                  :update-fn (fn bouncy [s]
                               (-> s
                                   sprite/update-pos
                                   bounce-x
                                   bounce-y))
                  :color [0 1 0])
   (sprite/image-sprite :captain
                        [100 100]
                        [1680 1440]
                        ;; @TODO: preload assets and get them easily
                        (image/load-texture "resources/img/captain.png"))
   (sprite/animated-sprite :animated-captain
                           [600 100]
                           [240 360]
                           (image/load-texture "resources/img/captain.png")
                           [1680 1440]
                           :animations {:none {:frames 1
                                               :y-offset 0
                                               :frame-delay 100}
                                        :idle {:frames 4
                                               :y-offset 1
                                               :frame-delay 15}
                                        :run  {:frames 4
                                               :y-offset 2
                                               :frame-delay 8}
                                        :jump {:frames 7
                                               :y-offset 3
                                               :frame-delay 8}}
                           :current-animation :run
                           :vel [2 -3]
                           :update-fn (fn [s]
                                        (-> s
                                            sprite/update-animated-sprite
                                            bounce-x
                                            bounce-y))
                           :debug? true
                           :debug-color p/cyan)])

(defn init
  []
  ;; reset the game state
  (reset! state initial-state)

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

  ;; @TODO: would be nice to return the window from this function, instead of deffing it
  ;; create the window
  (def window (GLFW/glfwCreateWindow initial-window-width initial-window-height "Hello, World!" 0 0))
  (when (zero? window)
    (throw (IllegalStateException. "Unable to create the GLFW window")))

  ;; @NOTE can stabilise framerate with this when framerate is uncapped
  ;; Hide mouse cursor, for some reason in X11 moving the mouse tanks framerate
  ;; (GLFW/glfwSetInputMode window GLFW/GLFW_CURSOR GLFW/GLFW_CURSOR_DISABLED)

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

  ;; when the mouse moves in the window set the position of the
  ;; example sprite to the cursor
  (GLFW/glfwSetCursorPosCallback
   window
   (reify GLFWCursorPosCallbackI
     (invoke [this window xpos ypos]
       (swap! state (fn [st]
                        (sprite/update-sprites
                         st
                         (sprite/has-group :example)
                         #(assoc % :pos [xpos ypos])))))))

  ;; when we press/release a mouse button print it
  (GLFW/glfwSetMouseButtonCallback
   window
   (reify GLFWMouseButtonCallbackI
     (invoke [this window button action mods]
       (let [buttons [:left :right :middle]
             actions [:released :pressed]]
         (prn (get buttons button) (get actions action))))))

  ;; @TODO: could refactor to use `u/window-size`
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
  (GLFW/glfwShowWindow window)

  ;; this line is critical for LWJGL's interoperation with GLFW's
  ;; OpenGL context, or any context that is managed externally. LWJGL
  ;; detects the context that is current in the current thread,
  ;; creates the GLCapabilities instance and makes the OpenGL bindings
  ;; available for use
  (GL/createCapabilities)

  ;; @NOTE test setting orthographic mode so we can use pixel positions for vertices
  (reset-ortho-projection initial-window-width initial-window-height)
  ;; set resize callback
  (GLFW/glfwSetFramebufferSizeCallback
   window
   (reify GLFWFramebufferSizeCallbackI
     (invoke [this window w h]
       ;; update the GL viewport to cover the new window
       (GL11/glViewport 0 0 w h)
       ;; re-build the ortho projection matrix to match the new size
       (reset-ortho-projection w h))))

  ;;;; initialise text rendering stuff
  ;; create NanoVG context
  (let [vg (NanoVGGL3/nvgCreate (bit-or NanoVGGL3/NVG_ANTIALIAS
                                        NanoVGGL3/NVG_STENCIL_STROKES))
        ;; load a font
        font (NanoVG/nvgCreateFont vg "sans" "resources/font/UbuntuMono-Regular.ttf")]
    ;; stick them in the state
    (swap! state #(assoc % :vg vg :font font)))

  ;; enable transprency for drawing images
  (GL11/glEnable GL11/GL_BLEND)
  (GL30/glBlendFuncSeparate GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA GL11/GL_ONE GL11/GL_ONE_MINUS_SRC_ALPHA)

  ;; start audio
  (when audio?
    (let [audio (init-audio)]
      (swap! state #(assoc % :audio audio))))

  (let [sprites (init-sprites)]
    (swap! state #(update-in %
                               [:scenes :demo :sprites]
                               concat
                               sprites)))

  ;; start polling for events
  (start-event-polling window))

(defn update-state
  [{:keys [vel] :as st}]
  (-> st
      (sprite/update-state)))

(defn draw-background
  []
  (let [[bgr bgg bgb bga] (p/hex->rgb "#A499B3")]
    ;; set the clear colour
    (GL11/glClearColor bgr bgg bgb bga)
    ;; clear the frameBuffer
    (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))))

(defn draw-text
  [vg font]
  (let [old-state (capture-gl-state)]
    (let [[window-w window-h] (u/window-size window)]
      (NanoVG/nvgBeginFrame vg window-w window-h 1)
      (NanoVG/nvgFontSize vg 64)
      (NanoVG/nvgFontFace vg "sans")
      (with-open [stack (MemoryStack/stackPush)]
        (let [white (NVGColor/malloc stack)
              f0 (float 0)
              f1 (float 1)]
          (NanoVG/nvgFillColor vg (NanoVG/nvgRGBAf f1 f1 f1 f1 white))))
      (NanoVG/nvgText vg (float 50) (float 175) "Hello NanoVG!" )
      (NanoVG/nvgEndFrame vg))
    (restore-gl-state old-state)))

(defn draw
  [{:keys [w h captain vg font]
    [x y] :pos
    [vx vy] :vel
    [r g b] :color
    :as st}]
  ;; draw background
  (draw-background)

  ;; draw some text
  (draw-text vg font)

  ;; draw he current scene sprites
  (sprite/draw-scene-sprites! st)

  ;; swap the colour buffers
  (GLFW/glfwSwapBuffers window))

;;@TODO: can we make this more functional?
(defn main-loop
  []
  ;; run the rendering loop until the user has attempted to close the
  ;; window or has pressed the ESC key.
  (while (not (GLFW/glfwWindowShouldClose window))
    ;; update the state
    (swap! state update-state)

    ;; draw everything
    (draw @state)))

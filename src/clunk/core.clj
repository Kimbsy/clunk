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
            [clunk.palette :as p]
            [clunk.collision :as collision]
            [clunk.tween :as tween]))

;; FEATURES

;; @TODO: need to look into timers

;; BUGS

;; @TODO: crashing on window resize (ortho projection?)

;; @TODO: need to be able to handle exceptions a bit better, currently
;; it kills the repl


;; @TODO: remove global vars
(def initial-window-width 1200)
(def initial-window-height 800)

(def audio? false)

(def initial-state
  {:window nil
   :scenes {:demo {:sprites []}}
   :current-scene :demo
   :debug? false})

(def state (atom initial-state))

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

;; @TODO: util?
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
  []
  ;; putting event polling in a separate thread (didn't do much for
  ;; mouse event flood though)
  (future
    (while (not (GLFW/glfwWindowShouldClose (:window @state)))
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

(defn initial-sprites
  []
  (let [[window-w window-h] (u/window-size (:window @state))]
    [(sprite/sprite :example
                    [500 50]
                    :vel [3 3]
                    :color [0 1 0])
     (sprite/image-sprite :captain-sheet
                          [100 100]
                          [1680 1440]
                          ;; @TODO: preload assets and get them easily
                          (image/load-texture "resources/img/captain.png")
                          :offsets [:left :top])
     (sprite/animated-sprite :animated-captain
                             [600 500]
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
                             :current-animation :none
                             :vel [2 -3]
                             :debug? true
                             :debug-color p/cyan)
     (-> (sprite/sprite :tween-example
                       [600 250]
                       :color p/magenta)
         (tween/add-tween
          (tween/tween :pos
                       100
                       :update-fn tween/tween-x-fn
                       :yoyo? true
                       :yoyo-update-fn tween/tween-x-yoyo-fn
                       :repeat-times ##Inf))
         (tween/add-tween
          (tween/tween :pos
                       -200
                       :step-count 50
                       :easing-fn tween/ease-out-quad
                       :update-fn tween/tween-y-fn
                       :yoyo? true
                       :yoyo-update-fn tween/tween-y-yoyo-fn
                       :repeat-times ##Inf)))

     ;; world bounds
     (sprite/sprite :wall-y [0 -100]
                    :size [window-w 100]
                    :update-fn identity
                    :offsets [:left :top])
     (sprite/sprite :wall-x [window-w 0]
                    :size [100 window-h]
                    :update-fn identity
                    :offsets [:left :top])
     (sprite/sprite :wall-y [0 window-h]
                    :size [window-w 100]
                    :update-fn identity
                    :offsets [:left :top])
     (sprite/sprite :wall-x [-100 0]
                    :size [100 window-h]
                    :update-fn identity
                    :offsets [:left :top])]))

(defn wall-colliders
  [sprite-group]
  [(collision/collider
    sprite-group
    :wall-x
    (fn [s _]
      (update-in s [:vel 0] * -1))
    collision/identity-collide-fn)
   (collision/collider
    sprite-group
    :wall-y
    (fn [s _]
      (update-in s [:vel 1] * -1))
    collision/identity-collide-fn)])

(defn initial-colliders
  []
  (concat
   [(collision/collider
     :animated-captain
     :example
     (fn [{:keys [current-animation] :as animated-captain} _example-sprite]
       (-> animated-captain
           (assoc :pos [600 500])
           (sprite/set-animation (rand-nth (remove #{current-animation}
                                                   [:none :idle :run :jump])))))
     (fn [example-sprite _animated-captain]
       (-> example-sprite
           (assoc :pos [700 100])
           (assoc :color [(rand) (rand) (rand)]))))]
   (wall-colliders :animated-captain)
   (wall-colliders :example)))

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

  ;; create the window and swap! it into the global state atom
  (let [window (GLFW/glfwCreateWindow initial-window-width initial-window-height "Hello, World!" 0 0)]
    (when (zero? window)
      (throw (IllegalStateException. "Unable to create the GLFW window")))
    (swap! state assoc :window window)

    ;; @NOTE can stabilise framerate with this when framerate is uncapped
    ;; Hide mouse cursor, for some reason in X11 moving the mouse tanks framerate
    ;; (GLFW/glfwSetInputMode window GLFW/GLFW_CURSOR GLFW/GLFW_CURSOR_DISABLED)

    ;; set up a key callback, it will be called every time a key is
    ;; pressed, repeated or released
    (GLFW/glfwSetKeyCallback
     window
     (reify GLFWKeyCallbackI
       (invoke [this win k scancode action mods]
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
       (invoke [this win xpos ypos]
         (swap! state (fn [st]
                        (sprite/update-sprites
                         st
                         (sprite/has-group :example)
                         #(assoc % :pos [xpos ypos])))))))

    ;; when we press/release a mouse button print it
    (GLFW/glfwSetMouseButtonCallback
     window
     (reify GLFWMouseButtonCallbackI
       (invoke [this win button action mods]
         (let [buttons [:left :right :middle]
               actions [:released :pressed]]
           (prn (get buttons button) (get actions action))))))

    (let [[window-w window-h] (u/window-size window)
          ;; get the resolution of the primary monitor
          vidmode (-> (GLFW/glfwGetPrimaryMonitor)
                      (GLFW/glfwGetVideoMode))
          x-pos (/ (- (.width vidmode)
                      window-w)
                   2)
          y-pos (/ (- (.height vidmode)
                      window-h)
                   2)]
      ;; centre the window
      (GLFW/glfwSetWindowPos window x-pos y-pos))

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

    ;; @TODO: this should use the current screen size, not the initial (we're crashing when window is resized right now)
    ;; @NOTE test setting orthographic mode so we can use pixel positions for vertices
    (reset-ortho-projection initial-window-width initial-window-height)
    ;; set resize callback
    (GLFW/glfwSetFramebufferSizeCallback
     window
     (reify GLFWFramebufferSizeCallbackI
       (invoke [this win w h]
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

    ;; add sprites
    (let [sprites (initial-sprites)]
      (swap! state #(update-in %
                               [:scenes :demo :sprites]
                               concat
                               sprites)))    

    ;; add colliders
    (let [colliders (initial-colliders)]
      (swap! state #(update-in %
                               [:scenes :demo :colliders]
                               concat
                               colliders)))

    ;; start polling for events
    (start-event-polling)))

(defn update-state
  [{:keys [vel] :as st}]
  (-> st
      sprite/update-state
      collision/update-state
      tween/update-state))

(defn draw-background!
  [[r g b a]]
  ;; set the clear colour
  (GL11/glClearColor r g b a)
  ;; clear the frameBuffer
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT)))

(defn draw-text
  [vg font]
  (let [old-state (capture-gl-state)]
    (let [[window-w window-h] (u/window-size (:window @state))]
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
  (draw-background! (p/hex->rgb "#1282A2"))

  ;; draw some text
  (draw-text vg font)

  ;; draw he current scene sprites
  (sprite/draw-scene-sprites! st)

  ;; swap the colour buffers
  (GLFW/glfwSwapBuffers (:window @state)))

;;@TODO: can we make this more functional? maybe not?
(defn main-loop
  []
  ;; run the rendering loop until the user has attempted to close the
  ;; window or has pressed the ESC key.
  (while (not (GLFW/glfwWindowShouldClose (:window @state)))
    ;; update the state
    (swap! state update-state)

    ;; draw everything
    (draw @state)))

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
  (Callbacks/glfwFreeCallbacks (:window @state))
  (GLFW/glfwDestroyWindow (:window @state))

  ;; terminate GLFW and terminate the error callback
  (GLFW/glfwTerminate)
  (-> (GLFW/glfwSetErrorCallback nil)
      .free))

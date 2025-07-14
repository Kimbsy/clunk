(ns clunk.core
  (:require [clojure.java.io :as io]
            [clunk.audio :as audio]
            [clunk.image :as image]
            [clunk.input :as i]
            [clunk.palette :as p]
            [clunk.shape :as shape]
            [clunk.sprite :as sprite]
            [clunk.util :as u])
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
                             GL30)
           (org.lwjgl.system MemoryStack)))

;; FEATURES

;; @TODO: need to look into timers

;; BUGS

;; @TODO: crashing on window resize (ortho projection?)

;; @TODO: need to be able to handle exceptions a bit better, currently
;; it kills the repl

;; @TODO: Description : Wayland: The platform does not support setting the window position

;; @TODO pare this down
(def initial-state
  {:window nil
   :scenes {:demo {:sprites []}}
   :current-scene :demo
   :debug? false})

(def empty-queue clojure.lang.PersistentQueue/EMPTY)
(def events (atom empty-queue))

(defn enqueue-event!
  [e]
  (swap! events conj e))

(defn drain-events!
  []
  (let [[es _] (swap-vals! events (constantly empty-queue))]
    es))

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

(defn create-font-mem
  "Load a .ttf from a classpath resource and register it as `name` in
  the NanoVG context `vg`."
  [vg name classpath-resource]
  (with-open [is (io/input-stream (io/resource classpath-resource))]
    ;; read all bytes
    (let [ba (byte-array (.available is))]
      (.read is ba)
      ;; wrap into a direct ByteBuffer
      (let [buf (doto (BufferUtils/createByteBuffer (alength ba))
                  (.put ba)
                  (.flip))]
        (NanoVG/nvgCreateFontMem vg name buf false)))))

;; @TODO: split this up into functions and do a (-> state ....) thread
;; @TODO: catch initialisation exceptions, and shut down gracefully
(defn init
  [{[initial-window-width initial-window-height] :size
    :as game-config}]
  (let [state game-config]
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

    ;; create the window and store it in the state
    (let [window (GLFW/glfwCreateWindow initial-window-width initial-window-height "Hello, World!" 0 0)]
      (when (zero? window)
        (throw (IllegalStateException. "Unable to create the GLFW window")))
      (let [state (assoc state :window window)]

        ;; @NOTE can stabilise framerate with this when framerate is uncapped
        ;; Hide mouse cursor, for some reason in X11 moving the mouse tanks framerate
        ;; (GLFW/glfwSetInputMode window GLFW/GLFW_CURSOR GLFW/GLFW_CURSOR_DISABLED)

        ;; set up a key callback, it will be called every time a key is
        ;; pressed, repeated or released
        (GLFW/glfwSetKeyCallback
         window
         (reify GLFWKeyCallbackI
           (invoke [this win k scancode action mods]
             ;; enqueue the event to be processed
             (enqueue-event! {:event-type :key
                              :k k
                              :scancode scancode
                              :action action
                              :mods mods})
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
             (enqueue-event! {:event-type :mouse-movement
                              :xpos xpos
                              :ypos ypos}))))

        ;; when we press/release a mouse button print it
        (GLFW/glfwSetMouseButtonCallback
         window
         (reify GLFWMouseButtonCallbackI
           (invoke [this win button action mods]
             (with-open [stack (MemoryStack/stackPush)]
               (let [p-x (.mallocDouble stack 1)
                     p-y (.mallocDouble stack 1)]
                 (GLFW/glfwGetCursorPos win p-x p-y)
                 (enqueue-event! {:event-type :mouse-button
                                  :button button
                                  :action action
                                  :mods mods
                                  :pos [(.get p-x 0)
                                        (.get p-y 0)]}))))))

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
          ;; @TODO: this fails on wayland, should check if supported first
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
              font (create-font-mem vg "UbuntuMono-Regular" "font/UbuntuMono-Regular.ttf")
              ;; @TODO: register other default fonts?
              ;; stick them in the state
              state (-> state
                        (assoc :vg vg)
                        (assoc :vg-color (NVGColor/create))
                        (assoc :default-font font))]

          ;; enable transprency for drawing images
          (GL11/glEnable GL11/GL_BLEND)
          (GL30/glBlendFuncSeparate GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA GL11/GL_ONE GL11/GL_ONE_MINUS_SRC_ALPHA)

          ;; optionally start audio
          (let [audio (audio/init-audio)
                state (assoc state :audio audio)]

            ;; start polling for events
            (start-event-polling state)

            ;; return the initial game sate
            state))))))

(defn draw-background!
  [[r g b a]]
  ;; set the clear colour
  (GL11/glClearColor r g b a)
  ;; clear the frameBuffer
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT)))

;; @TODO: supply default key/mouse functions for updating some
;; `:currently-held` keys/buttons field
(defn process-event
  [{:keys [current-scene scenes] :as state}
   {:keys [event-type] :as e}]
  (let [scene (get scenes current-scene)
        kw (keyword (str (name event-type) "-fns"))
        applicable-fns (get scene kw)
        default-fns (get i/default-event-fns kw)]
    (reduce (fn [acc f]
              (f acc e))
            state
            (concat default-fns
                    applicable-fns))))

;; @TODO: add a `:dt` field which is the time in millis since the last frame
(defn update-game
  "Update the game state based on the current scenes `:update-fn`.

  Applies all unprocessed events to the game."
  [{:keys [scenes current-scene last-frame-time] :as state}]
  (let [unprocessed-events (drain-events!)
        scene-update-fn (or (get-in scenes [current-scene :update-fn])
                            identity)
        now (System/currentTimeMillis)]
    (-> state
        (assoc :last-frame-time now)
        (assoc :dt (- now last-frame-time))
        (#(reduce process-event
                  %
                  unprocessed-events))
        scene-update-fn)))

(defn default-draw!
  [{:keys [vg default-font window current-scene] :as state}]
  (draw-background! (conj p/black 0))
  (let [[window-w window-h] (u/window-size window)]
    (sprite/draw-text-sprite!
     state
     (sprite/text-sprite
      :default-message
      [100 (/ window-h 2)]
      (str "No draw-fn found for current scene " current-scene)))))

(defn draw-game!
  "Draw the game using the current scenes `:draw-fn`."
  [{:keys [scenes current-scene] :as state}]
  (if-let [scene-draw-fn (get-in scenes [current-scene :draw-fn])]
    (scene-draw-fn state)
    (default-draw! state)))

(defn default-on-close
  [& _]
  (prn "******** SHUTTING DOWN ********"))

(def default-opts
  {:title "Example game"
   :size [800 600]
   :update-fn update-game
   :draw-fn draw-game!
   :on-start-fn identity
   :on-close-fn default-on-close
   :init-scenes-fn (constantly {})
   :current-scene :none
   :last-frame-time 0
   :dt 0
   :assets {}
   :held-keys #{}})

(defn game
  "Create a game config map"
  [override-opts]
  (merge default-opts override-opts))

(defn main-loop
  [{:keys [window] :as state}]

  ;; poll for window events
  (GLFW/glfwPollEvents)

  ;; update the state
  (let [new-state (update-game state)]

    ;; draw everything
    (draw-game! new-state)
    (GLFW/glfwSwapBuffers window)

    ;; clean up stopped audio sources
    (audio/cleanup-stopped-sources!)

    ;; return the new state
    new-state))

(defn draw-preload-progress!
  [window current total]
  (draw-background! p/black)
  (let [[w h] (u/window-size window)]
    (shape/draw-rect!
     [(* w 0.1) (* h 0.5)]
     [(* w 0.8) (* h 0.1)]
     p/white)
    (shape/fill-rect!
     [(* w 0.1) (* h 0.5)]
     [(* w 0.8 (/ current total)) (* h 0.1)]
     p/white))
  ;; @TODO: could draw text naming the file underneath?
  (GLFW/glfwSwapBuffers window))

(defn preload-assets!
  "Pre-load assets and display a loading bar"
  [window
   {audio-assets :audio
    image-assets :image}]
  (draw-background! p/black)
  (GLFW/glfwSwapBuffers window)
  (let [assets (concat (map (partial vector audio/load-ogg-file!) audio-assets)
                       (map (partial vector image/load-texture!) image-assets))
        total (count assets)
        window-size (u/window-size window)]
    (doseq [[i [f [k path]]] (map-indexed vector assets)]
      (draw-preload-progress! window i total)
      (println (str "Loading " k " from: " path))
      (f k path))))

(defn start!
  [{:keys [init-scenes-fn on-start-fn on-close-fn] :as game}]
  (let [{:keys [window audio assets] :as initialised-lwjgl} (init game)
        _ (preload-assets! window assets)
        scenes (init-scenes-fn initialised-lwjgl)
        state (merge initialised-lwjgl {:scenes scenes})
        state (on-start-fn state)]

    (when-let
        [final-state
         (try
           (doall
            (iteration
             main-loop
             :initk state
             ;; run the rendering loop until the user has attempted to
             ;; close the window or has pressed the ESC key.
             :somef (fn [{:keys [window]}]
                      (not (GLFW/glfwWindowShouldClose window)))))
           (catch Exception e
             (prn e)))]
      ;; call game defined `:on-close-fn`
      (on-close-fn final-state))

    ;; clean up audio stuff on close
    (audio/cleanup-audio audio)

    ;; free window callbacks and destroy the window
    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwDestroyWindow window)

    ;; terminate GLFW and terminate the error callback
    (GLFW/glfwTerminate)
    (-> (GLFW/glfwSetErrorCallback nil)
        .free)))

(defn quit!
  [{:keys [window]}]
  (GLFW/glfwSetWindowShouldClose window true))

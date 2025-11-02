(ns clunk.core
  (:require [clunk.audio :as audio]
            [clunk.image :as image]
            [clunk.input :as i]
            [clunk.palette :as p]
            [clunk.shape :as shape]
            [clunk.text :as text]
            [clunk.util :as u]
            [clunk.shader :as shader])
  (:import (org.joml Matrix4f)
           (org.lwjgl.glfw Callbacks
                           GLFW
                           GLFWCursorPosCallbackI
                           GLFWErrorCallback
                           GLFWFramebufferSizeCallbackI
                           GLFWKeyCallbackI
                           GLFWMouseButtonCallbackI)
           (org.lwjgl.nanovg NanoVGGL3
                             NVGColor)
           (org.lwjgl.opengl GL
                             GL11
                             GL30)
           (org.lwjgl.system MemoryStack)))

;; @TODO: need to be able to handle exceptions a bit better, currently
;; it kills the repl
;; @TODO: catch initialisation exceptions, and shut down gracefully

;; @TODO: Description : Wayland: The platform does not support setting the window position

(def empty-queue clojure.lang.PersistentQueue/EMPTY)
(def events (atom empty-queue))

(defn enqueue-event!
  [e]
  (swap! events conj e))

(defn drain-events!
  []
  (let [[es _] (swap-vals! events (constantly empty-queue))]
    es))

(defn reset-ortho-projection
  [{:keys [window] :as state}]
  (let [[w h] (u/window-size window)]
    (GL11/glViewport 0 0 w h)
    (assoc state :ortho-projection
           (doto (Matrix4f.)
             (.setOrtho2D 0 w h 0)))))

(defn init-error-callback
  "Set up an error callback, the default implementation will print
    the error message in System.err"
  [state]
  (.set (GLFWErrorCallback/createPrint System/err))
  state)

(defn init-glfw
  "Initialise GLFW, most GLFW functions will not work before doing this"
  [{[initial-window-width initial-window-height] :size
    :as state}]
  (when (not (GLFW/glfwInit))
    (throw (IllegalStateException. "Unable to initialise GLFW")))

  ;; optional, the current window hints are already the default
  (GLFW/glfwDefaultWindowHints)
  ;; the window will stay hidden after creation
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  ;; the window will be resizable
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)

  ;; request OpenGL 3.3 core profile
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
  ;; required on macOS
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GLFW/GLFW_TRUE)
  ;; for nanovg
  (GLFW/glfwWindowHint GLFW/GLFW_STENCIL_BITS 8)


  ;; create the window and store it in the state
  (let [window (GLFW/glfwCreateWindow initial-window-width initial-window-height "Hello, World!" 0 0)]
    (if (zero? window)
      (throw (IllegalStateException. "Unable to create the GLFW window"))
      (assoc state :window window))))

(defn init-event-handlers
  [{:keys [window] :as state}]
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
                        :mods mods}))))

  ;; when the mouse moves in the window set the position of the
  ;; example sprite to the cursor
  (GLFW/glfwSetCursorPosCallback
   window
   (reify GLFWCursorPosCallbackI
     (invoke [this win xpos ypos]
       (enqueue-event! {:event-type :mouse-movement
                        :pos [xpos ypos]}))))

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

  ;; set resize callback
  (GLFW/glfwSetFramebufferSizeCallback
   window
   (reify GLFWFramebufferSizeCallbackI
     (invoke [this win w h]
       ;; let the game respond to window resizing by emitting an event
       (enqueue-event! {:event-type :window-resize
                        :size [w h]}))))
  state)

(defn position-window
  [{:keys [window] :as state}]
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
  state)

(defn init-context
  [{:keys [window] :as state}]
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

  ;; create a dummy VAO for compatibility stuff?
  (GL30/glBindVertexArray (GL30/glGenVertexArrays))

  state)

(defn init-ortho
  [state]
  (reset-ortho-projection state))

(defn init-nanovg
  "Initialise text rendering stuff"
  [state]
  ;; create NanoVG context
  (let [vg (NanoVGGL3/nvgCreate (bit-or NanoVGGL3/NVG_ANTIALIAS
                                        NanoVGGL3/NVG_STENCIL_STROKES))
        ;; load a font
        font-name "UbuntuMono-Regular"
        font (text/create-font vg font-name "font/UbuntuMono-Regular.ttf")]
    ;; @TODO: register other default fonts?
    ;; stick them in the state
    (-> state
        (assoc :vg vg)
        (assoc :vg-color (NVGColor/create))
        (assoc :default-font font-name))))

(defn init-transparency
  "enable transprency for drawing images"
  [state]
  (GL11/glEnable GL11/GL_BLEND)
  (GL30/glBlendFuncSeparate GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA GL11/GL_ONE GL11/GL_ONE_MINUS_SRC_ALPHA)
  state)

(defn init-audio
  [state]
  (assoc state :audio (audio/init-audio)))

(defn init-shaders
  [state]
  (assoc state :shader-programs (shader/default-shader-programs)))

(defn init
  [{[initial-window-width initial-window-height] :size
    :as game-config}]
  (-> game-config
      init-error-callback
      init-glfw
      init-event-handlers
      position-window
      init-context
      init-ortho
      shape/init-shape-rendering
      init-nanovg
      init-transparency
      init-audio
      init-shaders))

(defn draw-background!
  [[r g b a]]
  ;; set the clear colour
  (GL11/glClearColor r g b a)
  ;; clear the frameBuffer
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT)))

;; this didn't want to live in input since it needs access to
;; `reset-ortho-projection`
(defn default-window-resize
  "Default handler for `:window-resize` events."
  [state e]
  (reset-ortho-projection state))

(defn process-event
  [{:keys [current-scene scenes] :as state}
   {:keys [event-type] :as e}]
  (let [scene (get scenes current-scene)
        kw (keyword (str (name event-type) "-fns"))
        applicable-fns (get scene kw)
        default-fns (get (assoc i/default-event-fns
                                :window-resize-fns
                                [default-window-resize])
                         kw)]
    (reduce (fn [acc f]
              (f acc e))
            state
            (concat default-fns
                    applicable-fns))))

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
  (draw-background! p/black)
  (text/draw-text! state
                   (u/window-pos window [0.05 0.5])
                   (str "No draw-fn found for current scene " current-scene)))

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
   :restart-fn identity
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
  [{:keys [window] :as state} current total]
  (draw-background! p/black)
  (let [[w h] (u/window-size window)]
    (shape/draw-rect!
     state
     [(* w 0.1) (* h 0.5)]
     [(* w 0.8) (* h 0.1)]
     p/white)
    (shape/fill-rect!
     state
     [(* w 0.1) (* h 0.5)]
     [(* w 0.8 (max 0.001 (/ current total))) (* h 0.1)]
     p/white))
  (GLFW/glfwSwapBuffers window))

(defn preload-assets!
  "Pre-load assets and display a loading bar"
  [{:keys [window] :as state}
   {audio-assets :audio
    image-assets :image}]
  (draw-background! p/black)
  (GLFW/glfwSwapBuffers window)
  (let [assets (concat (map (partial vector audio/load-ogg-file!) audio-assets)
                       (map (partial vector image/load-texture!) image-assets))
        total (count assets)
        window-size (u/window-size window)]
    (doseq [[i [f [k path]]] (map-indexed vector assets)]
      (draw-preload-progress! state i total)
      (println (str "Loading " k " from: " path))
      (f k path))))

(defn start!
  [{:keys [init-scenes-fn on-start-fn on-close-fn] :as game}]
  (let [{:keys [window audio assets] :as initialised-lwjgl} (init game)
        _ (preload-assets! initialised-lwjgl assets)
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
             ;; close the window or has called `clunk.core/quit!`
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

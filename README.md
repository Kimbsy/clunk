# clunk

[![Clojars Project](https://img.shields.io/clojars/v/com.kimbsy/clunk.svg)](https://clojars.org/com.kimbsy/clunk)

A Clojure game engine built using LWJGL ([Light Weight Java Game Library](https://www.lwjgl.org/guide)).

## Overview

Clunk is the successor to [quip](https://github.com/Kimbsy/quip), it's cleaner, (hopefully) faster, and allows you access to OpenGL for when the going gets tough.

It's currently very early in it's development, but it has support for sprites (static images, animated spritesheets and text), audio, scenes and transitions, collision detection, and tweens (inspired by Phaser 3 tweens).

It's still a little rough round the edges, please feel free to raise issues for anything that trips you up.

## Getting Started quickly

Create a new game using the Leiningen template:

``` Bash
lein new com.kimbsy/clunk my-game
```

Take a look at the games in the [examples](/examples) directory, these simple games demonstrate various clunk features.

## Getting started slowly

To make a simple game start with the `clunk.core/game` function and run it with `clunk.core/start!`:

``` Clojure
(ns my-game
  ;; common aliases
  (:require [clunk.collision :as collision]
            [clunk.core :as c]
            [clunk.delay :as delay]
            [clunk.input :as i]
            [clunk.palette :as p]
            [clunk.sprite :as sprite]
            [clunk.tween :as tween]
            [clunk.util :as u]
            [clunk.scene :as scene]
            [clunk.audio :as audio]))

(def game (c/game {:title "Example Clunk Game"
                   :size [1200 800]
                   :init-scenes-fn init-scenes ;; @TODO: implement me!
                   :current-scene :demo
                   :assets {:image {:captain-spritesheet "resources/img/captain.png"
                                    :heart "resources/img/heart.png"}
                            :audio {:music "resources/audio/music/music.ogg"}}}))

(defn -main [& args]
  (c/start! game))
```

## Scenes

Define the scenes in your game as a map:

``` Clojure
(defn init-scenes
  [state]
  {:demo {:sprites (demo-sprites state)
          :update-fn update-demo
          :draw-fn draw-demo!}
   :menu {:sprites (menu-sprites state)
          :update-fn update-menu
          :draw-fn draw-menu!}})
```

Scene update functions should take the current state and return the new state. Many clunk namespaces have ready-made update functions. The `clunk.sprite/update-state` function moves sprites based on their velocity and updates animations, the `clunk.collision/update-state` function checks for sprite collisions and applies appropriate `on-collide` functions (see section on [collisions](#collisions) below), the `clunk.delay/update-state` function updates any ongoing delays and handles ones which are finished (see secion on [delays](#delays) below) the `clunk.tween/update-state` function updates sprite tweens (see section on [tweens](#tweens) below).

``` Clojure
(defn update-demo
  [state]
  (-> state
      sprite/update-state
      collision/update-state
      delay/update-state
      tween/update-state))
```

Scene draw functions take the current game state and should draw it to the screen. Often this will just consist of a call to `clunk.core/draw-background!` to set the background colour, then a call to `clunk.sprite/draw-scene-sprites!`:

``` Clojure
(defn draw-demo!
  [state]
  (c/draw-background! (p/hex->rgba "#3A435E"))
  (sprite/draw-scene-sprites! state))
```

## Sprites

Take a look at the [basic-sprite](/examples/basic-sprite) example game.

A sprite has a position, a velocity and a number of other fields for managing collision detection and animation as appropriate. Each built-in sprite creation function takes at least the `:sprite-group` (any keyword) and an `[x y]` position vector.

In order to use images for your sprites, you must declare them in the game config `:assets` in the `:image` map. You can refer to them by the key they are associated with.

The default `clunk.sprite/sprite` function returns a minimal sprite which draws itself as a white box. You can give it a new `:draw-fn` using keyword args to override this.

The `clunk.sprite/geometry-sprite` function returns a sprite which draws a polygon shape specified by a sequence of points (`[x y]` vectors) relative to the sprite `pos`. These points should be listed in counter-clockwise (CCW) order. It is recommended to supply `:size` dimensions for geometry sprites to ensure alignment and offsets work correctly.

The `clunk.sprite/image-sprite` function creates a sprite which draws an image. (This image must be declared as an `:asset` in the game config).

The `clunk.sprite/animated-sprite` function creates an image sprite which draws sections of a sprite sheet image, configured by its `:animations` and `:current-animation` keyword args.

The `clunk.sprite/text-sprite` function creates a sprite which draws itself as text, with options for choosing the font, size color etc.

``` Clojure
(defn demo-sprites
  [state]
  [(sprite/sprite :player      ; sprite group, used for collision detection and other things
                  [100 100])   ; `[x y]` position

   (sprite/geometry-sprite :poly
                           [350 350]       ; position
                           [[0 0]          ; `[x y]` shape vertices relative to position
                            [10 0]
                            [5 10]]
                           :size [10 5])   ; if unspecified size will be calculated from the vertices

   (sprite/image-sprite :health
                        [200 200]     ; position
                        [32 32]       ; image size
                        :heart)       ; image asset key (specified in call to `clunk.core/game`)

   (sprite/animated-sprite :captain
                           [300 300]               ; position
                           [240 360]               ; sprite image size
                           :captain-spritesheet    ; image asset key
                           [1680 1440]             ; full spritesheet size
                           :animations {:none {:frames 1            ; number of frames in animation
                                               :y-offset 0          ; y-position in sprite sheet
                                               :frame-delay 100}    ; how many game frames before advancing animation frame
                                        :idle {:frames 4
                                               :y-offset 1
                                               :frame-delay 15}
                                        :run  {:frames 4
                                               :y-offset 2
                                               :frame-delay 8}
                                        :jump {:frames 7
                                               :y-offset 3
                                               :frame-delay 8}}
                           :current-animation :none)            ; initial sprite animation

   (sprite/text-sprite :title-text
                       [200 50]                     ; position
                       "Press enter to play!")])    ; content
```

Each sprite has a `:debug?` field which, if `true`, will draw the collision-detection boundary for the sprite as well as its precise position.

For custom fields each sprite creation function takes an optional `:extra` keyword arg where you can pass a map which will get merged into the sprite map.

``` Clojure
(sprite/sprite :player
               [100 100]
               :debug? true
               :extra {:health 100
                       :damage 20})
```

## Collisions

Take a look at the [collision-detection](/examples/collision-detection) example game.

To detect collisions between sprites in your scene you must do two things.

- Ensure your scene update function calls `clunk.collision/update-state`.
- Add a collection of colliders to the scene under the `:colliders` key.

``` Clojure
(defn update-demo
  [state]
  (-> state
      sprite/update-state
      collision/update-state  ;; <= this one
      delay/update-state
      tween/update-state))

(defn init-scenes
  [state]
  {:demo {:sprites (demo-sprites state)
          :colliders (demo-colliders)   ;; @TODO: implement me!
          :update-fn update-demo
          :draw-fn draw-demo!}})x
```

A collider can be created with the `clunk.collision/collider` function. It takes the `:sprite-group` of the first kind of sprite (group-a-key), the `:sprite-group` of the second kind of sprite (group-b-key), the function to call which modifies A when a collision is detected (collide-fn-a), and the function which modifies B when a collision is detected (collide-fn-b).

The two collide-fn functions take both sprites as arguments, and should return the updated version of their first argument (collide-fn-a return the new sprite a, collide-fn-b returns the new b) the other sprite is pased in for reference only. If a collide-fn function returns `nil` then that sprite will be removed from the scene.

``` Clojure
(defn demo-colliders
  []
  [(collision/collider
    :captain
    :health
    (fn [captain _health]
      (-> animated-captain
          (update :health + 100)
          (sprite/set-animation :jump)))
    (fn [health _captain]
      nil))])
```

> [!TIP]
> If you need to affect the game state in a collide-fn function, you can emit a custom event and register an event handler (see section on [Custom events](#custom-events) below).

The default collision detection function check if the `width x height` rectangles of the sprites overlap, this is fast and often good enough, but sometimes you need something more accurate. You can specify the collision detection function with the `:collision-detection-fn` keyword arg. These functions take the group-a sprite and group-b sprite and return a boolean. The `clunk.collision` namespace has many built-in collision detection functions and helpers, if you want to write a custom collision detection function you may find them a useful starting point.

## Tweens

Take a look at the [tweens](/examples/tweens) example game.

To use tweens in your scene you must add the `clunk.tween/update-state` function to your scene update function.

``` Clojure
(defn update-demo
  [state]
  (-> state
      sprite/update-state
      collision/update-state
      delay/update-state
      tween/update-state))   ;; <= this one
```

Tweens are an incredibly flexible tool. They allow you to modify an attribute of a sprite by an amount, over a duration (specified by `:step-count`, the number of frames over which the change will occur), following a specific progress curve (`:easing-fn`, a large number of built-in easing functions are provided in the `clunk.tween` namespace).

This change can then be performed in reverse (by setting `:yoyo?` to `true`), and the whole process can be repeated any number of times (set `:repeat-times` to `##Inf` for continuous looping).

In addition you can specify trigger functions which happen when the tween starts to yoyo (`:on-yoyo-fn`), when the whole cycle starts to repeat (`:on-repeat-fn`), and when the tween completes (`:on-complete-fn`).

For fields which are not a single numeric value (like `pos`, `vel`, etc.) you can specify an `:update-fn` and (if needed) a `:yoyo-update-fn` which take the current value of the field, and a delta to modify it by (over the course of the tween the delta add up to the desired change) you can modify the field in whatever way makes sense. For 2d vector fields like `pos` and `vel`, the functions `clunk.tween/tween-<x|y>-fn` and `clunk.tween/tween-<x|y>-yoyo-fn` are provided to modify the `x` and `y` values of these fields.

You can create a tween with `clunk.tween/tween` and attach it to a sprite with `clunk.tween/add-tween`.

``` Clojure
;; create a sprite
(-> (sprite/image-sprite :bouncing-heart
                         [200 300]
                         [32 32]
                         :heart)
    (tween/add-tween
     ;; spin 360, then reverse
     (tween/tween :rotation
                  360
                  :yoyo? true
                  :repeat-times ##Inf))
    (tween/add-tween
     ;; move from left to right, then reverse
     (tween/tween :pos
                  100
                  :update-fn tween/tween-x-fn
                  :yoyo? true
                  :yoyo-update-fn tween/tween-x-yoyo-fn
                  :repeat-times ##Inf))
    (tween/add-tween
     ;; move up and then reverse, describing a parabola because of the `:easing-fn`
     (tween/tween :pos
                  -200
                  :step-count 50
                  :easing-fn tween/ease-out-quad
                  :update-fn tween/tween-y-fn
                  :yoyo? true
                  :yoyo-update-fn tween/tween-y-yoyo-fn
                  :repeat-times ##Inf)))
```

## Audio

Take a look at the [sounds](/examples/sounds) example game.

To play an audio file (currently `*.ogg` files are supported, conversion tools are widely available), you must declare it in the game config `:assets` in the `:audio` map. This ensures the file is loaded before the game starts and can be played using the key it is associated with.

You can play loaded files with `clunk.audio/play!` passing in the reference key you set. You can loop the audio playback by setting the optional `:loop?` keyword arg to `true`. This function additionally returns a reference to the audio source, you can call `clunk.audio/stop!` passing in this source reference to stop it early.

> [!IMPORTANT]
> If you loop an audio file (like a music track) you should capture a reference to the source returned by the call to `clunk.audio/play!` this will let you stop it and play something else later.

## Input

@TODO: add example games demonstrating different kinds of input handling.

There are three ways of handling use input in clunk.

### The `:held-keys` field

The game state maintains the set of currently held keys in the `:held-keys` field.

``` Clojure
(if (contains? (:held-keys state) i/K_ENTER)
  (prn "ENTER IS HELD")
  (prn "ENTER IS NOT HELD"))
```

### Clickable sprites

You can make a sprite clickable with the `clunk.input/add-on-click` function which takes a sprite and an on-click function, this on-click function takes the state and the sprite and returns an updated state.

``` Clojure
(-> (sprite/image-sprite :balloon
                         [200 200]
                         [32 32]
                         :balloon)
    (i/add-on-click (fn [state s]
                      (prn "POP!")
                      (remove-balloon state s))))  ;; @TODO: implement me!
```

### Event handlers

The most flexible option is to define event handlers. Mouse buttons, mouse movement, window resize and keyboard events are supported.

First define an event handler function, this takes the current state, the triggered event, and should return the new state.

``` Clojure
(defn mouse-handler-1
  [state e]
  (audio/play! :laser-gun)
  state)
```

This handler function must be attached to your scene by putting it in the appropriate `<event-type>-fns` collection.

``` Clojure
(defn init-scenes
  [state]
  {:demo {:sprites (demo-sprites state)
          :update-fn update-demo
          :draw-fn draw-demo!
          :mouse-button-fns [left-click-fire right-click-reload]
          :mouse-movement-fns [move-reticule]
          :key-fns [player-movement]
          :window-resize-fns [update-world-bounds]}})
```

You technically only need one handler for each type of event, but having multiple small ones can make control flow simpler. Event handlers are invoked in the order they are listed.

The `clunk.input/is` function is a helpful way of pattern matching on the events. Mouse events will have `:button` and `:action` fields, key events will have `:key` and `:action` fields. The `:action` field defaults to `i/PRESS`.

``` Clojure
(defn key-handler-1
  [state e]
  (if (i/is e :key i/K_SPACE :action i/RELEASE)
    (update state :some-useful-flag not)
    state))
```

### Custom events

You can create your own events which will be handled in the same way using the `clunk.core/enqueue-event!` function. Your event should be a map containing an `:event-type` and any data that your handler will need. Your handlers should be registered in the scene under the `:<event-type>-fns` key.

``` Clojure
;; Anywhere in your game code
(c/enqueue-event! {:event-type :my-event
                   :time 42
                   :space 9000})

;; Event handler
(defn handle-custom-events
  [state {:keys [time space]}
   (modify-foo state time space)])

;; Register event handlers in the scene
(defn init-scenes
  [state]
  {:demo {:sprites (demo-sprites state)
          :update-fn update-demo
          :draw-fn draw-demo!
          :my-event-fns [handle-custom-events]}})
```

This can be handy as a bit of an impure escape hatch since your handler (when processed, normally on the next frame) is passed the whole game state and can modify anything it likes.

> [!CAUTION]
> Generally you should avoid _overusing_ this feature since it leads to hard to debug code.

## Delays

Take a look at the [delays](/examples/delays) example game.

Delays allow you to execute code after a certain amount of time has passed. This is very useful for complex animations, cutscenes, spawning enemies/items etc.

To use delays in your scene you must add the `clunk.delay/update-state` function to your scene update function.

``` Clojure
(defn update-demo
  [state]
  (-> state
      sprite/update-state
      collision/update-state
      delay/update-state      ;; <= this one
      tween/update-state))
```

Delays can be created with the `clunk.delay/delay-fn` function, it takes a duration in milliseconds and a function which takes the current game state (when the delay finishes) and returns the new game state.

``` Clojure
(delay/delay-fn 1000 (fn [state] (spawn-enemy state)))
```

You can add these delays to your scene with the `clunk.delay/add-delay-fn` function.

``` Clojure
(delay/add-delay-fn
 state
 (delay/delay-fn ... )))
```

When a delay duration passes, the function is invoked. Only delays on the _current_ scene are processed, so if we switch scenes any active delays will be paused until we return.

Often it's desirable to add a bunch of delays to the scene at the same time with timings that are relative to each other (very helpful for cutscenes, especially when fine-tuning the timings). You can create a list of delays with the `clunk.delay/sequential-delay-fns` function which takes a collection of `[duration function]` tuples. These can be added to the scene with the `clunk.delay/add-delay-fns` function.

``` Clojure
(delay/add-delay-fns
 state
 (delay/sequential-delay-fns
  [[0 (fn [state] (player-enters state))]
   [1000 (fn [state] (bad-guy-enters state))]
   [100 (fn [state] (epic-fight-music state))]]
  :initial-delay 1000))
```

## Utils

The `clunk/util`, `clunk.shape` and `clunk/palette` namespaces provide a number of helper functions for positioning sprites, working with 2d vectors, working with polygon point collections, drawing primitive shapes, and creating and modifying colours.

## Shaders

@TOOD: add docs describing how to add and use custom shaders

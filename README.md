# clunk

A Clojure game engine built using LWJGL ([Light Weight Java Game Library](https://www.lwjgl.org/guide)).

## Overview

Clunk is the successor to [quip](https://github.com/Kimbsy/quip), it's cleaner, (hopefully) faster, and allows you access to OpenGL for when the going gets tough.

It's currently very early in it's development, but it has support for sprites (static images, animated spritesheets and text), audio, scenes and transitions, collision detection, and tweens (inspired by Phaser 3 tweens).

It's still a little rough round the edges, please feel free to raise issues for anything that trips you up.

## Getting Started

To make a simple game start with the `clunk.core/game` function and run it with `clunk.core/start!`:

``` Clojure
(ns my-game
  ;; common aliases
  (:require [clunk.collision :as collision]
            [clunk.core :as c]
            [clunk.input :as i]
            [clunk.image :as image]
            [clunk.palette :as p]
            [clunk.sprite :as sprite]
            [clunk.tween :as tween]
            [clunk.util :as u]
            [clunk.scene :as scene]
            [clunk.audio :as audio]))

(def game (c/game {:title "Example Clunk Game"
                   :size [1200 800]
                   :init-scenes-fn init-scenes ;; @TODO: implement me!
                   :current-scene :demo}))

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

Scene update functions should take the current state and return the new state. Many clunk namespaces have ready-made update functions. The `clunk.sprite` update function moves sprites based on their velocity and updates animations, the `clunk.collision` update function checks for sprite collisions and applies appropriate `on-collide` functions (see section on collisions below), the `clunk.tween` update function updates sprite tweens (see section on tweens below).

``` Clojure
(defn update-demo
  [state]
  (-> state
      sprite/update-state
      collision/update-state
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

A sprite has a position, a velocity and a number of other fields for managing collision detection and animation as appropriate. Each built-in sprite function takes at least the `:sprite-group` (any keyword) and an `[x y]` position vector.

The default `clunk.sprite/sprite` function returns a minimal sprite which draws itself as a red box. You can give it a new `:draw-fn` using keyword args to override this.

The `clunk.sprite/image-sprite` function creates a sprite which draws an image.

The `clunk.sprite/animated-sprite` function creates an image sprite which draws sections of a sprite sheet, configured by it's `:animations` and `:current-animation` keyword arg.

The `clunk.sprite/text-sprite` function creates a sprite which draws itself as text, with options for choosing the font, size color etc.


``` Clojure
(defn demo-sprites
  [state]
  [(sprite/sprite :player [100 100])

   (sprite/image-sprite :health
                        [200 200]
                        [32 32]
                        (image/load-texture "resources/img/heart.png"))

   (sprite/animated-sprite :captain
                           [300 300]
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
                           :current-animation :none)

   (sprite/text-sprite :title-text
                       [200 50]
                       "Press enter to play!")])
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

To detect collisions between sprites in your scene you must do two things.

- Ensure your scene update function calls `clunk.colision/update-state`.
- Add a collection of colliders to the scene under the `:colliders` key.

``` Clojure
(defn update-demo
  [state]
  (-> state
      sprite/update-state
      collision/update-state  ;; <= this one
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

## Tweens

To use tweens in your scene you must add the `clunk.tween/update-state` function to your scene update function.

``` Clojure
(defn update-demo
  [state]
  (-> state
      sprite/update-state
      collision/update-state
      tween/update-state))   ;; <= this one
```

Tweens are an incredibly flexible tool. They allow you to modify an attribute of a sprite by a some amount, over an optionally defined duration (`:step-count`, the number of frames over which the change will occur), following a specific progress curve (`:easing-fn`, a large number of built-in easing functions are provided in the `clunk.tween` namespace).

This change can then be performed in reverse (by setting `:yoyo?` to `true`), and the whole process can be repeated any number of times (set `:repeat-times` to `##Inf` for continuous looping).

In addition you can specify trigger functions which happen when the tween starts to yoyo (`:on-yoyo-fn`), when the whole cycle starts to repeat (`:on-repeat-fn`), and when the tween completes (`:on-complete-fn`).

For fields which are not a single numeric value (like `pos`, `vel`, etc.) you can specify an `:update-fn` and (if needed) a `:yoyo-update-fn` which take the current value of the field, and a delta to modify it by (over the course of the tween the delta add up to the desired change) you can modify the field in whatever way makes sense. For 2d vector fields like `pos` and `vel`, the functions `clunk.tween/tween-<x|y>-fn` and `clunk.tween/tween-<x|y>-yoyo-fn` are provided to modify the `x` and `y` values of these fields.

You can create a tween with `clunk.tween/tween` and attach it to a sprite with `clunk.tween/add-tween`.

``` Clojure
;; create a sprite
(-> (sprite/image-sprite :bouncing-heart
                         [200 300]
                         [32 32]
                         (image/load-texture "resources/img/heart.png"))
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

@TODO

## Utils

@TODO

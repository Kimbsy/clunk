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

@TODO

## Collisions

@TODO

## Tweens

@TODO

## Audio

@TODO

## Utils

@TODO

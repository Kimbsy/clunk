(ns clunk.input
  (:require [clunk.collision :as collision])
  (:import (org.lwjgl.glfw GLFW)
           (org.lwjgl.system MemoryStack)))

(def PRESS GLFW/GLFW_PRESS)
(def RELEASE GLFW/GLFW_RELEASE)
(def REPEAT GLFW/GLFW_REPEAT)

;; @TODO: support modifier keys

(def M_1 GLFW/GLFW_MOUSE_BUTTON_1)
(def M_2 GLFW/GLFW_MOUSE_BUTTON_2)
(def M_3 GLFW/GLFW_MOUSE_BUTTON_3)
(def M_4 GLFW/GLFW_MOUSE_BUTTON_4)
(def M_5 GLFW/GLFW_MOUSE_BUTTON_5)
(def M_6 GLFW/GLFW_MOUSE_BUTTON_6)
(def M_7 GLFW/GLFW_MOUSE_BUTTON_7)
(def M_8 GLFW/GLFW_MOUSE_BUTTON_8)
(def M_LAST GLFW/GLFW_MOUSE_BUTTON_LAST)
(def M_LEFT GLFW/GLFW_MOUSE_BUTTON_LEFT)
(def M_MIDDLE GLFW/GLFW_MOUSE_BUTTON_MIDDLE)
(def M_RIGHT GLFW/GLFW_MOUSE_BUTTON_RIGHT)

;; @TODO: support gamepad input
(def G_AXIS_LAST GLFW/GLFW_GAMEPAD_AXIS_LAST)
(def G_AXIS_LEFT_TRIGGER GLFW/GLFW_GAMEPAD_AXIS_LEFT_TRIGGER)
(def G_AXIS_LEFT_X GLFW/GLFW_GAMEPAD_AXIS_LEFT_X)
(def G_AXIS_LEFT_Y GLFW/GLFW_GAMEPAD_AXIS_LEFT_Y)
(def G_AXIS_RIGHT_TRIGGER GLFW/GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER)
(def G_AXIS_RIGHT_X GLFW/GLFW_GAMEPAD_AXIS_RIGHT_X)
(def G_AXIS_RIGHT_Y GLFW/GLFW_GAMEPAD_AXIS_RIGHT_Y)
(def G_BUTTON_A GLFW/GLFW_GAMEPAD_BUTTON_A)
(def G_BUTTON_B GLFW/GLFW_GAMEPAD_BUTTON_B)
(def G_BUTTON_BACK GLFW/GLFW_GAMEPAD_BUTTON_BACK)
(def G_BUTTON_CIRCLE GLFW/GLFW_GAMEPAD_BUTTON_CIRCLE)
(def G_BUTTON_CROSS GLFW/GLFW_GAMEPAD_BUTTON_CROSS)
(def G_BUTTON_DPAD_DOWN GLFW/GLFW_GAMEPAD_BUTTON_DPAD_DOWN)
(def G_BUTTON_DPAD_LEFT GLFW/GLFW_GAMEPAD_BUTTON_DPAD_LEFT)
(def G_BUTTON_DPAD_RIGHT GLFW/GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)
(def G_BUTTON_DPAD_UP GLFW/GLFW_GAMEPAD_BUTTON_DPAD_UP)
(def G_BUTTON_GUIDE GLFW/GLFW_GAMEPAD_BUTTON_GUIDE)
(def G_BUTTON_LAST GLFW/GLFW_GAMEPAD_BUTTON_LAST)
(def G_BUTTON_LEFT_BUMPER GLFW/GLFW_GAMEPAD_BUTTON_LEFT_BUMPER)
(def G_BUTTON_LEFT_THUMB GLFW/GLFW_GAMEPAD_BUTTON_LEFT_THUMB)
(def G_BUTTON_RIGHT_BUMPER GLFW/GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)
(def G_BUTTON_RIGHT_THUMB GLFW/GLFW_GAMEPAD_BUTTON_RIGHT_THUMB)
(def G_BUTTON_SQUARE GLFW/GLFW_GAMEPAD_BUTTON_SQUARE)
(def G_BUTTON_START GLFW/GLFW_GAMEPAD_BUTTON_START)
(def G_BUTTON_TRIANGLE GLFW/GLFW_GAMEPAD_BUTTON_TRIANGLE)
(def G_BUTTON_X GLFW/GLFW_GAMEPAD_BUTTON_X)
(def G_BUTTON_Y GLFW/GLFW_GAMEPAD_BUTTON_Y)

(def K_0 GLFW/GLFW_KEY_0)
(def K_1 GLFW/GLFW_KEY_1)
(def K_2 GLFW/GLFW_KEY_2)
(def K_3 GLFW/GLFW_KEY_3)
(def K_4 GLFW/GLFW_KEY_4)
(def K_5 GLFW/GLFW_KEY_5)
(def K_6 GLFW/GLFW_KEY_6)
(def K_7 GLFW/GLFW_KEY_7)
(def K_8 GLFW/GLFW_KEY_8)
(def K_9 GLFW/GLFW_KEY_9)
(def K_A GLFW/GLFW_KEY_A)
(def K_APOSTROPHE GLFW/GLFW_KEY_APOSTROPHE)
(def K_B GLFW/GLFW_KEY_B)
(def K_BACKSLASH GLFW/GLFW_KEY_BACKSLASH)
(def K_BACKSPACE GLFW/GLFW_KEY_BACKSPACE)
(def K_C GLFW/GLFW_KEY_C)
(def K_CAPS_LOCK GLFW/GLFW_KEY_CAPS_LOCK)
(def K_COMMA GLFW/GLFW_KEY_COMMA)
(def K_D GLFW/GLFW_KEY_D)
(def K_DELETE GLFW/GLFW_KEY_DELETE)
(def K_DOWN GLFW/GLFW_KEY_DOWN)
(def K_E GLFW/GLFW_KEY_E)
(def K_END GLFW/GLFW_KEY_END)
(def K_ENTER GLFW/GLFW_KEY_ENTER)
(def K_EQUAL GLFW/GLFW_KEY_EQUAL)
(def K_ESCAPE GLFW/GLFW_KEY_ESCAPE)
(def K_F GLFW/GLFW_KEY_F)
(def K_F1 GLFW/GLFW_KEY_F1)
(def K_F10 GLFW/GLFW_KEY_F10)
(def K_F11 GLFW/GLFW_KEY_F11)
(def K_F12 GLFW/GLFW_KEY_F12)
(def K_F13 GLFW/GLFW_KEY_F13)
(def K_F14 GLFW/GLFW_KEY_F14)
(def K_F15 GLFW/GLFW_KEY_F15)
(def K_F16 GLFW/GLFW_KEY_F16)
(def K_F17 GLFW/GLFW_KEY_F17)
(def K_F18 GLFW/GLFW_KEY_F18)
(def K_F19 GLFW/GLFW_KEY_F19)
(def K_F2 GLFW/GLFW_KEY_F2)
(def K_F20 GLFW/GLFW_KEY_F20)
(def K_F21 GLFW/GLFW_KEY_F21)
(def K_F22 GLFW/GLFW_KEY_F22)
(def K_F23 GLFW/GLFW_KEY_F23)
(def K_F24 GLFW/GLFW_KEY_F24)
(def K_F25 GLFW/GLFW_KEY_F25)
(def K_F3 GLFW/GLFW_KEY_F3)
(def K_F4 GLFW/GLFW_KEY_F4)
(def K_F5 GLFW/GLFW_KEY_F5)
(def K_F6 GLFW/GLFW_KEY_F6)
(def K_F7 GLFW/GLFW_KEY_F7)
(def K_F8 GLFW/GLFW_KEY_F8)
(def K_F9 GLFW/GLFW_KEY_F9)
(def K_G GLFW/GLFW_KEY_G)
(def K_GRAVE_ACCENT GLFW/GLFW_KEY_GRAVE_ACCENT)
(def K_H GLFW/GLFW_KEY_H)
(def K_HOME GLFW/GLFW_KEY_HOME)
(def K_I GLFW/GLFW_KEY_I)
(def K_INSERT GLFW/GLFW_KEY_INSERT)
(def K_J GLFW/GLFW_KEY_J)
(def K_K GLFW/GLFW_KEY_K)
(def K_KP_0 GLFW/GLFW_KEY_KP_0)
(def K_KP_1 GLFW/GLFW_KEY_KP_1)
(def K_KP_2 GLFW/GLFW_KEY_KP_2)
(def K_KP_3 GLFW/GLFW_KEY_KP_3)
(def K_KP_4 GLFW/GLFW_KEY_KP_4)
(def K_KP_5 GLFW/GLFW_KEY_KP_5)
(def K_KP_6 GLFW/GLFW_KEY_KP_6)
(def K_KP_7 GLFW/GLFW_KEY_KP_7)
(def K_KP_8 GLFW/GLFW_KEY_KP_8)
(def K_KP_9 GLFW/GLFW_KEY_KP_9)
(def K_KP_ADD GLFW/GLFW_KEY_KP_ADD)
(def K_KP_DECIMAL GLFW/GLFW_KEY_KP_DECIMAL)
(def K_KP_DIVIDE GLFW/GLFW_KEY_KP_DIVIDE)
(def K_KP_ENTER GLFW/GLFW_KEY_KP_ENTER)
(def K_KP_EQUAL GLFW/GLFW_KEY_KP_EQUAL)
(def K_KP_MULTIPLY GLFW/GLFW_KEY_KP_MULTIPLY)
(def K_KP_SUBTRACT GLFW/GLFW_KEY_KP_SUBTRACT)
(def K_L GLFW/GLFW_KEY_L)
(def K_LAST GLFW/GLFW_KEY_LAST)
(def K_LEFT GLFW/GLFW_KEY_LEFT)
(def K_LEFT_ALT GLFW/GLFW_KEY_LEFT_ALT)
(def K_LEFT_BRACKET GLFW/GLFW_KEY_LEFT_BRACKET)
(def K_LEFT_CONTROL GLFW/GLFW_KEY_LEFT_CONTROL)
(def K_LEFT_SHIFT GLFW/GLFW_KEY_LEFT_SHIFT)
(def K_LEFT_SUPER GLFW/GLFW_KEY_LEFT_SUPER)
(def K_M GLFW/GLFW_KEY_M)
(def K_MENU GLFW/GLFW_KEY_MENU)
(def K_MINUS GLFW/GLFW_KEY_MINUS)
(def K_N GLFW/GLFW_KEY_N)
(def K_NUM_LOCK GLFW/GLFW_KEY_NUM_LOCK)
(def K_O GLFW/GLFW_KEY_O)
(def K_P GLFW/GLFW_KEY_P)
(def K_PAGE_DOWN GLFW/GLFW_KEY_PAGE_DOWN)
(def K_PAGE_UP GLFW/GLFW_KEY_PAGE_UP)
(def K_PAUSE GLFW/GLFW_KEY_PAUSE)
(def K_PERIOD GLFW/GLFW_KEY_PERIOD)
(def K_PRINT_SCREEN GLFW/GLFW_KEY_PRINT_SCREEN)
(def K_Q GLFW/GLFW_KEY_Q)
(def K_R GLFW/GLFW_KEY_R)
(def K_RIGHT GLFW/GLFW_KEY_RIGHT)
(def K_RIGHT_ALT GLFW/GLFW_KEY_RIGHT_ALT)
(def K_RIGHT_BRACKET GLFW/GLFW_KEY_RIGHT_BRACKET)
(def K_RIGHT_CONTROL GLFW/GLFW_KEY_RIGHT_CONTROL)
(def K_RIGHT_SHIFT GLFW/GLFW_KEY_RIGHT_SHIFT)
(def K_RIGHT_SUPER GLFW/GLFW_KEY_RIGHT_SUPER)
(def K_S GLFW/GLFW_KEY_S)
(def K_SCROLL_LOCK GLFW/GLFW_KEY_SCROLL_LOCK)
(def K_SEMICOLON GLFW/GLFW_KEY_SEMICOLON)
(def K_SLASH GLFW/GLFW_KEY_SLASH)
(def K_SPACE GLFW/GLFW_KEY_SPACE)
(def K_T GLFW/GLFW_KEY_T)
(def K_TAB GLFW/GLFW_KEY_TAB)
(def K_U GLFW/GLFW_KEY_U)
(def K_UNKNOWN GLFW/GLFW_KEY_UNKNOWN)
(def K_UP GLFW/GLFW_KEY_UP)
(def K_V GLFW/GLFW_KEY_V)
(def K_W GLFW/GLFW_KEY_W)
(def K_WORLD_1 GLFW/GLFW_KEY_WORLD_1)
(def K_WORLD_2 GLFW/GLFW_KEY_WORLD_2)
(def K_X GLFW/GLFW_KEY_X)
(def K_Y GLFW/GLFW_KEY_Y)
(def K_Z GLFW/GLFW_KEY_Z)

(defn is
  [e &
   {:keys [action button mods]
    k :key
    :or {action PRESS}}]
  (and (or (nil? action)
           (= action (:action e)))
       (or (nil? button)
           (= button (:button e)))
       (or (nil? k)
           (= k (:k e)))
       (or (nil? mods)
           (= mods (:mods e)))))

(defn mouse-pos
  [{:keys [window]}]
  (with-open [stack (MemoryStack/stackPush)]
    (let [p-x (.mallocDouble stack 1)
          p-y (.mallocDouble stack 1)]
      (GLFW/glfwGetCursorPos window p-x p-y)
      [(.get p-x 0)
       (.get p-y 0)])))

(defn default-key-pressed
  "Add the pressed key to the set of currently held keys."
  [state e]
  (if (= PRESS (:action e))
    (update state :held-keys #(conj % (:k e)))
    state))

(defn default-key-released
  "Remove the released key from the set of currently held keys."
  [state e]
  (if (= RELEASE (:action e))
    (update state :held-keys #(disj % (:k e)))
    state))

(defn default-mouse-pressed
  "Check all `:clickable?` sprites for collision with the mouse event,
  apply the `:on-click-fn` of all that have been clicked on."
  [{:keys [scenes current-scene] :as state} e]
  (if (= PRESS (:action e))
    (let [sprites (get-in scenes [current-scene :sprites])
          clickable (filter :clickable? sprites)]
      (reduce (fn [acc {:keys [on-click-fn] :as s}]
                ;; Using our most powerful (albeit expensive) collision detection.
                (if (collision/pos-in-rotating-poly? e s)
                  (on-click-fn acc s)
                  acc))
              state
              clickable))
    state))

(defn add-on-click
  "Make a sprite `:clickable?` by adding an `:on-click-fn` to be invoked
  by the default mouse-pressed handler.

  An `:on-click-fn` takes the game state and the clicked sprite and
  should return the new game state."
  [sprite f]
  (-> sprite
      (assoc :clickable? true)
      (assoc :on-click-fn f)))

(def default-event-fns
  {:key-fns [default-key-pressed
             default-key-released]
   :mouse-button-fns [default-mouse-pressed]})

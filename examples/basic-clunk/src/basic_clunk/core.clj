(ns basic-clunk.core
  (:gen-class)
  (:require [basic-clunk.scenes.demo :as demo]
            [basic-clunk.scenes.other :as other]
            [clunk.core :as c]))

(defn init-scenes
  [state]
  {:demo (demo/init state)
   :other (other/init state)})

(def game (c/game {:title "Example Clunk Game"
                   :size [1200 800]
                   :init-scenes-fn init-scenes
                   :current-scene :demo
                   :on-start-fn (fn [state]
                                  (prn "STARTING!!!!!")
                                  state)
                   :on-close-fn (fn [final-state]
                                  (prn "CLOSING!!!!!!")
                                  final-state)}))

(defn -main
  []
  (c/start! game))

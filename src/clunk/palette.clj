(ns clunk.palette)

(defn rand-color
  []
  [(rand) (rand) (rand) 1])

(defn hex->rgba
  ([hex-string]
   (hex->rgba hex-string 1))
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

(defn darken
  "Darken a colour by 30%, preserving alpha component if present."
  [[r g b a :as color]]
  (if a
    [(max 0 (* r 0.7)) (max 0 (* g 0.7)) (max 0 (* b 0.7)) (max 0 (* a 0.7))]
    [(max 0 (* r 0.7)) (max 0 (* g 0.7)) (max 0 (* b 0.7))]))

(defn lighten
  "Lighten a colour by 30%, preserving alpha component if present."
  [[r g b a :as color]]
  ;; (/ 1 0.7) => 1.4285714285714286
  (let [factor 1.4285714285714286]
    (if a
      [(min 1 (* r factor)) (min 1 (* g factor)) (min 1 (* b factor)) (min 1 (* a factor))]
      [(min 1 (* r factor)) (min 1 (* g factor)) (min 1 (* b factor))])))

(def black [0 0 0 1])
(def white [1 1 1 1])
(def grey [0.5 0.5 0.5 1])
(def gray grey)
(def red [1 0 0 1])
(def green [0 1 0 1])
(def blue [0 0 1 1])
(def cyan [0 1 1 1])
(def magenta [1 0 1 1])
(def yellow [1 1 0 1])

(ns clunk.palette)

(defn hex->rgb
  ([hex-string]
   (hex->rgb hex-string 0))
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

(def black [0 0 0])
(def white [1 1 1])
(def grey [0.5 0.5 0.5])
(def gray grey)
(def red [1 0 0])
(def green [0 1 0])
(def blue [0 0 1])
(def cyan [0 1 1])
(def magenta [1 0 1])
(def yellow [1 1 0])

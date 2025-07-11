(ns clunk.collision-test
  (:require [clojure.test :refer :all]
            [clunk.collision :as sut]
            [clunk.sprite :as sprite]))

(defn test-sprite
  [group pos]
  {:sprite-group group :pos pos :collide-count 0 :uuid (random-uuid)})

(def sprite-group-foo [(test-sprite :foo [0 0])
                       (test-sprite :foo [0 1])
                       (test-sprite :foo [0 1])
                       (test-sprite :foo [0 3])])
(def sprite-group-bar [(test-sprite :bar [0 0])
                       (test-sprite :bar [0 2])
                       (test-sprite :bar [0 3])
                       (test-sprite :bar [0 3])])
(def sprite-group-baz [(test-sprite :baz [0 0])
                       (test-sprite :baz [0 0])
                       (test-sprite :baz [0 0])])

(def sprites (concat sprite-group-foo sprite-group-bar sprite-group-baz))

(defn increment-collide-count
  [s _]
  (update s :collide-count inc))

;;; Testing application of colliders

(deftest standard-collisions-ab-collider
  (let [collider-ab (sut/collider :foo :bar increment-collide-count increment-collide-count
                                  :collision-detection-fn sut/equal-pos?)]
    (testing "colliding a with group-b"
      (let [results (sut/collide-group (first sprite-group-foo)
                                       sprite-group-bar
                                       collider-ab)]
        (is (= 1 (-> results
                     :a
                     :collide-count)))
        (is (= [1 0 0 0] (->> (:group-b results)
                              (map :collide-count))))
        (is (= 4 (count (:group-b results))))))

    (testing "colliding group-a with group-b"
      (let [results (sut/collide-groups {:foo sprite-group-foo
                                         :bar sprite-group-bar}
                                        collider-ab)]
        (is (= 4 (count (:foo results))))
        (is (= 4 (count (:bar results))))
        (is (= [1 0 0 2] (->> (:foo results)
                              (map :collide-count))))
        (is (= [1 0 1 1] (->> (:bar results)
                              (map :collide-count))))))

    (testing "applying our collider at the top level"
      (let [state {:current-scene :test
                   :scenes {:test {:sprites sprites
                                   :colliders [collider-ab]}}}

            results        (sut/update-state state)
            result-sprites (get-in results [:scenes :test :sprites])]
        (is (= 11 (count result-sprites)))
        (let [foo-sprites (filter #(#{:foo} (:sprite-group %)) result-sprites)
              bar-sprites (filter #(#{:bar} (:sprite-group %)) result-sprites)
              baz-sprites (filter #(#{:baz} (:sprite-group %)) result-sprites)]
          (is (= 4 (count foo-sprites)))
          (is (= 4 (count bar-sprites)))
          (is (= 3 (count baz-sprites)))
          (is (= [1 0 0 2] (map :collide-count foo-sprites)))
          (is (= [1 0 1 1] (map :collide-count bar-sprites)))
          (is (= [0 0 0] (map :collide-count baz-sprites))))))))


(deftest self-collisions-aa-collider
  (let [collider-aa (sut/collider :foo :foo increment-collide-count increment-collide-count
                                  :collision-detection-fn sut/equal-pos?)]
    (testing "colliding a with group-a"
      (let [results (sut/collide-group (second sprite-group-foo)
                                       sprite-group-foo
                                       collider-aa)]
        (is (= 1 (-> results
                     :a
                     :collide-count)))
        (is (= [0 0 1 0] (->> (:group-b results)
                              (map :collide-count))))
        (is (= 4 (count (:group-b results))))))

    (testing "colliding group-a with group-a"
      (let [results (sut/collide-groups {:foo sprite-group-foo
                                         :bar sprite-group-foo}
                                        collider-aa)]
        (is (= 1 (count results)))
        (is (= 4 (count (:foo results))))
        (is (= [0 1 1 0] (->> (:foo results)
                              (map :collide-count))))))

    (testing "applying our collider at the top level"
      (let [state {:current-scene :test
                   :scenes {:test {:sprites sprites
                                   :colliders [collider-aa]}}}

            results        (sut/update-state state)
            result-sprites (get-in results [:scenes :test :sprites])]
        (is (= 11 (count result-sprites)))
        (let [foo-sprites (filter #(#{:foo} (:sprite-group %)) result-sprites)
              bar-sprites (filter #(#{:bar} (:sprite-group %)) result-sprites)
              baz-sprites (filter #(#{:baz} (:sprite-group %)) result-sprites)]
          (is (= 4 (count foo-sprites)))
          (is (= 4 (count bar-sprites)))
          (is (= 3 (count baz-sprites)))
          (is (= [0 1 1 0] (map :collide-count foo-sprites)))
          (is (= [0 0 0 0] (map :collide-count bar-sprites)))
          (is (= [0 0 0] (map :collide-count baz-sprites))))))))

(deftest empty-group-collider
  (testing "when group b is empty, group a should be unchanged."
    (let [collider         (sut/collider :foo :non-existent-group increment-collide-count increment-collide-count)
          only-foo-sprites (filter #(#{:foo} (:sprite-group %)) sprites)

          state {:current-scene :test
                 :scenes {:test {:sprites only-foo-sprites
                                 :colliders [collider]}}}

          results        (sut/update-state state)
          result-sprites (get-in results [:scenes :test :sprites])]
      (is (= result-sprites only-foo-sprites)))))

(defn removing-collide-fn
  [x _]
  nil)

(deftest removing-sprites-collider
  (testing "sprites in group a are removed on collision"
    (let [removing-a-collider (sut/collider :foo :bar removing-collide-fn increment-collide-count
                                            :collision-detection-fn sut/equal-pos?)

          state {:current-scene :test
                 :scenes {:test {:sprites sprites
                                 :colliders [removing-a-collider]}}}

          results        (sut/update-state state)
          result-sprites (get-in results [:scenes :test :sprites])]
      (is (= 9 (count result-sprites)))
      (let [foo-sprites (filter #(#{:foo} (:sprite-group %)) result-sprites)
            bar-sprites (filter #(#{:bar} (:sprite-group %)) result-sprites)
            baz-sprites (filter #(#{:baz} (:sprite-group %)) result-sprites)]
        (is (= 2 (count foo-sprites)))
        (is (= 4 (count bar-sprites)))
        (is (= 3 (count baz-sprites)))
        (is (= [0 0] (map :collide-count foo-sprites)))
        (is (= [1 0 1 0] (map :collide-count bar-sprites)))
        (is (= [0 0 0] (map :collide-count baz-sprites))))))

  (testing "sprites in both groups are removed on collision"
    (let [removing-a-collider (sut/collider :foo :bar removing-collide-fn removing-collide-fn
                                            :collision-detection-fn sut/equal-pos?)

          state {:current-scene :test
                 :scenes {:test {:sprites sprites
                                 :colliders [removing-a-collider]}}}

          results        (sut/update-state state)
          result-sprites (get-in results [:scenes :test :sprites])]
      (is (= 7 (count result-sprites)))
      (let [foo-sprites (filter #(#{:foo} (:sprite-group %)) result-sprites)
            bar-sprites (filter #(#{:bar} (:sprite-group %)) result-sprites)
            baz-sprites (filter #(#{:baz} (:sprite-group %)) result-sprites)]
        (is (= 2 (count foo-sprites)))
        (is (= 2 (count bar-sprites)))
        (is (= 3 (count baz-sprites)))
        (is (= [0 0] (map :collide-count foo-sprites)))
        (is (= [0 0] (map :collide-count bar-sprites)))
        (is (= [0 0 0] (map :collide-count baz-sprites))))))

  (testing "sprite in group a is removed when hitting the first of multiple sprites in b"
    (let [removing-a-collider (sut/collider :foo :baz removing-collide-fn increment-collide-count
                                            :collision-detection-fn sut/equal-pos?)

          state {:current-scene :test
                 :scenes {:test {:sprites sprites
                                 :colliders [removing-a-collider]}}}

          results        (sut/update-state state)
          result-sprites (get-in results [:scenes :test :sprites])]
      (is (= 10 (count result-sprites)))
      (let [foo-sprites (filter #(#{:foo} (:sprite-group %)) result-sprites)
            bar-sprites (filter #(#{:bar} (:sprite-group %)) result-sprites)
            baz-sprites (filter #(#{:baz} (:sprite-group %)) result-sprites)]
        (is (= 3 (count foo-sprites)))
        (is (= 4 (count bar-sprites)))
        (is (= 3 (count baz-sprites)))
        (is (= [0 0 0] (map :collide-count foo-sprites)))
        (is (= [0 0 0 0] (map :collide-count bar-sprites)))
        (is (= [1 0 0] (map :collide-count baz-sprites)))))))

;;; Testing collision detection predicates

(deftest equal-pos?
  (let [a {:pos [1 3]}
        b {:pos [1 3]}
        c {:pos [4 4]}
        d {:pos []}]
    (is (and (sut/equal-pos? a b)
             (sut/equal-pos? b a)))
    (is (and (not (sut/equal-pos? a c))
             (not (sut/equal-pos? c a))))
    (is (and (not (sut/equal-pos? b c))
             (not (sut/equal-pos? c b))))
    (is (not (sut/equal-pos? a d)))
    (is (not (sut/equal-pos? d a)))))

(deftest w-h-rects-collide?
  (testing "intersections"
    ;; ┌───┐  ┌───┐
    ;; │ b │  │ c │
    ;; │ ┌─┼──┼─┐ │
    ;; └─┼─┘  └─┼─┘
    ;;   │  a   │
    ;; ┌─┼─┐  ┌─┼─┐
    ;; │ └─┼──┼─┘ │
    ;; │ d │  │ e │
    ;; └───┘  └───┘
    (let [a {:pos [5.5 4] :size [7 4]}
          b {:pos [2 1.5] :size [4 3]}
          c {:pos [9 1.5] :size [4 3]}
          d {:pos [2 6.5] :size [4 3]}
          e {:pos [9 6.5] :size [4 3]}]
      (testing "a collides with every other sprite"
        (is (and (sut/w-h-rects-collide? a b)
                 (sut/w-h-rects-collide? b a)))
        (is (and (sut/w-h-rects-collide? a c)
                 (sut/w-h-rects-collide? c a)))
        (is (and (sut/w-h-rects-collide? a d)
                 (sut/w-h-rects-collide? d a)))
        (is (and (sut/w-h-rects-collide? a e)
                 (sut/w-h-rects-collide? e a))))

      (testing "b collides with no other sprite"
        (is (and (not (sut/w-h-rects-collide? b c))
                 (not (sut/w-h-rects-collide? c b))))
        (is (and (not (sut/w-h-rects-collide? b d))
                 (not (sut/w-h-rects-collide? d b))))
        (is (and (not (sut/w-h-rects-collide? b e))
                 (not (sut/w-h-rects-collide? e b)))))

      (testing "c collides with no other sprite"
        (is (and (not (sut/w-h-rects-collide? c d))
                 (not (sut/w-h-rects-collide? d c))))
        (is (and (not (sut/w-h-rects-collide? c e))
                 (not (sut/w-h-rects-collide? e c)))))

      (testing "d collides with no other sprite"
        (is (and (not (sut/w-h-rects-collide? d e))
                 (not (sut/w-h-rects-collide? e d)))))))

  (testing "partial overlaps"
    ;; ┌────┬─┬────┐
    ;; │ a  │ │ b  │
    ;; ├────┼─┤    │
    ;; ├────┴─┼────┘
    ;; │ c    │
    ;; └──────┘
    (let [a {:pos [3.5 1.5] :size [7 3]}
          b {:pos [8.5 1.5] :size [7 3]}
          c {:pos [3.5 3.5] :size [7 3]}]
      ;; all sprites collide with each other
      (is (and (sut/w-h-rects-collide? a b)
               (sut/w-h-rects-collide? b a)))
      (is (and (sut/w-h-rects-collide? a c)
               (sut/w-h-rects-collide? c a)))
      (is (and (sut/w-h-rects-collide? b c)
               (sut/w-h-rects-collide? c b)))))

  (testing "overlaps exactly"
    ;; ╔══════╗
    ;; ║      ║
    ;; ║ a  b ║
    ;; ║      ║
    ;; ╚══════╝
    (let [a {:pos [3.5 2] :size [7 4]}
          b {:pos [3.5 2] :size [7 4]}]
      (is (and (sut/w-h-rects-collide? a b)
               (sut/w-h-rects-collide? b a)))))

  (testing "fully contains"
    ;; ┌────────┐
    ;; │   a    │
    ;; │┌──────┐│
    ;; ││  b   ││
    ;; │└──────┘│
    ;; └────────┘
    (let [a {:pos [4.5 2.5] :size [9 5]}
          b {:pos [4.5 3] :size [7 2]}]
      (is (and (sut/w-h-rects-collide? a b)
               (sut/w-h-rects-collide? b a))))))

(deftest pos-in-rect?
  (testing "pos-in-rect? and rect-contains-pos?"
    ;; ┌───────┐
    ;; │ .b    │
    ;; │   a   │
    ;; │       │ .d
    ;; └───.c──┘
    (let [a {:pos [4 2] :size [8 4]}
          b {:pos [2 1] :size [0 0]}
          c {:pos [4 4] :size [0 0]}
          d {:pos [10 3] :size [0 0]}]
      (is (and (sut/pos-in-rect? b a)
               (sut/rect-contains-pos? a b)))
      (is (and (sut/pos-in-rect? c a)
               (sut/rect-contains-pos? a c)))
      (is (and (not (sut/pos-in-rect? d a))
               (not (sut/rect-contains-pos? a d)))))))

(deftest pos-in-poly?
  (testing "underlying predicates"
    (testing "coarse collision detection"
      ;; ┌─────────┐
      ;; │ a      /
      ;; │       /       .d
      ;; │      /
      ;; │ .b  / .c
      ;; └────┘
      (let [a [[0 0] [10 0] [5 5] [0 5]]
            b [2 4]
            c [8 4]
            d [16 2]]
        (is (sut/coarse-pos-in-poly?* b a))
        (is (sut/coarse-pos-in-poly?* c a))
        (is (not (sut/coarse-pos-in-poly?* d a)))))

    (testing "fine collision detection"
      ;; ┌─────────┐
      ;; │ a      /
      ;; │       /       .d
      ;; │      /
      ;; │ .b  / .c
      ;; └────┘
      (let [a [[0 0] [10 0] [5 5] [0 5]]
            b [2 4]
            c [8 4]
            d [16 2]]
        (is (sut/fine-pos-in-poly?* b a))
        (is (not (sut/fine-pos-in-poly?* c a)))
        (is (not (sut/fine-pos-in-poly?* d a))))))

  (testing "pos-in-poly? and poly-contains-pos?"
    (testing "works on rectangles"
      ;; ┌────────┐
      ;; │ .b     │
      ;; │   a    │
      ;; │        │ .d
      ;; └───.c───┘
      (let [a {:pos [4.5 2] :size [9 4] :bounds-fn sprite/default-bounding-poly}
            b {:pos [2 1]}
            c {:pos [4 4]}
            d {:pos [11 3]}]
        (is (and (sut/pos-in-poly? b a)
                 (sut/poly-contains-pos? a b)))
        (is (and (sut/pos-in-poly? c a)
                 (sut/poly-contains-pos? a c)))
        (is (and (not (sut/pos-in-poly? d a))
                 (not (sut/poly-contains-pos? a d))))))

    (testing "works on simple polygons"
      ;; ┌─────────┐
      ;; │ a      /
      ;; │       /       .d
      ;; │      /
      ;; │ .b  / .c
      ;; └────┘
      (let [points [[0 0] [10 0] [5 5] [0 5]]
            a {:pos [5 2.5] :size [10 5] :bounds-fn :points :points points}
            b {:pos [2 4]}
            c {:pos [8 4]}
            d {:pos [16 2]}]
        (is (and (sut/pos-in-poly? b a)
                 (sut/poly-contains-pos? a b)))
        (is (and (not (sut/pos-in-poly? c a))
                 (not (sut/poly-contains-pos? a c))))
        (is (and (not (sut/pos-in-poly? d a))
                 (not (sut/poly-contains-pos? a d))))))

    (testing "works on complex polygons"
      ;; ┌─────────────┐
      ;; │ a     .     │
      ;; │      / \    │ .d
      ;; │     /   \   │
      ;; │ .b /  .c \  │
      ;; └───┘       └─┘
      (let [points [[0 0] [14 0] [14 5] [12 5] [8 1] [4 5] [0 5]]
            a {:pos [7 2.5] :size [14 5] :bounds-fn :points :points points}
            b {:pos [2 4]}
            c {:pos [8 4]}
            d {:pos [16 2]}]
        (is (and (sut/pos-in-poly? b a)
                 (sut/poly-contains-pos? a b)))
        (is (and (not (sut/pos-in-poly? c a))
                 (not (sut/poly-contains-pos? a c))))
        (is (and (not (sut/pos-in-poly? d a))
                 (not (sut/poly-contains-pos? a d))))))

    (testing "works on very complex polygons"
      ;; ┌─────────────┐
      ;; │    ┌──────┐ │
      ;; │ a   \ .e /  │
      ;; │      \  /   │
      ;; │       \/    │
      ;; │       X     │
      ;; │      / \    │ .d
      ;; │     /   \   │
      ;; │ .b /  .c \  │
      ;; └───┘       └─┘
      (let [points [[0 0] [14 0] [14 9] [12 9] [5 1] [12 1] [4 9] [0 9]]
            a {:pos [7 4.5] :size [14 9] :bounds-fn :points :points points}
            b {:pos [2 8]}
            c {:pos [8 8]}
            d {:pos [16 6]}
            e {:pos [8 2]}]
        (is (and (sut/pos-in-poly? b a)
                 (sut/poly-contains-pos? a b)))
        (is (and (not (sut/pos-in-poly? c a))
                 (not (sut/poly-contains-pos? a c))))
        (is (and (not (sut/pos-in-poly? d a))
                 (not (sut/poly-contains-pos? a d))))
        (is (and (not (sut/pos-in-poly? e a))
                 (not (sut/poly-contains-pos? a e))))))))

(deftest polys-collide
  (testing "rectangular polygons"
    (testing "intersections"
      ;; ┌───┐  ┌───┐
      ;; │ b │  │ c │
      ;; │ ┌─┼──┼─┐ │
      ;; └─┼─┘  └─┼─┘
      ;;   │  a   │
      ;; ┌─┼─┐  ┌─┼─┐
      ;; │ └─┼──┼─┘ │
      ;; │ d │  │ e │
      ;; └───┘  └───┘
      (let [a {:pos [5.5 4] :size [7 4] :bounds-fn sprite/default-bounding-poly}
            b {:pos [2 1.5] :size [4 3] :bounds-fn sprite/default-bounding-poly}
            c {:pos [9 1.5] :size [4 3] :bounds-fn sprite/default-bounding-poly}
            d {:pos [2 6.5] :size [4 3] :bounds-fn sprite/default-bounding-poly}
            e {:pos [9 6.5] :size [4 3] :bounds-fn sprite/default-bounding-poly}]
        (testing "a collides with every other sprite"
          (is (and (sut/polys-collide? a b)
                   (sut/polys-collide? b a)))
          (is (and (sut/polys-collide? a c)
                   (sut/polys-collide? c a)))
          (is (and (sut/polys-collide? a d)
                   (sut/polys-collide? d a)))
          (is (and (sut/polys-collide? a e)
                   (sut/polys-collide? e a))))

        (testing "b collides with no other sprite"
          (is (and (not (sut/polys-collide? b c))
                   (not (sut/polys-collide? c b))))
          (is (and (not (sut/polys-collide? b d))
                   (not (sut/polys-collide? d b))))
          (is (and (not (sut/polys-collide? b e))
                   (not (sut/polys-collide? e b)))))

        (testing "c collides with no other sprite"
          (is (and (not (sut/polys-collide? c d))
                   (not (sut/polys-collide? d c))))
          (is (and (not (sut/polys-collide? c e))
                   (not (sut/polys-collide? e c)))))

        (testing "d collides with no other sprite"
          (is (and (not (sut/polys-collide? d e))
                   (not (sut/polys-collide? e d)))))))

    (testing "partial overlaps"
      ;; ┌────┬─┬────┐
      ;; │ a  │ │ b  │
      ;; ├────┼─┤    │
      ;; ├────┴─┼────┘
      ;; │ c    │
      ;; └──────┘
      (let [a {:pos [3.5 1.5] :size [7 3] :bounds-fn sprite/default-bounding-poly}
            b {:pos [8.5 1.5] :size [7 3] :bounds-fn sprite/default-bounding-poly}
            c {:pos [3.5 3.5] :size [7 3] :bounds-fn sprite/default-bounding-poly}]
        (testing "all sprites collide with each other"
          (is (and (sut/polys-collide? a b)
                   (sut/polys-collide? b a)))
          (is (and (sut/polys-collide? a c)
                   (sut/polys-collide? c a)))
          (is (and (sut/polys-collide? b c)
                   (sut/polys-collide? c b))))))

    (testing "overlaps exactly"
      ;; ╔══════╗
      ;; ║      ║
      ;; ║ a  b ║
      ;; ║      ║
      ;; ╚══════╝
      (let [a {:pos [3.5 2] :size [7 4] :bounds-fn sprite/default-bounding-poly}
            b {:pos [3.5 2] :size [7 4] :bounds-fn sprite/default-bounding-poly}]
        (is (and (sut/polys-collide? a b)
                 (sut/polys-collide? b a)))))

    (testing "fully contains"
      ;; ┌────────┐
      ;; │   a    │
      ;; │┌──────┐│
      ;; ││  b   ││
      ;; │└──────┘│
      ;; └────────┘
      (let [a {:pos [4.5 2.5] :size [9 5] :bounds-fn sprite/default-bounding-poly}
            b {:pos [4.5 3] :size [7 2] :bounds-fn sprite/default-bounding-poly}]
        (is (and (sut/polys-collide? a b)
                 (sut/polys-collide? b a))))))

  (testing "simple polygons"
    ;; ┌──────┐
    ;;  \  a  │
    ;;   \    │
    ;;    \   │
    ;;     \  │
    ;; ┌────┼─┼───┐
    ;; │     \│   │
    ;; │  b   │   │
    ;; │          │
    ;; └──────────┘
    (let [a-points [[0 0] [7 0] [7 7]]
          b-points [[0 0] [11 0] [11 4] [4 0]]
          a {:pos [3.5 3.5] :size [7 7] :points a-points :bounds-fn :points}
          b {:pos [5.5 7] :size [11 4] :points b-points :bounds-fn :points}]
      (is (and (sut/polys-collide? a b)
               (sut/polys-collide? b a)))))

  (testing "irregular polygons"
    ;; ┌──────┐  ┌─────┐
    ;; │      └──┼─┐   │
    ;; │  a      │ │ b │
    ;; │         └─┼─┐ │
    ;; │      ┌────┘ │ │
    ;; └──────┘ ┌────┘ │
    ;;          └──────┘
    (let [a-points [[0 0] [7 0] [7 1] [12 1] [12 4] [7 4] [7 5] [0 5]]
          b-points [[1 0] [7 0] [7 6] [0 6] [0 5] [5 5] [5 3] [1 3]]
          a {:pos [6 2.5] :size [12 5] :points a-points :bounds-fn :points}
          b {:pos [13.5 3] :size [7 6] :points b-points :bounds-fn :points}]
      (is (and (sut/polys-collide? a b)
               (sut/polys-collide? b a))))))

(deftest pos-in-rotating-poly?
  (testing "pos-in-rotating-poly? and rotating-poly-contains-pos?"
    (testing "works on rectangles"
      ;;    ┌──┐
      ;;    │a2│
      ;;    │  │
      ;; ┌──┼──┼──┐
      ;; │a1│  │  │
      ;; │  │  │.b│
      ;; └──┼──┼──┘
      ;;    │  │
      ;;    │.c│.d
      ;;    └──┘
      (let [a1 {:pos [4.5 4.5]
                :rotation 0
                :size [9 3]
                :bounds-fn sprite/default-bounding-poly}
            a2 (assoc a1 :rotation 90)
            b {:pos [7 5]}
            c {:pos [4 8]}
            d {:pos [7 8]}]
        (is (and (sut/pos-in-rotating-poly? b a1)
                 (sut/rotating-poly-contains-pos? a1 b)))
        (is (and (not (sut/pos-in-rotating-poly? b a2))
                 (not (sut/rotating-poly-contains-pos? a2 b))))
        (is (and (sut/pos-in-rotating-poly? c a2)
                 (sut/rotating-poly-contains-pos? a2 c)))
        (is (and (not (sut/pos-in-rotating-poly? c a1))
                 (not (sut/rotating-poly-contains-pos? a1 c))))
        (is (and (not (sut/pos-in-rotating-poly? d a1))
                 (not (sut/rotating-poly-contains-pos? a1 d))))
        (is (and (not (sut/pos-in-rotating-poly? d a2))
                 (not (sut/rotating-poly-contains-pos? a2 d))))))

    (testing "works on polygons"
      ;; ┌───────┐
      ;; │\ a1  /
      ;; │ \ .c/
      ;; │  \ /
      ;; │.b *  .e
      ;; │  / \
      ;; │ / .d\
      ;; │/ a2  \
      ;; └───────┘
      (let [points [[0 0] [8 0] [0 8]]
            a1 {:pos [4 4]
                :rotation 0
                :size [8 8]
                :bounds-fn :points
                :points points}
            a2 (assoc a1 :rotation 270)
            b {:pos [1 4]}
            c {:pos [4 2]}
            d {:pos [4 6]}
            e {:pos [7 4]}]
        (is (and (sut/pos-in-rotating-poly? b a1)
                 (sut/rotating-poly-contains-pos? a1 b)))
        (is (and (sut/pos-in-rotating-poly? b a2)
                 (sut/rotating-poly-contains-pos? a2 b)))
        (is (and (sut/pos-in-rotating-poly? c a1)
                 (sut/rotating-poly-contains-pos? a1 c)))
        (is (and (not (sut/pos-in-rotating-poly? c a2))
                 (not (sut/rotating-poly-contains-pos? a2 c))))
        (is (and (not (sut/pos-in-rotating-poly? d a1))
                 (not (sut/rotating-poly-contains-pos? a1 d))))
        (is (and (sut/pos-in-rotating-poly? d a2)
                 (sut/rotating-poly-contains-pos? a2 d)))
        (is (and (not (sut/pos-in-rotating-poly? e a1))
                 (not (sut/rotating-poly-contains-pos? a1 e))))
        (is (and (not (sut/pos-in-rotating-poly? e a2))
                 (not (sut/rotating-poly-contains-pos? a2 e))))))))

(deftest rotating-polys-collide
  (testing "rectangular polygons"
    ;;   ┌────────┐
    ;;   │b ┌──┐  │
    ;;   │  │a2│  │
    ;;   │  │  │  │
    ;;   │  │  │  │
    ;;   └──┼──┼──┘
    ;; ┌────┼──┼────┐
    ;; │a1  │  │    │
    ;; │    │  │    │
    ;; └────┼──┼────┘
    ;;   ┌──┼──┼──┐
    ;;   │c │  │  │
    ;;   │  │  │  │
    ;;   │  │  │  │
    ;;   │  └──┘  │
    ;;   └────────┘
    (let [a1 {:pos [6.5 8]
              :rotation 0
              :size [13 3]
              :bounds-fn sprite/default-bounding-poly}
          a2 (assoc a1 :rotation 90)
          b {:pos [6.5 2.5] :size [9 5] :bounds-fn sprite/default-bounding-poly}
          c {:pos [6.5 12.5] :size [9 5] :bounds-fn sprite/default-bounding-poly}]
      (is (and (not (sut/rotating-polys-collide? a1 b))
               (not (sut/rotating-polys-collide? b a1))))
      (is (and (sut/rotating-polys-collide? a2 b)
               (sut/rotating-polys-collide? b a2)))
      (is (and (not (sut/rotating-polys-collide? a1 c))
               (not (sut/rotating-polys-collide? c a1))))
      (is (and (sut/rotating-polys-collide? a2 c)
               (sut/rotating-polys-collide? c a2)))))

  (testing "simple polygons"
    ;; ┌──────┐
    ;; │a1   /│
    ;; │    / │
    ;; │   /  │
    ;; │  / a2│
    ;; │ /┌───┼──────┐
    ;; │/ │   │      │
    ;; └──┼───┘      │
    ;;    │ b        │
    ;;    └──────────┘
    (let [a-points [[0 0] [7 0] [0 7]]
          b-points [[0 0] [11 0] [11 4] [0 4]]
          a1 {:pos [3.5 3.5]
              :rotation 0
              :size [7 7]
              :points a-points
              :bounds-fn :points}
          a2 (assoc a1 :rotation 180)
          b {:pos [8.5 7]
             :rotation 0
             :size [11 4]
             :points b-points
             :bounds-fn :points}]
      (is (and (not (sut/rotating-polys-collide? a1 b))
               (not (sut/rotating-polys-collide? b a1))))
      (is (and (sut/rotating-polys-collide? a2 b)
               (sut/rotating-polys-collide? b a2))))))

(deftest collisions-with-offsets
  (testing "simple offset collisions"
    ;; ┌────┐        ┌────┐
    ;; │ b  │        │ c  │
    ;; │  ┌─┼──┐  ┌──┼─┐  │
    ;; └──┼─┘  │  │  └─┼──┘
    ;;    │ a1 │  │ a2 │
    ;;    │  ┌─┼──┼─┐  │
    ;;    └──┼─┘  └─┼──┘
    ;;       │  a0  │   ;; <- approximation, a1-4 should all touch in the middle
    ;;    ┌──┼─┐  ┌─┼──┐
    ;;    │  └─┼──┼─┘  │
    ;;    │ a3 │  │ a4 │
    ;; ┌──┼─┐  │  │  ┌─┼──┐
    ;; │  └─┼──┘  └──┼─┘  │
    ;; │ d  │        │ e  │
    ;; └────┘        └────┘
    ;; a0 = [:center :center]
    ;; a1 = [:right :bottom]
    ;; a2 = [:left :bottom]
    ;; a3 = [:right :top]
    ;; a4 = [:left :top]
    (let [a0 {:pos [0 0] :size [20 20] :bounds-fn sprite/default-bounding-poly}
          a1 (assoc a0 :offsets [:right :bottom])
          a2 (assoc a0 :offsets [:left :bottom])
          a3 (assoc a0 :offsets [:right :top])
          a4 (assoc a0 :offsets [:left :top])
          b {:pos [-20 -20] :size [10 10] :bounds-fn sprite/default-bounding-poly}
          c {:pos [20 -20] :size [10 10] :bounds-fn sprite/default-bounding-poly}
          d {:pos [-20 20] :size [10 10] :bounds-fn sprite/default-bounding-poly}
          e {:pos [20 20] :size [10 10] :bounds-fn sprite/default-bounding-poly}]

      (testing "a0 does not collide with others"
        (is (not (sut/polys-collide? a0 b)))
        (is (not (sut/polys-collide? a0 c)))
        (is (not (sut/polys-collide? a0 d)))
        (is (not (sut/polys-collide? a0 e)))
        (is (not (sut/w-h-rects-collide? a0 b)))
        (is (not (sut/w-h-rects-collide? a0 c)))
        (is (not (sut/w-h-rects-collide? a0 d)))
        (is (not (sut/w-h-rects-collide? a0 e))))

      (testing "a1 collides with only b"
        (is (sut/polys-collide? a1 b))
        (is (not (sut/polys-collide? a1 c)))
        (is (not (sut/polys-collide? a1 d)))
        (is (not (sut/polys-collide? a1 e)))
        (is (sut/w-h-rects-collide? a1 b))
        (is (not (sut/w-h-rects-collide? a1 c)))
        (is (not (sut/w-h-rects-collide? a1 d)))
        (is (not (sut/w-h-rects-collide? a1 e))))

      (testing "a2 collides with only c"
        (is (not (sut/polys-collide? a2 b)))
        (is (sut/polys-collide? a2 c))
        (is (not (sut/polys-collide? a2 d)))
        (is (not (sut/polys-collide? a2 e)))
        (is (not (sut/w-h-rects-collide? a2 b)))
        (is (sut/w-h-rects-collide? a2 c))
        (is (not (sut/w-h-rects-collide? a2 d)))
        (is (not (sut/w-h-rects-collide? a2 e))))

      (testing "a3 collides with only d"
        (is (not (sut/polys-collide? a3 b)))
        (is (not (sut/polys-collide? a3 c)))
        (is (sut/polys-collide? a3 d))
        (is (not (sut/polys-collide? a3 e)))
        (is (not (sut/w-h-rects-collide? a3 b)))
        (is (not (sut/w-h-rects-collide? a3 c)))
        (is (sut/w-h-rects-collide? a3 d))
        (is (not (sut/w-h-rects-collide? a3 e))))

      (testing "a4 collides with only e"
        (is (not (sut/polys-collide? a4 b)))
        (is (not (sut/polys-collide? a4 c)))
        (is (not (sut/polys-collide? a4 d)))
        (is (sut/polys-collide? a4 e))
        (is (not (sut/w-h-rects-collide? a4 b)))
        (is (not (sut/w-h-rects-collide? a4 c)))
        (is (not (sut/w-h-rects-collide? a4 d)))
        (is (sut/w-h-rects-collide? a4 e)))))

  (testing "rotated offset collisions"
    (testing "using `[:center :center]` offsets"
      ;;          ┌──┐
      ;;          │  │
      ;;          │  │
      ;;          │  │
      ;; ┌─────┐  │  │
      ;; │ b ┌─┼──┼──┼────┐
      ;; │   │ │  │  │ a2 │
      ;; │   │ │  │  │    │
      ;; │   └─┼──┼──┼────┘
      ;; └─────┘  │  │
      ;;          │a1│
      ;;          │  │
      ;;          │  │
      ;;          └──┘
      (let [a1 {:pos [0 0]
                :rotation 0
                :size [5 20]
                :bounds-fn sprite/default-bounding-poly}
            a2 (assoc a1 :rotation 90)
            b {:pos [-10 0] :size [10 10] :bounds-fn sprite/default-bounding-poly}]
        (is (not (sut/rotating-polys-collide? a1 b)))
        (is (sut/rotating-polys-collide? a2 b))))

    (testing "using `[:left :top]` offsets"
      ;;            ┌─────┐
      ;; ┌──┬───────┼─┐ b │
      ;; │  │ a2    │ │   │
      ;; │  │       │ │   │
      ;; ├──┼───────┼─┘   │
      ;; │  │       └─────┘
      ;; │  │
      ;; │  │
      ;; │  │
      ;; │  │
      ;; │a1│
      ;; │  │
      ;; │  │
      ;; └──┘
      (let [a1 {:pos [0 0]
                :rotation 0
                :size [5 20]
                :bounds-fn sprite/default-bounding-poly
                :offsets [:left :top]}
            a2 (assoc a1 :rotation 270)
            b  {:pos [20 0] :size [10 10] :bounds-fn sprite/default-bounding-poly}]
        (is (not (sut/rotating-polys-collide? a1 b)))
        (is (sut/rotating-polys-collide? a2 b))))))

(deftest lines-intersect?
  (testing "simple intersection"
    ;;   |
    ;; --┼--
    ;;   |
    (let [l1 [[0 0] [10 0]]
          l2 [[5 -5] [5 5]]]
      (is (sut/lines-intersect? l1 l2))))

  (testing "direction of line doesn't matter"
    ;;   |
    ;; --┼--
    ;;   |
    (let [l1 [[0 0] [10 0]]
          l2 [[5 -5] [5 5]]
          l3 (reverse l1)
          l4 (reverse l2)]
      (is (sut/lines-intersect? l1 l2))
      (is (sut/lines-intersect? l3 l4))
      (is (sut/lines-intersect? l1 l4))
      (is (sut/lines-intersect? l3 l2))))

  (testing "line intersects with itself"
    ;; ====
    (let [l1 [[0 0] [10 0]]]
      (is (sut/lines-intersect? l1 l1)))
    ;; ║
    ;; ║
    (let [l1 [[0 0] [0 10]]]
      (is (sut/lines-intersect? l1 l1))))

  (testing "intersection with same start point"
    ;; ┌--
    ;; |
    (let [l1 [[0 0] [10 0]]
          l2 [[0 0] [0 10]]]
      (is (sut/lines-intersect? l1 l2))))

  (testing "intersection starting on line"
    ;; --┬--
    ;;   |
    (let [l1 [[0 0] [10 0]]
          l2 [[5 0] [5 10]]]
      (is (sut/lines-intersect? l1 l2)))
    ;; |
    ;; ├--
    ;; |
    (let [l1 [[0 0] [0 10]]
          l2 [[0 5] [10 5]]]
      (is (sut/lines-intersect? l1 l2))))

  (testing "non-intersection for segments when lines would intersect"
    ;; |
    ;; |  -----
    ;; |
    (let [l1 [[0 0] [0 10]]
          l2 [[5 5] [15 5]]]
      (is (not (sut/lines-intersect? l1 l2)))))

  (testing "Zero-length lines do not intersect."
    (testing "segment and 0-length line which would intersect"
      ;; ----  .
      (let [l1 [[0 0] [10 0]]
            l2 [[15 0] [15 0]]]
        (is (not (sut/lines-intersect? l1 l2)))))

    (testing "non-equal 0-length lines"
      ;; .  .
      (let [l1 [[0 0] [0 0]]
            l2 [[10 0] [10 0]]]
        (is (not (sut/lines-intersect? l1 l2)))))))

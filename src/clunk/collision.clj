(ns clunk.collision
  "Group-based sprite collision tools and sprite collision detection
  predicates.

  The predicate functions in this namespace apply to sprites if they
  are named `<foo>?` and apply to geometric data structures (lines,
  rectangles, polygons etc.) if they are names `<foo>?*`. "
  (:require [clojure.math.combinatorics :as combo]
            [clojure.set :as s]
            [clunk.sprite :as sprite]
            [clunk.util :as u]))

(defn equal-pos?*
  "Predicate to check if two positions are equal."
  [pos-a pos-b]
  (and (seq pos-a)
       (seq pos-b)
       (every? true? (map = pos-a pos-b))))

(defn equal-pos?
  "Predicate to check if two sprites have the same position."
  [{pos-a :pos} {pos-b :pos}]
  (equal-pos?* pos-a pos-b))

(defn rects-overlap?*
  "Predicate to determine if two rectangles overlap."
  [[ax1 ay1 ax2 ay2]
   [bx1 by1 bx2 by2]]
  (let [x-preds [(<= ax1 bx1 ax2)
                 (<= ax1 bx2 ax2)
                 (<= bx1 ax1 ax2 bx2)]
        y-preds [(<= ay1 by1 ay2)
                 (<= ay1 by2 ay2)
                 (<= by1 ay1 ay2 by2)]]
    (and (some true? x-preds)
         (some true? y-preds))))

(defn w-h-rects-collide?
  "Predicate to check for overlap of the `w` by `h` rects of two sprites
  centered on their positions.

  Accounts for the respective `:offsets` configuration of each sprite."
  [{[ax ay] :pos
    [aw ah] :size
    :as a}
   {[bx by] :pos
    [bw bh] :size
    :as b}]
  (let [[adx ady] (sprite/pos-offsets a)
        [bdx bdy] (sprite/pos-offsets b)
        ax1 (+ ax adx)
        ay1 (+ ay ady)
        ax2 (+ ax adx aw)
        ay2 (+ ay ady ah)
        bx1 (+ bx bdx)
        by1 (+ by bdy)
        bx2 (+ bx bdx bw)
        by2 (+ by bdy bh)]
    (rects-overlap?* [ax1 ay1 ax2 ay2]
                     [bx1 by1 bx2 by2])))

(defn pos-in-rect?*
  "Predicate to check if a position is inside a rectangle."
  [[ax ay]
   [bx1 by1 bx2 by2]]
  (and (<= bx1 ax bx2)
       (<= by1 ay by2)))

(defn pos-in-rect?
  "Predicate to check if the position of sprite `a` is inside the `w` by
  `h` rect of sprite `b` centered on its position.

  Accounts for the respective `:offsets` configuration of each sprite."
  [{pos-a :pos
    :as a}
   {[bx by] :pos
    [bw bh] :size
    :as b}]
  (let [a-offsets (sprite/pos-offsets a)
        [bdx bdy] (sprite/pos-offsets b)
        rect-b [(+ bx bdx)
                (+ by bdy)
                (+ bx bdx bw)
                (+ by bdy bh)]]
    (pos-in-rect?* (map + pos-a a-offsets) rect-b)))

(defn rect-contains-pos?
  "Predicate to check if the position of sprite `b` is inside the `w` by
  `h` rect of sprite `a` centered on its position.

  Accounts for the respective `:offsets` configuration of each sprite."
  [a b]
  (pos-in-rect? b a))

(defn coarse-pos-in-poly?*
  "Predicate to determine if a point is possibly inside a polygon.

  Checks if the point is contanied by the minimum rectangle containing
  the polygon. If the point is inside this rectangle we should use
  `fine-pos-in-poly?` to check properly."
  [[x y] poly]
  (let [xs (map first poly)
        ys (map second poly)]
    (and (<= (apply min xs) x (apply max xs))
         (<= (apply min ys) y (apply max ys)))))

(defn pos->ray
  "Creates an arbitrarily long line starting at the specified pos.

  When doing poly->point collision detection a point lying on a
  horizontal edge of a poly would cause a division by zero if we used
  a horizontal ray.

  This would be handled, but would not count as a collision so we
  increment y to make it much less likely that the intersecting lines
  are parallel."
  [[x y]]
  [[x y] [(+ x 100000) (+ y 1)]])

(defn lines-intersect?
  "Predicate to determine if two lines intersect.

  We have decided that zero-length lines do not intersect as the
  complexity in determining their intersection is not worth the
  performance hit.

  line a: (x1, y1) -> (x2, y2)
  line b: (x3, y3) -> (x4, y4)

  lines intersect iff:
       0.0 <= numerator-t/denominator-t <= 1.0
  and  0.0 <= numerator-u/denominator-u <= 1.0

  We can just assert that the fraction is bottom-heavy."
  [[[x1 y1 :as p1] [x2 y2 :as p2] :as l1]
   [[x3 y3 :as p3] [x4 y4 :as p4] :as l2]]
  ;; We ignore zero-length lines
  (when-not (or (= p1 p2) (= p3 p4))
    (let [numerator-t (- (* (- x1 x3) (- y3 y4))
                         (* (- y1 y3) (- x3 x4)))
          denominator-t (- (* (- x1 x2) (- y3 y4))
                           (* (- y1 y2) (- x3 x4)))
          numerator-u (- (* (- x2 x1) (- y1 y3))
                         (* (- y2 y1) (- x1 x3)))
          denominator-u (- (* (- x1 x2) (- y3 y4))
                           (* (- y1 y2) (- x3 x4)))]
      (and (or (<= 0 numerator-t denominator-t)
               (<= denominator-t numerator-t 0))
           (or (<= 0 numerator-u denominator-u)
               (<= denominator-u numerator-u 0))))))

(defn fine-pos-in-poly?*
  "Uses ray casting to check if a polygon encloses a pos.

  We construct a line starting at our point and count how many of the
  polygon lines it intersects, an odd number of intersections means
  the point is inside the polygon.

  Our line should be infinite, but in practice any large number will
  suffice."
  [pos poly]
  (let [ray (pos->ray pos)]
    (->> (u/poly-lines poly)
         (filter #(lines-intersect? % ray))
         count
         odd?)))

(defn pos-in-poly?*
  "Predicate to check if a pos is inside a polygon.

  The `fine-pos-in-poly?` predicate is expensive so we only do it if
  the cheaper `coarse-pos-in-poly?` says this is a possible
  collision."
  [pos poly]
  (when (and (seq poly)
             (coarse-pos-in-poly?* pos poly))
    (fine-pos-in-poly?* pos poly)))

(defn pos-in-poly?
  "Predicate to check if the position of sprite `a` is inside the
  bounding polygon of sprite `b` centered on its position.

  Accounts for the respective `:offsets` configuration of each sprite."
  [{pos-a :pos :as a}
   {bounds-fn :bounds-fn pos-b :pos :as b}]
  (let [bounding-poly (->> (bounds-fn b)
                           (map (fn [p] (map + p pos-b (sprite/pos-offsets b)))))]
    (pos-in-poly?* pos-a bounding-poly)))

(defn poly-contains-pos?
  "Predicate to check if the position of sprite `b` is inside the
  bounding polygon of sprite `a` centered on its position.

  Accounts for the respective `:offsets` configuration of each sprite."
  [a b]
  (pos-in-poly? b a))

(defn poly-w-h
  [poly]
  (let [xs (map first poly)
        ys (map second poly)]
    [(- (max xs) (min xs))
     (- (max ys) (min ys))]))

(defn coarse-polys-collide?*
  "Predicate to determine if two polygons possibly collide.

  Checks if the minimum rectangles containing the polygons overlap. If
  they do we should use `fine-polys-collide?` to check properly."
  [poly-a poly-b]
  (let [a-xs (map first poly-a)
        a-ys (map second poly-a)
        b-xs (map first poly-b)
        b-ys (map second poly-b)]
    (rects-overlap?* [(apply min a-xs) (apply min a-ys) (apply max a-xs) (apply max a-ys)]
                     [(apply min b-xs) (apply min b-ys) (apply max b-xs) (apply max b-ys)])))

(defn fine-polys-collide?*
  "Predicate to determine if two polygons overlap.

  We first check if there are any points shared by the polygons, then
  we check if any of the lines intersect.

  If no lines intersect it is still possible that one polygon is fully
  containing the other. In this case one polygon will contain all the
  points of the other. So we can just check if the first point of
  poly-a is contained in poly-b or vice versa."
  [poly-a poly-b]
  (or
   ;; any identical points
   (seq (s/intersection (set poly-a) (set poly-b)))
   ;; any intersecting lines
   (some (partial apply lines-intersect?)
         (combo/cartesian-product (u/poly-lines poly-a)
                                  (u/poly-lines poly-b)))
   ;; either fully contains the other
   (or (pos-in-poly?* (first poly-a) poly-b)
       (pos-in-poly?* (first poly-b) poly-a))))

(defn polys-collide?*
  "Predicate to check if two polygons overlap.

  The `fine-polys-collide?` predicate is expensive so we only do it if
  the cheaper `coarse-polys-collide?` says this is a possible
  collision."
  [poly-a poly-b]
  (when (coarse-polys-collide?* poly-a poly-b)
    (fine-polys-collide?* poly-a poly-b)))

(defn polys-collide?
  "Predicate to check an intersection of the bounding polygons of
  sprites `a` and `b` centered on their positions.

  Accounts for the respective `:offsets` configuration of each sprite."
  [{bounds-fn-a :bounds-fn pos-a :pos :as a}
   {bounds-fn-b :bounds-fn pos-b :pos :as b}]
  (let [poly-a (->> (bounds-fn-a a)
                    (map (fn [p] (map + p pos-a (sprite/pos-offsets a)))))
        poly-b (->> (bounds-fn-b b)
                    (map (fn [p] (map + p pos-b (sprite/pos-offsets b)))))]
    (polys-collide?* poly-a poly-b)))

(defn pos-in-rotating-poly?
  "Predicate to check if the position of sprite `a` is inside the
  bounding polygon of sprite `b` centered on its position, taking into
  account its rotation.

  Accounts for the respective `:offsets` configuration of each sprite."
  [{pos-a :pos :as a}
   {bounds-fn :bounds-fn pos-b :pos rotation :rotation :as b}]
  (let [bounding-poly (->> (bounds-fn b)
                           (map (fn [p] (map + p (sprite/pos-offsets b))))
                           (map #(u/rotate-vector % rotation))
                           (map (fn [p] (map + p pos-b))))]
    (pos-in-poly?* pos-a bounding-poly)))

(defn rotating-poly-contains-pos?
  "Predicate to check if the position of sprite `b` is inside the
  bounding polygon of sprite `a` centered on its position, taking into
  account its rotation.

  Accounts for the respective `:offsets` configuration of each sprite."
  [a b]
  (pos-in-rotating-poly? b a))

(defn rotating-polys-collide?
  "Predicate to check for an intersection of the bounding polys of
  sprites `a` and `b` centered on their positions, taking into account
  the rotation of both sprites.

  Accounts for the respective `:offsets` configuration of each sprite."
  [{bounds-fn-a :bounds-fn pos-a :pos rotation-a :rotation wa :w ha :h :as a}
   {bounds-fn-b :bounds-fn pos-b :pos rotation-b :rotation wb :w hb :h :as b}]
  (let [poly-a (->> (bounds-fn-a a)
                    (map (fn [p] (map + p (sprite/pos-offsets a))))
                    (map #(u/rotate-vector % rotation-a))
                    (map (fn [p] (map + p pos-a))))
        poly-b (->> (bounds-fn-b b)
                    (map (fn [p] (map + p (sprite/pos-offsets b))))
                    (map #(u/rotate-vector % rotation-b))
                    (map (fn [p] (map + p pos-b))))]
    (polys-collide?* poly-a poly-b)))

(defn identity-collide-fn
  "Collide functions should return an optionally modified `a` sprite."
  [a b]
  a)

(defn collider
  "Define a check for collision between to groups of sprites with
  functions to be invoked on the sprites when collision is detected."
  [group-a-key group-b-key collide-fn-a collide-fn-b &
   {:keys [collision-detection-fn
           non-collide-fn-a
           non-collide-fn-b]
    :or   {collision-detection-fn w-h-rects-collide?
           non-collide-fn-a identity-collide-fn
           non-collide-fn-b identity-collide-fn}}]
  {:group-a-key group-a-key
   :group-b-key group-b-key
   :collision-detection-fn collision-detection-fn
   :collide-fn-a collide-fn-a
   :collide-fn-b collide-fn-b
   :non-collide-fn-a non-collide-fn-a
   :non-collide-fn-b non-collide-fn-b})

(defn collide-sprites
  "Check two sprites for collision and update them with the appropriate
  `collide-fn-<a|b>` provided by the collider. These functions should
  return an optionally modified version of their first argument, the
  second is passed in only as a reference.

  In the case that we're checking a group of sprites for collisions in
  the same group we need to check the uuid on the sprites to ensure
  they're not colliding with themselves."
  [a b {:keys [group-a-key
               group-b-key
               collision-detection-fn
               collide-fn-a
               collide-fn-b
               non-collide-fn-a
               non-collide-fn-b]}]
  (let [collision-predicate (if (= group-a-key group-b-key)
                              #(and (not= (:uuid a) (:uuid b))
                                    (collision-detection-fn %1 %2))
                              #(collision-detection-fn %1 %2))]
    (if (and a b (collision-predicate a b))
      {:a (collide-fn-a a b)
       :b (collide-fn-b b a)}
      {:a (non-collide-fn-a a b)
       :b (non-collide-fn-b b a)})))

(defn collide-group
  "Check a sprite from one group for collisions with all sprites from
  another group, updating both as necessary.

  Reducing over group-b lets us build up a new version of group-b,
  updating the value of a as we go.

  We filter out any b that returns `nil` after colliding to allow
  collide functions to kill sprites."
  [a group-b collider]
  (reduce (fn [acc b]
            (let [results (collide-sprites (:a acc) b collider)]
              (-> acc
                  (assoc :a (:a results))
                  (update :group-b #(->> (conj % (:b results))
                                         (filter some?)
                                         vec)))))
          {:a a
           :group-b []}
          group-b))

(defn collide-groups
  "Check a group of sprites for collisions with another group of
  sprites, updating all sprites as necessary.

  We're iterating using a reducing function over the first group, this
  means that each time we check an `a` against `group-b` we get the
  new value for a, and the new values for each sprite in `group-b`.

  We filter out any a that returns `nil` after colliding to allow
  collide functions to kill sprites.

  We build our results map using the threading macro to handle the
  case where `group-a-key` and `group-b-key` are the same."
  [sprite-groups {:keys [group-a-key group-b-key]
                  :as collider}]
  (let [group-a (filter some? (group-a-key sprite-groups))
        group-b (filter some? (group-b-key sprite-groups))
        results (reduce (fn [acc a]
                          (let [group-result (collide-group a (:group-b acc) collider)]
                            (-> acc
                                (assoc :group-b (:group-b group-result))
                                (update :group-a #(->> (conj % (:a group-result))
                                                       (filter some?)
                                                       vec)))))
                        {:group-a []
                         :group-b group-b}
                        group-a)]
    (-> {}
        (assoc group-b-key (:group-b results))
        (assoc group-a-key (:group-a results)))))

;; @TODO: would be nice to be able to specify some kind of `:any` sprite group so we can define (e.g.) a single collider for walls vs everything.

(defn update-state
  "Update the sprites in the current scene based on the scene colliders."
  [{:keys [current-scene] :as state}]
  (let [sprites (get-in state [:scenes current-scene :sprites])
        sprite-groups (group-by :sprite-group sprites)
        colliders (get-in state [:scenes current-scene :colliders])
        colliding-group-keys (set (mapcat (juxt :group-a-key :group-b-key)
                                          colliders))
        colliding-groups (select-keys sprite-groups colliding-group-keys)
        non-colliding-sprites (remove #(colliding-group-keys (:sprite-group %)) sprites)]
    (assoc-in state [:scenes current-scene :sprites]
              (concat non-colliding-sprites
                      (->> colliders
                           (reduce (fn [acc-groups {:keys [group-a-key group-b-key]
                                                    :as collider}]
                                     (let [results (collide-groups acc-groups collider)]
                                       (-> acc-groups
                                           (assoc group-b-key (group-b-key results))
                                           (assoc group-a-key (group-a-key results)))))
                                   colliding-groups)
                           vals
                           (apply concat))))))

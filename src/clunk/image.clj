(ns clunk.image
  (:import (org.lwjgl.opengl GL11 GL30)
           (org.lwjgl.stb STBImage)
           (org.lwjgl.system MemoryStack)))

;; @TODO: would be nice to be able to flip in x or y when drawing images

(defn load-texture
  [path]
  ;; prepare buffers for width and height info
  (with-open [stack (MemoryStack/stackPush)]
    (let [w (.mallocInt stack 1)
          h (.mallocInt stack 1)
          cmp (.mallocInt stack 1)]
      ;; tell STB to flip images on load if png origin differs
      (STBImage/stbi_set_flip_vertically_on_load false)

      ;; load the image (forge 4 channel RGBA), we're not using
      ;; `cmp` (normally called `comp`) it grabs the number of
      ;; channels (components) actually found in the original image.
      (let [image (STBImage/stbi_load path w h cmp 4)]
        (when-not image
          (throw (RuntimeException.
                  (str "Failed to load image '" path "': "
                       (STBImage/stbi_failure_reason)))))
        (let [tex-id (GL11/glGenTextures)]
          (GL11/glBindTexture GL11/GL_TEXTURE_2D tex-id)
          ;; upload to GPU
          (GL11/glTexImage2D GL11/GL_TEXTURE_2D
                             0
                             GL11/GL_RGBA8
                             (.get w 0)
                             (.get h 0)
                             0
                             GL11/GL_RGBA
                             GL11/GL_UNSIGNED_BYTE image)

          ;; set filtering
          (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR_MIPMAP_LINEAR)
          (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
          ;; clamp edges
          (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL30/GL_CLAMP_TO_EDGE)
          (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL30/GL_CLAMP_TO_EDGE)

          ;; generate mipmaps
          (GL30/glGenerateMipmap GL11/GL_TEXTURE_2D)
          
          ;; cleanup
          (STBImage/stbi_image_free image)
          tex-id)))))

(defn draw-bound-texture-quad
  "Draw the currently bound texture."
  ;; draw the whole image
  ([pos parent-dims]
   (draw-bound-texture-quad pos parent-dims [0 0] parent-dims))
  ;; draw a subsection of the image
  ([[pos-x pos-y] [parent-w parent-h] [off-x off-y] [draw-w draw-h]]
   (GL11/glColor4f 1 1 1 1)
   (GL11/glEnable GL11/GL_TEXTURE_2D)

   ;; @NOTE we need to know the width and height of the spritesheet so
   ;; we can draw a subsection. we need to specify the `glTexCoord2f`
   ;; vlalues as floats in the range 0.0 - 1.0 wo we deivide desired
   ;; pixel values by image dimensions.

   (let [;; left is x-offset / image-width
         u0 (/ off-x parent-w)
         ;; top is y-offset / image-height
         v0 (/ off-y parent-h)
         ;; right is (x-offset + subrect-width) / image-width
         u1 (/ (+ off-x draw-w) parent-w)
         ;; bottom is (y-offset + subrect-height) / image-height
         v1 (/ (+ off-y draw-h) parent-h)]
     (GL11/glBegin GL11/GL_QUADS)
     ;; top-left
     (GL11/glTexCoord2f u0 v0)
     (GL11/glVertex2f pos-x pos-y)
     ;; top-right
     (GL11/glTexCoord2f u1 v0)
     (GL11/glVertex2f (+ pos-x draw-w) pos-y)
     ;; bottom-right
     (GL11/glTexCoord2f u1 v1)
     (GL11/glVertex2f (+ pos-x draw-w) (+ pos-y draw-h))
     ;; bottom-left
     (GL11/glTexCoord2f u0 v1)
     (GL11/glVertex2f pos-x (+ pos-y draw-h))
     (GL11/glEnd))))

(defn draw-image!
  [texture pos image-dims]
  ;; @TODO: if we need to draw the same image multiple times we should
  ;; only bind the texture once.
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
  (draw-bound-texture-quad pos image-dims))

(defn draw-sub-image!
  [texture pos parent-dims offsets draw-dims]
  ;; @TODO: if we need to draw the same image multiple times we should
  ;; only bind the texture once.
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
  (draw-bound-texture-quad pos parent-dims offsets draw-dims))


;; 1. Unhandled java.lang.NullPointerException
;;    Cannot invoke "java.lang.Character.charValue()" because "x" is null

;;                    RT.java: 1241  clojure.lang.RT/intCast
;;                  image.clj:   97  clunk.image/draw-image!
;;                  image.clj:   93  clunk.image/draw-image!
;;                 sprite.clj:   67  clunk.sprite/draw-image-sprite!
;;                 sprite.clj:   65  clunk.sprite/draw-image-sprite!
;;                 sprite.clj:  186  clunk.sprite/draw-scene-sprites!/fn
;;                   core.clj: 2770  clojure.core/map/fn
;;               LazySeq.java:   50  clojure.lang.LazySeq/force
;;               LazySeq.java:   89  clojure.lang.LazySeq/realize
;;               LazySeq.java:  106  clojure.lang.LazySeq/seq
;;                    RT.java:  555  clojure.lang.RT/seq
;;                   core.clj:  139  clojure.core/seq
;;                   core.clj: 3141  clojure.core/dorun
;;                   core.clj: 3156  clojure.core/doall
;;                   core.clj: 3156  clojure.core/doall
;;                 sprite.clj:  184  clunk.sprite/draw-scene-sprites!
;;                 sprite.clj:  180  clunk.sprite/draw-scene-sprites!
;;                   core.clj:  525  clunk.core/draw
;;                   core.clj:  510  clunk.core/draw
;;                   core.clj:  540  clunk.core/main-loop
;;                   core.clj:  531  clunk.core/main-loop
;;                   core.clj:  202  clunk.core/-main
;;                   core.clj:  197  clunk.core/-main
;;                RestFn.java:  400  clojure.lang.RestFn/invoke
;;                       REPL:   43  clunk.core/eval13232
;;                       REPL:   43  clunk.core/eval13232
;;              Compiler.java: 7739  clojure.lang.Compiler/eval
;;     interruptible_eval.clj:  106  nrepl.middleware.interruptible-eval/evaluator/run/fn
;;     interruptible_eval.clj:  101  nrepl.middleware.interruptible-eval/evaluator/run
;;                session.clj:  230  nrepl.middleware.session/session-exec/session-loop
;;         SessionThread.java:   21  nrepl.SessionThread/run

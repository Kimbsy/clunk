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

      ;; load the image (force 4 channel RGBA), we're not using
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
  ([pos parent-dims rotation]
   (draw-bound-texture-quad pos parent-dims [0 0] parent-dims rotation))
  ;; draw a subsection of the image
  ([[pos-x pos-y] [parent-w parent-h] [off-x off-y] [draw-w draw-h] rotation]
   (GL11/glColor4f 1 1 1 1)
   (GL11/glEnable GL11/GL_TEXTURE_2D)

   ;; save the existing transformation matrix
   (GL11/glPushMatrix)

   ;; translate to put the center of the image at the origin
   (GL11/glTranslatef (+ pos-x (/ draw-w 2)) (+ pos-y (/ draw-h 2)) 0)
   ;; rotate around the Z-axis
   (GL11/glRotatef rotation 0 0 1)
   ;; translate back
   (GL11/glTranslatef (- (/ draw-w 2)) (- (/ draw-h 2)) 0)

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
     (GL11/glVertex2f 0 0)
     ;; top-right
     (GL11/glTexCoord2f u1 v0)
     (GL11/glVertex2f draw-w 0)
     ;; bottom-right
     (GL11/glTexCoord2f u1 v1)
     (GL11/glVertex2f draw-w draw-h)
     ;; bottom-left
     (GL11/glTexCoord2f u0 v1)
     (GL11/glVertex2f 0 draw-h)
     (GL11/glEnd)

     ;; restore the previous transformation matrix
     (GL11/glPopMatrix))))

(defn draw-image!
  [texture pos image-dims rotation]
  ;; @TODO: if we need to draw the same image multiple times we should
  ;; only bind the texture once.
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
  (draw-bound-texture-quad pos image-dims rotation))

(defn draw-sub-image!
  [texture pos parent-dims offsets draw-dims rotation]
  ;; @TODO: if we need to draw the same image multiple times we should
  ;; only bind the texture once.
  (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
  (draw-bound-texture-quad pos parent-dims offsets draw-dims rotation))

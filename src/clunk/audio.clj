(ns clunk.audio
  (:import (java.nio ByteBuffer
                     IntBuffer)
           (org.lwjgl.openal AL
                             AL10
                             ALC
                             ALC10)
           (org.lwjgl.stb STBVorbis)
           (org.lwjgl.system MemoryStack
                             MemoryUtil)))

(def buffers (atom {}))
(def sources (atom #{}))

(defn init-audio
  "Init openAL, returns the context and device so we can clean them up
  later."
  []
  ;; init openAL
  (let [device (ALC10/alcOpenDevice (cast ByteBuffer nil))
        device-capabilities (ALC/createCapabilities device)
        context (ALC10/alcCreateContext device (cast IntBuffer nil))]
    (ALC10/alcMakeContextCurrent context)
    (AL/createCapabilities device-capabilities)
    {:context context
     :device device}))

(defn cleanup-audio
  "Clean up the openAL sources, buffers, device and context."
  [{:keys [context device]}]
  (doseq [source @sources]
    (AL10/alSourceStop source)
    (AL10/alDeleteSources source))
  (doseq [buffer (vals @buffers)]
    (AL10/alDeleteBuffers buffer))
  (ALC10/alcDestroyContext context)
  (ALC10/alcCloseDevice device))

(defn cleanup-stopped-sources!
  "Run every frame to clean up any sources which have ended (looped
  sources don't end)."
  []
  (swap!
   sources
   (fn [sources]
     (reduce (fn [acc src]
               (let [state (AL10/alGetSourcei src AL10/AL_SOURCE_STATE)]
                 (if (= AL10/AL_STOPPED state)
                   (do (AL10/alDeleteSources src)
                       acc)
                   (conj acc src))))
             #{}
             sources))))

(defn load-ogg-file!
  "Load a *.ogg music file, returns the al-buffer id (so we can call
  `alDeleteBuffers` later)."
  [buffer-key path]
  ;; decode .ogg -> AL buffer
  (with-open [stack (MemoryStack/stackPush)]
    (let [p-channels (.mallocInt stack 1)
          p-sample-rate (.mallocInt stack 1)
          raw-audio (STBVorbis/stb_vorbis_decode_filename
                     path
                     p-channels
                     p-sample-rate)]
      (when-not raw-audio
        (throw (RuntimeException.
                (str "Failed to load OGG: " (STBVorbis/stb_vorbis_get_error nil)))))

      ;; choose format based on channels
      (let [fmt (if (= 1 (.get p-channels 0))
                  AL10/AL_FORMAT_MONO16
                  AL10/AL_FORMAT_STEREO16)
            al-buffer (AL10/alGenBuffers)]
        (AL10/alBufferData al-buffer fmt raw-audio (.get p-sample-rate 0))

        ;; we can free this up now it's in the buffer
        (MemoryUtil/memFree raw-audio)

        ;; @TODO: could make this idempotent by deleting the buffer at
        ;; this key if it's set already
        
        ;; add to the buffers atom
        (swap! buffers assoc buffer-key al-buffer)

        ;; return the buffer id
        al-buffer))))

(defn play!
  "Play a buffered file, returns the source in case you need to manually
  `stop!` it."
  [buffer-key & {:keys [loop?] :or {loop? false}}]
  ;; create a source, hook up the buffer, optionally loop and play
  (let [al-buffer (get @buffers buffer-key)
        source (AL10/alGenSources)]
    (AL10/alSourcei source AL10/AL_BUFFER al-buffer)
    (AL10/alSourcei source AL10/AL_LOOPING (if loop? AL10/AL_TRUE AL10/AL_FALSE))
    (AL10/alSourcePlay source)

    ;; add to the sources atom
    (swap! sources conj source)
    
    source))

(defn stop!
  [source]
  (AL10/alSourceStop source))

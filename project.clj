(defproject com.kimbsy/clunk "0.3.0-SNAPSHOT"
  :description "A 2D game engine based on LWJGL (Light Weight Java Game Library)"
  :url "https://github.com/Kimbsy/clunk"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.12.1"]
                 [org.clojure/math.combinatorics "0.3.0"]
                 [org.lwjgl/lwjgl "3.3.6"]
                 [org.lwjgl/lwjgl "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-assimp "3.3.6"]
                 [org.lwjgl/lwjgl-assimp "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-glfw "3.3.6"]
                 [org.lwjgl/lwjgl-glfw "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-openal "3.3.6"]
                 [org.lwjgl/lwjgl-openal "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-opengl "3.3.6"]
                 [org.lwjgl/lwjgl-opengl "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-stb "3.3.6"]
                 [org.lwjgl/lwjgl-stb "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-nanovg "3.3.6"]
                 [org.lwjgl/lwjgl-nanovg "3.3.6" :classifier "natives-linux"]]
  :target-path "target/%s"
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo/"
                                    :username :env/CLOJARS_USER
                                    :password :env/CLOJARS_PASS
                                    :sign-releases false}]])

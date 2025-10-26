(defproject com.kimbsy/clunk "1.1.0-SNAPSHOT"
  :description "A 2D game engine based on LWJGL (Light Weight Java Game Library)"
  :url "https://github.com/Kimbsy/clunk"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.12.1"]
                 [org.clojure/math.combinatorics "0.3.0"]
                 [org.joml/joml "1.10.8"]
                 [org.lwjgl/lwjgl "3.3.6"]
                 [org.lwjgl/lwjgl-assimp "3.3.6"]
                 [org.lwjgl/lwjgl-glfw "3.3.6"]
                 [org.lwjgl/lwjgl-openal "3.3.6"]
                 [org.lwjgl/lwjgl-opengl "3.3.6"]
                 [org.lwjgl/lwjgl-stb "3.3.6"]
                 [org.lwjgl/lwjgl-nanovg "3.3.6"]
                 [org.lwjgl/lwjgl "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-assimp "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-glfw "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-openal "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-opengl "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-stb "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl-nanovg "3.3.6" :classifier "natives-linux"]
                 [org.lwjgl/lwjgl "3.3.6" :classifier "natives-macos"]
                 [org.lwjgl/lwjgl-assimp "3.3.6" :classifier "natives-macos"]
                 [org.lwjgl/lwjgl-glfw "3.3.6" :classifier "natives-macos"]
                 [org.lwjgl/lwjgl-openal "3.3.6" :classifier "natives-macos"]
                 [org.lwjgl/lwjgl-opengl "3.3.6" :classifier "natives-macos"]
                 [org.lwjgl/lwjgl-stb "3.3.6" :classifier "natives-macos"]
                 [org.lwjgl/lwjgl-nanovg "3.3.6" :classifier "natives-macos"]
                 [org.lwjgl/lwjgl "3.3.6" :classifier "natives-macos-arm64"]
                 [org.lwjgl/lwjgl-assimp "3.3.6" :classifier "natives-macos-arm64"]
                 [org.lwjgl/lwjgl-glfw "3.3.6" :classifier "natives-macos-arm64"]
                 [org.lwjgl/lwjgl-openal "3.3.6" :classifier "natives-macos-arm64"]
                 [org.lwjgl/lwjgl-opengl "3.3.6" :classifier "natives-macos-arm64"]
                 [org.lwjgl/lwjgl-stb "3.3.6" :classifier "natives-macos-arm64"]
                 [org.lwjgl/lwjgl-nanovg "3.3.6" :classifier "natives-macos-arm64"]
                 [org.lwjgl/lwjgl "3.3.6" :classifier "natives-windows"]
                 [org.lwjgl/lwjgl-assimp "3.3.6" :classifier "natives-windows"]
                 [org.lwjgl/lwjgl-glfw "3.3.6" :classifier "natives-windows"]
                 [org.lwjgl/lwjgl-openal "3.3.6" :classifier "natives-windows"]
                 [org.lwjgl/lwjgl-opengl "3.3.6" :classifier "natives-windows"]
                 [org.lwjgl/lwjgl-stb "3.3.6" :classifier "natives-windows"]
                 [org.lwjgl/lwjgl-nanovg "3.3.6" :classifier "natives-windows"]]
  :target-path "target/%s"
  :deploy-repositories [["clojars" {:url "https://clojars.org/repo/"
                                    :username :env/CLOJARS_USER
                                    :password :env/CLOJARS_PASS
                                    :sign-releases false}]])

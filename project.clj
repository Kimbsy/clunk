(defproject clunk "0.1.0-SNAPSHOT"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.12.1"]
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
  :main ^:skip-aot clunk.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})

(defproject basic-sprite "0.1.0"
  :dependencies [[org.clojure/clojure "1.12.1"]
                 [com.kimbsy/clunk "0.3.0"]]
  :main ^:skip-aot basic-sprite.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})

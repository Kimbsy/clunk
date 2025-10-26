(defproject resizable-world-bounds "0.1.0"
  :dependencies [[org.clojure/clojure "1.12.1"]
                 [com.kimbsy/clunk "1.1.0-SNAPSHOT"]]
  :main ^:skip-aot resizable-world-bounds.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})

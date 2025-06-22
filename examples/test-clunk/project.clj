(defproject test-clunk "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.12.1"]
                 [com.kimbsy/clunk "0.1.0-SNAPSHOT"]]
  :main ^:skip-aot test-clunk.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})

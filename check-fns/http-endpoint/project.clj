(defproject io.logicblocks/salutem.check-fns.http-endpoint "0.1.7-RC2"
  :description "An HTTP endpoint check function for salutem."

  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[io.logicblocks/salutem.core :version]

                 [clj-http "_"]]

  :profiles
  {:shared
   ^{:pom-scope :test}
   {:dependencies [[clj-http-fake "_"]]}

   :unit
   [:shared {:eftest {:multithread? false}}]}

  :test-paths ["test/shared"
               "test/unit"
               "test/performance"])

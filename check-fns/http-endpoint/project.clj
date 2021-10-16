(defproject io.logicblocks/salutem.check-fns.http-endpoint "0.1.7-RC3"
  :description "An HTTP endpoint check function for salutem."

  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[io.logicblocks/salutem.core :version]

                 [clj-http "_"]]

  :profiles
  {:shared
   ^{:pom-scope :test}
   {:dependencies [[clj-http-fake "_"]
                   [kelveden/clj-wiremock "_"]
                   [org.slf4j/slf4j-nop "_"]]}

   :unit
   [:shared {:eftest {:multithread? false}}]}

  :test-paths ["test/shared"
               "test/unit"
               "test/integration"])

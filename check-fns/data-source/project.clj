(defproject io.logicblocks/salutem.check-fns.data-source "0.1.7-RC9"
  :description "A data source check function for salutem."

  :scm {:dir "../.."
        :name "git"
        :url  "https://github.com/logicblocks/salutem"}

  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[io.logicblocks/salutem.core :version]

                 [io.logicblocks/cartus.core "_"]
                 [io.logicblocks/cartus.null "_"]

                 [tick "_"]

                 [com.github.seancorfield/next.jdbc "_"]]

  :profiles
  {:shared
   ^{:pom-scope :test}
   {:dependencies [[org.jooq/jooq "_"]
                   [com.h2database/h2 "_"]
                   [com.impossibl.pgjdbc-ng/pgjdbc-ng "0.8.9"]]}

   :unit
   {:eftest {:multithread? false}}

   :integration
   {:eftest {:multithread? false}}}

  :test-paths ["test/shared"
               "test/unit"
               "test/integration"])

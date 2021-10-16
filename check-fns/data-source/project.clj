(defproject io.logicblocks/salutem.check-fns.data-source "0.1.7-RC2"
  :description "A data source check function for salutem."

  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[io.logicblocks/salutem.core :version]

                 [com.github.seancorfield/next.jdbc "_"]]

  :profiles
  {:shared
   ^{:pom-scope :test}
   {:dependencies [[org.jooq/jooq "_"]]}}

  :test-paths ["test/shared"
               "test/unit"])

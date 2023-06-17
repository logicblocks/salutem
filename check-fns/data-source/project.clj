(defproject io.logicblocks/salutem.check-fns.data-source "0.1.8-RC5"
  :description "A data source check function for salutem."

  :parent-project {:path    "../../parent/project.clj"
                   :inherit [:scm
                             :url
                             :license
                             :plugins
                             [:profiles :parent-shared]
                             [:profiles :parent-reveal]
                             [:profiles :parent-dev]
                             [:profiles :parent-unit]
                             [:profiles :parent-integration]
                             :deploy-repositories
                             :managed-dependencies
                             :cloverage
                             :bikeshed
                             :cljfmt
                             :eastwood]}

  :plugins [[lein-parent "0.3.8"]]

  :dependencies [[io.logicblocks/salutem.core]

                 [io.logicblocks/cartus.core]
                 [io.logicblocks/cartus.null]

                 [tick]

                 [com.github.seancorfield/next.jdbc]]

  :profiles
  {:shared      ^{:pom-scope :test}
                {:dependencies [[org.jooq/jooq]
                                [com.impossibl.pgjdbc-ng/pgjdbc-ng]]}

   :dev         [:parent-dev :shared]

   :reveal      [:parent-reveal]

   :unit        [:parent-unit :shared
                 {:eftest {:multithread? false}}]

   :integration [:parent-integration :shared
                 {:eftest {:multithread? false}}]}

  :test-paths ["test/shared"
               "test/unit"
               "test/integration"]
  :resource-paths [])

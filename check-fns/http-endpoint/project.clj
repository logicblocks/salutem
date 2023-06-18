(defproject io.logicblocks/salutem.check-fns.http-endpoint "0.1.8-RC6"
  :description "An HTTP endpoint check function for salutem."

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

                 [clj-http]]

  :profiles
  {:shared      ^{:pom-scope :test}
                {:dependencies [[clj-http-fake]
                                [kelveden/clj-wiremock]
                                [org.slf4j/slf4j-nop]]}

   :dev         [:parent-dev :shared]

   :reveal      [:parent-reveal]

   :unit        [:parent-unit :shared
                 {:eftest {:multithread? false}}]

   :integration [:parent-integration :shared]}

  :test-paths ["test/shared"
               "test/unit"
               "test/integration"]
  :resource-paths [])

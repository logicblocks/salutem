(defproject io.logicblocks/salutem.core "0.1.8-RC3"
  :description "A health check library for sync / async health checks."

  :parent-project {:path    "../parent/project.clj"
                   :inherit [:scm
                             :url
                             :license
                             :plugins
                             [:profiles :parent-shared]
                             [:profiles :parent-reveal]
                             [:profiles :parent-dev]
                             [:profiles :parent-unit]
                             [:profiles :parent-performance]
                             :deploy-repositories
                             :managed-dependencies
                             :cloverage
                             :bikeshed
                             :cljfmt
                             :eastwood]}

  :plugins [[lein-parent "0.3.8"]]

  :dependencies [[org.clojure/core.async]

                 [io.logicblocks/cartus.core]
                 [io.logicblocks/cartus.null]

                 [tick]]

  :profiles {:shared      {:dependencies [[tortue/spy]]}
             :reveal      [:parent-reveal]
             :dev         [:parent-dev :shared]
             :unit        [:parent-unit :shared]
             :performance [:parent-performance :shared]}

  :test-paths ["test/shared"
               "test/unit"
               "test/performance"]
  :resource-paths [])

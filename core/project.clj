(defproject io.logicblocks/salutem.core "0.1.3-RC2"
  :description "A health check library for sync / async health checks."

  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[org.clojure/core.async "_"]

                 [io.logicblocks/cartus.core "_"]
                 [io.logicblocks/cartus.null "_"]

                 [tick "_"]]

  :test-paths ["test/shared"
               "test/unit"
               "test/performance"])

(defproject io.logicblocks/salutem.core "0.1.0-RC11"
  :description "A health check library for sync / async health checks."

  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[org.clojure/core.async "_"]

                 [io.logicblocks/cartus.core "_"]
                 [io.logicblocks/cartus.null "_"]

                 [tick "_"]])

(defproject io.logicblocks/salutem.check-fns "0.1.8"
  :description "A set of standard check functions for salutem."

  :parent-project {:path "../parent/project.clj"
                   :inherit [:scm
                             :url
                             :license
                             :plugins
                             :deploy-repositories
                             :managed-dependencies]}

  :plugins [[lein-parent "0.3.8"]]

  :dependencies [[io.logicblocks/salutem.check-fns.data-source]
                 [io.logicblocks/salutem.check-fns.http-endpoint]]

  :source-paths []
  :test-paths []
  :resource-paths [])

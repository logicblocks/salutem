(defproject io.logicblocks/salutem.check-fns "0.1.7-RC7"
  :description "A set of standard check functions for salutem."

  :scm {:dir  ".."
        :name "git"
        :url  "https://github.com/logicblocks/salutem"}

  :plugins [[lein-modules "0.3.11"]]

  :dependencies [[io.logicblocks/salutem.check-fns.data-source :version]]

  :source-paths ^:replace ["src"]
  :test-paths ^:replace []
  :resource-paths ^:replace [])

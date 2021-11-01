(defproject io.logicblocks/salutem "0.1.8-RC1"
  :description "Aggregate project for all salutem modules."

  :parent-project {:path    "parent/project.clj"
                   :inherit [:scm
                             :url
                             :license
                             :plugins
                             [:profiles :parent-shared]
                             :deploy-repositories
                             :managed-dependencies]}

  :plugins [[lein-changelog "0.3.2"]
            [lein-codox "0.10.7"]
            [lein-parent "0.3.8"]
            [lein-sub "0.3.0"]]

  :sub ["parent"
        "core"
        "check-fns/data-source"
        "check-fns/http-endpoint"
        "check-fns"
        "."]

  :dependencies [[io.logicblocks/salutem.core]
                 [io.logicblocks/salutem.check-fns]]

  :profiles
  {:unit
   {:aliases {"eftest"
              ["sub"
               "-s" "core:check-fns/data-source:check-fns/http-endpoint"
               "with-profile" "unit"
               "eftest"]}}

   :integration
   {:aliases {"eftest"
              ["sub"
               "-s" "check-fns/data-source:check-fns/http-endpoint"
               "with-profile" "integration"
               "eftest"]}}

   :performance
   {:aliases {"eftest"
               ["sub"
                "-s" "core"
                "with-profile" "performance"
                "eftest"]}}

   :codox
   [:parent-shared
    {:dependencies [[io.logicblocks/salutem.core "0.1.7-RC10"]

                    [org.clojure/core.async]

                    [io.logicblocks/cartus.core]
                    [io.logicblocks/cartus.null]

                    [clj-http]

                    [com.github.seancorfield/next.jdbc]

                    [tick]]

     :source-paths ["core/src"
                    "check-fns/data-source/src"
                    "check-fns/http-endpoint/src"]}]

   :prerelease
   {:release-tasks
    [
     ["vcs" "assert-committed"]
     ["sub" "change" "version" "leiningen.release/bump-version" "rc"]
     ["sub" "change" "version" "leiningen.release/bump-version" "release"]
     ["vcs" "commit" "Pre-release version %s [skip ci]"]
     ["vcs" "tag"]
     ["sub" "-s" "core:check-fns/data-source:check-fns/http-endpoint:check-fns:." "deploy"]]}

   :release
   {:release-tasks
    [["vcs" "assert-committed"]
     ["sub" "change" "version" "leiningen.release/bump-version" "release"]
     ["sub" "-s" "core:check-fns/data-source:check-fns/http-endpoint:check-fns:." "install"]
     ["changelog" "release"]
     ["shell" "sed" "-E" "-i.bak" "s/salutem\\.(.+) \"[0-9]+\\.[0-9]+\\.[0-9]+\"/salutem.\\\\1 \"${:version}\"/g" "README.md"]
     ["shell" "rm" "-f" "README.md.bak"]
     ["shell" "sed" "-E" "-i.bak" "s/salutem\\.(.+) \"[0-9]+\\.[0-9]+\\.[0-9]+\"/salutem.\\\\1 \"${:version}\"/g" "docs/01-getting-started.md"]
     ["shell" "sed" "-E" "-i.bak" "s/salutem\\.(.+) \"[0-9]+\\.[0-9]+\\.[0-9]+\"/salutem.\\\\1 \"${:version}\"/g" "docs/02-check-functions.md"]
     ["shell" "rm" "-f" "docs/01-getting-started.md.bak"]
     ["shell" "rm" "-f" "docs/02-check-functions.md.bak"]
     ["codox"]
     ["shell" "git" "add" "."]
     ["vcs" "commit" "Release version %s [skip ci]"]
     ["vcs" "tag"]
     ["sub" "-s" "core:check-fns/data-source:check-fns/http-endpoint:check-fns:." "deploy"]
     ["sub" "change" "version" "leiningen.release/bump-version" "patch"]
     ["sub" "change" "version" "leiningen.release/bump-version" "rc"]
     ["sub" "change" "version" "leiningen.release/bump-version" "release"]
     ["vcs" "commit" "Pre-release version %s [skip ci]"]
     ["vcs" "tag"]
     ["vcs" "push"]]}}

  :source-paths []
  :test-paths []
  :resource-paths []

  :codox
  {:namespaces  [#"^salutem\."]
   :metadata    {:doc/format :markdown}
   :output-path "docs"
   :doc-paths   ["docs"]
   :source-uri  "https://github.com/logicblocks/salutem/blob/{version}/{filepath}#L{line}"}

  :aliases {"install"
            ["do"
             ["sub"
              "-s" "core:check-fns/data-source:check-fns/http-endpoint:check-fns"
              "install"]
             ["install"]]

            "eastwood"
            ["sub"
             "-s" "core:check-fns/data-source:check-fns/http-endpoint"
             "eastwood"]

            "cljfmt"
            ["sub"
             "-s" "core:check-fns/data-source:check-fns/http-endpoint"
             "cljfmt"]

            "kibit"
            ["sub"
             "-s" "core:check-fns/data-source:check-fns/http-endpoint"
             "kibit"]

            "check"
            ["sub"
             "-s" "core:check-fns/data-source:check-fns/http-endpoint"
             "check"]

            "bikeshed"
            ["sub"
             "-s" "core:check-fns/data-source:check-fns/http-endpoint"
             "bikeshed"]})

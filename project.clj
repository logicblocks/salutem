(defproject io.logicblocks/salutem "0.1.7-RC12"
  :description "Aggregate project for all salutem modules."

  :parent-project {:path    "parent/project.clj"
                   :inherit [:scm
                             :url
                             :license
                             :plugins
                             :deploy-repositories
                             :managed-dependencies]}

  :plugins [[lein-changelog "0.3.2"]
            [lein-codox "0.10.7"]
            [lein-parent "0.3.8"]
            [lein-sub "0.3.0"]]

  :sub ["core"
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
   [:shared
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
     ["shell" "git" "diff" "--exit-code"]
     ["sub" "change" "version" "leiningen.release/bump-version" "rc"]
     ["sub" "change" "version" "leiningen.release/bump-version" "release"]
     ["vcs" "commit" "Pre-release version %s [skip ci]"]
     ["vcs" "tag"]
     ["sub" "deploy"]]}

   :release
   {:release-tasks
    [["shell" "git" "diff" "--exit-code"]
     ["sub" "change" "version" "leiningen.release/bump-version" "release"]
     ["sub" "install"]
     ["changelog" "release"]
     ["shell" "sed" "-E" "-i.bak" "s/salutem\\.(.+) \"[0-9]+\\.[0-9]+\\.[0-9]+\"/salutem.\\\\1 \"${:version}\"/g" "README.md"]
     ["shell" "rm" "-f" "README.md.bak"]
     ["shell" "sed" "-E" "-i.bak" "s/salutem\\.(.+) \"[0-9]+\\.[0-9]+\\.[0-9]+\"/salutem.\\\\1 \"${:version}\"/g" "docs/getting-started.md"]
     ["shell" "rm" "-f" "docs/getting-started.md.bak"]
     ["codox"]
     ;["shell" "sed" "-E" "-i.bak" "s/\\[io\\.logicblocks\\/salutem \"[0-9]+\\.[0-9]+\\.[0-9]+\"\\]/[io.logicblocks\\/salutem.core \"${:version}\"]/g" "docs/index.html"]
     ;["shell" "rm" "-f" "docs/index.html.bak"]
     ["shell" "git" "add" "."]
     ["vcs" "commit" "Release version %s [skip ci]"]
     ["vcs" "tag"]
     ["sub" "deploy"]
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

  :aliases {"eastwood"
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

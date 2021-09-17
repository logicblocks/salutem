(defproject io.logicblocks/salutem "0.1.3-RC1"
  :description "Parent for all salutem modules."

  :plugins [[lein-modules "0.3.11"]
            [lein-changelog "0.3.2"]
            [lein-codox "0.10.7"]]

  :modules
  {:subprocess
   nil

   :inherited
   {:url
             "https://github.com/logicblocks/salutem"

    :license
             {:name "The MIT License"
              :url  "https://opensource.org/licenses/MIT"}

    :deploy-repositories
             {"releases"  {:url "https://repo.clojars.org" :creds :gpg}
              "snapshots" {:url "https://repo.clojars.org" :creds :gpg}}

    :plugins [[lein-cloverage "1.1.2"]
              [lein-shell "0.5.0"]
              [lein-pprint "1.3.2"]
              [lein-ancient "0.6.15"]
              [lein-eftest "0.5.9"]
              [lein-cljfmt "0.7.0"]
              [lein-kibit "0.1.8"]
              [lein-bikeshed "0.5.2"]
              [jonase/eastwood "0.9.9"]]

    :cloverage
             {:ns-exclude-regex [#"^user"]}

    :bikeshed
             {:name-collisions false
              :long-lines      false}

    :cljfmt
             {:indents {#".*"     [[:inner 0]]
                        defrecord [[:block 1] [:inner 1]]
                        deftype   [[:block 1] [:inner 1]]}}

    :eastwood
             {:config-files
              [~(str (System/getProperty "user.dir") "/config/linter.clj")]}}

   :versions
   {org.clojure/clojure         "1.10.3"
    org.clojure/tools.trace     "0.7.11"
    org.clojure/core.async      "1.2.603"

    io.logicblocks/cartus.core  "0.1.14"
    io.logicblocks/cartus.null  "0.1.14"
    io.logicblocks/cartus.test  "0.1.14"

    tick                        "0.4.32"

    nrepl                       "0.8.3"

    eftest                      "0.5.9"
    tortue/spy                  "2.9.0"

    io.logicblocks/salutem.core :version}}

  :profiles
  {:shared
   ^{:pom-scope :test}
   {:dependencies [[org.clojure/clojure "_"]
                   [org.clojure/tools.trace "_"]

                   [io.logicblocks/cartus.test "_"]

                   [nrepl "_"]

                   [eftest "_"]
                   [tortue/spy "_"]]}

   :dev
   [:shared
    {:source-paths ["dev"]}]

   :unit
   [:shared {:test-paths ^:replace ["test/shared"
                                    "test/unit"]}]

   :performance
   [:shared {:test-paths ^:replace ["test/shared"
                                    "test/performance"]}]
   :codox
   [:shared
    {:dependencies [[io.logicblocks/salutem.core :version]

                    [org.clojure/core.async "_"]

                    [io.logicblocks/cartus.core "_"]
                    [io.logicblocks/cartus.null "_"]

                    [tick "_"]]

     :source-paths ["core/src"]}]

   :prerelease
   {:release-tasks
    [["shell" "git" "diff" "--exit-code"]
     ["change" "version" "leiningen.release/bump-version" "rc"]
     ["modules" "change" "version" "leiningen.release/bump-version" "rc"]
     ["change" "version" "leiningen.release/bump-version" "release"]
     ["modules" "change" "version" "leiningen.release/bump-version" "release"]
     ["vcs" "commit" "Pre-release version %s [skip ci]"]
     ["vcs" "tag"]
     ["modules" "deploy"]]}

   :release
   {:release-tasks
    [["shell" "git" "diff" "--exit-code"]
     ["change" "version" "leiningen.release/bump-version" "release"]
     ["modules" "change" "version" "leiningen.release/bump-version" "release"]
     ["modules" "install"]
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
     ["modules" "deploy"]
     ["change" "version" "leiningen.release/bump-version" "patch"]
     ["modules" "change" "version" "leiningen.release/bump-version" "patch"]
     ["change" "version" "leiningen.release/bump-version" "rc"]
     ["modules" "change" "version" "leiningen.release/bump-version" "rc"]
     ["change" "version" "leiningen.release/bump-version" "release"]
     ["modules" "change" "version" "leiningen.release/bump-version" "release"]
     ["vcs" "commit" "Pre-release version %s [skip ci]"]
     ["vcs" "tag"]
     ["vcs" "push"]]}}

  :source-paths []
  :test-paths ["test/shared"
               "test/unit"
               "test/performance"]

  :codox
  {:namespaces  [#"^salutem\."]
   :metadata    {:doc/format :markdown}
   :output-path "docs"
   :doc-paths   ["docs"]
   :source-uri  "https://github.com/logicblocks/salutem/blob/{version}/{filepath}#L{line}"}

  :aliases {"eastwood" ["modules" "eastwood"]
            "cljfmt"   ["modules" "cljfmt"]
            "kibit"    ["modules" "kibit"]
            "check"    ["modules" "check"]
            "bikeshed" ["modules" "bikeshed"]
            "eftest"   ["modules" "eftest"]})

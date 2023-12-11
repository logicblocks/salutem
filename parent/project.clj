(defproject io.logicblocks/salutem.parent "0.1.9-RC4"
  :scm {:dir  "."
        :name "git"
        :url  "https://github.com/logicblocks/salutem"}

  :url "https://github.com/logicblocks/salutem"

  :license
  {:name "The MIT License"
   :url  "https://opensource.org/licenses/MIT"}

  :plugins [[io.logicblocks/lein-interpolate "0.1.1-RC3"]
            [jonase/eastwood "1.4.2"]
            [lein-ancient "0.7.0"]
            [lein-bikeshed "0.5.2"]
            [lein-cljfmt "0.9.2"]
            [lein-cloverage "1.2.4"]
            [lein-cprint "1.3.3"]
            [lein-eftest "0.6.0"]
            [lein-kibit "0.1.8"]
            [lein-shell "0.5.0"]
            [fipp "0.6.26"]]

  :deploy-repositories
  {"releases"  {:url "https://repo.clojars.org" :creds :gpg}
   "snapshots" {:url "https://repo.clojars.org" :creds :gpg}}

  :managed-dependencies
  [[org.clojure/clojure "1.11.1"]
   [org.clojure/tools.trace "0.7.11"]
   [org.clojure/core.async "1.6.673"]

   [io.logicblocks/cartus.core "0.1.18"]
   [io.logicblocks/cartus.test "0.1.18"]
   [io.logicblocks/cartus.null "0.1.18"]

   [io.logicblocks/salutem.core :project/version]
   [io.logicblocks/salutem.check-fns :project/version]
   [io.logicblocks/salutem.check-fns.data-source :project/version]
   [io.logicblocks/salutem.check-fns.http-endpoint :project/version]

   [tick "0.7.5"]

   [nrepl "1.1.0"]

   [eftest "0.6.0"]
   [tortue/spy "2.14.0"]

   [vlaaad/reveal "1.3.280"]

   [org.jooq/jooq "3.15.3"]
   [com.impossibl.pgjdbc-ng/pgjdbc-ng "0.8.9"]

   [com.github.seancorfield/next.jdbc "1.3.894"]

   [clj-http "3.12.3"]

   [clj-http-fake "1.0.4"]
   [kelveden/clj-wiremock "1.8.0"]

   [org.slf4j/slf4j-nop "2.0.7"]]

  :profiles
  {:parent-shared
   ^{:pom-scope :test}
   {:dependencies [[org.clojure/clojure]
                   [org.clojure/tools.trace]

                   [io.logicblocks/cartus.test]

                   [nrepl]

                   [eftest]]}

   :parent-reveal
   [:parent-shared
    {:dependencies [[vlaaad/reveal]]
     :repl-options {:nrepl-middleware [vlaaad.reveal.nrepl/middleware]}
     :jvm-opts     ["-Dvlaaad.reveal.prefs={:theme :light :font-family \"FiraCode Nerd Font Mono\" :font-size 13}"]}]

   :parent-dev
   ^{:pom-scope :test}
   [:parent-shared
    {:source-paths ["dev"]}]

   :parent-unit
   [:parent-shared {:test-paths ^:replace ["test/shared"
                                           "test/unit"]}]

   :parent-integration
   [:parent-shared {:test-paths ^:replace ["test/shared"
                                           "test/integration"]}]

   :parent-performance
   [:parent-shared {:test-paths ^:replace ["test/shared"
                                           "test/performance"]}]}

  :source-paths []
  :test-paths []
  :resource-paths []

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
   [~(str (System/getProperty "user.dir") "/config/linter.clj")]})

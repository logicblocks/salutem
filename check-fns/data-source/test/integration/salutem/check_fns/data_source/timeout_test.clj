(ns salutem.check-fns.data-source.timeout-test
  (:require
   [clojure.test :refer :all]
   [clojure.pprint :as pp]

   [next.jdbc :as jdbc]

   [salutem.core :as salutem]
   [salutem.check-fns.data-source.core :as scfds]
   [tick.alpha.api :as time])
  (:import
   [com.impossibl.postgres.jdbc PGSQLSimpleException]))

(def db-spec
  {:dbtype   "pgsql"
   :dbname   "test"
   :host     "localhost"
   :port     "5432"
   :user     "tester"
   :password "test-password"})

(deftest data-source-check-fn-times-out-after-5-seconds-by-default
  (let [data-source (jdbc/get-datasource db-spec)]
    (let [check-fn (scfds/data-source-check-fn data-source
                     {:query-sql-params
                      ["SELECT pg_sleep(10) AS up;"]
                      :failure-reason-fn
                      (fn [_ ^Exception exception]
                        (if (and
                              (isa? (class exception) PGSQLSimpleException)
                              (= (.getMessage exception)
                                "canceling statement due to user request"))
                          :timed-out
                          :threw-exception))})
          context {}
          result-promise (promise)
          result-cb (partial deliver result-promise)]
      (check-fn context result-cb)

      (let [result (deref result-promise 7500 nil)]
        (is (salutem/unhealthy? result))
        (is (= :timed-out (:salutem/reason result)))
        (is (not (nil? (:salutem/exception result))))))))

(deftest data-source-check-fn-uses-supplied-timeout-when-specified
  (let [data-source (jdbc/get-datasource db-spec)]
    (let [check-fn (scfds/data-source-check-fn data-source
                     {:query-sql-params
                      ["SELECT pg_sleep(5) AS up;"]
                      :query-timeout
                      (salutem/duration 1 :seconds)
                      :failure-reason-fn
                      (fn [_ ^Exception exception]
                        (if (and
                              (isa? (class exception) PGSQLSimpleException)
                              (= (.getMessage exception)
                                "canceling statement due to user request"))
                          :timed-out
                          :threw-exception))})
          context {}
          result-promise (promise)
          result-cb (partial deliver result-promise)]
      (check-fn context result-cb)

      (let [result (deref result-promise 3000 nil)]
        (is (salutem/unhealthy? result))
        (is (= :timed-out (:salutem/reason result)))
        (is (not (nil? (:salutem/exception result))))))))

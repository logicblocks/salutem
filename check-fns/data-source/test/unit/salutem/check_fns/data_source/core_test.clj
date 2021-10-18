(ns salutem.check-fns.data-source.core-test
  (:require
   [clojure.test :refer :all]

   [cartus.test :as ct]

   [next.jdbc.result-set :as jdbc-rs]

   [salutem.core :as salutem]
   [salutem.check-fns.data-source.core :as scfds]

   [salutem.test.support.jdbc :as jdbc])
  (:import
   [java.sql SQLException SQLTimeoutException]))

(declare logged?)

(deftest data-source-check-fn-returns-healthy-when-default-query-successful
  (let [data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [{:keys [sql]}]
              (if (= "SELECT 1 AS up;" sql)
                [[1 [{:up 1}]]]
                (throw (SQLException. (str "Unexpected query: " sql)))))))

        check-fn (scfds/data-source-check-fn data-source)
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result))
      (is (= 1 (:up result))))))

(deftest data-source-check-fn-returns-unhealthy-on-sql-exception
  (let [exception (SQLException. "Something went wrong...")
        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [_] (throw exception))))

        check-fn (scfds/data-source-check-fn data-source)
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result))
      (is (= :threw-exception (:salutem/reason result)))
      (is (= exception (:salutem/exception result))))))

(deftest data-source-check-fn-returns-unhealthy-on-sql-timeout-exception
  (let [exception (SQLTimeoutException. "Something went wrong...")
        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [_] (throw exception))))

        check-fn (scfds/data-source-check-fn data-source)
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result))
      (is (= :timed-out (:salutem/reason result)))
      (is (= exception (:salutem/exception result))))))

(deftest data-source-check-fn-returns-unhealthy-on-other-exceptions
  (let [exception (IllegalArgumentException. "Something went wrong...")
        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [_] (throw exception))))

        check-fn (scfds/data-source-check-fn data-source
                   {:query-sql-params ["SELECT version();"]})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result))
      (is (= :threw-exception (:salutem/reason result)))
      (is (= exception (:salutem/exception result))))))

(deftest data-source-check-fn-uses-supplied-query-sql-params-when-provided
  (let [data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [{:keys [sql]}]
              (if (= "SELECT version();" sql)
                [[1 [{:version "PostgreSQL 14.0"}]]]
                (throw (SQLException. (str "Unexpected query: " sql)))))))

        check-fn (scfds/data-source-check-fn data-source
                   {:query-sql-params ["SELECT version();"]})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result))
      (is (= "PostgreSQL 14.0" (:version result))))))

(deftest data-source-check-fn-uses-supplied-fn-to-obtain-query-sql-params
  (let [context {:user-type "gold"}

        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [{:keys [sql bindings]}]
              (if
               (and
                 (= "SELECT COUNT(*) AS count FROM users WHERE type = ?;" sql)
                 (= ["gold"] bindings))
                [[1 [{:count 36}]]]
                (throw (SQLException. (str "Unexpected query: " sql)))))))

        check-fn (scfds/data-source-check-fn data-source
                   {:query-sql-params
                    (fn [context]
                      ["SELECT COUNT(*) AS count FROM users WHERE type = ?;"
                       (:user-type context)])})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result))
      (is (= 36 (:count result))))))

(deftest data-source-check-fn-uses-supplied-query-opts
  (let [data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [{:keys [sql]}]
              (if (= "SELECT 1 AS up;" sql)
                [[1 [{:up 1}]]]
                (throw (SQLException. (str "Unexpected query: " sql)))))))

        check-fn (scfds/data-source-check-fn data-source
                   {:query-opts {:builder-fn   jdbc-rs/as-modified-maps
                                 :label-fn     identity
                                 :qualifier-fn (fn [_] "test.namespace")}})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result))
      (is (= 1 (:test.namespace/up result))))))

(deftest data-source-check-fn-uses-supplied-query-opts-fn
  (let [context {:ns "context.namespace"}

        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [{:keys [sql]}]
              (if (= "SELECT 1 AS up;" sql)
                [[1 [{:up 1}]]]
                (throw (SQLException. (str "Unexpected query: " sql)))))))

        check-fn (scfds/data-source-check-fn data-source
                   {:query-opts
                    (fn [context]
                      {:builder-fn   jdbc-rs/as-modified-maps
                       :label-fn     identity
                       :qualifier-fn (fn [_] (:ns context))})})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result))
      (is (= 1 (:context.namespace/up result))))))

(deftest data-source-check-fn-uses-supplied-query-results-result-fn-on-success
  (let [context {:caller "thing-service"}

        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [{:keys [sql]}]
              (if (= "SELECT version();" sql)
                [[1 [{:version "PostgreSQL 14.0"}]]]
                (throw (SQLException. (str "Unexpected query: " sql)))))))

        check-fn
        (scfds/data-source-check-fn data-source
          {:query-sql-params
           ["SELECT version();"]
           :query-results-result-fn
           (fn [context query-results]
             (let [version
                   (second
                     (re-matches
                       #".*?(\d+\.\d+).*?"
                       (:version (first query-results))))]
               (salutem/healthy
                 {:version version
                  :caller  (:caller context)})))})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result))
      (is (= "14.0" (:version result)))
      (is (= "thing-service" (:caller result))))))

(deftest data-source-check-fn-uses-supplied-exception-fn-on-sql-exception
  (let [context {:caller "thing-service"}
        exception (SQLException. "Something went wrong...")

        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [_] (throw exception))))

        check-fn
        (scfds/data-source-check-fn data-source
          {:exception-result-fn
           (fn [context ^Exception exception]
             (salutem/unhealthy
               {:type   (.getClass exception)
                :error  (.getMessage exception)
                :caller (:caller context)}))})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result))
      (is (= SQLException (:type result)))
      (is (= "Something went wrong..." (:error result)))
      (is (= "thing-service" (:caller result))))))

(deftest
  data-source-check-fn-uses-supplied-exception-fn-on-sql-timeout-exception
  (let [exception (SQLTimeoutException. "Timed out...")
        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [_] (throw exception))))

        check-fn
        (scfds/data-source-check-fn data-source
          {:exception-result-fn
           (fn [_ ^Exception exception]
             (salutem/unhealthy
               {:type  (.getClass exception)
                :error (.getMessage exception)}))})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result))
      (is (= SQLTimeoutException (:type result)))
      (is (= "Timed out..." (:error result))))))

(deftest
  data-source-check-fn-uses-supplied-exception-fn-on-other-exceptions
  (let [exception (IllegalArgumentException. "Weird argument...")
        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [_] (throw exception))))

        check-fn
        (scfds/data-source-check-fn data-source
          {:exception-result-fn
           (fn [_ ^Exception exception]
             (salutem/unhealthy
               {:type  (.getClass exception)
                :error (.getMessage exception)}))})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result))
      (is (= IllegalArgumentException (:type result)))
      (is (= "Weird argument..." (:error result))))))

(deftest data-source-check-fn-logs-on-start-when-logger-in-context
  (let [logger (ct/logger)
        context {:logger logger}

        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [{:keys [sql]}]
              (if (= "SELECT 1 AS up;" sql)
                [[1 [{:up 1}]]]
                (throw (SQLException. (str "Unexpected query: " sql)))))))

        check-fn (scfds/data-source-check-fn data-source)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (deref result-promise 500 nil)

    (is (logged? logger
          {:context {:query-sql-params ["SELECT 1 AS up;"]}
           :level   :info
           :type    :salutem.check-fns.data-source/check.starting}))))

(deftest data-source-check-fn-logs-on-success-when-logger-in-context
  (let [logger (ct/logger)
        context {:logger logger}

        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [{:keys [sql]}]
              (if (= "SELECT 1 AS up;" sql)
                [[1 [{:up 1}]]]
                (throw (SQLException. (str "Unexpected query: " sql)))))))

        check-fn (scfds/data-source-check-fn data-source)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (deref result-promise 500 nil)

    (is (logged? logger
          {:level :info
           :type  :salutem.check-fns.data-source/check.successful}))))

(deftest data-source-check-fn-logs-on-sql-exception-when-logger-in-context
  (let [logger (ct/logger)
        context {:logger logger}

        exception (SQLException. "Something went wrong.")

        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [_] (throw exception))))

        check-fn (scfds/data-source-check-fn data-source)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (deref result-promise 500 nil)

    (is (logged? logger
          {:context   {:reason :threw-exception}
           :exception exception
           :level     :warn
           :type      :salutem.check-fns.data-source/check.failed}))))

(deftest
  data-source-check-fn-logs-on-sql-timeout-exception-when-logger-in-context
  (let [logger (ct/logger)
        context {:logger logger}

        exception (SQLTimeoutException. "Something timed out.")

        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [_] (throw exception))))

        check-fn (scfds/data-source-check-fn data-source)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (deref result-promise 500 nil)

    (is (logged? logger
          {:context   {:reason :timed-out}
           :exception exception
           :level     :warn
           :type      :salutem.check-fns.data-source/check.failed}))))

(deftest data-source-check-fn-logs-on-other-exception-when-logger-in-context
  (let [logger (ct/logger)
        context {:logger logger}

        exception (IllegalArgumentException. "Something else happened.")

        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [_] (throw exception))))

        check-fn (scfds/data-source-check-fn data-source)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (deref result-promise 500 nil)

    (is (logged? logger
          {:context   {:reason :threw-exception}
           :exception exception
           :level     :warn
           :type      :salutem.check-fns.data-source/check.failed}))))

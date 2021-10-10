(ns salutem.check-fns.data-source.core-test
  (:require
   [clojure.test :refer :all]

   [salutem.core :as salutem]
   [salutem.check-fns.data-source.core :as scfds]

   [salutem.test.support.jdbc :as jdbc])
  (:import
   [java.sql SQLException SQLTimeoutException]))

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
                   {:query "SELECT version();"})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result))
      (is (= :threw-exception (:salutem/reason result)))
      (is (= exception (:salutem/exception result))))))

(deftest data-source-check-fn-uses-supplied-query-when-provided
  (let [data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [{:keys [sql]}]
              (if (= "SELECT version();" sql)
                [[1 [{:version "PostgreSQL 14.0"}]]]
                (throw (SQLException. (str "Unexpected query: " sql)))))))

        check-fn (scfds/data-source-check-fn data-source
                   {:query "SELECT version();"})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result))
      (is (= "PostgreSQL 14.0" (:version result))))))

(deftest data-source-check-fn-uses-supplied-result-fn-on-success
  (let [data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [{:keys [sql]}]
              (if (= "SELECT version();" sql)
                [[1 [{:version "PostgreSQL 14.0"}]]]
                (throw (SQLException. (str "Unexpected query: " sql)))))))

        check-fn
        (scfds/data-source-check-fn data-source
          {:query "SELECT version();"
           :success-result-fn
           (fn [query-result]
             (let [version
                   (second
                     (re-matches
                       #".*?(\d+\.\d+).*?"
                       (:version query-result)))]
               (salutem/healthy
                 {:version version})))})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result))
      (is (= "14.0" (:version result))))))

(deftest data-source-check-fn-uses-supplied-exception-fn-on-sql-exception
  (let [exception (SQLException. "Something went wrong...")
        data-source
        (jdbc/mock-data-source
          (jdbc/mock-connection
            (fn [_] (throw exception))))

        check-fn
        (scfds/data-source-check-fn data-source
          {:exception-result-fn
           (fn [^Exception exception]
             (salutem/unhealthy
               {:type  (.getClass exception)
                :error (.getMessage exception)}))})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result))
      (is (= SQLException (:type result)))
      (is (= "Something went wrong..." (:error result))))))

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
           (fn [^Exception exception]
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
           (fn [^Exception exception]
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

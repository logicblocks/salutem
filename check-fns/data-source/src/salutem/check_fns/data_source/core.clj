(ns salutem.check-fns.data-source.core
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc-rs]

   [cartus.core :as log]
   [cartus.null :as cn]

   [salutem.core :as salutem])
  (:import
   [java.sql SQLTimeoutException]))

(defn failure-reason [exception]
  (if (= (class exception) SQLTimeoutException)
    :timed-out
    :threw-exception))

(defn data-source-check-fn
  ([data-source] (data-source-check-fn data-source {}))
  ([data-source
    {:keys [query-sql-params
            query-results-result-fn
            exception-result-fn]
     :or   {query-sql-params
            ["SELECT 1 AS up;"]

            query-results-result-fn
            (fn [results]
              (salutem/healthy (first results)))

            exception-result-fn
            (fn [exception]
              (salutem/unhealthy
                {:salutem/reason    (failure-reason exception)
                 :salutem/exception exception}))}}]
   (fn [context result-cb]
     (let [logger (get context :logger (cn/logger))]
       (future
         (try
           (log/info logger :salutem.check-fns.data-source/check.starting
             {:query-sql-params query-sql-params})
           (let [results
                 (jdbc/execute! data-source query-sql-params
                   {:builder-fn jdbc-rs/as-unqualified-kebab-maps})]
             (log/info logger :salutem.check-fns.data-source/check.successful)
             (result-cb
               (query-results-result-fn results)))
           (catch Exception exception
             (log/warn logger :salutem.check-fns.data-source/check.failed
               {:reason (failure-reason exception)}
               {:exception exception})
             (result-cb
               (exception-result-fn exception)))))))))

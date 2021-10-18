(ns salutem.check-fns.data-source.core
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc-rs]

   [cartus.core :as log]
   [cartus.null :as cn]

   [salutem.core :as salutem])
  (:import
   [java.sql SQLTimeoutException]))

(defn- resolve-if-fn [thing context]
  (if (fn? thing) (thing context) thing))

(defn failure-reason [exception]
  (if (= (class exception) SQLTimeoutException)
    :timed-out
    :threw-exception))

(defn data-source-check-fn
  ([data-source] (data-source-check-fn data-source {}))
  ([data-source
    {:keys [query-sql-params
            query-opts
            query-results-result-fn
            exception-result-fn]
     :or   {query-sql-params
            ["SELECT 1 AS up;"]

            query-opts
            {:builder-fn jdbc-rs/as-unqualified-kebab-maps}

            query-results-result-fn
            (fn [_ results]
              (salutem/healthy (first results)))

            exception-result-fn
            (fn [_ exception]
              (salutem/unhealthy
                {:salutem/reason    (failure-reason exception)
                 :salutem/exception exception}))}}]
   (fn [context result-cb]
     (let [logger (get context :logger (cn/logger))]
       (future
         (try
           (let [query-sql-params (resolve-if-fn query-sql-params context)
                 query-opts (resolve-if-fn query-opts context)]
             (log/info logger
               :salutem.check-fns.data-source/check.starting
               {:query-sql-params query-sql-params})
             (let [results
                   (jdbc/execute! data-source query-sql-params query-opts)]
               (log/info logger
                 :salutem.check-fns.data-source/check.successful)
               (result-cb
                 (query-results-result-fn context results))))
           (catch Exception exception
             (log/warn logger
               :salutem.check-fns.data-source/check.failed
               {:reason (failure-reason exception)}
               {:exception exception})
             (result-cb
               (exception-result-fn context exception)))))))))

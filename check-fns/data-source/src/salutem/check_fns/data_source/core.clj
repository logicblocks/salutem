(ns salutem.check-fns.data-source.core
  "Provides a data source check function for salutem.

  Packaged in a separate module, `salutem.check-fns.data-source` versioned
  in lock step with `salutem.core`."
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc-rs]

   [tick.core :as time]

   [cartus.core :as log]
   [cartus.null :as cn]

   [salutem.core :as salutem])
  (:import
   [java.sql SQLTimeoutException]))

(defn- resolve-if-fn [thing context]
  (if (fn? thing) (thing context) thing))

(defn failure-reason
  "Determines the failure reason associated with an exception.

   This failure reason function, the default used by the check function, uses a
   reason of `:threw-exception` for all exceptions other than a
   [java.sql.SQLTimeoutException](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/SQLTimeoutException.html)
   for which the reason is `:timed-out`.

   In the case that this default behaviour is insufficient, an alternative
   failure reason function can be passed to [[data-source-check-fn]] using the
   `:failure-reason-fn` option."
  [exception]
  (if (= (class exception) SQLTimeoutException)
    :timed-out
    :threw-exception))

(defn data-source-check-fn
  "Returns a check function for the provided
  [javax.sql.DataSource](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/javax/sql/DataSource.html).

  Accepts the following options in the option map:

    - `:query-sql-params`: an SQL parameter vector (as defined in
      [`next.jdbc`](https://github.com/seancorfield/next-jdbc)) or a function
      of context that will return an SQL parameter vector containing the
      health check query to execute against the data source, defaults to
      `[\"SELECT 1 AS up;\"]`.
    - `:query-timeout`: a [[salutem.core/duration]] or a function of context
      that will return a [[salutem.core/duration]] representing the amount of
      time to wait for the query to finish before considering it failed;
      defaults to 5 seconds.
    - `:query-opts`: additional options (as defined in
      [`next.jdbc`](https://github.com/seancorfield/next-jdbc)) or a function
      of context that will return additional options to pass at query execution
      time; by default, includes a builder function for rows that returns
      unqualified kebab-cased maps; additional options are merged into the
      default option map.
    - `:query-results-result-fn`: a function, of context and the results of the
      query, used to produce a result for the check; by default, a healthy
      result is returned, including the contents of the first record from the
      results.
    - `:failure-reason-fn`: a function, of context and an exception, to
      determine the reason for a failure; by default uses [[failure-reason]].
    - `:exception-result-fn`: a function, of context and an exception, used to
      produce a result for the check in the case that an exception is thrown;
      by default, an unhealthy result is returned including a `:salutem/reason`
      entry with the reason derived by `:failure-reason-fn` and a
      `:salutem/exception` entry containing the thrown exception.

    If the returned check function is invoked with a context map including a
    `:logger` key with a
    [`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
    value, the check function will emit a number of log events whilst
    executing."
  ([data-source] (data-source-check-fn data-source {}))
  ([data-source
    {:keys [query-sql-params
            query-timeout
            query-opts
            query-results-result-fn
            failure-reason-fn
            exception-result-fn]}]
   ; Defaulting this way keeps argument documentation more readable while
   ; allowing defaults to be viewed more clearly using 'view source'.
   (let [query-sql-params
         (or query-sql-params ["SELECT 1 AS up;"])

         query-timeout
         (or query-timeout (time/new-duration 5 :seconds))

         query-results-result-fn
         (or query-results-result-fn
           (fn [_ results]
             (salutem/healthy (first results))))

         failure-reason-fn
         (or failure-reason-fn
           (fn [_ exception]
             (failure-reason exception)))

         exception-result-fn
         (or exception-result-fn
           (fn [context exception]
             (salutem/unhealthy
               {:salutem/reason    (failure-reason-fn context exception)
                :salutem/exception exception})))]
     (fn [context result-cb]
       (let [logger (get context :logger (cn/logger))]
         (future
           (try
             (let [query-sql-params (resolve-if-fn query-sql-params context)
                   query-timeout (resolve-if-fn query-timeout context)
                   query-opts
                   (merge
                     {:builder-fn jdbc-rs/as-unqualified-kebab-maps}
                     (resolve-if-fn query-opts context)
                     {:timeout (time/seconds query-timeout)})]
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
                 {:reason (failure-reason-fn context exception)}
                 {:exception exception})
               (result-cb
                 (exception-result-fn context exception))))))))))

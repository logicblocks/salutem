(ns salutem.check-fns.data-source.core
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc-rs]

   [cartus.core :as log]
   [cartus.null :as cn]

   [salutem.core :as salutem])
  (:import [java.sql SQLTimeoutException]))

(defn failure-reason [exception]
  (if (= (class exception) SQLTimeoutException)
    :timed-out
    :threw-exception))

(defn data-source-check-fn
  ([data-source] (data-source-check-fn data-source {}))
  ([data-source
    {:keys [query success-result-fn exception-result-fn]
     :or   {query
            "SELECT 1 AS up;"

            success-result-fn
            salutem/healthy

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
             {:query query})
           (let [result
                 (jdbc/execute-one! data-source [query]
                   {:builder-fn jdbc-rs/as-unqualified-kebab-maps})]
             (log/info logger :salutem.check-fns.data-source/check.successful)
             (result-cb
               (success-result-fn result)))
           (catch Exception exception
             (log/warn logger :salutem.check-fns.data-source/check.failed
               {:reason (failure-reason exception)}
               {:exception exception})
             (result-cb
               (exception-result-fn exception)))))))))

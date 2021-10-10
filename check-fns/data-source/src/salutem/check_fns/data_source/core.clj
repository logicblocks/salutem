(ns salutem.check-fns.data-source.core
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc-rs]

   [salutem.core :as salutem])
  (:import [java.sql SQLTimeoutException]))

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
              (if (= (class exception) SQLTimeoutException)
                (salutem/unhealthy
                  {:salutem/reason    :timed-out
                   :salutem/exception exception})
                (salutem/unhealthy
                  {:salutem/reason    :threw-exception
                   :salutem/exception exception})))}}]
   (fn [context result-cb]
     (future
       (try
         (let [result
               (jdbc/execute-one! data-source [query]
                 {:builder-fn jdbc-rs/as-unqualified-kebab-maps})]
           (result-cb
             (success-result-fn result)))
         (catch Exception exception
           (result-cb
             (exception-result-fn exception))))))))

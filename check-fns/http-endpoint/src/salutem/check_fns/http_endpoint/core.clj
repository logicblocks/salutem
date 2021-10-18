(ns salutem.check-fns.http-endpoint.core
  (:require
   [clj-http.client :as http]

   [tick.alpha.api :as time]

   [cartus.core :as log]
   [cartus.null :as cn]

   [salutem.core :as salutem])
  (:import
   [org.apache.http.conn ConnectTimeoutException]
   [java.net SocketTimeoutException ConnectException]))

(defn- resolve-if-fn [thing context]
  (if (fn? thing) (thing context) thing))

(defn failure-reason [exception]
  (let [exception-class (class exception)
        exception-message (ex-message exception)
        contains-timeout (re-matches #".*Timeout.*" exception-message)]
    (if (or
          (isa? exception-class ConnectTimeoutException)
          (isa? exception-class SocketTimeoutException)
          (and (isa? exception-class ConnectException) contains-timeout))
      :timed-out
      :threw-exception)))

(defn successful? [{:keys [status]}]
  (http/unexceptional-status? status))

(defn http-endpoint-check-fn
  ([url] (http-endpoint-check-fn url {}))
  ([url
    {:keys [method
            body
            headers
            query-params

            opts

            connection-request-timeout
            connection-timeout
            socket-timeout

            connection-manager

            response-result-fn
            exception-result-fn]
     :or   {method :get

            connection-request-timeout
            (time/new-duration 5 :seconds)
            connection-timeout
            (time/new-duration 5 :seconds)
            socket-timeout
            (time/new-duration 5 :seconds)

            response-result-fn
            (fn [_ response]
              (if (successful? response)
                (salutem/healthy)
                (salutem/unhealthy)))

            exception-result-fn
            (fn [_ exception]
              (salutem/unhealthy
                {:salutem/reason    (failure-reason exception)
                 :salutem/exception exception}))}}]
   (fn [context result-cb]
     (let [logger (get context :logger (cn/logger))]
       (try
         (let [endpoint-params
               {:url          (resolve-if-fn url context)
                :method       (resolve-if-fn method context)
                :body         (resolve-if-fn body context)
                :headers      (resolve-if-fn headers context)
                :query-params (resolve-if-fn query-params context)}]
           (log/info logger :salutem.check-fns.http-endpoint/check.starting
             endpoint-params)
           (http/request
             (merge
               endpoint-params
               {:throw-exceptions false}
               (resolve-if-fn opts context)
               {:async?             true
                :connection-timeout (time/millis connection-timeout)
                :socket-timeout     (time/millis socket-timeout)
                :connection-request-timeout
                (time/millis connection-request-timeout)
                :connection-manager connection-manager})
             (fn [response]
               (log/info logger
                 :salutem.check-fns.http-endpoint/check.successful)
               (result-cb
                 (response-result-fn
                   context response)))
             (fn [exception]
               (log/warn logger :salutem.check-fns.http-endpoint/check.failed
                 {:reason (failure-reason exception)}
                 {:exception exception})
               (result-cb
                 (exception-result-fn
                   context exception)))))
         (catch Exception exception
           (log/warn logger :salutem.check-fns.http-endpoint/check.failed
             {:reason (failure-reason exception)}
             {:exception exception})
           (result-cb
             (exception-result-fn context exception))))))))

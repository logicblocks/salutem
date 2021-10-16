(ns salutem.check-fns.http-endpoint.core
  (:require
   [clj-http.client :as http]

   [tick.alpha.api :as time]

   [salutem.core :as salutem])
  (:import
   [org.apache.http.conn ConnectTimeoutException]
   [java.net SocketTimeoutException ConnectException]))

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
  ([url-or-url-fn] (http-endpoint-check-fn url-or-url-fn {}))
  ([url-or-url-fn
    {:keys [method
            body
            response-result-fn
            exception-result-fn
            connection-request-timeout
            connection-timeout
            socket-timeout
            connection-manager]
     :or   {method :get

            response-result-fn
            (fn [_ response]
              (if (successful? response)
                (salutem/healthy)
                (salutem/unhealthy)))

            exception-result-fn
            (fn [_ exception]
              (salutem/unhealthy
                {:salutem/reason    (failure-reason exception)
                 :salutem/exception exception}))

            connection-request-timeout
            (time/new-duration 5 :seconds)
            connection-timeout
            (time/new-duration 5 :seconds)
            socket-timeout
            (time/new-duration 5 :seconds)}}]
   (fn [context result-cb]
     (try
       (let [url
             (if (fn? url-or-url-fn)
               (url-or-url-fn context)
               url-or-url-fn)]
         (http/request
           {:url                url
            :method             method
            :body               body
            :throw-exceptions   false
            :async?             true
            :connection-timeout (time/millis connection-timeout)
            :socket-timeout     (time/millis socket-timeout)
            :connection-request-timeout
            (time/millis connection-request-timeout)
            :connection-manager connection-manager}
           (fn [response]
             (result-cb
               (response-result-fn
                 context response)))
           (fn [exception]
             (result-cb
               (exception-result-fn
                 context exception)))))
       (catch Exception exception
         (result-cb
           (exception-result-fn context exception)))))))

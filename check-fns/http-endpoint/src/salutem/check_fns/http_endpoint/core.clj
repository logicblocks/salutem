(ns salutem.check-fns.http-endpoint.core
  "Provides an HTTP endpoint check function for salutem."
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

(defn failure-reason
  "Determines the failure reason associated with an exception.

   This failure reason function, the default used by the check function, uses a
   reason of `:threw-exception` for all exceptions other than:

     * [org.apache.http.conn.ConnectTimeoutException](https://www.javadoc.io/doc/org.apache.httpcomponents/httpclient/latest/org/apache/http/conn/ConnectTimeoutException.html)
     * [java.net.SocketTimeoutException](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/SQLTimeoutException.html)
     * [java.net.ConnectException](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/SQLTimeoutException.html)
       when the exception message includes \"Timeout\"

   for which the reason is `:timed-out`.

   In the case that this default behaviour is insufficient, an alternative
   failure reason function can be passed to [[http-endpoint-check-fn]] using the
   `:failure-reason-fn` option."
  [exception]
  (let [exception-class (class exception)
        exception-message (ex-message exception)
        contains-timeout (re-matches #".*Timeout.*" exception-message)]
    (if (or
          (isa? exception-class ConnectTimeoutException)
          (isa? exception-class SocketTimeoutException)
          (and (isa? exception-class ConnectException) contains-timeout))
      :timed-out
      :threw-exception)))

(defn successful?
  "Returns true if the provided response has a successful status, false
   otherwise.

   This response success function, the default used by the check function,
   treats status codes of 200, 201, 202, 203, 204, 205, 206, 207, 300, 301, 302,
   303, 304, 307 and 308 as successful.

   In the case that this default behaviour is insufficient, an alternative
   response success function can be passed to [[http-endpoint-check-fn]] using
   the `:successful-response-fn` option."
  [{:keys [status]}]
  (http/unexceptional-status? status))

(defn http-endpoint-check-fn
  "Returns a check function for the HTTP endpoint identified by the provided
   URL.

   Accepts the following options in the option map:

     - `:method`: a keyword representing the method used to check the endpoint
       (one of `:get`, `:head`, `:post`, `:put`, `:delete`, `:options`, `:copy`,
       `:move` or `:patch`) or a function of context that will return such a
       keyword; defaults to `:get`.
     - `:body`: an object representing the body sent to the endpoint on check
       execution (supporting anything [`clj-http`](https://github.com/dakrone/clj-http)
       will accept) or a function of context that will return such an object;
       defaults to `nil`.
     - `:headers`: a map of headers to be sent to the endpoint on check
       execution (as supported by [`clj-http`](https://github.com/dakrone/clj-http))
       or a function of context that will return such a map; defaults to
       `nil`.
     - `:query-params`: a map of query parameters to be sent to the endpoint on
       check execution (as supported by [`clj-http`](https://github.com/dakrone/clj-http))
       or a function of context that will return such a map; defaults to
       `nil`.
     - `:opts`: a map of additional query options (as supported by
       [`clj-http`](https://github.com/dakrone/clj-http)) or a function of
       context that will return such a map; defaults to
       `{:throw-exceptions false}` since we want response success to be deduced
       by the `:response-result-fn` rather than treating unsuccessful statuses
       as exceptions; note that any timeouts passed in this query options map are
       ignored and should be set using `:connection-request-timeout`,
       `:connection-timeout` and `:socket-timeout`.
     - `:connection-request-timeout`: the [[salutem.core/duration]] to wait
       when obtaining a connection from the connection manager before
       considering the request failed; defaults to 5 seconds.
     - `:connection-timeout`: the [[salutem.core/duration]] to wait when
       establishing an HTTP connection before considering the request failed;
       defaults to 5 seconds.
     - `:socket-timeout`: the [[salutem.core/duration]] to wait while streaming
       response data since the last data was received before considering the
       request failed; defaults to 5 seconds.
     - `:successful-response-fn`: a function of context and the response from a
       request to the endpoint, returning true if the response was successful,
       false otherwise; by default uses [[successful?]].
     - `:response-result-fn`: a function, of context and the response from a
       request to the endpoint, used to produce a result for the check; by
       default, a healthy result is returned if the response is successful
       according to `:successful-response-fn`, otherwise an unhealthy result is
       returned.
     - `:failure-reason-fn`: a function, of context and an exception, to
       determine the reason for a failure; by default uses [[failure-reason]].
     - `:exception-result-fn`: a function, of context and an exception, used to
       produce a result for the check in the case that an exception is thrown;
       by default, an unhealthy result is returned including a `:salutem/reason`
       entry with the reason derived by `:failure-reason-fn` and a
       `:salutem/exception` entry containing the thrown exception.

     Additionally, if the URL parameter is instead a function, it will be called
     with the context map at check execution time in order to obtain the
     endpoint URL.

     If the returned check function is invoked with a context map including a
     `:logger` key with a
     [`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
     value, the check function will emit a number of log events whilst
     executing."
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

            successful-response-fn
            response-result-fn

            failure-reason-fn
            exception-result-fn]}]
   ; Defaulting this way keeps argument documentation more readable while
   ; allowing defaults to be viewed more clearly using 'view source'.
   (let [method (or method :get)

         connection-request-timeout
         (or connection-request-timeout (time/new-duration 5 :seconds))
         connection-timeout
         (or connection-timeout (time/new-duration 5 :seconds))
         socket-timeout
         (or socket-timeout (time/new-duration 5 :seconds))

         successful-response-fn
         (or successful-response-fn
           (fn [_ response]
             (successful? response)))
         response-result-fn
         (or response-result-fn
           (fn [context response]
             (if (successful-response-fn context response)
               (salutem/healthy)
               (salutem/unhealthy))))

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
                  (time/millis connection-request-timeout)})
               (fn [response]
                 (log/info logger
                   :salutem.check-fns.http-endpoint/check.successful)
                 (result-cb
                   (response-result-fn
                     context response)))
               (fn [exception]
                 (log/warn logger :salutem.check-fns.http-endpoint/check.failed
                   {:reason (failure-reason-fn context exception)}
                   {:exception exception})
                 (result-cb
                   (exception-result-fn
                     context exception)))))
           (catch Exception exception
             (log/warn logger :salutem.check-fns.http-endpoint/check.failed
               {:reason (failure-reason-fn context exception)}
               {:exception exception})
             (result-cb
               (exception-result-fn context exception)))))))))

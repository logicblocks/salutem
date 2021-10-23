(ns salutem.check-fns.http-endpoint.core-test
  (:require
   [clojure.test :refer :all]
   [clojure.set :as set]

   [cartus.test :as ct]

   [clj-http.fake :as http]

   [salutem.core :as salutem]
   [salutem.check-fns.http-endpoint.core :as scfhe])
  (:import
   [org.apache.http.conn ConnectTimeoutException]
   [java.net SocketTimeoutException ConnectException]))

(declare logged?)

(def all-status-codes
  (into #{} (range 100 600)))
(def success-status-codes
  #{200 201 202 203 204 205 206 207 300 301 302 303 304 307 308})
(def failure-status-codes
  (set/difference all-status-codes success-status-codes))

(deftest failure-reason-returns-timed-out-for-timeout-exceptions
  (is (= :timed-out
        (scfhe/failure-reason
          (ConnectTimeoutException. "Out of time."))))
  (is (= :timed-out
        (scfhe/failure-reason
          (SocketTimeoutException. "Out of time."))))
  (is (= :timed-out
        (scfhe/failure-reason
          (ConnectException. "Timeout connecting to something.")))))

(deftest failure-reason-returns-threw-exception-for-non-timeout-exceptions
  (is (= :threw-exception
        (scfhe/failure-reason
          (IllegalArgumentException. "Does not compute."))))
  (is (= :threw-exception
        (scfhe/failure-reason
          (ConnectException. "Connection refused.")))))

(deftest successful?-returns-true-for-response-with-success-status-code
  (doseq [status-code success-status-codes]
    (is (scfhe/successful? {:status status-code}))))

(deftest successful?-returns-false-for-response-with-failure-status-code
  (doseq [status-code failure-status-codes]
    (is (not (scfhe/successful? {:status status-code})))))

(deftest http-endpoint-check-fn-returns-healthy-when-request-successful
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (doseq [status-code success-status-codes]
      (http/with-global-fake-routes-in-isolation
        {endpoint-url
         (fn [_] {:status status-code})}
        (check-fn context result-cb))

      (let [result (deref result-promise 500 nil)]
        (is (salutem/healthy? result)
          (str status-code " should have produced healthy result"))))))

(deftest http-endpoint-check-fn-uses-endpoint-url-fn-when-provided
  (let [context {:access-key "123"}
        endpoint-url-fn
        (fn [context]
          (str "http://service.example.com/ping?access_key="
            (:access-key context)))

        check-fn (scfhe/http-endpoint-check-fn endpoint-url-fn)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping?access_key=123"
       (fn [_] {:status 200})}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-uses-get-as-method-by-default
  (let [context {}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       {:get (fn [_] {:status 200})}}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-uses-supplied-method-when-provided
  (let [context {}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:method :head})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       {:head (fn [_] {:status 200})}}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-uses-supplied-method-fn-when-provided
  (let [context {:method :head}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:method (fn [context] (:method context))})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       {:head (fn [_] {:status 200})}}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-uses-empty-body-by-default
  (let [context {}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       (fn [request]
         (if (nil? (:body request))
           {:status 200}
           (throw (IllegalStateException. "Expected no body but got one."))))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-uses-supplied-body-when-provided
  (let [context {}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:body "access_key = 123"})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       (fn [request]
         (if (= "access_key = 123" (slurp (:body request)))
           {:status 200}
           (throw (IllegalStateException.
                    "Expected supplied body but got something else."))))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-uses-supplied-body-fn-when-provided
  (let [context {:access-key "123"}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:body (fn [context]
                            (str "access_key = "
                              (:access-key context)))})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       (fn [request]
         (if (= "access_key = 123" (slurp (:body request)))
           {:status 200}
           (throw (IllegalStateException.
                    "Expected supplied body but got something else."))))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-passes-no-headers-by-default
  (let [context {}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       (fn [request]
         ; accept-encoding added by default
         (if (empty? (dissoc (:headers request) "accept-encoding"))
           {:status 200}
           (throw (IllegalStateException.
                    "Expected no headers but got some."))))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-passes-supplied-headers-when-provided
  (let [context {}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:headers {"x-important-header" 56}})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       (fn [request]
         ; accept-encoding added by default
         (if (= 56 (get-in request [:headers "x-important-header"]))
           {:status 200}
           (throw (IllegalStateException.
                    "Expected no headers but got some."))))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-passes-supplied-headers-fn-when-provided
  (let [context {:access-key "123"}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:headers
                    (fn [context]
                      {"x-access-key" (:access-key context)})})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       (fn [request]
         ; accept-encoding added by default
         (if (= (:access-key context)
               (get-in request [:headers "x-access-key"]))
           {:status 200}
           (throw (IllegalStateException.
                    "Expected no headers but got some."))))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-passes-no-query-params-by-default
  (let [context {}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       (fn [request]
         (if (nil? (get request :query-params))
           {:status 200}
           (throw (IllegalStateException.
                    "Expected supplied query-params."))))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-passes-supplied-query-params-when-provided
  (let [context {}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:query-params {:a 1}})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping?a=1"
       (fn [_] {:status 200})}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-passes-supplied-query-params-fn-when-provided
  (let [context {:access-key "123"}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:query-params
                    (fn [context]
                      {:access-key (:access-key context)})})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping?access-key=123"
       (fn [_] {:status 200})}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-passes-additional-options-map
  (let [context {}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:opts {:max-redirects     5
                           :redirect-strategy :graceful}})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       (fn [request]
         (if (and
               (= 5 (get request :max-redirects))
               (= :graceful (get request :redirect-strategy)))
           {:status 200}
           (throw (IllegalStateException.
                    "Expected opts."))))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-passes-additional-options-from-fn
  (let [context {:max-redirects 5}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:opts
                    (fn [context]
                      {:max-redirects     (:max-redirects context)
                       :redirect-strategy :graceful})})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping"
       (fn [request]
         (if (and
               (= 5 (get request :max-redirects))
               (= :graceful (get request :redirect-strategy)))
           {:status 200}
           (throw (IllegalStateException.
                    "Expected opts."))))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-returns-unhealthy-when-request-unsuccessful
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (doseq [status-code failure-status-codes]
      (http/with-global-fake-routes-in-isolation
        {endpoint-url
         (fn [_] {:status status-code})}
        (check-fn context result-cb))

      (let [result (deref result-promise 500 nil)]
        (is (salutem/unhealthy? result)
          (str status-code " should have produced unhealthy result"))))))

(deftest http-endpoint-check-fn-returns-unhealthy-on-timeout-exceptions
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)

        context {}]
    (doseq [timeout-exception
            [(ConnectTimeoutException. "We're out of time.")
             (SocketTimeoutException. "We're out of time.")
             (ConnectException. "Timeout when connecting.")]]
      (let [result-promise (promise)
            result-cb (partial deliver result-promise)]
        (http/with-global-fake-routes-in-isolation
          {endpoint-url
           (fn [_] (throw timeout-exception))}
          (check-fn context result-cb))

        (let [result (deref result-promise 500 nil)]
          (is (salutem/unhealthy? result))
          (is (= :timed-out (:salutem/reason result)))
          (is (= timeout-exception (:salutem/exception result))))))))

(deftest http-endpoint-check-fn-returns-unhealthy-on-other-exception
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)

        context {}
        exception (IllegalArgumentException. "That doesn't look right.")
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {endpoint-url
       (fn [_] (throw exception))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result))
      (is (= :threw-exception (:salutem/reason result)))
      (is (= exception (:salutem/exception result))))))

(deftest http-endpoint-check-fn-uses-supplied-successful-response-fn-on-success
  (let [context {:success-statuses #{200}}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:successful-response-fn
                    (fn [context response]
                      (contains? (:success-statuses context)
                        (get response :status)))})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {endpoint-url
       (fn [_] {:status 200 :body "All is right with the world."})}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-uses-supplied-successful-response-fn-on-failure
  (let [context {:success-statuses #{200}}
        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:successful-response-fn
                    (fn [context response]
                      (contains? (:success-statuses context)
                        (get response :status)))})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {endpoint-url
       (fn [_] {:status 201 :body "All is right with the world."})}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result)))))

(deftest http-endpoint-check-fn-uses-supplied-response-result-fn-on-success
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:response-result-fn
                    (fn [context response]
                      (if (= 200 (get response :status))
                        (salutem/healthy
                          (merge context
                            {:message (get response :body)}))
                        (salutem/unhealthy)))})
        context {:important :runtime-value}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {endpoint-url
       (fn [_] {:status 200 :body "All is right with the world."})}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result))
      (is (= "All is right with the world." (:message result)))
      (is (= :runtime-value (:important result))))))

(deftest http-endpoint-check-fn-uses-supplied-response-result-fn-on-failure
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:response-result-fn
                    (fn [context response]
                      (if (= 200 (get response :status))
                        (salutem/healthy)
                        (salutem/unhealthy
                          (merge context
                            {:message (get response :body)}))))})
        context {:important :runtime-value}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {endpoint-url
       (fn [_] {:status 500 :body "Things have gone awry."})}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result))
      (is (= "Things have gone awry." (:message result)))
      (is (= :runtime-value (:important result))))))

(deftest http-endpoint-check-fn-uses-supplied-failure-reason-fn
  (let [context {:argument-failure-reason :received-invalid-argument}
        exception (IllegalArgumentException. "That's not what I need...")

        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:failure-reason-fn
                    (fn [context ^Exception exception]
                      (if (isa? (class exception) IllegalArgumentException)
                        (:argument-failure-reason context)
                        :threw-exception))})]
    (let [result-promise (promise)
          result-cb (partial deliver result-promise)]
      (http/with-global-fake-routes-in-isolation
        {endpoint-url
         (fn [_] (throw exception))}
        (check-fn context result-cb))

      (let [result (deref result-promise 500 nil)]
        (is (salutem/unhealthy? result))
        (is (= :received-invalid-argument (:salutem/reason result)))
        (is (= exception (:salutem/exception result)))))))

(deftest
  http-endpoint-check-fn-uses-exception-result-fn-on-timeout-exceptions
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:exception-result-fn
                    (fn [context ^Exception exception]
                      (salutem/unhealthy
                        (merge context
                          {:message (.getMessage exception)})))})
        context {:important :runtime-value}]
    (doseq [timeout-exception
            [(ConnectTimeoutException. "We're out of time.")
             (SocketTimeoutException. "We're out of time.")
             (ConnectException. "Timeout when connecting.")]]
      (let [result-promise (promise)
            result-cb (partial deliver result-promise)]
        (http/with-global-fake-routes-in-isolation
          {endpoint-url
           (fn [_] (throw timeout-exception))}
          (check-fn context result-cb))

        (let [result (deref result-promise 500 nil)]
          (is (salutem/unhealthy? result))
          (is (= (.getMessage ^Exception timeout-exception) (:message result)))
          (is (= :runtime-value (:important result))))))))

(deftest
  http-endpoint-check-fn-uses-exception-result-fn-on-other-exceptions
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:exception-result-fn
                    (fn [context ^Exception exception]
                      (salutem/unhealthy
                        (merge context
                          {:message (.getMessage exception)})))})
        context {:important :runtime-value}]
    (doseq [standard-exception
            [(IllegalArgumentException. "Well that's strange.")
             (ConnectException. "Connection refused.")]]
      (let [result-promise (promise)
            result-cb (partial deliver result-promise)]
        (http/with-global-fake-routes-in-isolation
          {endpoint-url
           (fn [_] (throw standard-exception))}
          (check-fn context result-cb))

        (let [result (deref result-promise 500 nil)]
          (is (salutem/unhealthy? result))
          (is (= (.getMessage ^Exception standard-exception) (:message result)))
          (is (= :runtime-value (:important result))))))))

(deftest http-endpoint-check-fn-logs-on-start-when-logger-in-context
  (let [logger (ct/logger)
        context {:logger logger}

        url "http://service.example.com/ping"

        method :head
        query-params {:caller "thing-service"}
        headers {"x-important-header" 56}
        body "ping"

        check-fn (scfhe/http-endpoint-check-fn url
                   {:method       method
                    :query-params query-params
                    :headers      headers
                    :body         body})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {url
       (fn [_] {:status 200})}
      (check-fn context result-cb))

    (deref result-promise 500 nil)

    (is (logged? logger
          {:context {:url          url
                     :method       method
                     :query-params query-params
                     :headers      headers
                     :body         body}
           :level   :info
           :type    :salutem.check-fns.http-endpoint/check.starting}))))

(deftest http-endpoint-check-fn-logs-on-response-when-logger-in-context
  (let [logger (ct/logger)
        context {:logger logger}

        url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn url)

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {url
       (fn [_] {:status 200})}
      (check-fn context result-cb))

    (deref result-promise 500 nil)

    (is (logged? logger
          {:level   :info
           :type    :salutem.check-fns.http-endpoint/check.successful}))))

(deftest
  http-endpoint-check-fn-logs-on-timeout-exceptions-when-logger-in-context
  (let [logger (ct/logger)
        context {:logger logger}

        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)]
    (doseq [timeout-exception
            [(ConnectTimeoutException. "We're out of time.")
             (SocketTimeoutException. "We're out of time.")
             (ConnectException. "Timeout when connecting.")]]
      (let [result-promise (promise)
            result-cb (partial deliver result-promise)]
        (http/with-global-fake-routes-in-isolation
          {endpoint-url
           (fn [_] (throw timeout-exception))}
          (check-fn context result-cb))

        (deref result-promise 500 nil)

        (is (logged? logger
              {:context   {:reason :timed-out}
               :exception timeout-exception
               :level     :warn
               :type      :salutem.check-fns.http-endpoint/check.failed}))))))

(deftest
  http-endpoint-check-fn-logs-on-other-exceptions-when-logger-in-context
  (let [logger (ct/logger)
        context {:logger logger}

        endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)]
    (doseq [standard-exception
            [(IllegalArgumentException. "Well that's strange.")
             (ConnectException. "Connection refused.")]]
      (let [result-promise (promise)
            result-cb (partial deliver result-promise)]
        (http/with-global-fake-routes-in-isolation
          {endpoint-url
           (fn [_] (throw standard-exception))}
          (check-fn context result-cb))

        (deref result-promise 500 nil)

        (is (logged? logger
              {:context   {:reason :threw-exception}
               :exception standard-exception
               :level     :warn
               :type      :salutem.check-fns.http-endpoint/check.failed}))))))

(ns salutem.check-fns.http-endpoint.core-test
  (:require
   [clojure.test :refer :all]
   [clojure.set :as set]

   [clj-http.fake :as http]

   [salutem.core :as salutem]
   [salutem.check-fns.http-endpoint.core :as scfhe])
  (:import
   [org.apache.http.conn ConnectTimeoutException]
   [java.net SocketTimeoutException ConnectException]))

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
       {:get (fn [_] {:status 200})}}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-uses-supplied-method-when-provided
  (let [context {:access-key "123"}
        endpoint-url-fn
        (fn [context]
          (str "http://service.example.com/ping?access_key="
            (:access-key context)))

        check-fn (scfhe/http-endpoint-check-fn endpoint-url-fn
                   {:method :head})

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {"http://service.example.com/ping?access_key=123"
       {:head (fn [_] {:status 200})}}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-uses-empty-body-by-default
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
       (fn [request]
         (if (nil? (:body request))
           {:status 200}
           (throw (IllegalStateException. "Expected no body but got one."))))}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-uses-supplied-body-when-provided
  (let [context {:access-key "123"}
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

; headers
; logs

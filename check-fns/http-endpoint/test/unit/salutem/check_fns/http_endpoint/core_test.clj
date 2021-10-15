(ns salutem.check-fns.http-endpoint.core-test
  (:require
   [clojure.test :refer :all]
   [clojure.set :as set]

   [clj-http.fake :as http]

   [salutem.core :as salutem]
   [salutem.check-fns.http-endpoint.core :as scfhe]))

(def all-status-codes
  (into #{} (range 100 600)))
(def success-status-codes
  #{200 201 202 203 204 205 206 207 300 301 302 303 304 307 308})
(def unsuccessful-status-codes
  (set/difference all-status-codes success-status-codes))

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

(deftest http-endpoint-check-fn-returns-unhealthy-when-request-unsuccessful
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (doseq [status-code unsuccessful-status-codes]
      (http/with-global-fake-routes-in-isolation
        {endpoint-url
         (fn [_] {:status status-code})}
        (check-fn context result-cb))

      (let [result (deref result-promise 500 nil)]
        (is (salutem/unhealthy? result)
          (str status-code " should have produced unhealthy result"))))))

(deftest http-endpoint-check-fn-returns-healthy-when-success-fn-returns-true
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:success-fn
                    (fn [response]
                      (= 200 (get response :status)))})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {endpoint-url
       (fn [_] {:status 200})}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/healthy? result)))))

(deftest http-endpoint-check-fn-returns-healthy-when-success-fn-returns-false
  (let [endpoint-url "http://service.example.com/ping"

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:success-fn
                    (fn [response]
                      (= 200 (get response :status)))})
        context {}
        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (http/with-global-fake-routes-in-isolation
      {endpoint-url
       (fn [_] {:status 201})}
      (check-fn context result-cb))

    (let [result (deref result-promise 500 nil)]
      (is (salutem/unhealthy? result)))))

(ns salutem.check-fns.http-endpoint.core-test
  (:require
   [clojure.test :refer :all]

   [clj-http.conn-mgr :as conn-mgr]
   [clj-wiremock.core :as wiremock]

   [tick.alpha.api :as time]

   [salutem.core :as salutem]
   [salutem.check-fns.http-endpoint.core :as scfhe]

   [salutem.test.support.ports :as ports]))

(deftest http-endpoint-check-fn-executes-request-asynchronously
  (let [wiremock-port (ports/free-port)
        endpoint-url (str "http://localhost:" wiremock-port "/ping")

        check-fn (scfhe/http-endpoint-check-fn endpoint-url)

        context {}

        result-promise (promise)
        result-cb (partial deliver result-promise)

        order-atom (atom [])]
    (wiremock/with-wiremock [{:port wiremock-port}]
      (wiremock/with-stubs
        [{:port wiremock-port
          :req [:GET "/ping"]
          :res [200 {:body                   "pong"
                     :fixedDelayMilliseconds 250}]}]
        (swap! order-atom conj :before)
        (check-fn context
          (fn [result]
            (swap! order-atom conj :finished)
            (result-cb result)))
        (swap! order-atom conj :after)

        (deref result-promise 500 nil)))

    (is (= [:before :after :finished]
          (deref order-atom)))))

(deftest http-endpoint-check-fn-times-out-after-supplied-socket-timeout
  (let [endpoint-port (ports/free-port)
        endpoint-url (str "http://localhost:" endpoint-port "/ping")

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:socket-timeout (time/new-duration 500 :millis)})

        context {}

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (wiremock/with-wiremock [{:port endpoint-port}]
      (wiremock/with-stubs
        [{:port endpoint-port
          :req [:GET "/ping"]
          :res [200 {:body                   "pong"
                     :fixedDelayMilliseconds 1000}]}]

        (check-fn context result-cb)

        (let [result (deref result-promise 1500 nil)]
          (is (salutem/unhealthy? result))
          (is (= :timed-out (:salutem/reason result))))))))

(deftest http-endpoint-check-fn-times-out-after-supplied-connection-timeout
  (let [unroutable-address "10.0.0.0"
        endpoint-url (str "http://" unroutable-address "/ping")

        ; Need to manage connection manager directly to control when shutdown
        ; wait time is incurred.
        connection-manager (conn-mgr/make-reusable-async-conn-manager {})

        check-fn (scfhe/http-endpoint-check-fn endpoint-url
                   {:connection-timeout (time/new-duration 500 :millis)
                    :connection-manager connection-manager})

        context {}

        result-promise (promise)
        result-cb (partial deliver result-promise)]
    (check-fn context result-cb)

    (let [result (deref result-promise 1500 nil)]
      (is (salutem/unhealthy? result))
      (is (= :timed-out (:salutem/reason result))))

    (conn-mgr/shutdown-manager connection-manager)))

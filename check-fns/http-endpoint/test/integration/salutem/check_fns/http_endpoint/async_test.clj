(ns salutem.check-fns.http-endpoint.async-test
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

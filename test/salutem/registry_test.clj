(ns salutem.registry-test
  (:require
   [clojure.test :refer :all]

   [spy.core :as spy]
   [tick.alpha.api :as t]

   [salutem.checks :as checks]
   [salutem.results :as results]
   [salutem.registry :as registry]))

(deftest adds-single-check-to-registry
  (let [check (checks/check :thing
                (fn [_ result-cb]
                  (result-cb (results/result :healthy))))
        registry (registry/with-check (registry/empty-registry) check)]
    (is (= (registry/find-check registry :thing) check))))

(deftest adds-many-checks-to-registry
  (let [check-1 (checks/check :thing-1
                  (fn [_ result-cb]
                    (result-cb (results/result :healthy))))
        check-2 (checks/check :thing-2
                  (fn [_ result-callback]
                    (result-callback (results/result :unhealthy))))

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2))]
    (is (= (registry/check-names registry) #{:thing-1 :thing-2}))))

(deftest resolves-realtime-check-every-time
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (results/result :healthy))))
        check (checks/check :thing
                check-fn
                {:type :realtime})
        registry (registry/with-check (registry/empty-registry) check)

        resolved-result-1 (registry/resolve-check registry :thing)
        _ (Thread/sleep 10)
        resolved-result-2 (registry/resolve-check registry :thing)]
    (is (= (count (spy/calls check-fn
                    )) 2))
    (is (results/healthy? resolved-result-1))
    (is (results/healthy? resolved-result-2))
    (is (t/>
          (:evaluated-at resolved-result-2)
          (:evaluated-at resolved-result-1)))))

(deftest resolves-cached-check-when-no-cached-result-available
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (results/result :unhealthy))))
        check (checks/check :thing
                check-fn
                {:type :cached})
        registry (registry/with-check (registry/empty-registry) check)
        resolved-result (registry/resolve-check registry :thing)]
    (is (= (count (spy/calls check-fn)) 1))
    (is (results/unhealthy? resolved-result))))

(deftest resolves-cached-check-as-previous-result-when-available
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (results/result :unhealthy))))
        check (checks/check :thing
                check-fn
                {:type :cached})
        cached-result (results/result :healthy)
        registry (-> (registry/empty-registry)
                   (registry/with-check check)
                   (registry/with-cached-result check cached-result))
        resolved-result (registry/resolve-check registry :thing)]
    (is (= (count (spy/calls check-fn)) 0))
    (is (results/healthy? resolved-result))))

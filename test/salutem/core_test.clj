(ns salutem.core-test
  (:require
   [clojure.test :refer :all]

   [salutem.core :as salutem]))

; use a single atom (registry) to store checks and results
; provide functions to look up parts of the registry
;   - if check being resolved is async, fetch latest result
;   - if check being resolved is sync, execute to get result and update registry
; use go routine on timer to run through and run checks that need running
; when async checks run, update registry in atom

(deftest adds-single-check-to-registry
  (let [check (salutem/check :thing (fn [_] (salutem/result :healthy)))
        registry (salutem/with-check (salutem/empty-registry) check)]
    (is (= (salutem/find-check registry :thing) check))))

(deftest adds-many-checks-to-registry
  (let [check-1 (salutem/check :thing-1 (fn [_] (salutem/result :healthy)))
        check-2 (salutem/check :thing-2 (fn [_] (salutem/result :unhealthy)))

        registry (-> (salutem/empty-registry)
                   (salutem/with-check check-1)
                   (salutem/with-check check-2))]
   (is (= (salutem/check-names registry) #{:thing-1 :thing-2}))))

(deftest resolves-synchronous-check-when-no-previous-result-available
  (let [check (salutem/check :thing
                (fn [_] (salutem/result :healthy))
                {:asynchronous false})
        registry (salutem/with-check (salutem/empty-registry) check)]
    (is (= (salutem/resolve-check registry :thing)
          (salutem/result :healthy)))))

(deftest resolves-synchronous-check-even-when-previous-result-available
  (let [check (salutem/check :thing
                (fn [_] (salutem/result :unhealthy))
                {:asynchronous false})
        result (salutem/result :healthy)
        registry (-> (salutem/empty-registry)
                   (salutem/with-check check)
                   (salutem/with-result check result))]
    (is (= (salutem/resolve-check registry :thing)
          (salutem/result :unhealthy)))))

(deftest resolves-asynchronous-check-when-no-previous-result-available
  (let [check (salutem/check :thing
                (fn [_] (salutem/result :unhealthy))
                {:asynchronous true})
        registry (salutem/with-check (salutem/empty-registry) check)]
    (is (= (salutem/resolve-check registry :thing)
          (salutem/result :unhealthy)))))

(deftest resolves-asynchronous-check-as-previous-result-when-available
  (let [check (salutem/check :thing
                (fn [_] (salutem/result :unhealthy))
                {:asynchronous true})
        result (salutem/result :healthy)
        registry (-> (salutem/empty-registry)
                   (salutem/with-check check)
                   (salutem/with-result check result))]
    (is (= (salutem/resolve-check registry :thing)
          (salutem/result :healthy)))))

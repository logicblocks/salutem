(ns salutem.results-test
  (:require
   [clojure.test :refer :all]

   [tick.alpha.api :as t]

   [salutem.results :as results]))

(deftest creates-result-with-provided-status
  (let [result (results/result :healthy)]
    (is (= (:status result) :healthy))
    (is (results/healthy? result))
    (is (not (results/unhealthy? result))))
  (let [result (results/result :unhealthy)]
    (is (= (:status result) :unhealthy))
    (is (results/unhealthy? result))
    (is (not (results/healthy? result)))))

(deftest creates-result-with-now-as-evaluated-at-datetime-by-default
  (let [before (t/- (t/now) (t/new-duration 1 :seconds))
        result (results/result :healthy)
        after (t/+ (t/now) (t/new-duration 1 :seconds))]
    (is (t/> after (:evaluated-at result) before))))

(deftest creates-result-with-specified-evaluated-at-datetime-when-provided
  (let [evaluated-at (t/- (t/now) (t/new-period 1 :weeks))
        result (results/result :healthy {:evaluated-at evaluated-at})]
    (is (= (:evaluated-at result) evaluated-at))))

(deftest creates-result-with-retained-extra-data
  (let [result (results/result :healthy {:thing-1 "one" :thing-2 "two"})]
    (is (= (:thing-1 result) "one"))
    (is (= (:thing-2 result) "two"))))

(deftest creates-result-including-evaluated-at-when-extra-data-supplied
  (let [before (t/- (t/now) (t/new-duration 1 :seconds))
        result (results/result :healthy {:thing-1 "one" :thing-2 "two"})
        after (t/+ (t/now) (t/new-duration 1 :seconds))]
    (is (t/> after (:evaluated-at result) before))))

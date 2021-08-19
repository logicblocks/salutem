(ns salutem.core.results-test
  (:require
   [clojure.test :refer :all]

   [tick.alpha.api :as t]

   [salutem.core.time :as time]
   [salutem.core.results :as results]
   [salutem.core.checks :as checks]))

(deftest creates-result-with-provided-status
  (let [result (results/result :healthy)]
    (is (= (:status result) :healthy))
    (is (results/healthy? result))
    (is (not (results/unhealthy? result))))
  (let [result (results/result :unhealthy)]
    (is (= (:status result) :unhealthy))
    (is (results/unhealthy? result))
    (is (not (results/healthy? result)))))

(deftest creates-healthy-result
  (let [result (results/healthy)]
    (is (= (:status result) :healthy))
    (is (results/healthy? result))
    (is (not (results/unhealthy? result)))))

(deftest creates-unhealthy-result
  (let [result (results/unhealthy)]
    (is (= (:status result) :unhealthy))
    (is (results/unhealthy? result))
    (is (not (results/healthy? result)))))

(deftest creates-result-with-now-as-evaluated-at-datetime-by-default
  (let [before (t/- (t/now) (t/new-duration 1 :seconds))
        result (results/healthy)
        after (t/+ (t/now) (t/new-duration 1 :seconds))]
    (is (t/> after (:evaluated-at result) before))))

(deftest creates-result-with-specified-evaluated-at-datetime-when-provided
  (let [evaluated-at (t/- (t/now) (t/new-period 1 :weeks))
        result (results/healthy {:evaluated-at evaluated-at})]
    (is (= (:evaluated-at result) evaluated-at))))

(deftest creates-result-with-retained-extra-data
  (let [result (results/healthy {:thing-1 "one" :thing-2 "two"})]
    (is (= (:thing-1 result) "one"))
    (is (= (:thing-2 result) "two"))))

(deftest creates-result-including-evaluated-at-when-extra-data-supplied
  (let [before (t/- (t/now) (t/new-duration 1 :seconds))
        result (results/healthy {:thing-1 "one" :thing-2 "two"})
        after (t/+ (t/now) (t/new-duration 1 :seconds))]
    (is (t/> after (:evaluated-at result) before))))

(deftest is-always-outdated-if-check-is-realtime
  (let [check (checks/realtime-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy))))
        result (results/healthy
                 {:evaluated-at (t/- (t/now) (t/new-duration 60 :seconds))})]
    (is (true? (results/outdated? result check)))))

(deftest is-outdated-if-evaluated-at-older-than-now-minus-ttl
  (let [check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:ttl (time/duration 30 :seconds)})
        result (results/healthy
                 {:evaluated-at (t/- (t/now) (t/new-duration 60 :seconds))})]
    (is (true? (results/outdated? result check)))))

(deftest is-not-outdated-if-evaluated-at-newer-than-now-minus-ttl
  (let [check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:ttl (time/duration 60 :seconds)})
        result (results/healthy
                 {:evaluated-at (t/- (t/now) (t/new-duration 30 :seconds))})]
    (is (false? (results/outdated? result check)))))

(deftest is-outdated-relative-to-provided-instant-when-older-than-ttl
  (let [relative-to-instant (t/- (t/now) (t/new-duration 2 :minutes))
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:ttl (time/duration 30 :seconds)})
        result (results/healthy
                 {:evaluated-at (t/- (t/now) (t/new-duration 151 :seconds))})]
    (is (true? (results/outdated? result check relative-to-instant)))))

(deftest is-not-outdated-relative-to-provided-instant-when-newer-than-ttl
  (let [relative-to-instant (t/- (t/now) (t/new-duration 2 :minutes))
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:ttl (time/duration 30 :seconds)})
        result (results/healthy
                 {:evaluated-at (t/- (t/now) (t/new-duration 149 :seconds))})]
    (is (false? (results/outdated? result check relative-to-instant)))))

(deftest treats-a-nil-result-as-outdated
  (let [check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:ttl (time/duration 30 :seconds)})
        result nil]
    (is (true? (results/outdated? result check)))))

(ns salutem.core.results-test
  (:require
   [clojure.test :refer :all]

   [tick.core :as t]

   [salutem.core.time :as time]
   [salutem.core.results :as results]
   [salutem.core.checks :as checks]))

(deftest creates-result-with-provided-status
  (let [result (results/result :healthy)]
    (is (= (:salutem/status result) :healthy))
    (is (results/healthy? result))
    (is (not (results/unhealthy? result))))
  (let [result (results/result :unhealthy)]
    (is (= (:salutem/status result) :unhealthy))
    (is (results/unhealthy? result))
    (is (not (results/healthy? result)))))

(deftest creates-healthy-result
  (let [result (results/healthy)]
    (is (= (:salutem/status result) :healthy))
    (is (results/healthy? result))
    (is (not (results/unhealthy? result)))))

(deftest creates-unhealthy-result
  (let [result (results/unhealthy)]
    (is (= (:salutem/status result) :unhealthy))
    (is (results/unhealthy? result))
    (is (not (results/healthy? result)))))

(deftest creates-result-with-now-as-evaluated-at-datetime-by-default
  (let [before (t/<< (t/now) (t/new-duration 1 :seconds))
        result (results/healthy)
        after (t/>> (t/now) (t/new-duration 1 :seconds))]
    (is (t/> after (:salutem/evaluated-at result) before))))

(deftest creates-result-with-specified-evaluated-at-datetime-when-provided
  (let [evaluated-at (t/<< (t/now) (t/new-period 1 :weeks))
        result (results/healthy {:salutem/evaluated-at evaluated-at})]
    (is (= (:salutem/evaluated-at result) evaluated-at))))

(deftest creates-result-with-retained-extra-data
  (let [result (results/healthy {:thing-1 "one" :thing-2 "two"})]
    (is (= (:thing-1 result) "one"))
    (is (= (:thing-2 result) "two"))))

(deftest creates-result-including-evaluated-at-when-extra-data-supplied
  (let [before (t/<< (t/now) (t/new-duration 1 :seconds))
        result (results/healthy {:thing-1 "one" :thing-2 "two"})
        after (t/>> (t/now) (t/new-duration 1 :seconds))]
    (is (t/> after (:salutem/evaluated-at result) before))))

(deftest status-returns-result-status
  (let [healthy-result (results/healthy)
        unhealthy-result (results/unhealthy)
        pending-result (results/result :pending)]
    (is (= (results/status healthy-result) :healthy))
    (is (= (results/status unhealthy-result) :unhealthy))
    (is (= (results/status pending-result) :pending))))

(deftest is-always-outdated-if-check-is-realtime
  (let [check (checks/realtime-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy))))
        result (results/healthy
                 {:salutem/evaluated-at
                  (t/<< (t/now) (t/new-duration 60 :seconds))})]
    (is (true? (results/outdated? result check)))))

(deftest is-outdated-if-evaluated-at-older-than-now-minus-time-to-re-evaluation
  (let [check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:salutem/time-to-re-evaluation (time/duration 30 :seconds)})
        result (results/healthy
                 {:salutem/evaluated-at
                  (t/<< (t/now) (t/new-duration 60 :seconds))})]
    (is (true? (results/outdated? result check)))))

(deftest
  is-not-outdated-if-evaluated-at-newer-than-now-minus-time-to-re-evaluation
  (let [check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:salutem/time-to-re-evaluation (time/duration 60 :seconds)})
        result (results/healthy
                 {:salutem/evaluated-at
                  (t/<< (t/now) (t/new-duration 30 :seconds))})]
    (is (false? (results/outdated? result check)))))

(deftest
  is-outdated-relative-to-provided-instant-when-older-than-time-to-re-evaluation
  (let [relative-to-instant (t/<< (t/now) (t/new-duration 2 :minutes))
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:salutem/time-to-re-evaluation (time/duration 30 :seconds)})
        result (results/healthy
                 {:salutem/evaluated-at
                  (t/<< (t/now) (t/new-duration 151 :seconds))})]
    (is (true? (results/outdated? result check relative-to-instant)))))

(deftest
  is-not-outdated-relative-to-instant-when-newer-than-time-to-re-evaluation
  (let [relative-to-instant (t/<< (t/now) (t/new-duration 2 :minutes))
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:salutem/time-to-re-evaluation (time/duration 30 :seconds)})
        result (results/healthy
                 {:salutem/evaluated-at
                  (t/<< (t/now) (t/new-duration 149 :seconds))})]
    (is (false? (results/outdated? result check relative-to-instant)))))

(deftest treats-a-nil-result-as-outdated
  (let [check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:salutem/time-to-re-evaluation (time/duration 30 :seconds)})
        result nil]
    (is (true? (results/outdated? result check)))))

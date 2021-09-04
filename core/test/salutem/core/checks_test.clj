(ns salutem.core.checks-test
  (:require
   [clojure.test :refer :all]

   [salutem.core.time :as time]
   [salutem.core.checks :as checks]
   [salutem.core.results :as results]))

(deftest creates-background-check-with-provided-name-and-check-fn
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/background-check check-name check-fn)]
    (is (checks/background? check))
    (is (not (checks/realtime? check)))

    (is (= (:name check) check-name))
    (is (= (:check-fn check) check-fn))

    (is (= (:timeout check) (time/duration 10 :seconds)))
    (is (= (:time-to-re-evaluation check) (time/duration 10 :seconds)))))

(deftest creates-background-check-with-provided-timeout
  (let [check-timeout (time/duration 5 :seconds)
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:timeout check-timeout})]
    (is (= (:timeout check) check-timeout))))

(deftest creates-background-check-with-provided-time-to-re-evaluation
  (let [check-time-to-re-evaluation (time/duration 5 :seconds)
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:time-to-re-evaluation check-time-to-re-evaluation})]
    (is (= (:time-to-re-evaluation check) check-time-to-re-evaluation))))

(deftest creates-background-check-using-deprecated-ttl-as-time-to-re-evaluation
  (let [check-time-to-re-evaluation (time/duration 5 :seconds)
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:ttl check-time-to-re-evaluation})]
    (is (= (:time-to-re-evaluation check) check-time-to-re-evaluation))))

(deftest creates-realtime-check-with-provided-name-and-check-fn
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/realtime-check check-name check-fn)]
    (is (checks/realtime? check))
    (is (not (checks/background? check)))

    (is (= (:name check) check-name))
    (is (= (:check-fn check) check-fn))

    (is (= (:timeout check) (time/duration 10 :seconds)))))

(deftest creates-realtime-check-with-provided-timeout
  (let [check-timeout (time/duration 5 :seconds)
        check (checks/realtime-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:timeout check-timeout})]
    (is (= (:timeout check) check-timeout))))

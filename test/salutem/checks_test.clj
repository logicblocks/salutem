(ns salutem.checks-test
  (:require
   [clojure.test :refer :all]

   [tick.alpha.api :as t]

   [salutem.checks :as checks]
   [salutem.results :as results]))

(deftest creates-background-check-with-provided-name-and-check-fn
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/background-check check-name check-fn)]
    (is (checks/background? check))
    (is (not (checks/realtime? check)))

    (is (= (:name check) check-name))
    (is (= (:check-fn check) check-fn))

    (is (= (:timeout check) (t/new-duration 10 :seconds)))
    (is (= (:ttl check) (t/new-duration 10 :seconds)))))

(deftest creates-background-check-with-provided-timeout
  (let [check-timeout (t/new-duration 5 :seconds)
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:timeout check-timeout})]
    (is (= (:timeout check) check-timeout))))

(deftest creates-background-check-with-provided-ttl
  (let [check-ttl (t/new-duration 5 :seconds)
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:ttl check-ttl})]
    (is (= (:ttl check) check-ttl))))

(deftest creates-realtime-check-with-provided-name-and-check-fn
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/realtime-check check-name check-fn)]
    (is (checks/realtime? check))
    (is (not (checks/background? check)))

    (is (= (:name check) check-name))
    (is (= (:check-fn check) check-fn))

    (is (= (:timeout check) (t/new-duration 10 :seconds)))))

(deftest creates-realtime-check-with-provided-timeout
  (let [check-timeout (t/new-duration 5 :seconds)
        check (checks/realtime-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:timeout check-timeout})]
    (is (= (:timeout check) check-timeout))))

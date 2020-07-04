(ns salutem.checks-test
  (:require
   [clojure.test :refer :all]

   [salutem.checks :as checks]
   [salutem.results :as results]))

(deftest creates-cached-check-by-default
  (let [check (checks/check :thing
                (fn [_ result-cb]
                  (result-cb (results/result :healthy))))]
    (is (checks/cached? check))
    (is (not (checks/realtime? check)))))

(deftest creates-cached-check-when-requested
  (let [check (checks/check :thing
                (fn [_ result-cb]
                  (result-cb (results/result :healthy)))
                {:type :cached})]
    (is (checks/cached? check))
    (is (not (checks/realtime? check)))))

(deftest creates-realtime-check-when-requested
  (let [check (checks/check :thing
                (fn [_ result-cb]
                  (result-cb (results/result :healthy)))
                {:type :realtime})]
    (is (checks/realtime? check))
    (is (not (checks/cached? check)))))

(ns salutem.core.time-test
  (:require
   [clojure.test :refer :all]

   [tick.alpha.api :as t]

   [salutem.core.time :as st]))

(deftest constructs-duration-will-specified-values
  (let [cases [[100 :millis]
               [5 :seconds]
               [10 :minutes]]]
    (doseq [[numeral unit] cases]
      (is (= (st/duration numeral unit)
            (t/new-duration numeral unit))))))

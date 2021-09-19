(ns salutem.core.registry.performance-test
  (:require
   [clojure.test :refer :all]

   [tick.alpha.api :as t]

   [salutem.core :as salutem]))

(defn check-name [prefix id]
  (keyword (str (name prefix) "-" id)))

(defn log-time [stage start]
  (let [since (t/between start (t/now))]
    (println (name stage) since)))

(deftest registry-resolve-checks-evaluates-checks-in-parallel
  (let [epoch (t/now)

        slow-realtime-checks
        (map
          (fn [id]
            (salutem/realtime-check
              (check-name :timeout-background id)
              (fn [_ result-cb]
                (future
                  (Thread/sleep 200)
                  (result-cb (salutem/healthy {:id id}))))))
          (range 1 251))
        all-checks slow-realtime-checks

        registry (salutem/empty-registry)
        registry (reduce salutem/with-check registry all-checks)

        results (salutem/resolve-checks registry)]

    (is (= #{:healthy} (into #{} (map :salutem/status (vals results)))))

    ; 250 realtime checks as defined above running sequentially would take
    ; anywhere up to 50 seconds
    (is (t/< (t/between epoch (t/now))
          (t/new-duration 2 :seconds)))))

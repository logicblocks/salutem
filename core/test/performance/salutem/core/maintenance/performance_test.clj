(ns salutem.core.maintenance.performance-test
  (:require
   [clojure.test :refer :all]

   [tick.alpha.api :as t]

   [salutem.core :as salutem]))

(defn check-name [prefix id]
  (keyword (str (name prefix) "-" id)))

(defn log-time [stage start]
  (let [since (t/between start (t/now))]
    (println (name stage) since)))

(deftest maintenance-pipeline-can-maintain-hundreds-of-checks
  (let [epoch (t/now)

        timeout-background-checks
        (map
          #(salutem/background-check
             (check-name :timeout-background %)
             (fn [_ result-cb]
               (future
                 (Thread/sleep 200)
                 (result-cb (salutem/result :never-returned))))
             {:salutem/timeout (salutem/duration 100 :millis)})
          (range 1 251))
        exception-background-checks
        (map
          #(salutem/background-check
             (check-name :exception-background %)
             (fn [_ _]
               (throw (ex-info "Oops" {:id %}))))
          (range 251 501))
        healthy-background-checks
        (map
          #(salutem/background-check
             (check-name :healthy-background %)
             (fn [_ result-cb]
               (future
                 (Thread/sleep (rand-int 50))
                 (result-cb (salutem/healthy {:id %})))))
          (range 501 751))
        unhealthy-background-checks
        (map
          #(salutem/background-check
             (check-name :unhealthy-background %)
             (fn [_ result-cb]
               (future
                 (Thread/sleep (rand-int 50))
                 (result-cb (salutem/unhealthy {:id %})))))
          (range 751 1001))
        all-checks
        (concat
          timeout-background-checks
          exception-background-checks
          healthy-background-checks
          unhealthy-background-checks)
        check-count
        (count all-checks)

        registry (salutem/empty-registry)
        registry (reduce salutem/with-check registry all-checks)

        update-counter (atom 0)
        registry-store (atom registry)
        registry-store (add-watch registry-store :counter
                         (fn [_ _ _ _]
                           (swap! update-counter inc)))

        maintenance-pipeline (salutem/maintain registry-store)]
    (loop []
      (when (< @update-counter check-count)
        (recur)))

    (is (= {:timed-out       250
            :threw-exception 250
            :healthy         250
            :unhealthy       250}
          (reduce
            (fn [acc result]
              (let [category
                    (or
                      (:salutem/reason result)
                      (:salutem/status result))]
                (update-in acc [category] inc)))
            {:timed-out       0
             :threw-exception 0
             :healthy         0
             :unhealthy       0}
            (vals (salutem/resolve-checks @registry-store)))))

    ; 1000 checks as defined above would take anywhere up to 100 seconds to run
    ; if they weren't running concurrently or in parallel
    (is (t/< (t/between epoch (t/now))
          (t/new-duration 7.5 :seconds)))

    (salutem/shutdown maintenance-pipeline)))

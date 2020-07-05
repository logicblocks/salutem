(ns salutem.maintenance-test
  (:require
   [clojure.test :refer :all]
   [clojure.core.async :as async]

   [salutem.checks :as checks]
   [salutem.results :as results]
   [salutem.maintenance :as maintenance]
   [salutem.registry :as registry]
   [tick.core :as t]))

(defn <!!-or-timeout
  ([chan]
   (<!!-or-timeout chan 100))
  ([chan timeout-millis]
   (async/alt!!
     chan ([v] v)
     (async/timeout timeout-millis)
     (throw (ex-info "Timed out waiting on channel."
              {:channel chan
               :timeout timeout-millis})))))

(deftest evaluator-evaluates-single-check
  (let [context {:some "context"}

        check (checks/background-check :thing
                (fn [context result-cb]
                  (result-cb
                    (results/unhealthy
                      (merge context {:latency 1000})))))

        evaluation-channel (async/chan)
        result-channel (async/chan)]
    (maintenance/evaluator evaluation-channel result-channel)

    (async/put! evaluation-channel {:check check :context context})

    (let [{:keys [result]} (<!!-or-timeout result-channel)]
      (is (results/unhealthy? result))
      (is (= (:latency result) 1000))
      (is (= (:some result) "context")))

    (async/close! evaluation-channel)))

(deftest evaluator-evaluates-multiple-checks
  (let [context {:some "context"}

        check-1 (checks/background-check :thing-1
                  (fn [context result-cb]
                    (result-cb
                      (results/unhealthy
                        (merge context {:latency 1000})))))
        check-2 (checks/background-check :thing-2
                  (fn [context result-cb]
                    (result-cb
                      (results/healthy
                        (merge context {:latency 120})))))
        check-3 (checks/background-check :thing-3
                  (fn [context result-cb]
                    (result-cb
                      (results/unhealthy
                        (merge context {:latency 2000})))))

        evaluation-channel (async/chan)
        result-channel (async/chan)]
    (maintenance/evaluator evaluation-channel result-channel)

    (async/put! evaluation-channel {:check check-1 :context context})
    (async/put! evaluation-channel {:check check-2 :context context})
    (async/put! evaluation-channel {:check check-3 :context context})

    (letfn [(find-result [results name]
              (->> results
                (filter #(= (get-in % [:check :name]) name))
                first
                :result))]
      (let [results (async/<!! (async/into [] (async/take 3 result-channel)))
            check-1-result (find-result results :thing-1)
            check-2-result (find-result results :thing-2)
            check-3-result (find-result results :thing-3)]
        (is (results/unhealthy? check-1-result))
        (is (= (:latency check-1-result) 1000))
        (is (= (:some check-1-result) "context"))

        (is (results/healthy? check-2-result))
        (is (= (:latency check-2-result) 120))
        (is (= (:some check-2-result) "context"))

        (is (results/unhealthy? check-3-result))
        (is (= (:latency check-3-result) 2000))
        (is (= (:some check-3-result) "context"))))

    (async/close! evaluation-channel)))

(deftest evaluator-times-out-evaluation-when-check-takes-longer-than-timeout
  (let [check (checks/background-check :thing
                (fn [_ result-cb]
                  (Thread/sleep 100)
                  (result-cb
                    (results/healthy)))
                {:timeout (t/new-duration 50 :millis)})

        evaluation-channel (async/chan)
        result-channel (async/chan)]
    (maintenance/evaluator evaluation-channel result-channel)

    (async/put! evaluation-channel {:check check})

    (let [{:keys [result]} (<!!-or-timeout result-channel 200)]
      (is (results/unhealthy? result)))

    (async/close! evaluation-channel)))

(deftest evaluator-evaluates-to-returned-result-channel-when-none-provided
  (let [context {:some "context"}

        check (checks/background-check :thing
                (fn [context result-cb]
                  (result-cb
                    (results/unhealthy
                      (merge context {:latency 1000})))))

        evaluation-channel (async/chan)
        result-channel (maintenance/evaluator evaluation-channel)]

    (async/put! evaluation-channel {:check check :context context})

    (let [{:keys [result]} (<!!-or-timeout result-channel)]
      (is (results/unhealthy? result))
      (is (= (:latency result) 1000))
      (is (= (:some result) "context")))

    (async/close! evaluation-channel)))

(deftest evaluator-closes-result-channel-when-evaluation-channel-closed
  (let [evaluation-channel (async/chan)
        result-channel (async/chan)]
    (maintenance/evaluator evaluation-channel result-channel)

    (async/close! evaluation-channel)

    (is (nil? (<!!-or-timeout result-channel)))))

(deftest refresher-puts-single-outdated-check-to-evaluation-channel
  (let [context {:some "context"}

        check
        (checks/background-check :thing
          (fn [context result-cb]
            (result-cb
              (results/healthy
                (merge context {:latency 100}))))
          {:ttl (t/new-duration 30 :seconds)})
        outdated-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 35 :seconds))})

        registry
        (-> (registry/empty-registry)
          (registry/with-check check)
          (registry/with-cached-result check outdated-result))

        trigger-channel (async/chan)
        evaluation-channel (async/chan)]
    (maintenance/refresher trigger-channel evaluation-channel)

    (async/put! trigger-channel {:registry registry :context context})

    (let [evaluation (<!!-or-timeout evaluation-channel)]
      (is (= (:check evaluation) check))
      (is (= (:context evaluation) context)))

    (async/close! trigger-channel)))

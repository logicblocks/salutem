(ns salutem.maintenance-test
  (:require
   [clojure.test :refer :all]
   [clojure.core.async :as async]

   [salutem.checks :as checks]
   [salutem.results :as results]
   [salutem.maintenance :as maintenance]))

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

(deftest evaluates-single-check
  (let [check (checks/check :thing
                (fn [context result-cb]
                  (result-cb
                    (results/result :unhealthy
                      (merge context {:latency 1000})))))
        context {:some "context"}

        check-channel (async/chan)
        result-channel (async/chan)]
    (maintenance/evaluator check-channel result-channel)

    (async/put! check-channel {:check check :context context})

    (let [{:keys [result]} (<!!-or-timeout result-channel)]
      (is (results/unhealthy? result))
      (is (= 1000 (:latency result)))
      (is (= "context" (:some result))))

    (async/close! check-channel)))

(deftest evaluates-multiple-checks
  (let [check-1 (checks/check :thing-1
                  (fn [context result-cb]
                    (result-cb
                      (results/result :unhealthy
                        (merge context {:latency 1000})))))
        check-2 (checks/check :thing-2
                  (fn [context result-cb]
                    (result-cb
                      (results/result :healthy
                        (merge context {:latency 120})))))
        check-3 (checks/check :thing-3
                  (fn [context result-cb]
                    (result-cb
                      (results/result :unhealthy
                        (merge context {:latency 2000})))))
        context {:some "context"}

        check-channel (async/chan)
        result-channel (async/chan)]
    (maintenance/evaluator check-channel result-channel)

    (async/put! check-channel {:check check-1 :context context})
    (async/put! check-channel {:check check-2 :context context})
    (async/put! check-channel {:check check-3 :context context})

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
        (is (= 1000 (:latency check-1-result)))
        (is (= "context" (:some check-1-result)))

        (is (results/healthy? check-2-result))
        (is (= 120 (:latency check-2-result)))
        (is (= "context" (:some check-2-result)))

        (is (results/unhealthy? check-3-result))
        (is (= 2000 (:latency check-3-result)))
        (is (= "context" (:some check-3-result)))))

    (async/close! check-channel)))

(deftest times-out-evaluation-when-check-takes-longer-than-check-timeout
  (let [check (checks/check :thing
                (fn [_ result-cb]
                  (Thread/sleep 100)
                  (result-cb
                    (results/result :healthy)))
                {:timeout 50})

        check-channel (async/chan)
        result-channel (async/chan)]
    (maintenance/evaluator check-channel result-channel)

    (async/put! check-channel {:check check})

    (let [{:keys [result]} (<!!-or-timeout result-channel 200)]
      (is (results/unhealthy? result)))

    (async/close! check-channel)))

(deftest evaluates-check-to-returned-output-channel-when-none-provided
  (let [check (checks/check :thing
                (fn [context result-cb]
                  (result-cb
                    (results/result :unhealthy
                      (merge context {:latency 1000})))))
        context {:some "context"}

        in (async/chan)
        out (maintenance/evaluator in)]

    (async/put! in {:check check :context context})

    (let [{:keys [result]} (<!!-or-timeout out)]
      (is (results/unhealthy? result))
      (is (= 1000 (:latency result)))
      (is (= "context" (:some result))))

    (async/close! in)))

(deftest closes-evaluator-output-channel-when-input-channel-closed
  (let [in (async/chan)
        out (async/chan)]
    (maintenance/evaluator in out)

    (async/close! in)

    (is (nil? (<!!-or-timeout out)))))

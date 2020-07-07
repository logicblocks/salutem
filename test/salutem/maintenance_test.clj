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
   (<!!-or-timeout chan (t/new-duration 100 :millis)))
  ([chan timeout]
   (async/alt!!
     chan ([v] v)
     (async/timeout (t/millis timeout))
     (throw (ex-info "Timed out waiting on channel."
              {:channel chan
               :timeout (t/millis timeout)})))))

(deftest evaluator-evaluates-single-check
  (let [context {:some "context"}

        check (checks/background-check :thing
                (fn [context result-cb]
                  (result-cb
                    (results/unhealthy
                      (merge context
                        {:latency (t/new-duration 1 :seconds)})))))

        evaluation-channel (async/chan)
        result-channel (async/chan)]
    (maintenance/evaluator evaluation-channel result-channel)

    (async/put! evaluation-channel {:check check :context context})

    (let [{:keys [result]} (<!!-or-timeout result-channel)]
      (is (results/unhealthy? result))
      (is (= (:latency result) (t/new-duration 1 :seconds)))
      (is (= (:some result) "context")))

    (async/close! evaluation-channel)))

(deftest evaluator-evaluates-multiple-checks
  (let [context {:some "context"}

        check-1 (checks/background-check :thing-1
                  (fn [context result-cb]
                    (result-cb
                      (results/unhealthy
                        (merge context
                          {:latency (t/new-duration 1 :seconds)})))))
        check-2 (checks/background-check :thing-2
                  (fn [context result-cb]
                    (result-cb
                      (results/healthy
                        (merge context
                          {:latency (t/new-duration 120 :millis)})))))
        check-3 (checks/background-check :thing-3
                  (fn [context result-cb]
                    (result-cb
                      (results/unhealthy
                        (merge context
                          {:latency (t/new-duration 2 :seconds)})))))

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
        (is (= (:latency check-1-result) (t/new-duration 1 :seconds)))
        (is (= (:some check-1-result) "context"))

        (is (results/healthy? check-2-result))
        (is (= (:latency check-2-result) (t/new-duration 120 :millis)))
        (is (= (:some check-2-result) "context"))

        (is (results/unhealthy? check-3-result))
        (is (= (:latency check-3-result) (t/new-duration 2 :seconds)))
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

    (let [{:keys [result]} (<!!-or-timeout result-channel
                             (t/new-duration 200 :millis))]
      (is (results/unhealthy? result)))

    (async/close! evaluation-channel)))

(deftest evaluator-evaluates-to-returned-result-channel-when-none-provided
  (let [context {:some "context"}

        check (checks/background-check :thing
                (fn [context result-cb]
                  (result-cb
                    (results/unhealthy
                      (merge context
                        {:latency (t/new-duration 1 :seconds)})))))

        evaluation-channel (async/chan)
        result-channel (maintenance/evaluator evaluation-channel)]

    (async/put! evaluation-channel {:check check :context context})

    (let [{:keys [result]} (<!!-or-timeout result-channel)]
      (is (results/unhealthy? result))
      (is (= (:latency result) (t/new-duration 1 :seconds)))
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
          (fn [_ result-cb] (result-cb (results/healthy)))
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

(deftest refresher-puts-many-outdated-checks-to-evaluation-channel
  (let [context {:some "context"}

        check-1
        (checks/background-check :thing-1
          (fn [_ result-cb] (result-cb (results/healthy)))
          {:ttl (t/new-duration 30 :seconds)})
        check-2
        (checks/background-check :thing-2
          (fn [_ result-cb] (result-cb (results/healthy)))
          {:ttl (t/new-duration 1 :minutes)})
        check-3
        (checks/background-check :thing-2
          (fn [_ result-cb] (result-cb (results/healthy)))
          {:ttl (t/new-duration 45 :seconds)})

        check-1-outdated-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 35 :seconds))})
        check-2-current-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 40 :seconds))})
        check-3-outdated-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 55 :seconds))})

        registry
        (-> (registry/empty-registry)
          (registry/with-check check-1)
          (registry/with-check check-2)
          (registry/with-check check-3)
          (registry/with-cached-result check-1 check-1-outdated-result)
          (registry/with-cached-result check-2 check-2-current-result)
          (registry/with-cached-result check-3 check-3-outdated-result))

        trigger-channel (async/chan)
        evaluation-channel (async/chan)]
    (maintenance/refresher trigger-channel evaluation-channel)

    (async/put! trigger-channel {:registry registry :context context})
    (async/close! trigger-channel)

    (let [evaluations (<!!-or-timeout (async/into #{} evaluation-channel))]
      (is (= #{{:check check-1 :context context}
               {:check check-3 :context context}}
            evaluations)))))

(deftest refresher-puts-to-returned-evaluation-channel-when-none-provided
  (let [context {:some "context"}

        check
        (checks/background-check :thing
          (fn [_ result-cb] (result-cb (results/healthy)))
          {:ttl (t/new-duration 30 :seconds)})
        outdated-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 35 :seconds))})

        registry
        (-> (registry/empty-registry)
          (registry/with-check check)
          (registry/with-cached-result check outdated-result))

        trigger-channel (async/chan)
        evaluation-channel (maintenance/refresher trigger-channel)]
    (async/put! trigger-channel {:registry registry :context context})

    (let [evaluation (<!!-or-timeout evaluation-channel)]
      (is (= (:check evaluation) check))
      (is (= (:context evaluation) context)))

    (async/close! trigger-channel)))

(deftest refresher-closes-evaluation-channel-when-trigger-channel-closed
  (let [trigger-channel (async/chan)
        evaluation-channel (async/chan)]
    (maintenance/evaluator trigger-channel evaluation-channel)

    (async/close! trigger-channel)

    (is (nil? (<!!-or-timeout evaluation-channel)))))

(deftest updater-adds-single-result-to-registry-in-registry-store-atom
  (let [check (checks/background-check :thing
                (fn [_ result-cb] (result-cb (results/healthy))))
        result (results/healthy
                 {:latency (t/new-duration 267 :millis)})

        registry (-> (registry/empty-registry)
                   (registry/with-check check))

        registry-store (atom registry)
        updated? (atom false)

        result-channel (async/chan)]
    (maintenance/updater registry-store result-channel)

    (add-watch registry-store :watcher
      (fn [_ _ _ _]
        (reset! updated? true)))

    (async/put! result-channel {:check check :result result})

    (loop [attempts 1]
      (if @updated?
        (is (= @registry-store
              (registry/with-cached-result registry check result)))
        (do
          (Thread/sleep 25)
          (and (< attempts 5) (recur (inc attempts))))))

    (remove-watch registry-store :watcher)
    (async/close! result-channel)))

(deftest updater-adds-manny-results-to-registry-in-registry-store-atom
  (let [check-1 (checks/background-check :thing-1
                  (fn [_ result-cb] (result-cb (results/healthy))))
        check-2 (checks/background-check :thing-2
                  (fn [_ result-cb] (result-cb (results/healthy))))
        check-3 (checks/background-check :thing-3
                  (fn [_ result-cb] (result-cb (results/healthy))))

        result-1 (results/healthy
                   {:latency (t/new-duration 267 :millis)})
        result-2 (results/unhealthy
                   {:version "1.2.3"})
        result-3 (results/unhealthy
                   {:items 1432})

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2)
                   (registry/with-check check-3))

        registry-store (atom registry)
        updated-count (atom 0)

        result-channel (async/chan 10)]
    (maintenance/updater registry-store result-channel)

    (add-watch registry-store :watcher
      (fn [_ _ _ _]
        (swap! updated-count inc)))

    (async/put! result-channel {:check check-1 :result result-1})
    (async/put! result-channel {:check check-2 :result result-2})
    (async/put! result-channel {:check check-3 :result result-3})

    (loop [attempts 1]
      (if (= @updated-count 3)
        (is (= @registry-store
              (-> registry
                (registry/with-cached-result check-1 result-1)
                (registry/with-cached-result check-2 result-2)
                (registry/with-cached-result check-3 result-3))))
        (do
          (Thread/sleep 25)
          (and (< attempts 5) (recur (inc attempts))))))

    (remove-watch registry-store :watcher)
    (async/close! result-channel)))

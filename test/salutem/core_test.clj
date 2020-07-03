(ns salutem.core-test
  (:require
   [clojure.test :refer :all]

   [spy.core :as spy]
   [tick.alpha.api :as t]

   [salutem.core :as salutem]
   [clojure.core.async :as async]))

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

(deftest creates-cached-check-by-default
  (let [check (salutem/check :thing
                (fn [_ result-cb]
                  (result-cb (salutem/result :healthy))))]
    (is (salutem/cached? check))
    (is (not (salutem/realtime? check)))))

(deftest creates-cached-check-when-requested
  (let [check (salutem/check :thing
                (fn [_ result-cb]
                  (result-cb (salutem/result :healthy)))
                {:type :cached})]
    (is (salutem/cached? check))
    (is (not (salutem/realtime? check)))))

(deftest creates-realtime-check-when-requested
  (let [check (salutem/check :thing
                (fn [_ result-cb]
                  (result-cb (salutem/result :healthy)))
                {:type :realtime})]
    (is (salutem/realtime? check))
    (is (not (salutem/cached? check)))))

(deftest creates-result-with-provided-status
  (let [result (salutem/result :healthy)]
    (is (= (:status result) :healthy))
    (is (salutem/healthy? result))
    (is (not (salutem/unhealthy? result))))
  (let [result (salutem/result :unhealthy)]
    (is (= (:status result) :unhealthy))
    (is (salutem/unhealthy? result))
    (is (not (salutem/healthy? result)))))

(deftest creates-result-with-now-as-evaluated-at-datetime-by-default
  (let [before (t/- (t/now) (t/new-duration 1 :seconds))
        result (salutem/result :healthy)
        after (t/+ (t/now) (t/new-duration 1 :seconds))]
    (is (t/> after (:evaluated-at result) before))))

(deftest creates-result-with-specified-evaluated-at-datetime-when-provided
  (let [evaluated-at (t/- (t/now) (t/new-period 1 :weeks))
        result (salutem/result :healthy {:evaluated-at evaluated-at})]
    (is (= (:evaluated-at result) evaluated-at))))

(deftest creates-result-with-retained-extra-data
  (let [result (salutem/result :healthy {:thing-1 "one" :thing-2 "two"})]
    (is (= (:thing-1 result) "one"))
    (is (= (:thing-2 result) "two"))))

(deftest creates-result-including-evaluated-at-when-extra-data-supplied
  (let [before (t/- (t/now) (t/new-duration 1 :seconds))
        result (salutem/result :healthy {:thing-1 "one" :thing-2 "two"})
        after (t/+ (t/now) (t/new-duration 1 :seconds))]
    (is (t/> after (:evaluated-at result) before))))

(deftest adds-single-check-to-registry
  (let [check (salutem/check :thing
                (fn [_ result-cb]
                  (result-cb (salutem/result :healthy))))
        registry (salutem/with-check (salutem/empty-registry) check)]
    (is (= (salutem/find-check registry :thing) check))))

(deftest adds-many-checks-to-registry
  (let [check-1 (salutem/check :thing-1
                  (fn [_ result-cb]
                    (result-cb (salutem/result :healthy))))
        check-2 (salutem/check :thing-2
                  (fn [_ result-callback]
                    (result-callback (salutem/result :unhealthy))))

        registry (-> (salutem/empty-registry)
                   (salutem/with-check check-1)
                   (salutem/with-check check-2))]
    (is (= (salutem/check-names registry) #{:thing-1 :thing-2}))))

(deftest resolves-realtime-check-every-time
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (salutem/result :healthy))))
        check (salutem/check :thing
                check-fn
                {:type :realtime})
        registry (salutem/with-check (salutem/empty-registry) check)

        resolved-result-1 (salutem/resolve-check registry :thing)
        _ (Thread/sleep 10)
        resolved-result-2 (salutem/resolve-check registry :thing)]
    (is (= (count (spy/calls check-fn
                    )) 2))
    (is (salutem/healthy? resolved-result-1))
    (is (salutem/healthy? resolved-result-2))
    (is (t/>
          (:evaluated-at resolved-result-2)
          (:evaluated-at resolved-result-1)))))

(deftest resolves-cached-check-when-no-cached-result-available
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (salutem/result :unhealthy))))
        check (salutem/check :thing
                check-fn
                {:type :cached})
        registry (salutem/with-check (salutem/empty-registry) check)
        resolved-result (salutem/resolve-check registry :thing)]
    (is (= (count (spy/calls check-fn)) 1))
    (is (salutem/unhealthy? resolved-result))))

(deftest resolves-cached-check-as-previous-result-when-available
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (salutem/result :unhealthy))))
        check (salutem/check :thing
                check-fn
                {:type :cached})
        cached-result (salutem/result :healthy)
        registry (-> (salutem/empty-registry)
                   (salutem/with-check check)
                   (salutem/with-cached-result check cached-result))
        resolved-result (salutem/resolve-check registry :thing)]
    (is (= (count (spy/calls check-fn)) 0))
    (is (salutem/healthy? resolved-result))))

(deftest evaluates-single-check-passed-on-input-channel-putting-result-on-provided-output-channel
  (let [check (salutem/check :thing
                (fn [context result-cb]
                  (result-cb
                    (salutem/result :unhealthy
                      (merge context {:latency 1000})))))
        context {:some "context"}

        check-channel (async/chan)
        result-channel (async/chan)]
    (salutem/evaluator check-channel result-channel)

    (async/put! check-channel {:check check :context context})

    (let [{:keys [result]} (<!!-or-timeout result-channel)]
      (is (salutem/unhealthy? result))
      (is (= 1000 (:latency result)))
      (is (= "context" (:some result))))

    (async/close! check-channel)))

(deftest evaluates-multiple-checks-passed-on-input-channel-putting-results-on-provided-output-channel
  (let [check-1 (salutem/check :thing-1
                  (fn [context result-cb]
                    (result-cb
                      (salutem/result :unhealthy
                        (merge context {:latency 1000})))))
        check-2 (salutem/check :thing-2
                  (fn [context result-cb]
                    (result-cb
                      (salutem/result :healthy
                        (merge context {:latency 120})))))
        context {:some "context"}

        check-channel (async/chan)
        result-channel (async/chan)]
    (salutem/evaluator check-channel result-channel)

    (async/put! check-channel {:check check-1 :context context})
    (async/put! check-channel {:check check-2 :context context})

    (letfn [(find-result [results name]
              (->> results
                (filter #(= (get-in % [:check :name]) name))
                first
                :result))]
      (let [results (async/<!! (async/into [] (async/take 2 result-channel)))
            check-1-result (find-result results :thing-1)
            check-2-result (find-result results :thing-2)]
        (is (salutem/unhealthy? check-1-result))
        (is (= 1000 (:latency check-1-result)))
        (is (= "context" (:some check-1-result)))

        (is (salutem/healthy? check-2-result))
        (is (= 120 (:latency check-2-result)))
        (is (= "context" (:some check-2-result)))))

    (async/close! check-channel)))

(deftest times-out-evaluation-when-check-takes-longer-than-check-timeout
  (let [check (salutem/check :thing
                (fn [_ result-cb]
                  (Thread/sleep 100)
                  (result-cb
                    (salutem/result :healthy)))
                {:timeout 50})

        check-channel (async/chan)
        result-channel (async/chan)]
    (salutem/evaluator check-channel result-channel)

    (async/put! check-channel {:check check})

    (let [{:keys [result]} (<!!-or-timeout result-channel 200)]
      (is (salutem/unhealthy? result)))

    (async/close! check-channel)))

(deftest evaluates-check-to-returned-output-channel-when-none-provided
  (let [check (salutem/check :thing
                (fn [context result-cb]
                  (result-cb
                    (salutem/result :unhealthy
                      (merge context {:latency 1000})))))
        context {:some "context"}

        in (async/chan)
        out (salutem/evaluator in)]

    (async/put! in {:check check :context context})

    (let [{:keys [result]} (<!!-or-timeout out)]
      (is (salutem/unhealthy? result))
      (is (= 1000 (:latency result)))
      (is (= "context" (:some result))))

    (async/close! in)))

(deftest closes-evaluator-output-channel-when-input-channel-closed
  (let [in (async/chan)
        out (async/chan)]
    (salutem/evaluator in out)

    (async/close! in)

    (is (nil? (<!!-or-timeout out)))))

(ns salutem.core.checks-test
  (:require
   [clojure.test :refer :all]
   [clojure.core.async :as async]

   [tick.alpha.api :as t]

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

(deftest evaluates-check-returning-result
  (let [latency (t/new-duration 356 :millis)
        evaluated-at (t/now)
        check (checks/realtime-check :thing
                (fn [_ result-cb]
                  (result-cb
                    (results/healthy
                      {:latency      latency
                       :evaluated-at evaluated-at}))))]
    (is (= (checks/evaluate check)
          (results/healthy
            {:latency      latency
             :evaluated-at evaluated-at})))))

(deftest passes-context-map-to-check-function-during-evaluation
  (let [latency (t/new-duration 356 :millis)
        evaluated-at (t/now)
        check (checks/realtime-check :thing
                (fn [context result-cb]
                  (result-cb
                    (results/healthy
                      {:caller       (:caller context)
                       :latency      latency
                       :evaluated-at evaluated-at}))))]
    (is (= (checks/evaluate check {:caller :thing-consumer})
          (results/healthy
            {:caller       :thing-consumer
             :latency      latency
             :evaluated-at evaluated-at})))))

(deftest evaluates-asynchronously-when-passed-callback
  (let [context {:caller :thing-consumer}

        latency (t/new-duration 356 :millis)
        evaluated-at (t/now)

        check (checks/realtime-check :thing
                (fn [context result-cb]
                  (result-cb
                    (results/healthy
                      {:caller       (:caller context)
                       :latency      latency
                       :evaluated-at evaluated-at}))))

        result-atom (atom nil)
        callback-fn (fn [result]
                      (reset! result-atom result))]
    (checks/evaluate check context callback-fn)

    (loop [attempts 1]
      (if (not (nil? @result-atom))
        (is (= @result-atom
              (results/healthy
                {:caller       :thing-consumer
                 :latency      latency
                 :evaluated-at evaluated-at})))
        (if (< attempts 5)
          (do
            (async/<!! (async/timeout 25))
            (recur (inc attempts)))
          (throw (ex-info "Callback function was not called before timeout."
                   {:check check})))))))

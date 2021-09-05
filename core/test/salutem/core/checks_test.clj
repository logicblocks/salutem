(ns salutem.core.checks-test
  (:require
   [clojure.test :refer :all]
   [clojure.core.async :as async]

   [tick.alpha.api :as t]

   [cartus.test :as cartus-test]

   [salutem.core.time :as time]
   [salutem.core.checks :as checks]
   [salutem.core.results :as results]

   [salutem.test.support.data :as data]
   [salutem.test.support.async :as tsa]
   [salutem.test.support.time :as tst]))

(deftest background-check-creates-background-check
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/background-check check-name check-fn)]
    (is (checks/background? check))
    (is (not (checks/realtime? check)))))

(deftest background-check-uses-provided-name-and-check-fn
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/background-check check-name check-fn)]
    (is (= (:name check) check-name))
    (is (= (:check-fn check) check-fn))))

(deftest background-check-uses-10-second-timeout-by-default
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/background-check check-name check-fn)]
    (is (= (:timeout check) (time/duration 10 :seconds)))))

(deftest background-check-uses-10-second-time-to-re-evaluation-by-default
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/background-check check-name check-fn)]
    (is (= (:time-to-re-evaluation check) (time/duration 10 :seconds)))))

(deftest background-check-uses-supplied-timeout-when-provided
  (let [check-timeout (time/duration 5 :seconds)
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:timeout check-timeout})]
    (is (= (:timeout check) check-timeout))))

(deftest background-check-uses-supplied-time-to-re-evaluation-when-provided
  (let [check-time-to-re-evaluation (time/duration 5 :seconds)
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:time-to-re-evaluation check-time-to-re-evaluation})]
    (is (= (:time-to-re-evaluation check) check-time-to-re-evaluation))))

(deftest background-check-uses-deprecated-ttl-as-time-to-re-evaluation
  (let [check-time-to-re-evaluation (time/duration 5 :seconds)
        check (checks/background-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:ttl check-time-to-re-evaluation})]
    (is (= (:time-to-re-evaluation check) check-time-to-re-evaluation))))

(deftest realtime-check-creates-realtime-check
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/realtime-check check-name check-fn)]
    (is (checks/realtime? check))
    (is (not (checks/background? check)))))

(deftest realtime-check-uses-provided-name-and-check-fn
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/realtime-check check-name check-fn)]
    (is (= (:name check) check-name))
    (is (= (:check-fn check) check-fn))))

(deftest realtime-check-uses-10-second-timeout-by-default
  (let [check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb (results/healthy)))
        check (checks/realtime-check check-name check-fn)]
    (is (= (:timeout check) (time/duration 10 :seconds)))))

(deftest realtime-check-uses-supplied-timeout-when-provided
  (let [check-timeout (time/duration 5 :seconds)
        check (checks/realtime-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy)))
                {:timeout check-timeout})]
    (is (= (:timeout check) check-timeout))))

(deftest attempt-executes-check-function-putting-result-message-to-channel
  (let [dependencies {}
        trigger-id :test
        context {}
        result-channel (async/chan 1)

        correlation-id (data/random-uuid)

        check (checks/realtime-check :thing
                (fn [_ result-cb]
                  (result-cb
                    (results/healthy
                      {:correlation-id correlation-id}))))]
    (checks/attempt dependencies trigger-id check context result-channel)

    (let [result-message (tsa/<!!-or-timeout result-channel)]
      (is (= {:trigger-id trigger-id
              :check      check
              :result     (tst/without-evaluation-date-time
                            (results/healthy
                              {:correlation-id correlation-id}))}
            (update-in result-message [:result]
              tst/without-evaluation-date-time))))))

(deftest attempt-passes-provided-context-to-check-fn
  (let [correlation-id (data/random-uuid)

        dependencies {}
        trigger-id :test
        context {:correlation-id correlation-id}
        result-channel (async/chan 1)

        check (checks/realtime-check :thing
                (fn [context result-cb]
                  (result-cb
                    (results/healthy
                      {:correlation-id (:correlation-id context)}))))]
    (checks/attempt dependencies trigger-id check context result-channel)

    (let [result-message (tsa/<!!-or-timeout result-channel)]
      (is (= {:trigger-id trigger-id
              :check      check
              :result     (tst/without-evaluation-date-time
                            (results/healthy
                              {:correlation-id correlation-id}))}
            (update-in result-message [:result]
              tst/without-evaluation-date-time))))))

(deftest attempt-puts-unhealthy-result-with-reason-when-check-fn-times-out
  (let [dependencies {}
        trigger-id :test
        context {}
        result-channel (async/chan 1)

        check-timeout (time/duration 100 :millis)
        check (checks/realtime-check :thing
                (fn [_ result-cb]
                  (future
                    (Thread/sleep 200)
                    (result-cb
                      (results/healthy
                        {:correlation-id (data/random-uuid)}))))
                {:timeout check-timeout})]
    (checks/attempt dependencies trigger-id check context result-channel)

    (let [result-message (tsa/<!!-or-timeout result-channel
                           (t/new-duration 300 :millis))]
      (is (= {:trigger-id trigger-id
              :check      check
              :result     (tst/without-evaluation-date-time
                            (results/unhealthy
                              {:salutem/reason :timeout}))}
            (update-in result-message [:result]
              tst/without-evaluation-date-time))))))

(deftest attempt-logs-event-when-starting-to-logger-supplied-in-dependencies
  (let [logger (cartus-test/logger)

        dependencies {:logger logger}
        trigger-id :test
        context {}
        result-channel (async/chan 1)

        check-name :thing
        check (checks/realtime-check check-name
                (fn [_ result-cb] (result-cb (results/healthy))))]

    (checks/attempt dependencies trigger-id check context result-channel)

    (tsa/<!!-or-timeout result-channel)

    (is (logged? logger
          {:context {:trigger-id trigger-id
                     :check-name check-name}
           :level   :info
           :type    :salutem.core.checks/attempt.starting}))))

(deftest attempt-logs-event-when-completed-to-logger-supplied-in-context
  (let [logger (cartus-test/logger)

        dependencies {:logger logger}
        trigger-id :test
        context {}
        result-channel (async/chan 1)

        correlation-id (data/random-uuid)
        result (results/healthy {:correlation-id correlation-id})

        check-name :thing
        check (checks/realtime-check check-name
                (fn [_ result-cb] (result-cb result)))]
    (checks/attempt dependencies trigger-id check context result-channel)

    (tsa/<!!-or-timeout result-channel)

    (is (logged? logger
          {:context {:trigger-id trigger-id
                     :check-name check-name
                     :result     result}
           :level   :info
           :type    :salutem.core.checks/attempt.completed}))))

(deftest attempt-logs-event-when-timeout-occurs-to-logger-supplied-in-context
  (let [logger (cartus-test/logger)

        dependencies {:logger logger}
        trigger-id :test
        context {}
        result-channel (async/chan 1)

        check-name :thing
        check-timeout (time/duration 100 :millis)
        check (checks/realtime-check check-name
                (fn [_ result-cb]
                  (future
                    (Thread/sleep 200)
                    (result-cb
                      (results/healthy
                        {:correlation-id (data/random-uuid)}))))
                {:timeout check-timeout})]
    (checks/attempt dependencies trigger-id check context result-channel)

    (tsa/<!!-or-timeout result-channel
      (t/new-duration 300 :millis))

    (is (logged? logger
          {:context {:trigger-id trigger-id
                     :check-name check-name}
           :level   :info
           :type    :salutem.core.checks/attempt.timed-out}))))

(deftest evaluate-evaluates-check-returning-result
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

(deftest evaluate-passes-context-map-to-check-function-during-evaluation
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

(deftest evaluate-evaluates-asynchronously-when-passed-callback
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

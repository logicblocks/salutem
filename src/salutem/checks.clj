(ns salutem.checks
  (:require
   [clojure.core.async :as async]

   [tick.alpha.api :as t]

   [cartus.core :as log]
   [cartus.null :as cartus-null]

   [salutem.results :as results]))

(defn- check
  ([check-name check-fn] (check check-name check-fn {}))
  ([check-name check-fn
    {:keys [timeout]
     :or   {timeout (t/new-duration 10 :seconds)}
     :as   opts}]
   (merge
     opts
     {:name     check-name
      :check-fn check-fn
      :timeout  timeout})))

(defn background-check
  ([check-name check-fn] (background-check check-name check-fn {}))
  ([check-name check-fn opts]
   (check check-name check-fn
     (merge
       {:ttl (t/new-duration 10 :seconds)}
       opts
       {:type :background}))))

(defn realtime-check
  ([check-name check-fn] (realtime-check check-name check-fn {}))
  ([check-name check-fn opts]
   (check check-name check-fn
     (merge
       opts
       {:type :realtime}))))

(defn background? [check]
  (= (:type check) :background))

(defn realtime? [check]
  (= (:type check) :realtime))

(defn attempt
  ([check context]
   (let [logger (or (:logger context) (cartus-null/logger))
         dependencies {:logger logger}]
     (attempt dependencies nil check context (async/chan 1))))
  ([dependencies trigger-id check context result-channel]
   (let [logger (:logger dependencies)
         check-name (:name check)]
     (async/go
       (let [{:keys [check-fn timeout]} check
             callback-channel (async/chan)]
         (log/info logger ::attempt.starting
           {:trigger-id trigger-id
            :check-name check-name})
         (check-fn context
           (fn [result]
             (async/put! callback-channel result)))
         (async/alt!
           callback-channel
           ([result]
            (do
              (log/info logger ::attempt.completed
                {:trigger-id trigger-id
                 :check-name check-name
                 :result     result})
              (async/>! result-channel
                {:trigger-id trigger-id
                 :check      check
                 :result     result})))

           (async/timeout (t/millis timeout))
           (do
             (log/info logger ::attempt.timed-out
               {:trigger-id trigger-id
                :check-name check-name})
             (async/>! result-channel
               {:trigger-id trigger-id
                :check check
                :result (results/unhealthy)}))

           :priority true)
         (async/close! callback-channel))))
   result-channel))

(defn evaluate
  ([check] (evaluate check {}))
  ([check context]
   (:result (async/<!! (attempt check context)))))

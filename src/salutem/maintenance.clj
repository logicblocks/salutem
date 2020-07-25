(ns salutem.maintenance
  (:require
   [clojure.core.async :as async]

   [cartus.core :as log]
   [cartus.null :as null]

   [tick.alpha.api :as t]

   [salutem.checks :as checks]
   [salutem.registry :as registry]))

(defn maintainer [dependencies registry-store context interval trigger-channel]
  (let [logger (:logger dependencies)
        interval-millis (t/millis interval)
        trigger-counter (atom 0)
        shutdown-channel (async/chan)]
    (log/info logger ::maintainer.starting {:interval interval})
    (async/go
      (loop []
        (async/alt!
          (async/timeout interval-millis)
          (let [trigger-id (swap! trigger-counter inc)
                registry (deref registry-store)]
            (log/info logger ::maintainer.triggering
              {:trigger-id trigger-id})
            (async/>! trigger-channel
              {:trigger-id trigger-id
               :registry   registry
               :context    context})
            (recur))

          shutdown-channel
          (let [triggers-sent (deref trigger-counter)]
            (async/close! trigger-channel)
            (log/info logger ::maintainer.stopped
              {:triggers-sent triggers-sent})))))
    shutdown-channel))

(defn refresher
  ([dependencies trigger-channel]
   (refresher dependencies trigger-channel (async/chan 1)))
  ([dependencies trigger-channel evaluation-channel]
   (let [logger (:logger dependencies)]
     (log/info logger ::refresher.starting)
     (async/go
       (loop []
         (let [{:keys [registry context trigger-id]
                :or   {context {}}
                :as   trigger-message} (async/<! trigger-channel)]
           (if trigger-message
             (do
               (log/info logger ::refresher.triggered
                 {:trigger-id trigger-id})
               (doseq [check (registry/outdated-checks registry)]
                 (log/info logger ::refresher.evaluating
                   {:trigger-id trigger-id
                    :check-name (:name check)})
                 (async/>! evaluation-channel
                   {:trigger-id trigger-id
                    :check      check
                    :context    context}))
               (recur))
             (do
               (async/close! evaluation-channel)
               (log/info logger ::refresher.stopped)))))))
   evaluation-channel))

(defn evaluator
  ([dependencies evaluation-channel]
   (evaluator dependencies evaluation-channel (async/chan 1)))
  ([dependencies evaluation-channel result-channel]
   (let [logger (:logger dependencies)]
     (log/info logger ::evaluator.starting)
     (async/go
       (loop []
         (let [{:keys [check context trigger-id]
                :or   {context {}}
                :as   evaluation-message} (async/<! evaluation-channel)]
           (if evaluation-message
             (do
               (log/info logger ::evaluator.evaluating
                 {:trigger-id trigger-id
                  :check-name (:name check)})
               (checks/attempt check context result-channel)
               (recur))
             (do
               (async/close! result-channel)
               (log/info logger ::evaluator.stopped))))))
     result-channel)))

(defn updater
  [dependencies registry-store result-channel]
  (async/go
    (loop []
      (let [{:keys [check result]
             :as   result-message} (async/<! result-channel)]
        (when result-message
          (swap! registry-store
            registry/with-cached-result check result)
          (recur))))))

(defn maintain
  [registry-store
   {:keys [context
           interval
           trigger-channel
           evaluation-channel
           result-channel]
    :or   {context            {}
           interval           (t/new-duration 200 :millis)
           trigger-channel    (async/chan (async/sliding-buffer 1))
           evaluation-channel (async/chan 10)
           result-channel     (async/chan 10)}}]
  (let [logger (get context :logger (null/logger))
        dependencies {:logger logger}]
    (updater dependencies registry-store result-channel)
    (evaluator dependencies evaluation-channel result-channel)
    (refresher dependencies trigger-channel evaluation-channel)
    (maintainer dependencies registry-store context interval trigger-channel)))

(defn shutdown [shutdown-channel]
  (async/close! shutdown-channel))

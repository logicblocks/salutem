(ns salutem.core.maintenance
  (:require
   [clojure.core.async :as async]

   [cartus.core :as log]
   [cartus.null :as null]

   [tick.alpha.api :as t]

   [salutem.core.checks :as checks]
   [salutem.core.registry :as registry]))

(defn maintainer
  ([dependencies registry-store context interval trigger-channel]
   (maintainer
     dependencies registry-store context interval
     trigger-channel (async/chan)))
  ([dependencies registry-store context interval
    trigger-channel shutdown-channel]
   (let [logger (:logger dependencies)
         interval-millis (t/millis interval)
         trigger-counter (atom 0)]
     (log/info logger ::maintainer.starting {:interval interval})
     (async/go-loop []
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
             {:triggers-sent triggers-sent}))))
     shutdown-channel)))

(defn refresher
  ([dependencies trigger-channel]
   (refresher dependencies trigger-channel (async/chan 1)))
  ([dependencies trigger-channel evaluation-channel]
   (let [logger (:logger dependencies)]
     (log/info logger ::refresher.starting)
     (async/go-loop []
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
             (log/info logger ::refresher.stopped))))))
   evaluation-channel))

(defn evaluator
  ([dependencies evaluation-channel]
   (evaluator dependencies evaluation-channel (async/chan 1)))
  ([dependencies evaluation-channel result-channel]
   (let [logger (:logger dependencies)]
     (log/info logger ::evaluator.starting)
     (async/go-loop []
       (let [{:keys [check context trigger-id]
              :or   {context {}}
              :as   evaluation-message} (async/<! evaluation-channel)]
         (if evaluation-message
           (do
             (log/info logger ::evaluator.evaluating
               {:trigger-id trigger-id
                :check-name (:name check)})
             (checks/attempt
               dependencies trigger-id check context result-channel)
             (recur))
           (do
             (async/close! result-channel)
             (log/info logger ::evaluator.stopped)))))
     result-channel)))

(defn updater
  [dependencies registry-store result-channel]
  (let [logger (:logger dependencies)]
    (log/info logger ::updater.starting)
    (async/go-loop []
      (let [{:keys [check result trigger-id]
             :as   result-message} (async/<! result-channel)]
        (if result-message
          (do
            (log/info logger ::updater.updating
              {:trigger-id trigger-id
               :check-name (:name check)
               :result     result})
            (swap! registry-store
              registry/with-cached-result check result)
            (recur))
          (log/info logger ::updater.stopped))))))

(defn notifier
  [dependencies callbacks result-channel]
  (let [logger (:logger dependencies)]
    (log/info logger ::notifier.starting
      {:callbacks (count callbacks)})
    (async/go-loop []
      (let [{:keys [check result trigger-id]
             :as   result-message} (async/<! result-channel)]
        (if result-message
          (do
            (doseq [[index callback] (map-indexed vector callbacks)]
              (log/info logger ::notifier.notifying
                {:trigger-id trigger-id
                 :check-name (:name check)
                 :result     result
                 :callback   (inc index)})
              (callback check result))
            (recur))
          (log/info logger ::notifier.stopped))))))

(defn maintain
  ([registry-store]
   (maintain registry-store {}))
  ([registry-store
    {:keys [context
            interval
            trigger-channel
            evaluation-channel
            result-channel
            updater-result-channel
            notifier-result-channel
            callback-fns]
     :or   {context                 {}
            interval                (t/new-duration 200 :millis)
            trigger-channel         (async/chan (async/sliding-buffer 1))
            evaluation-channel      (async/chan 10)
            result-channel          (async/chan 10)
            updater-result-channel  (async/chan 10)
            notifier-result-channel (async/chan 10)
            callback-fns            []}}]
   (let [logger (get context :logger (null/logger))
         dependencies {:logger logger}
         result-mult (async/mult result-channel)
         shutdown-channel (async/chan)]
     (async/go
       (updater dependencies registry-store
         (async/tap result-mult updater-result-channel))
       (notifier dependencies callback-fns
         (async/tap result-mult notifier-result-channel))
       (evaluator dependencies evaluation-channel result-channel)
       (refresher dependencies trigger-channel evaluation-channel)
       (maintainer dependencies registry-store context interval
         trigger-channel shutdown-channel))
     shutdown-channel)))

(defn shutdown [shutdown-channel]
  (async/close! shutdown-channel))

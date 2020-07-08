(ns salutem.maintenance
  (:require
   [clojure.core.async :as async]

   [tick.alpha.api :as t]

   [salutem.checks :as checks]
   [salutem.registry :as registry]))

(defn maintainer [registry-store context interval trigger-channel]
  (let [interval-millis (t/millis interval)
        shutdown-channel (async/chan)]
    (async/go
      (loop []
        (async/alt!
          (async/timeout interval-millis)
          (do
            (async/>! trigger-channel
              {:registry @registry-store :context context})
            (recur))

          shutdown-channel
          (async/close! trigger-channel))))
    shutdown-channel))

(defn refresher
  ([trigger-channel]
   (refresher trigger-channel (async/chan 1)))
  ([trigger-channel evaluation-channel]
   (async/go
     (loop []
       (let [{:keys [registry context]
              :or   {context {}}
              :as   trigger-message} (async/<! trigger-channel)]
         (if trigger-message
           (do
             (doseq [check (registry/outdated-checks registry)]
               (async/>! evaluation-channel {:check check :context context}))
             (recur))
           (async/close! evaluation-channel)))))
   evaluation-channel))

(defn evaluator
  ([evaluation-channel]
   (evaluator evaluation-channel (async/chan 1)))
  ([evaluation-channel result-channel]
   (async/go
     (loop []
       (let [{:keys [check context]
              :or   {context {}}
              :as   evaluation-message} (async/<! evaluation-channel)]
         (if evaluation-message
           (do
             (checks/attempt check context result-channel)
             (recur))
           (async/close! result-channel)))))
   result-channel))

(defn updater [registry-store result-channel]
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
  (updater registry-store result-channel)
  (evaluator evaluation-channel result-channel)
  (refresher trigger-channel evaluation-channel)
  (maintainer registry-store context interval trigger-channel))

(defn shutdown [shutdown-channel]
  (async/close! shutdown-channel))

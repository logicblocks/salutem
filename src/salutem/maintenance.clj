(ns salutem.maintenance
  (:require
   [clojure.core.async :as async]

   [salutem.checks :as checks]
   [salutem.registry :as registry]))

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

(defn updater [registry-store result-channel]
  (async/go
    (loop []
      (let [{:keys [check result]
             :as   result-message} (async/<! result-channel)]
        (when result-message
          (swap! registry-store
            registry/with-cached-result check result)
          (recur))))))

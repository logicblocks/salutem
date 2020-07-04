(ns salutem.maintenance
  (:require
   [clojure.core.async :as async]

   [salutem.results :as results]))

(defn attempt
  ([check context]
   (attempt check context (async/chan 1)))
  ([check context result-channel]
   (async/go
     (let [{:keys [check-fn timeout]} check
           callback-channel (async/chan)]
       (async/thread
         (check-fn context
           (fn [result]
             (async/>!! callback-channel result))))
       (async/alt!
         callback-channel
         ([result]
          (async/>! result-channel
            {:check check :result result}))

         (async/timeout timeout)
         (async/>! result-channel
           {:check check :result (results/result :unhealthy)})

         :priority true)
       (async/close! callback-channel)))
   result-channel))

(defn evaluator
  ([check-channel]
   (evaluator check-channel (async/chan 1)))
  ([check-channel result-channel]
   (async/go
     (loop []
       (let [{:keys [check context]
              :or   {context {}}
              :as   evaluation} (async/<! check-channel)]
         (if evaluation
           (do
             (attempt check context result-channel)
             (recur))
           (async/close! result-channel)))))
   result-channel))

(ns salutem.core
  (:require
   [clojure.core.async :as async]
   [tick.alpha.api :as t]))

(defn result
  ([status] (result status {}))
  ([status {:keys [evaluated-at]
            :or   {evaluated-at (t/now)}
            :as   extra-data}]
   (merge extra-data
     {:status       status
      :evaluated-at evaluated-at})))

(defn healthy? [result]
  (= (:status result) :healthy))

(defn unhealthy? [result]
  (= (:status result) :unhealthy))

(defn check
  ([name check-fn] (check name check-fn {}))
  ([name check-fn
    {:keys [type
            timeout]
     :or   {type    :cached
            timeout 10000}}]
   {:name     name
    :check-fn check-fn
    :type     type
    :timeout  timeout}))

(defn cached? [check]
  (= (:type check) :cached))

(defn realtime? [check]
  (= (:type check) :realtime))

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
           (let [{:keys [check-fn timeout]} check]
             (async/go
               (let [callback-channel (async/chan)]
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
                     {:check check :result (result :unhealthy)})

                   :priority true)
                 (async/close! callback-channel)))
             (recur))
           (async/close! result-channel)))))
   result-channel))

(defn evaluate
  ([check] (evaluate check {}))
  ([check context]
   (let [check-channel (async/chan 1)
         result-channel (async/chan 1)]
     (evaluator check-channel result-channel)
     (async/put! check-channel {:check check :context context})
     (let [{:keys [result]} (async/<!! result-channel)]
       (async/close! check-channel)
       result))))

(defn empty-registry []
  {:checks         {}
   :cached-results {}})

(defn with-check [registry check]
  (update-in registry [:checks] assoc (:name check) check))

(defn with-cached-result [registry check result]
  (update-in registry [:cached-results] assoc (:name check) result))

(defn find-check [registry name]
  (get-in registry [:checks name]))

(defn find-cached-result [registry name]
  (get-in registry [:cached-results name]))

(defn check-names [registry]
  (set (keys (:checks registry))))

(defn resolve-check
  ([registry name]
   (resolve-check registry name {}))
  ([registry name context]
   (let [check (find-check registry name)
         result (find-cached-result registry name)]
     (if (or (realtime? check) (not result))
       (evaluate check context)
       result))))

(ns salutem.core.maintenance
  "Provides an asynchronous maintenance pipeline for maintaining up-to-date
  results for the checks in a registry."
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
          (let [check-name (:name check)]
            (log/info logger ::updater.updating
              {:trigger-id trigger-id
               :check-name check-name
               :result     result})
            (swap! registry-store
              registry/with-cached-result check-name result)
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
  "Constructs and starts a maintenance pipeline to maintain up-to-date results
   for the checks in the registry in the provided registry store atom.

   The maintenance pipeline consists of a number of independent processes:

     - a _maintainer_ which triggers an attempt to refresh the results
       periodically,
     - a _refresher_ which requests evaluation of each outdated check on each
       refresh attempt,
     - an _evaluator_ which evaluates outdated checks to obtain a fresh result,
     - an _updater_ which updates the registry store atom with fresh check
       results,
     - a _notifier_ which calls callback functions when fresh check results are
       available.

   The maintenance pipeline can be configured via an optional map which
   can contain the following options:

     - `:context`: a map containing arbitrary context required by checks in
       order to run and passed to the check functions as the first
       argument; defaults to an empty map
     - `:interval`: a [[duration]] describing the wait interval between
       attempts to refresh the results in the registry; defaults to 200
       milliseconds
     - `:notification-callback-fns`: a sequence of arity-2 functions, with the
       first argument being a check and the second argument being a result,
       which are called whenever a new result is available for a check; empty by
       default
     - `:trigger-channel`: the channel on which trigger messages are sent, to
       indicate that a refresh of the registry should be attempted, defaults
       to a channel with a sliding buffer of length 1, i.e., in the case of a
       long running attempt, all but the latest trigger message will be dropped
       from the channel
     - `:evaluation-channel`: the channel on which messages requesting
       evaluation of checks are sent, defaults to a channel with a buffer of
       size 10
     - `:result-channel`: the channel on which results are placed after
       evaluation, defaults to a channel with a buffer of size 10
     - `:updater-result-channel`: a tap of the `result-channel` which sends
       result messages on to the updater, defaults to a channel with a buffer
       of size 10
     - `:notifier-result-channel`: a tap of the `result-channel` which sends
       result messages on to the notifier, defaults to a channel with a buffer
       of size 10

   If the context map contains a `:logger` key with a
   [`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
   value, the maintenance pipeline will emit a number of log events
   throughout operation.

   Returns the maintenance pipeline which can be passed to [[shutdown]] in
   order to stop operation."
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
            notification-callback-fns]
     :or   {context                   {}
            interval                  (t/new-duration 200 :millis)
            trigger-channel           (async/chan (async/sliding-buffer 1))
            evaluation-channel        (async/chan 10)
            result-channel            (async/chan 10)
            updater-result-channel    (async/chan 10)
            notifier-result-channel   (async/chan 10)
            notification-callback-fns []}}]
   (let [logger (get context :logger (null/logger))
         dependencies {:logger logger}
         result-mult (async/mult result-channel)
         shutdown-channel (async/chan)]
     (async/go
       (updater dependencies registry-store
         (async/tap result-mult updater-result-channel))
       (notifier dependencies notification-callback-fns
         (async/tap result-mult notifier-result-channel))
       (evaluator dependencies evaluation-channel result-channel)
       (refresher dependencies trigger-channel evaluation-channel)
       (maintainer dependencies registry-store context interval
         trigger-channel shutdown-channel))
     {:shutdown-channel shutdown-channel})))

(defn shutdown
  "Shuts down the maintenance pipeline preventing further updates to the
   registry."
  [maintenance-pipeline]
  (async/close! (:shutdown-channel maintenance-pipeline)))

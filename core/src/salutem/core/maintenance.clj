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
  "Starts a maintainer process which periodically triggers a registry refresh.

  Takes the following parameters:

    - `dependencies`: a map of dependencies used by the maintainer, currently
      supporting only a `:logger` entry with a
      [`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
      value.
    - `registry-store`: an atom containing the registry for which refreshes
      should be periodically triggered.
    - `context`: a map containing arbitrary context required by checks
      in order to run and passed to the check functions as the first argument.
    - `interval`: a [[salutem.core/duration]] describing the wait interval
      between refresh triggers.
    - `trigger-channel`: the channel on which to send trigger messages.
    - `shutdown-channel`: an optional channel which when closed, closes the
      trigger channel; included in the map returned by the function; if not
      provided, a channel with no buffer is used.

  The trigger messages sent to the trigger channel include:

    - `:trigger-id`: a monotonically increasing integer identifying the trigger.
    - `:registry`: the registry which should be refreshed.
    - `:context`: the context map to provide to checks."
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
     {:shutdown-channel shutdown-channel})))

(defn refresher
  "Starts a refresher process which triggers evaluation of outdated checks.

  Takes the following parameters:

    - `dependencies`: a map of dependencies used by the refresher, currently
      supporting only a `:logger` entry with a
      [`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
      value.
    - `trigger-channel`: a channel on which trigger messages are received.
    - `evaluation-channel`: an optional channel on which evaluation messages are
      sent; included in the map returned by the function; if not
      provided, a channel with a buffer of length 1 is used.

  The trigger messages received on the trigger channel should include:

    - `:trigger-id`: a monotonically increasing integer identifying the trigger.
    - `:registry`: the registry which should be refreshed.
    - `:context`: the context map to provide to checks.

  The evaluation messages sent on the evaluation channel include:

    - `:trigger-id`: the trigger ID from the trigger message.
    - `:check`: the check which should be evaluated.
    - `:context`: the context map to provide to the check.

  If the provided trigger channel is closed, the refresher will in turn close
  the evaluation channel."
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
                  :check-name (:salutem/name check)})
               (async/>! evaluation-channel
                 {:trigger-id trigger-id
                  :check      check
                  :context    context}))
             (recur))
           (do
             (async/close! evaluation-channel)
             (log/info logger ::refresher.stopped))))))
   {:evaluation-channel evaluation-channel}))

(def ^:private empty-evaluation-state
  {:checks #{} :response-channels #{}})

(defn evaluation-state-store
  "Returns a ref containing empty evaluation state, used by the evaluator to
  keep track of in flight checks.

  Optionally takes a
  [`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
  in which case any change to the ref is logged at debug level to the logger."
  ([] (ref empty-evaluation-state))
  ([logger]
   (add-watch (ref empty-evaluation-state)
     :logger
     (fn [_ _ old-state new-state]
       (log/debug logger ::evaluator.state-changed
         {:old-state old-state
          :new-state new-state})))))

(defn- in-flight-checks [state-store]
  (:checks @state-store))

(defn- in-flight-response-channels [state-store]
  (vec (:response-channels (deref state-store))))

(defn- in-flight? [state-store check-name]
  (contains? (in-flight-checks state-store) check-name))

(defn- mark-check-in-flight [state-store check-name]
  (alter state-store update-in [:checks] conj check-name))

(defn- mark-check-complete [state-store check-name]
  (alter state-store update-in [:checks] disj check-name))

(defn- remember-in-flight-response-channel
  [state-store response-channel]
  (alter state-store update-in [:response-channels]
    conj response-channel))

(defn- forget-in-flight-response-channel
  [state-store response-channel]
  (alter state-store update-in [:response-channels]
    disj response-channel))

(defn- hold-check [state-store check-name]
  (dosync
    (when-not (in-flight? state-store check-name)
      (mark-check-in-flight state-store check-name))))

(defn- park-check [state-store check-name response-channel]
  (dosync
    (remember-in-flight-response-channel state-store response-channel)))

(defn- complete-check [state-store check-name response-channel]
  (dosync
    (forget-in-flight-response-channel state-store response-channel)
    (mark-check-complete state-store check-name)))

(defn- evaluation-message? [message channel evaluation-channel]
  (and (= channel evaluation-channel) (not (nil? message))))

(defn- shutdown-message? [message channel evaluation-channel]
  (and (= channel evaluation-channel) (nil? message)))

(defn evaluator
  "Starts an evaluator process which evaluates checks.

  The evaluator process ensures that only one evaluation of any check can be in
  progress at a time. It does so by keeping track of in flight checks in a state
  ref, which can be passed as an argument, useful in the case that multiple
  evaluators are working as competing consumers of an evaluation channel.

  Takes the following parameters:

    - `dependencies`: a map of dependencies used by the evaluator, currently
      supporting only a `:logger` entry with a
      [`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
      value.
    - `state-store`: an optional ref containing evaluation state, best created
      by [[evaluation-state-store]]; defaults to an empty state store.
    - `evaluation-channel`: a channel on which evaluation messages are received.
    - `result-channel`: an optional channel on which result messages are
      sent; included in the map returned by the function; if not provided, a
      channel with a buffer of length 10 is used.
    - `skip-channel`: an optional channel on which skipped evaluation messages
      are sent; included in the map returned by the function; if not provided, a
      channel with a sliding buffer of length 10 is used.

  The evaluation messages received on the evaluation channel should include:

    - `:trigger-id`: a trigger ID used to correlate multiple evaluations.
    - `:check`: the check to be evaluated.
    - `:context`: the context map to provide to the check.

  The result messages sent on the result channel include:

    - `:trigger-id`: the trigger ID passed in the evaluation message.
    - `:check`: the evaluated check.
    - `:result`: the result of evaluating the check.

  If the provided evaluation channel is closed, the evaluator will in turn close
  the result channel and the skip channel."
  ([dependencies evaluation-channel]
   (evaluator dependencies
     (evaluation-state-store)
     evaluation-channel))

  ([dependencies state-store evaluation-channel]
   (evaluator dependencies state-store evaluation-channel
     (async/chan 10) (async/chan (async/sliding-buffer 10))))

  ([dependencies evaluation-channel result-channel skip-channel]
   (evaluator dependencies
     (evaluation-state-store)
     evaluation-channel
     result-channel skip-channel))

  ([dependencies state-store evaluation-channel result-channel skip-channel]
   (let [logger (:logger dependencies)
         output-channels {:skip-channel   skip-channel
                          :result-channel result-channel}]
     (log/info logger ::evaluator.starting)
     (async/go-loop []
       (let [response-channels (in-flight-response-channels state-store)
             [message channel]
             (async/alts! (conj response-channels evaluation-channel)
               :priority true)]
         (cond
           (shutdown-message? message channel evaluation-channel)
           (do
             (doseq [channel (vals output-channels)]
               (async/close! channel))
             (doseq [channel (in-flight-response-channels state-store)]
               (async/close! channel))
             (log/info logger ::evaluator.stopped))

           (evaluation-message? message channel evaluation-channel)
           (let [{:keys [check context trigger-id] :or {context {}}} message
                 check-name (:salutem/name check)]
             (log/info logger ::evaluator.holding
               {:trigger-id trigger-id
                :check-name check-name})
             (if (hold-check state-store check-name)
               (do
                 (log/info logger ::evaluator.evaluating
                   {:trigger-id trigger-id
                    :check-name check-name})
                 (park-check state-store check-name
                   (checks/attempt
                     dependencies trigger-id check context)))
               (do
                 (log/info logger ::evaluator.skipping
                   {:trigger-id trigger-id
                    :check-name check-name})
                 (async/>! skip-channel
                   {:trigger-id trigger-id
                    :check      check})))
             (recur))

           :else
           (let [{:keys [trigger-id check result]} message
                 check-name (:salutem/name check)]
             (log/info logger ::evaluator.completing
               {:trigger-id trigger-id
                :check-name check-name
                :result     result})
             (complete-check state-store check-name channel)
             (async/>! result-channel message)
             (async/close! channel)
             (recur)))))
     output-channels)))

(defn updater
  "Starts an updater process which updates the registry in a registry store with
  new results.

  Takes the following parameters:

    - `dependencies`: a map of dependencies used by the updater, currently
      supporting only a `:logger` entry with a
      [`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
      value.
    - `registry-store`: an atom containing the registry which should be updated
      with new results.
    - `result-channel`: a channel on which result messages are received.

  The result messages received on the result channel should include:

    - `:trigger-id`: a trigger ID used to correlate multiple results.
    - `:check`: the check that produced the result.
    - `:result`: the result with which to update the registry."
  [dependencies registry-store result-channel]
  (let [logger (:logger dependencies)]
    (log/info logger ::updater.starting)
    (async/go-loop []
      (let [{:keys [check result trigger-id]
             :as   result-message} (async/<! result-channel)]
        (if result-message
          (let [check-name (:salutem/name check)]
            (log/info logger ::updater.updating
              {:trigger-id trigger-id
               :check-name check-name
               :result     result})
            (swap! registry-store
              registry/with-cached-result check-name result)
            (recur))
          (log/info logger ::updater.stopped))))))

(defn notifier
  "Starts a notifier process which executes a number of notification callbacks
  with new results.

  Takes the following parameters:

    - `dependencies`: a map of dependencies used by the notifier, currently
      supporting only a `:logger` entry with a
      [`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
      value.
    - `callbacks`: a sequence of arity-2 functions, with the first argument
      being a check and the second argument being a result, to call whenever a
      new result is available.
    - `result-channel`: a channel on which result messages are received.

  The result messages received on the result channel should include:

    - `:trigger-id`: a trigger ID used to correlate multiple results.
    - `:check`: the check that produced the result.
    - `:result`: the result with which to update the registry."
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
                 :check-name (:salutem/name check)
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
     - `:interval`: a [[salutem.core/duration]] describing the wait interval
       between attempts to refresh the results in the registry; defaults to 200
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
            notification-callback-fns
            trigger-channel
            evaluation-channel
            result-channel
            skip-channel
            updater-result-channel
            notifier-result-channel]
     :or   {context                   {}
            interval                  (t/new-duration 200 :millis)
            notification-callback-fns []
            trigger-channel           (async/chan (async/sliding-buffer 1))
            evaluation-channel        (async/chan 10)
            result-channel            (async/chan 10)
            skip-channel              (async/chan (async/sliding-buffer 10))
            updater-result-channel    (async/chan 10)
            notifier-result-channel   (async/chan 10)}}]
   (let [logger (get context :logger (null/logger))
         dependencies {:logger logger}
         result-mult (async/mult result-channel)
         shutdown-channel (async/chan)]
     (async/go
       (updater dependencies registry-store
         (async/tap result-mult updater-result-channel))
       (notifier dependencies notification-callback-fns
         (async/tap result-mult notifier-result-channel))
       (evaluator dependencies evaluation-channel result-channel skip-channel)
       (refresher dependencies trigger-channel evaluation-channel)
       (maintainer dependencies registry-store context interval
         trigger-channel shutdown-channel))
     {:shutdown-channel shutdown-channel})))

(defn shutdown
  "Shuts down the maintenance pipeline preventing further updates to the
   registry."
  [maintenance-pipeline]
  (async/close! (:shutdown-channel maintenance-pipeline)))

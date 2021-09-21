(ns salutem.core.checks
  "Provides constructors, predicates and evaluation functions for checks."
  (:require
   [clojure.core.async :as async]

   [tick.alpha.api :as t]

   [cartus.core :as log]
   [cartus.null :as cartus-null]

   [salutem.core.results :as results]))

(defn- check
  ([check-name check-fn]
   (check check-name check-fn {}))
  ([check-name check-fn
    {:keys [salutem/timeout]
     :or   {timeout (t/new-duration 10 :seconds)}
     :as   opts}]
   (merge
     opts
     {:salutem/name     check-name
      :salutem/check-fn check-fn
      :salutem/timeout  timeout})))

(defn background-check
  "Constructs a background check with the provided name and check function.

   A background check is one that is evaluated periodically with the result
   cached in a registry until the next evaluation, conducted by a maintenance
   pipeline, which will occur once the time to re-evaluation of the check has
   passed.

   Background checks are useful for external dependencies where it is
   important not to perform the check too frequently and where the health
   status only needs to be accurate on the order of the time to re-evaluation.

   Takes the following parameters:

     - `check-name`: a keyword representing the name of the check
     - `check-fn`: an arity-2 function, with the first argument being a context
       map as provided during evaluation or at maintenance pipeline construction
       and the second argument being a callback function which should be called
       with the result of the check to signal the check is complete; note, check
       functions _must_ be non-blocking.
     - `opts`: an optional map of additional options for the check, containing:
       - `:salutem/timeout`: a [[salutem.time/duration]] representing the amount
         of time to wait for the check to complete before considering it failed,
         defaulting to 10 seconds.
       - `:salutem/time-to-re-evaluation`: a [[salutem.time/duration]]
         representing the time to wait after a check is evaluated before
         attempting to re-evaluate it, defaulting to 10 seconds.

   Any extra entries provided in the `opts` map are retained on the check for
   later use.

   Note that a result for a background check may live for longer than the
   time to re-evaluation since evaluation takes time and the result will
   continue to be returned from the registry whenever the check is resolved
   until the evaluation has completed and the new result has been added to the
   registry."
  ([check-name check-fn]
   (background-check check-name check-fn {}))
  ([check-name check-fn opts]
   (let [time-to-re-evaluation
         (or
           (:salutem/time-to-re-evaluation opts)
           (:salutem/ttl opts)
           (t/new-duration 10 :seconds))]
     (check check-name check-fn
       (merge
         {:salutem/time-to-re-evaluation time-to-re-evaluation}
         (dissoc opts :salutem/time-to-re-evaluation :salutem/ttl)
         {:salutem/type :background})))))

(defn realtime-check
  "Constructs a realtime check with the provided name and check function.

   A realtime check is one that is re-evaluated whenever the check is resolved,
   with no caching of results taking place.

   Realtime checks are useful when the accuracy of the check needs to be very
   high or where the check itself is inexpensive.

   Takes the following parameters:

     - `check-name`: a keyword representing the name of the check
     - `check-fn`: an arity-2 function, with the first argument being a context
       map as provided during evaluation or at maintenance pipeline construction
       and the second argument being a callback function which should be called
       with the result fo the check to signal the check is complete; note, check
       functions _must_ be non-blocking.
     - `opts`: an optional map of additional options for the check, containing:
       - `:salutem/timeout`: a [[salutem.time/duration]] representing the amount
         of time to wait for the check to complete before considering it failed,
         defaulting to 10 seconds.

   Any extra entries provided in the `opts` map are retained on the check for
   later use."
  ([check-name check-fn]
   (realtime-check check-name check-fn {}))
  ([check-name check-fn opts]
   (check check-name check-fn
     (merge
       opts
       {:salutem/type :realtime}))))

(defn background?
  "Returns `true` if the provided check is a background check, `false`
   otherwise."
  [check]
  (= (:salutem/type check) :background))

(defn realtime?
  "Returns `true` if the provided check is a realtime check, `false`
   otherwise."
  [check]
  (= (:salutem/type check) :realtime))

(defn attempt
  "Attempts to obtain a result for a check, handling timeouts and exceptions.

  Takes the following parameters:

    - `dependencies`: A map of dependencies used by `attempt` in obtaining the
      result, currently supporting only a `:logger` entry with a
      [`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
      value.
    - `trigger-id`: An ID identifying the attempt in any subsequently produced
      messages and used in logging.
    - `check`: the check to be attempted.
    - `context`: an optional map containing arbitrary context required by the
      check in order to run and passed to the check functions as the first
      argument; defaults to an empty map.
    - `result-channel`: an optional channel on which to send the result message;
      defaults to a channel with a buffer length of 1.

  The attempt is performed asynchronously and the result channel is returned
  immediately.

  In the case that the attempt takes longer than the check's timeout, an
  unhealthy result is produced, including `:salutem/reason` as `:timed-out`.

  In the case that the attempt throws an exception, an unhealthy result is
  produced, including `:salutem/reason` as `:exception-thrown` and including
  the exception at `:salutem/exception`.

  In all other cases, the result produced by the check is passed on to the
  result channel.

  All produced results include a `:salutem/evaluation-duration` entry with the
  time taken to obtain the result, which can be overridden within check
  functions if required."
  ([dependencies trigger-id check]
   (attempt dependencies trigger-id check {}))
  ([dependencies trigger-id check context]
   (attempt dependencies trigger-id check context (async/chan 1)))
  ([dependencies trigger-id check context result-channel]
   (let [logger (or (:logger dependencies) (cartus-null/logger))
         check-name (:salutem/name check)]
     (async/go
       (let [{:keys [salutem/check-fn salutem/timeout]} check
             callback-channel (async/chan)
             exception-channel (async/chan 1)
             before (t/now)]
         (log/info logger ::attempt.starting
           {:trigger-id trigger-id
            :check-name check-name})
         (try
           (check-fn context
             (fn [result]
               (async/put! callback-channel result)))
           (catch Exception exception
             (async/>! exception-channel exception)))
         (async/alt!
           exception-channel
           ([exception]
            (let [after (t/now)
                  duration (t/between before after)]
              (log/info logger ::attempt.threw-exception
                {:trigger-id trigger-id
                 :check-name check-name
                 :exception  exception})
              (async/>! result-channel
                {:trigger-id trigger-id
                 :check      check
                 :result     (results/unhealthy
                               {:salutem/reason              :threw-exception
                                :salutem/exception           exception
                                :salutem/evaluation-duration duration})})))

           (async/timeout (t/millis timeout))
           (let [after (t/now)
                 duration (t/between before after)]
             (log/info logger ::attempt.timed-out
               {:trigger-id trigger-id
                :check-name check-name})
             (async/>! result-channel
               {:trigger-id trigger-id
                :check      check
                :result     (results/unhealthy
                              {:salutem/reason              :timed-out
                               :salutem/evaluation-duration duration})}))

           callback-channel
           ([result]
            (let [after (t/now)
                  duration (t/between before after)
                  result (results/prepend result
                           {:salutem/evaluation-duration duration})]
              (log/info logger ::attempt.completed
                {:trigger-id trigger-id
                 :check-name check-name
                 :result     result})
              (async/>! result-channel
                {:trigger-id trigger-id
                 :check      check
                 :result     result})))

           :priority true)
         (async/close! exception-channel)
         (async/close! callback-channel))))
   result-channel))

(defn- evaluation-attempt [check context]
  (let [logger (or (:logger context) (cartus-null/logger))
        trigger-id (or (:trigger-id context) :ad-hoc)]
    (attempt {:logger logger} trigger-id check context
      (async/chan 1 (map :result)))))

(defn evaluate
  "Evaluates the provided check, returning the result of the evaluation.

   Optionally takes a context map containing arbitrary context required by the
   check in order to run and passed to the check function as the first argument.

   By default, the check is evaluated synchronously. If a callback function is
   provided, the function starts evaluation asynchronously, returns immediately
   and invokes the callback function with the result once available."
  ([check] (evaluate check {}))
  ([check context]
   (async/<!! (evaluation-attempt check context)))
  ([check context callback-fn]
   (async/go (callback-fn (async/<! (evaluation-attempt check context))))))

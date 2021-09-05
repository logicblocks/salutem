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
    {:keys [timeout]
     :or   {timeout (t/new-duration 10 :seconds)}
     :as   opts}]
   (merge
     opts
     {:name     check-name
      :check-fn check-fn
      :timeout  timeout})))

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
       - `:timeout`: a [[salutem.time/duration]] representing the amount of time
         to wait for the check to complete before considering it failed,
         defaulting to 10 seconds
       - `:time-to-re-evaluation`: a [[salutem.time/duration]] representing the
         time to wait after a check is evaluated before attempting to
         re-evaluate it, defaulting to 10 seconds.

   Note that a result for a background check may live for longer than the
   time to re-evaluation since evaluation takes time and the result will
   continue to be returned from the registry whenever the check us resolved
   until the evaluation has completed and the new result has been added to the
   registry."
  ([check-name check-fn]
   (background-check check-name check-fn {}))
  ([check-name check-fn opts]
   (let [time-to-re-evaluation
         (or
           (:time-to-re-evaluation opts)
           (:ttl opts)
           (t/new-duration 10 :seconds))]
     (check check-name check-fn
       (merge
         {:time-to-re-evaluation time-to-re-evaluation}
         (dissoc opts :time-to-re-evaluation :ttl)
         {:type :background})))))

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
       - `:timeout`: a [[salutem.time/duration]] representing the amount of time
         to wait for the check to complete before considering it failed,
         defaulting to 10 seconds"
  ([check-name check-fn]
   (realtime-check check-name check-fn {}))
  ([check-name check-fn opts]
   (check check-name check-fn
     (merge
       opts
       {:type :realtime}))))

(defn background?
  "Returns `true` if the provided check is a background check, `false`
   otherwise."
  [check]
  (= (:type check) :background))

(defn realtime?
  "Returns `true` if the provided check is a realtime check, `false`
   otherwise."
  [check]
  (= (:type check) :realtime))

(defn attempt
  ([check] (attempt check {}))
  ([check context] (attempt check context (async/chan 1)))
  ([check context result-channel]
   (let [logger (or (:logger context) (cartus-null/logger))
         dependencies {:logger logger}]
     (attempt dependencies nil check context result-channel)))
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
                :check      check
                :result     (results/unhealthy
                              {:salutem/reason :timeout})}))

           :priority true)
         (async/close! callback-channel))))
   result-channel))

(defn evaluate
  "Evaluates the provided check, returning the result of the evaluation.

   Optionally takes a context map containing arbitrary context required by the
   check in order to run and passed to the check function as the first argument.

   By default, the check is evaluated synchronously. If a callback function is
   provided, the function starts evaluation asynchronously, returns immediately
   and invokes the callback function with the result once available."
  ([check] (evaluate check {}))
  ([check context]
   (async/<!!
     (attempt check context
       (async/chan 1 (map :result)))))
  ([check context callback-fn]
   (async/go
     (callback-fn
       (async/<!
         (attempt check context
           (async/chan 1 (map :result))))))))

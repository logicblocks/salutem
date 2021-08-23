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
   cached until the next evaluation, which will occur once the time-to-live
   (TTL) of the check has passed.

   Background checks are useful for external dependencies where it is
   important not to perform the check too frequently and where the health
   status only needs to be accurate to within the TTL.

   Takes the following parameters:

     - `check-name`: a keyword representing the name of the check
     - `check-fn`: an arity-2 function, with with the first argument being a
       context map as provided during evaluation or at maintenance pipeline
       construction and the second argument being a callback function which
       should be called with the result fo the check to signal the check is
       complete; note, check functions _must_ be non-blocking.
     - `opts`: an optional map of additional options for the check, containing:
       - `:ttl`: a [[duration]] representing the TTL for a result of this check,
         defaulting to 10 seconds
       - `:timeout`: a [[duration]] representing the amount of time to wait for
         the check to complete before considering it failed, defaulting to
         10 seconds"
  ([check-name check-fn]
   (background-check check-name check-fn {}))
  ([check-name check-fn opts]
   (check check-name check-fn
     (merge
       {:ttl (t/new-duration 10 :seconds)}
       opts
       {:type :background}))))

(defn realtime-check
  "Constructs a realtime check with the provided name and check function.

   A realtime check is one that is re-evaluated whenever the check is resolved,
   with no caching of results taking place.

   Realtime checks are useful when the accuracy of the check needs to be very
   accurate or where the check itself is inexpensive.

   Takes the following parameters:

     - `check-name`: a keyword representing the name of the check
     - `check-fn`: an arity-2 function, with with the first argument being a
       context map as provided during evaluation or at maintenance pipeline
       construction and the second argument being a callback function which
       should be called with the result fo the check to signal the check is
       complete; note, check functions _must_ be non-blocking.
     - `opts`: an optional map of additional options for the check, containing:
       - `:timeout`: a [[duration]] representing the amount of time to wait for
         the check to complete before considering it failed, defaulting to
         10 seconds"
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
  ([check context]
   (let [logger (or (:logger context) (cartus-null/logger))
         dependencies {:logger logger}]
     (attempt dependencies nil check context (async/chan 1))))
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
                :result     (results/unhealthy)}))

           :priority true)
         (async/close! callback-channel))))
   result-channel))

(defn evaluate
  "Evaluates the provided check synchronously, returning the result of the
   evaluation.

   Optionally takes a context map containing arbitrary context required
   by the check in order to run and passed to the check function as the first
   argument."
  ([check] (evaluate check {}))
  ([check context]
   (:result (async/<!! (attempt check context)))))

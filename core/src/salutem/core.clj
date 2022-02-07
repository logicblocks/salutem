(ns salutem.core
  "A system for defining and maintaining a collection of health checks with
   support for both realtime and background checks.

   The `salutem.core` namespace is the public interface of the system and should
   be used in preference of the contained namespaces.

   `salutem` is somewhat inspired by [dropwizard-health](https://github.com/dropwizard/dropwizard-health)
   which may provide additional insight into its design."
  (:require
   [salutem.core.time :as time]
   [salutem.core.checks :as checks]
   [salutem.core.results :as results]
   [salutem.core.registry :as registry]
   [salutem.core.maintenance :as maintenance]))

; time
(defn duration
  "Constructs an object representing a duration of time.

   This object is used to specify, for example, the time to re-evaluation and
   timeout on a check or the interval passed to a maintenance pipeline.

   Takes an amount and a unit:

     - `amount` is the length of the duration, measured in terms of the unit
     - `unit` is one of `:nanos`, `:micros`, `:millis`, `:seconds`, `:minutes`,
       `:hours`, `:half-days`, `:days`, `:weeks`, `:months`, `:years`,
       `:decades`, `:centuries`, `:millennia`, `:eras` or `:forever`

   Note: internally, this constructs a `java.time.Duration` and is merely a
   convenience function. As such, a `java.time.Duration` can be passed directly
   wherever this function would be used."
  [amount unit]
  (time/duration amount unit))

; results
(defn result
  "Constructs a result with the provided `status`.

   The optional map of extra data is stored with the result for future use.
   Unless overridden in the extra data map, a `:salutem/evaluated-at` field is
   added to the result, set to the current date time in the system default time
   zone."
  ([status] (results/result status))
  ([status extra-data] (results/result status extra-data)))

(defn healthy
  "Constructs a healthy result.

   The optional map of extra data is stored with the result for future use.
   Unless overridden in the extra data map, an `:salutem/evaluated-at` field is
   added to the result, set to the current date time in the system default time
   zone."
  ([] (results/healthy))
  ([extra-data] (results/healthy extra-data)))

(defn unhealthy
  "Constructs an unhealthy result.

   The optional map of extra data is stored with the result for future use.
   Unless overridden in the extra data map, an `:salutem/evaluated-at` field is
   added to the result, set to the current date time in the system default time
   zone."
  ([] (results/unhealthy))
  ([extra-data] (results/unhealthy extra-data)))

(defn status
  "Returns the status of the provided result."
  [result]
  (results/status result))

(defn healthy?
  "Returns `true` if the result has a `:healthy` status, `false`
   otherwise."
  [result]
  (results/healthy? result))

(defn unhealthy?
  "Returns `true` if the result has an `:unhealthy` status, `false`
   otherwise."
  [result]
  (results/unhealthy? result))

(defn outdated?
  "Returns `true` if the result of the check is outdated, `false`
   otherwise.

   For a realtime check, a result is always considered outdated.

   For a background check, a result is considered outdated if the
   time to re-evaluation of the check has passed, i.e., if its evaluation date
   time is before the current date time minus the check's time to re-evaluation.

   If `relative-to` is provided, the calculation is performed relative to that
   date time rather than to the current date time."
  ([result check] (results/outdated? result check))
  ([result check relative-to] (results/outdated? result check relative-to)))

; checks
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
       - `:salutem/timeout`: a [[duration]] representing the amount of time to
         wait for the check to complete before considering it failed, defaulting
         to 10 seconds.
       - `:salutem/time-to-re-evaluation`: a [[duration]] representing the time
         to wait after a check is evaluated before attempting to re-evaluate it,
         defaulting to 10 seconds.

   Any extra entries provided in the `opts` map are retained on the check for
   later use.

   Note that a result for a background check may live for longer than the
   time to re-evaluation since evaluation takes time and the result will
   continue to be returned from the registry whenever the check is resolved
   until the evaluation has completed and the new result has been added to the
   registry."
  ([check-name check-fn] (checks/background-check check-name check-fn))
  ([check-name check-fn opts]
   (checks/background-check check-name check-fn opts)))

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
       - `:salutem/timeout`: a [[duration]] representing the amount of time to
         wait for the check to complete before considering it failed, defaulting
         to 10 seconds.

   Any extra entries provided in the `opts` map are retained on the check for
   later use."
  ([check-name check-fn] (checks/realtime-check check-name check-fn))
  ([check-name check-fn opts]
   (checks/realtime-check check-name check-fn opts)))

(defn background?
  "Returns `true` if the provided check is a background check, `false`
   otherwise."
  [check]
  (checks/background? check))

(defn realtime?
  "Returns `true` if the provided check is a realtime check, `false`
   otherwise."
  [check]
  (checks/realtime? check))

(defn check-name
  "Returns the name of the provided check."
  [check]
  (checks/check-name check))

(defn evaluate
  "Evaluates the provided check, returning the result of the evaluation.

   Optionally takes a context map containing arbitrary context required by the
   check in order to run and passed to the check function as the first argument.

   By default, the check is evaluated synchronously. If a callback function is
   provided, the function starts evaluation asynchronously, returns immediately
   and invokes the callback function with the result once available."
  ([check] (checks/evaluate check))
  ([check context] (checks/evaluate check context))
  ([check context callback-fn] (checks/evaluate check context callback-fn)))

; registry
(defn empty-registry
  "Constructs an empty registry which can be populated using [[with-check]] and
   [[with-cached-result]]."
  []
  (registry/empty-registry))

(defn with-check
  "Adds the check to the registry, returning a new registry."
  [registry check]
  (registry/with-check registry check))

(defn with-cached-result
  "Adds the result for the check with the given name to the registry,
   returning a new registry."
  [registry check-name result]
  (registry/with-cached-result registry check-name result))

(defn find-check
  "Finds the check with the given name in the registry. Returns `nil` if no
   check can be found."
  [registry check-name]
  (registry/find-check registry check-name))

(defn find-cached-result
  "Finds the cached result for the check with the given name in the registry.
   Returns `nil` if no result can be found or if the check does not exist."
  [registry check-name]
  (registry/find-cached-result registry check-name))

(defn check-names
  "Returns the set of check names present in the registry."
  [registry]
  (registry/check-names registry))

(defn all-checks
  "Returns the set of checks present in the registry."
  [registry]
  (registry/all-checks registry))

(defn outdated-checks
  "Returns the set of checks that are currently outdated in the registry based
   on the type of the check and the cached results available.

   See [[outdated?]] for details on which it means for a check to be outdated."
  [registry]
  (registry/outdated-checks registry))

(defn resolve-check
  "Resolves a result for the check of the given name in the registry.

   If the check is a background check and there is a cached result available,
   it is returned. If no cached result is available, the check is evaluated in
   order to obtain a result to return.

   If the check is a realtime check, it is always evaluated in order to obtain
   a result to return and caching is not used.

   Optionally takes a context map containing arbitrary context required
   by the check in order to run and passed to the check function as the first
   argument.

   By default, the check is resolved synchronously. If a callback function is
   provided, the function starts resolution asynchronously, returns immediately
   and invokes the callback function with the result once available."
  ([registry check-name]
   (registry/resolve-check registry check-name))
  ([registry check-name context]
   (registry/resolve-check registry check-name context))
  ([registry check-name context callback-fn]
   (registry/resolve-check registry check-name context callback-fn)))

(defn resolve-checks
  "Resolves all checks in the registry, returning a map of check names to
   results.

   Checks requiring re-evaluation are evaluated in parallel such that this
   function should take about as long as the slowest check (assuming IO is the
   dominant blocker).

   Optionally takes a context map containing arbitrary context required by
   checks in order to run and passed to the check functions as the first
   argument.

   By default, the checks are resolved synchronously. If a callback function is
   provided, the function starts resolution asynchronously, returns immediately
   and invokes the callback function with the results once available.

   See [[resolve-check]] for details on how each check is resolved."
  ([registry] (registry/resolve-checks registry))
  ([registry context] (registry/resolve-checks registry context))
  ([registry context callback-fn]
   (registry/resolve-checks registry context callback-fn)))

; maintenance
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
  ([registry-store] (maintenance/maintain registry-store))
  ([registry-store options] (maintenance/maintain registry-store options)))

(defn shutdown
  "Shuts down the maintenance pipeline preventing further updates to the
   registry."
  [maintenance-pipeline]
  (maintenance/shutdown maintenance-pipeline))

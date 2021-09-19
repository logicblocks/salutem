(ns salutem.core.registry
  "Provides constructors, query functions and resolution functions for
   registries."
  (:require
   [salutem.core.checks :as checks]
   [salutem.core.results :as results]))

(defn empty-registry
  "Constructs an empty registry which can be populated using [[with-check]] and
   [[with-cached-result]]."
  []
  {:salutem/checks         {}
   :salutem/cached-results {}})

(defn with-check
  "Adds the check to the registry, returning a new registry."
  [registry check]
  (update-in registry [:salutem/checks] assoc (:salutem/name check) check))

(defn with-cached-result
  "Adds the result for the check with the given name to the registry,
   returning a new registry."
  [registry check-name result]
  (update-in registry [:salutem/cached-results] assoc check-name result))

(defn find-check
  "Finds the check with the given name in the registry. Returns `nil` if no
   check can be found."
  [registry check-name]
  (get-in registry [:salutem/checks check-name]))

(defn find-cached-result
  "Finds the cached result for the check with the given name in the registry.
   Returns `nil` if no result can be found or if the check does not exist."
  [registry check-name]
  (get-in registry [:salutem/cached-results check-name]))

(defn check-names
  "Returns the set of check names present in the registry."
  [registry]
  (set (keys (:salutem/checks registry))))

(defn all-checks
  "Returns the set of checks present in the registry."
  [registry]
  (set (vals (:salutem/checks registry))))

(defn outdated-checks
  "Returns the set of checks that are currently outdated in the registry based
   on the type of the check and the cached results available.

   See [[salutem.results/outdated?]] for details on which it means for a check
   to be outdated."
  [registry]
  (set
    (filter
      #(results/outdated? (find-cached-result registry (:salutem/name %)) %)
      (all-checks registry))))

(defn- requires-re-evaluation? [check result]
  (or (checks/realtime? check) (not result)))

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
   (resolve-check registry check-name {}))
  ([registry check-name context]
   (let [promise (promise)]
     (resolve-check registry check-name context #(deliver promise %))
     (deref promise)))
  ([registry check-name context callback-fn]
   (let [check (find-check registry check-name)
         result (find-cached-result registry check-name)]
     (if (requires-re-evaluation? check result)
       (checks/evaluate check context callback-fn)
       (callback-fn result)))))

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
  ([registry]
   (resolve-checks registry {}))
  ([registry context]
   (let [promise (promise)]
     (resolve-checks registry context #(deliver promise %))
     (deref promise)))
  ([registry context callback-fn]
   (let [{:keys [requiring-re-evaluation resolved-from-cache]}
         (reduce
           (fn [accumulator check-name]
             (let [check (find-check registry check-name)
                   result (find-cached-result registry check-name)]
               (apply update-in accumulator
                 (if (requires-re-evaluation? check result)
                   [[:requiring-re-evaluation] conj check]
                   [[:resolved-from-cache] assoc check-name result]))))
           {:requiring-re-evaluation []
            :resolved-from-cache     {}}
           (check-names registry))

         re-evaluation-promises
         (map
           (fn [check]
             (let [promise (promise)]
               (checks/evaluate check context
                 (fn [result]
                   (deliver promise [(:salutem/name check) result])))
               promise))
           requiring-re-evaluation)]
     (future
       (let [resolved-through-re-evaluation
             (into {} (map deref re-evaluation-promises))]
         (callback-fn
           (merge
             resolved-from-cache
             resolved-through-re-evaluation)))))))

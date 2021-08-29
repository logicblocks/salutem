(ns salutem.core.registry
  "Provides constructors, query functions and resolution functions for
   registries."
  (:require
   [tick.alpha.api :as t]

   [salutem.core.checks :as checks]
   [salutem.core.results :as results]))

(defn empty-registry
  "Constructs an empty registry which can be populated using [[with-check]] and
   [[with-cached-result]]."
  []
  {:checks         {}
   :cached-results {}})

(defn with-check
  "Adds the check to the registry, returning a new registry."
  [registry check]
  (update-in registry [:checks] assoc (:name check) check))

(defn with-cached-result
  "Adds the result for the check to the registry, returning a new registry."
  [registry check result]
  (update-in registry [:cached-results] assoc (:name check) result))

(defn find-check
  "Finds the check with the given name in the registry. Returns `nil` if no
   check can be found."
  [registry check-name]
  (get-in registry [:checks check-name]))

(defn find-cached-result
  "Finds the cached result for the check with the given name in the registry.
   Returns `nil` if no result can be found or if the check does not exist."
  [registry check-name]
  (get-in registry [:cached-results check-name]))

(defn check-names
  "Returns the set of check names present in the registry."
  [registry]
  (set (keys (:checks registry))))

(defn all-checks
  "Returns the set of checks present in the registry."
  [registry]
  (set (vals (:checks registry))))

(defn outdated-checks
  "Returns the set of checks that are currently outdated in the registry based
   on the type of the check and the cached results available.

   See [[outdated?]] for details on which it means for a check to be outdated."
  [registry]
  (set
    (filter
      (fn [check]
        (results/outdated?
          (find-cached-result registry (:name check)) check (t/now)))
      (all-checks registry))))

(defn resolve-check
  "Resolves a result for the check of the given name in the registry.

   If the check is a background check and there is a cached result available,
   it is returned. If no cached result is available, the check is evaluated in
   order to obtain a result to return.

   If the check is a realtime check, it is always evaluated in order to obtain
   a result to return and caching is not used.

   Optionally takes a context map containing arbitrary context required
   by the check in order to run and passed to the check function as the first
   argument."
  ([registry check-name]
   (resolve-check registry check-name {}))
  ([registry check-name context]
   (let [check (find-check registry check-name)
         result (find-cached-result registry check-name)]
     (if (or (checks/realtime? check) (not result))
       (checks/evaluate check context)
       result))))

(defn resolve-checks
  "Resolves all checks in the registry, returning a map of check names to
   results.

   Optionally takes a context map containing arbitrary context required by
   checks in order to run and passed to the check functions as the first
   argument.

   See [[resolve-check]] for details on how each check is resolved."
  ([registry]
   (resolve-checks registry {}))
  ([registry context]
   (into {}
     (map
       (fn [check-name]
         [check-name (resolve-check registry check-name context)])
       (check-names registry)))))

(defn refresh-result
  [registry check-name]
  (with-cached-result registry
    (find-check registry check-name)
    (resolve-check registry check-name)))

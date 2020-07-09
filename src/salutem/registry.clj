(ns salutem.registry
  (:require
   [tick.alpha.api :as t]

   [salutem.checks :as checks]
   [salutem.results :as results]))

(defn empty-registry []
  {:checks         {}
   :cached-results {}})

(defn with-check [registry check]
  (update-in registry [:checks] assoc (:name check) check))

(defn with-cached-result [registry check result]
  (update-in registry [:cached-results] assoc (:name check) result))

(defn find-check [registry check-name]
  (get-in registry [:checks check-name]))

(defn find-cached-result [registry check-name]
  (get-in registry [:cached-results check-name]))

(defn check-names [registry]
  (set (keys (:checks registry))))

(defn all-checks [registry]
  (set (vals (:checks registry))))

(defn outdated-checks [registry]
  (set
    (filter
      (fn [check]
        (results/outdated?
          (find-cached-result registry (:name check)) check (t/now)))
      (all-checks registry))))

(defn resolve-check
  ([registry check-name]
   (resolve-check registry check-name {}))
  ([registry check-name context]
   (let [check (find-check registry check-name)
         result (find-cached-result registry check-name)]
     (if (or (checks/realtime? check) (not result))
       (checks/evaluate check context)
       result))))

(defn resolve-checks
  ([registry]
   (resolve-checks registry {}))
  ([registry context]
   (into {}
     (map
       (fn [check-name]
         [check-name (resolve-check registry check-name context)])
       (check-names registry)))))

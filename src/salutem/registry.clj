(ns salutem.registry
  (:require
   [salutem.checks :as checks]))

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

(defn resolve-check
  ([registry check-name]
   (resolve-check registry check-name {}))
  ([registry check-name context]
   (let [check (find-check registry check-name)
         result (find-cached-result registry check-name)]
     (if (or (checks/realtime? check) (not result))
       (checks/attempt check context)
       result))))

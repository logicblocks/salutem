(ns salutem.registry
  (:require
   [salutem.checks :as checks]
   [salutem.maintenance :as maintenance]))

(defn empty-registry []
  {:checks         {}
   :cached-results {}})

(defn with-check [registry check]
  (update-in registry [:checks] assoc (:name check) check))

(defn with-cached-result [registry check result]
  (update-in registry [:cached-results] assoc (:name check) result))

(defn find-check [registry name]
  (get-in registry [:checks name]))

(defn find-cached-result [registry name]
  (get-in registry [:cached-results name]))

(defn check-names [registry]
  (set (keys (:checks registry))))

(defn resolve-check
  ([registry name]
   (resolve-check registry name {}))
  ([registry name context]
   (let [check (find-check registry name)
         result (find-cached-result registry name)]
     (if (or (checks/realtime? check) (not result))
       (checks/attempt check context)
       result))))

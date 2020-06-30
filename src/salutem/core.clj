(ns salutem.core
  (:require
   [tick.alpha.api :as t]))

(defn check
  ([name check-fn] (check name check-fn {}))
  ([name check-fn
    {:keys [type]
     :or   {type :cached}}]
   {:name     name
    :check-fn check-fn
    :type     type}))

(defn cached? [check]
  (= (:type check) :cached))

(defn realtime? [check]
  (= (:type check) :realtime))

(defn result
  ([status] (result status {}))
  ([status {:keys [evaluated-at]
            :or   {evaluated-at (t/now)}
            :as   extra-data}]
   (merge extra-data
     {:status       status
      :evaluated-at evaluated-at})))

(defn healthy? [result]
  (= (:status result) :healthy))

(defn unhealthy? [result]
  (= (:status result) :unhealthy))

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

(defn evaluate-check
  ([registry name]
   (evaluate-check registry name {}))
  ([registry name context]
   (let [{:keys [check-fn]} (find-check registry name)]
     (check-fn context))))

(defn resolve-check
  ([registry name]
   (resolve-check registry name {}))
  ([registry name context]
   (let [check (find-check registry name)
         result (find-cached-result registry name)]
     (if (or (realtime? check) (not result))
       (evaluate-check registry name context)
       result))))

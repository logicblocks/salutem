(ns salutem.core)

(defn empty-registry []
  {:checks  {}
   :results {}})

(defn check
  ([name check-fn] (check name check-fn {}))
  ([name check-fn
    {:keys [asynchronous]
     :or   {asynchronous true}}]
   {:name         name
    :check-fn     check-fn
    :asynchronous asynchronous}))

(defn asynchronous? [check]
  (:asynchronous check))

(defn synchronous? [check]
  (not (:asynchronous check)))

(defn result
  ([status] (result status {}))
  ([status {:keys []}]
   {:status status}))

(defn with-check [registry check]
  (update-in registry [:checks] assoc (:name check) check))

(defn with-result [registry check result]
  (update-in registry [:results] assoc (:name check) result))

(defn find-check [registry name]
  (get-in registry [:checks name]))

(defn find-result [registry name]
  (get-in registry [:results name]))

(defn check-names [registry]
  (set (keys (:checks registry))))

(defn resolve-check
  ([registry name]
   (resolve-check registry name {}))
  ([registry name context]
   (let [check (find-check registry name)
         result (find-result registry name)]
     (if (or (synchronous? check) (not result))
       ((:check-fn check) context)
       result))))

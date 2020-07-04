(ns salutem.checks
  (:require
   [clojure.core.async :as async]

   [salutem.maintenance :as maintenance]))

(defn check
  ([check-name check-fn] (check check-name check-fn {}))
  ([check-name check-fn
    {:keys [type
            timeout]
     :or   {type    :cached
            timeout 10000}}]
   {:name     check-name
    :check-fn check-fn
    :type     type
    :timeout  timeout}))

(defn cached? [check]
  (= (:type check) :cached))

(defn realtime? [check]
  (= (:type check) :realtime))

(defn attempt
  ([check] (attempt check {}))
  ([check context]
   (:result (async/<!! (maintenance/attempt check context)))))
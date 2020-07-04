(ns salutem.results
  (:require
   [tick.alpha.api :as t]))

(defn result
  ([status] (result status {}))
  ([status {:keys [evaluated-at]
            :or   {evaluated-at (t/now)}
            :as   extra-data}]
   (merge extra-data
     {:status       status
      :evaluated-at evaluated-at})))

(defn healthy
  ([] (healthy {}))
  ([opts]
   (result :healthy opts)))

(defn unhealthy
  ([] (unhealthy {}))
  ([opts]
   (result :unhealthy opts)))

(defn healthy? [result]
  (= (:status result) :healthy))

(defn unhealthy? [result]
  (= (:status result) :unhealthy))

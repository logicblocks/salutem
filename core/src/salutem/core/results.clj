(ns salutem.core.results
  "Provides constructors and predicates for check results."
  (:require
   [tick.alpha.api :as t]))

(defn result
  "Constructs a result with the provided `status`.

   The optional map of extra data is stored with the result for future use.
   Unless overridden in the extra data map, an `:salutem/evaluated-at` field is
   added to the result, set to the current date time in the system default time
   zone."
  ([status] (result status {}))
  ([status {:keys [salutem/evaluated-at]
            :or   {evaluated-at (t/now)}
            :as   extra-data}]
   (merge extra-data
     {:salutem/status       status
      :salutem/evaluated-at evaluated-at})))

(defn healthy
  "Constructs a healthy result.

   The optional map of extra data is stored with the result for future use.
   Unless overridden in the extra data map, an `:salutem/evaluated-at` field is
   added to the result, set to the current date time in the system default time
   zone."
  ([] (healthy {}))
  ([extra-data]
   (result :healthy extra-data)))

(defn unhealthy
  "Constructs an unhealthy result.

   The optional map of extra data is stored with the result for future use.
   Unless overridden in the extra data map, an `:salutem/evaluated-at` field is
   added to the result, set to the current date time in the system default time
   zone."
  ([] (unhealthy {}))
  ([extra-date]
   (result :unhealthy extra-date)))

(defn prepend [result extra-data]
  (merge extra-data result))

(defn healthy?
  "Returns `true` if the result has a `:healthy` status, `false`
   otherwise."
  [result]
  (= (:salutem/status result) :healthy))

(defn unhealthy?
  "Returns `true` if the result has an `:unhealthy` status, `false`
   otherwise."
  [result]
  (= (:salutem/status result) :unhealthy))

(defn outdated?
  "Returns `true` if the result of the check is outdated, `false`
   otherwise.

   For a realtime check, a result is always considered outdated.

   For a background check, a result is considered outdated if the
   time to re-evaluation of the check has passed, i.e., if its evaluation date
   time is before the current date time minus the check's time to re-evaluation.

   If `relative-to` is provided, the calculation is performed relative to that
   date time rather than to the current date time."
  ([result check]
   (outdated? result check (t/now)))
  ([result check relative-to]
   (or
     (= (:salutem/type check) :realtime)
     (nil? result)
     (t/< (:salutem/evaluated-at result)
       (t/- relative-to (:salutem/time-to-re-evaluation check))))))

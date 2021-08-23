(ns salutem.core.results
  "Provides constructors and predicates for check results."
  (:require
   [tick.alpha.api :as t]))

(defn result
  "Constructs a result with the provided `status`.

   The optional map of extra data is stored with the result for future use.
   Unless overridden in the extra data map, an `:evaluated-at` field is added to
   the result, set to the current date time in the system default time zone."
  ([status] (result status {}))
  ([status {:keys [evaluated-at]
            :or   {evaluated-at (t/now)}
            :as   extra-data}]
   (merge extra-data
     {:status       status
      :evaluated-at evaluated-at})))

(defn healthy
  "Constructs a healthy result.

   The optional map of extra data is stored with the result for future use.
   Unless overridden in the extra data map, an `:evaluated-at` field is added to
   the result, set to the current date time in the system default time zone."
  ([] (healthy {}))
  ([extra-data]
   (result :healthy extra-data)))

(defn unhealthy
  "Constructs an unhealthy result.

   The optional map of extra data is stored with the result for future use.
   Unless overridden in the extra data map, an `:evaluated-at` field is added to
   the result, set to the current date time in the system default time zone."
  ([] (unhealthy {}))
  ([extra-date]
   (result :unhealthy extra-date)))

(defn healthy?
  "Returns `true` if the result has a `:healthy` status, `false`
   otherwise."[result]
  (= (:status result) :healthy))

(defn unhealthy?
  "Returns `true` if the result has an `:unhealthy` status, `false`
   otherwise."
  [result]
  (= (:status result) :unhealthy))

(defn outdated?
  "Returns `true` if the result of the check is outdated, `false`
   otherwise.

   A result is considered outdated if its time-to-live (TTL) has expired,
   i.e., if its evaluation date time is before the current date time
   minus the TTL. If `relative-to` is provided, the calculation is
   performed relative to that date time rather than to the current date
   time.

   Note: the result of a realtime check is always considered outdated."
  ([result check]
   (outdated? result check (t/now)))
  ([result check relative-to]
   (or
     (= (:type check) :realtime)
     (nil? result)
     (t/< (:evaluated-at result)
       (t/- relative-to (:ttl check))))))

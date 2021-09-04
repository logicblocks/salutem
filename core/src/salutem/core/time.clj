(ns salutem.core.time
  "Provides time utilities for use in check definitions and the maintenance
   pipeline."
  (:require
   [tick.alpha.api :as t]))

(defn duration
  "Constructs an object representing a duration of time.

   This object is used to specify, for example, the time to re-evaluation and
   timeout on a check or the interval passed to a maintenance pipeline.

   Takes an amount and a unit:

     - `amount` is the length of the duration, measured in terms of the unit
     - `unit` is one of `:nanos`, `:micros`, `:millis`, `:seconds`, `:minutes`,
       `:hours`, `:half-days`, `:days`, `:weeks`, `:months`, `:years`,
       `:decades`, `:centuries`, `:millennia`, `:eras` or `:forever`

   Note: internally, this constructs a `java.time.Duration` and is merely a
   convenience function. As such, a `java.time.Duration` can be passed directly
   wherever this function would be used."
  [amount unit]
  (t/new-duration amount unit))

(ns salutem.core.time
  (:require
   [tick.alpha.api :as t]))

(defn duration [numeral unit]
  (t/new-duration numeral unit))

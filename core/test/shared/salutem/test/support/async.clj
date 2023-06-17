(ns salutem.test.support.async
  (:require
   [clojure.core.async :as async]

   [tick.core :as t]))

(defn <!!-or-timeout
  ([chan]
   (<!!-or-timeout chan (t/new-duration 100 :millis)))
  ([chan timeout]
   (async/alt!!
     chan ([v] v)
     (async/timeout (t/millis timeout))
     (throw (ex-info "Timed out waiting on channel."
              {:channel chan
               :timeout (t/millis timeout)})))))

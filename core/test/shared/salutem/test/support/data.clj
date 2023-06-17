(ns salutem.test.support.data
  (:refer-clojure :exclude [random-uuid])
  (:import
   [java.util UUID]))

(defn random-uuid []
  (str (UUID/randomUUID)))

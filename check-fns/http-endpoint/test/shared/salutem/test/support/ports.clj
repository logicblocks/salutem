(ns salutem.test.support.ports
  (:import [java.net ServerSocket]))

(defn free-port []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

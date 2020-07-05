(ns salutem.core
  (:require
   [salutem.checks :as checks]
   [salutem.results :as results]
   [salutem.registry :as registry]))

; results
(def result results/result)
(def healthy? results/healthy?)
(def unhealthy? results/unhealthy?)

; checks
(def background-check checks/background-check)
(def realtime-check checks/realtime-check)
(def background? checks/background?)
(def realtime? checks/realtime?)

; registry
(def empty-registry registry/empty-registry)

(def with-check registry/with-check)
(def with-cached-result registry/with-cached-result)

(def find-check registry/find-check)
(def find-cached-result registry/find-cached-result)

(def check-names registry/check-names)

(def resolve-check registry/resolve-check)

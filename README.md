# salutem

[![Clojars Project](https://img.shields.io/clojars/v/io.logicblocks/salutem.core.svg)](https://clojars.org/io.logicblocks/salutem.core)
[![Clojars Downloads](https://img.shields.io/clojars/dt/io.logicblocks/salutem.core.svg)](https://clojars.org/io.logicblocks/salutem.core)
[![GitHub Contributors](https://img.shields.io/github/contributors-anon/logicblocks/salutem.svg)](https://github.com/logicblocks/salutem/graphs/contributors)

A health check library for sync / async health checks.

## Install

Add the following to your `project.clj` file:

```clojure
[io.logicblocks/salutem.core "0.1.0-RC14"]
```

## Documentation

TODO

## Usage

```clojure
(require '[salutem.core :as health])

(defn database-health-check-fn
  [context callback-fn]
  (callback-fn
    (health/unhealthy
      {:error :connection-failed})))

(defn external-service-health-check-fn
  [context callback-fn]
  (callback-fn
    (health/healthy
      {:latency-ms 200})))

(def registry-atom
  (atom
    (-> (health/empty-registry)
      (health/with-check
        (health/realtime-check :database
          database-health-check-fn
          {:timeout (health/duration 5 :seconds)}))
      (health/with-check
        (health/background-check :external-service
          external-service-health-check-fn
          {:ttl (health/duration 30 :seconds)})))))

(def maintainer
  (health/maintain registry-atom))

(health/resolve-checks @registry-atom)
; => {:database
;      {:error :connection-failed
;       :status :unhealthy
;       :evaluated-at #time/instant"2021-08-18T23:39:29.234Z"}
;     :external-service 
;      {:latency-ms 200,
;       :status :healthy,
;       :evaluated-at #time/instant"2021-08-18T23:39:10.383Z"}}

; ...5 seconds later...

(health/resolve-checks @registry-atom)
; => {:database
;      {:error :connection-failed
;       :status :unhealthy
;       :evaluated-at #time/instant"2021-08-18T23:39:34.234Z"}
;     :external-service 
;      {:latency-ms 200,
;       :status :healthy,
;       :evaluated-at #time/instant"2021-08-18T23:39:10.383Z"}}

(health/shutdown maintainer)
```

## License

Copyright &copy; 2021 LogicBlocks Maintainers

Distributed under the terms of the
[MIT License](http://opensource.org/licenses/MIT).

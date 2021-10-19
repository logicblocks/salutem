# salutem

[![Clojars Project](https://img.shields.io/clojars/v/io.logicblocks/salutem.core.svg)](https://clojars.org/io.logicblocks/salutem.core)
[![Clojars Downloads](https://img.shields.io/clojars/dt/io.logicblocks/salutem.core.svg)](https://clojars.org/io.logicblocks/salutem.core)
[![GitHub Contributors](https://img.shields.io/github/contributors-anon/logicblocks/salutem.svg)](https://github.com/logicblocks/salutem/graphs/contributors)

A system for defining and maintaining a collection of health checks.

`salutem` supports:
* both realtime and background checks
* a registry for storing, finding and resolving checks
* an asynchronous maintenance system for ensuring that the results of checks 
  are kept up-to-date according to their definition

## Install

Add the following to your `project.clj` file:

```clojure
[io.logicblocks/salutem.core "0.1.6"]
```

## Documentation

* [API Docs](http://logicblocks.github.io/salutem)
* [Getting Started](https://logicblocks.github.io/salutem/getting-started.html)

## Usage

```clojure
(require '[salutem.core :as salutem])

(defn database-health-check-fn
  [context callback-fn]
  (callback-fn
    (salutem/unhealthy
      {:error :connection-failed})))

(defn external-service-health-check-fn
  [context callback-fn]
  (callback-fn
    (salutem/healthy
      {:latency-ms 200})))

(def registry-atom
  (atom
    (-> (salutem/empty-registry)
      (salutem/with-check
        (salutem/realtime-check :database
          database-health-check-fn
          {:salutem/timeout (salutem/duration 5 :seconds)}))
      (salutem/with-check
        (salutem/background-check :external-service
          external-service-health-check-fn
          {:salutem/time-to-re-evaluation (salutem/duration 30 :seconds)})))))

(def maintainer
  (salutem/maintain registry-atom))

(salutem/resolve-checks @registry-atom)
; => {:database
;      {:error :connection-failed
;       :salutem/status :unhealthy
;       :salutem/evaluated-at #time/instant"2021-08-18T23:39:29.234Z"}
;     :external-service 
;      {:latency-ms 200,
;       :salutem/status :healthy,
;       :salutem/evaluated-at #time/instant"2021-08-18T23:39:10.383Z"}}

; ...5 seconds later...

(salutem/resolve-checks @registry-atom)
; => {:database
;      {:error :connection-failed
;       :salutem/status :unhealthy
;       :salutem/evaluated-at #time/instant"2021-08-18T23:39:34.234Z"}
;     :external-service 
;      {:latency-ms 200,
;       :salutem/status :healthy,
;       :salutem/evaluated-at #time/instant"2021-08-18T23:39:10.383Z"}}

(salutem/shutdown maintainer)
```

## License

Copyright &copy; 2021 LogicBlocks Maintainers

Distributed under the terms of the
[MIT License](http://opensource.org/licenses/MIT).

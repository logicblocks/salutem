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
[io.logicblocks/salutem.core "0.1.0-RC14"]
```

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
          {:time-to-re-evaluation (health/duration 30 :seconds)})))))

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

## Documentation

Salutem allows for health checking of dependencies and other systems without 
causing circular references between services.

It does this by taking a configurable set of checks and polling them 
asynchronously on a schedule that you decide. This is then cached within 
Salutem and can be read from when needed.

Salutem also allows for synchronous checks that can be performed at the time of 
request.

The two main components of Salutem are the `registry` and the `checks`.

### Registry

The registry should be an `atom` that you maintain within your system. This is 
responsible for holding the latest state of your checks. A full example of how 
to set up a registry can be seen in [Usage](#Usage).

### Checks

Checks take a function with `context` and `callback-fn` parameters which should 
return either a `salutem.core/healthy` or `salutem.core/unhealthy` result, like 
so:

```clojure
(require '[salutem.core :as health])

(defn is-healthy?
  []
  (if (> (rand-int 10) 5)
    (health/healthy)
    (health/unhealthy)))

(defn external-service-health-check-fn
  [context callback-fn]
  (callback-fn
    (is-healthy?)))
```

These can also return additional information along with the health status by 
passing a map.

```clojure
(require '[salutem.core :as health])

(defn is-healthy?
  []
  (if (> (rand-int 10) 5)
    (health/healthy {:version 1.0})
    (health/unhealthy {:tag "@151f3575-0.01"})))
```

### Additional Callback Functions

Additionally, `maintain` can be called with a list of functions that will be 
called when the check is updated. These are passed a map by Salutem and can be 
used to update other systems with results of the checks.

```clojure
(defn print-check-data
  [data]
  (println data))

(checks/background-check
  :thing
  (fn [_ result-cb]
    (result-cb (results/healthy {:arbitrary-data "foo"}))))

(maintenance/maintain (atom registry)
  {:callback-fns [callback-fn]})

; calls print-check-data with
{:time-to-re-evaluation #time/duration "PT10S",
 :type :background,
 :name :check-name,
 :check-fn
 #object[salutem.core.maintenance_test$fn__28191$fn__28194 0x1ab86933 "salutem.core.maintenance_test$fn__28191$fn__28194@1ab86933"],
 :timeout #time/duration "PT10S",
 :arbitrary-data "pizza",
 :status :healthy,
 :evaluated-at #time/instant "2021-08-20T17:23:58.172499Z"}
```

This allows you to update logs or other systems that would benefit from having 
the information retrieved by the check, without having to do this yourself.

## License

Copyright &copy; 2021 LogicBlocks Maintainers

Distributed under the terms of the
[MIT License](http://opensource.org/licenses/MIT).

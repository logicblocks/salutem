ToDo
====

* Can build database dependency check
* Can build service dependency check
* Can run a check asynchronously

```clojure
; checks should have:
;  - timeout (always)
;  - freshness (when cacheable)
; every poll interval, run through all checks
; if any check's result is no longer fresh (result :checked-at < now - freshness),
; schedule a run of the check
; if the run of the check takes longer than the timeout, fail the check, use an
; unhealthy status
; as each check succeeds, the registry is updated with the new result

; so, in terms of functions:
;   - result
;     - outdated? - is the result too old?
;   - registry
;     - with-result - replaces the result for the check
;     - run-check - executes the check with context
;
; need one function to start a background process to keep the registry up to
; date
;   - returns a result that can be passed to stop-<whatever> to shutdown the
;     registry-refresher
;   - on a timer, put an event in a channel indicating that a registry-refresh
;     is needed
; need another function to 'tick' and perform a check of the registry, doing
; any updates necessary
;   - reads from the channel above and on each read, runs through the registry
;     to find checks needing update
;   - how do we parallelise the check evaluation?
;   - push message into channel
;
; maintain-registry (?)
; evaluate (?)
```

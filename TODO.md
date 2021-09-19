ToDo
====

* Convert keys to namespaced where relevant (e.g., `:evaluated-at`)
* Document: 
  * `checks/attempt`
  * `maintenance/maintainer`
  * `maintenance/refreshher`
  * `maintenance/evaluation-state-store`
  * `maintenance/evaluator`
  * `maintenance/updater`
  * `maintenance/notifier`
* Include context of timeout in unhealthy response on attempt timeout
* Add database check function
* Add service check function
* Add `refresh-results` function to registry namespace and expose
* Consider introducing start-channel to separate creation from execution, and 
  renaming shutdown channel to stop channel
* Consider a Completer process to capture when a trigger has fully completed

Open Questions
==============

* How should we handle checks that continuously time out?
  * This could be the responsibility of the implementer of a check function
  * We could also use exponential backoff in the maintenance pipeline
  * Might be better to leave this at the discretion of the implementer
* How would we support composite checks, where the result depends on the results
  of other checks?
  * We could build a dependency tree between checks and then manage evaluation
    of those checks based on the dependency tree
  * Alternatively, we could introduce a different check type and lazily resolve 
    those checks at resolution time 
  * This would likely still require a dependency tree to prevent re-evaluating
    realtime checks many times

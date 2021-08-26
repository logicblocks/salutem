# Getting Started

`salutem` is a system for defining and maintaining a collection of 
health checks with support for:

* both realtime and background checks,
* a registry for storing, finding and resolving checks, and
* an asynchronous maintenance system for ensuring that the results of checks
  are kept up-to-date according to their definition, notifying on the results of 
  those checks as needed.

`salutem` is somewhat inspired by 
[dropwizard-health](https://github.com/dropwizard/dropwizard-health) which may 
provide additional insight into its design.

## Contents

- [Installation](#installation)
- [Definitions](#definitions)

## Installation

Add the following to your `project.clj` file:

```clojure
[io.logicblocks/salutem.core "0.1.1"]
```

## Definitions

`salutem` introduces some domain terminology which we use throughout this 
guide. The following domain model and definitions detail the domain.

<img 
  src="images/domain-model.png"
  alt="Domain Model"
  style="width: 100%; max-width: 680px;"/>

* A ___Check___ is identified by its name and includes a function that performs 
  the corresponding health check. Checks have a timeout such that if the health 
  check takes too long, it can be aborted.
* Checks produce ___Results___ when they are evaluated, indicating the outcome 
  of the health check. Results have a status, with built-in support for 
  _healthy_ and _unhealthy_ results. Results also keep track of the instant at 
  which evaluation occurred. Results can also include arbitrary extra data for 
  storing and other required health check information.
* There are currently two types of checks supported, _BackgroundChecks_ and
  _RealtimeChecks_.
* A ___BackgroundCheck___ is intended to be evaluated in the background 
  periodically such that a cached value is returned whenever the Check is 
  resolved.
* A ___RealtimeCheck___ is evaluated every time it is resolved, with no caching
  of Results taking place.
* A ___Registry___ stores a collection of Checks along with any previously 
  generated Results that should be cached.

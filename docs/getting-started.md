# Getting Started

`salutem` is a system for defining and maintaining a collection of 
health checks with support for:

* both realtime and background checks,
* a registry for storing, finding and resolving checks, and
* an asynchronous maintenance system for ensuring that the results of checks
  are kept up-to-date according to their definition.

`salutem` is somewhat inspired by 
[dropwizard-health](https://github.com/dropwizard/dropwizard-health) which may 
provide additional insight into its design.

## Contents

- [Installation](#installation)

## Installation

Add the following to your `project.clj` file:

```clojure
[io.logicblocks/salutem.core "0.1.1"]
```

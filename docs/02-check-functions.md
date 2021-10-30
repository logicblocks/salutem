# Check Functions

`salutem` includes a set of standard check functions for commonly checked 
things. Currently, `salutem` includes check functions for:

* data sources; and
* HTTP endpoints.

## Contents

- [HTTP endpoint check function](#http-endpoint-check-function)
    - [Installation](#http-endpoint-check-function-installation)
    - [Usage](#http-endpoint-check-function-usage)
- [Data source check function](#data-source-check-function)
    - [Installation](#data-source-check-function-installation)
    - [Usage](#data-source-check-function-usage)

## HTTP endpoint check function

The HTTP endpoint check function is highly configurable allowing it to support
most types of HTTP endpoint.

### <span id="http-endpoint-check-function-installation">Installation</span>

To install the check function, add the following to your `project.clj` file:

```clojure
[io.logicblocks/salutem.check-fns.http-endpoint "0.1.7"]
```

### <span id="http-endpoint-check-function-usage">Usage</span>

The HTTP endpoint check function supports:

* changing the request method;
*


## Data source check function

The data source check function is highly configurable and supports any 
[javax.sql.DataSource](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/javax/sql/DataSource.html).

### <span id="data-source-check-function-installation">Installation</span>

To install the check function, add the following to your `project.clj` file:

```clojure
[io.logicblocks/salutem.check-fns.data-source "0.1.7"]
```

### <span id="data-source-check-function-usage">Usage</span>

The data source check function supports:

*

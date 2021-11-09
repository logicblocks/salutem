# Check Functions

`salutem` includes a set of standard check functions for commonly checked
things. Currently, `salutem` includes check functions for:

* data sources; and
* HTTP endpoints.

Each check function is packaged as a separate module to `salutem.core` since 
they have a number of dependencies that aren't needed for core `salutem` 
operation. See the installation instructions for each for more details. 

## Contents

- [HTTP endpoint check function](#http-endpoint-check-function)
    - [Installation](#hecf-installation)
    - [Usage](#hecf-usage)
        - [Customising the request](#customising-the-request)
        - [Customising timeouts](#hecf-customising-timeouts)
        - [Customising response success determination](#customising-response-success-determination)
        - [Customising failure reason determination](#hecf-customising-failure-reason-determination)
        - [Customising result generation](#hecf-customising-result-generation)
        - [Customising advanced HTTP client options](#customising-advanced-http-client-options)
        - [Logging during execution](#hecf-logging-during-execution)
- [Data source check function](#data-source-check-function)
    - [Installation](#dscf-installation)
    - [Usage](#dscf-usage)
        - [Customising the query](#customising-the-query)
        - [Customising timeouts](#dscf-customising-timeouts)
        - [Customising failure reason determination](#dscf-customising-failure-reason-determination)
        - [Customising result generation](#dscf-customising-result-generation)
        - [Logging during execution](#dscf-logging-during-execution)

## HTTP endpoint check function

The HTTP endpoint check function is highly configurable allowing it to support
most types of HTTP endpoint. Specifically, the HTTP endpoint check function
allows configuration of:

* the request method, body, headers and query string;
* timeouts (connection request, connection and socket);
* what constitutes a successful response;
* the failure reason determination for any thrown exceptions;
* the functions used to generate results; and
* many more advanced HTTP client options.

### <span id="hecf-installation">Installation</span>

To install the check function module, add the following to your `project.clj`
file:

```clojure
[io.logicblocks/salutem.check-fns.http-endpoint "0.1.7"]
```

### <span id="hecf-usage">Usage</span>

To create a check, using an HTTP endpoint check function, of a hypothetical
external user profile service:

```clojure
(require '[salutem.core :as salutem])
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])

(def user-profile-service-ping-url
  "https://user-profile-service.example.com/ping")

(def user-profile-service-check
  (salutem/background-check
    :services/user-profile
    (salutem-http/http-endpoint-check-fn
      user-profile-service-ping-url)))
```

By default, the check function will:

* be a `GET` request with no body, request headers or query parameters;
* use connection request, connection and socket timeouts of 5 seconds;
* treat responses with standard 200 and 300 status codes as healthy and all
  others as unhealthy; and
* include nothing from the request or response in the produced result.

#### Customising the request

##### Method

To change the request method for the check, pass the `:method` option:

```clojure
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])

(def check-fn
  (salutem-http/http-endpoint-check-fn
    "https://user-profile-service.example.com/ping"
    {:method :head}))
```

The `:method` option supports `:get`, `:head`, `:post`, `:put`, `:delete`,
`:options`, `:copy`, `:move` and `:patch`.

If the `:method` option is instead a function, it will be called with the
context map at execution time in order to obtain the method to use.

##### Body

To set a body on the request, pass the `:body` option:

```clojure
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])

(def check-fn
  (salutem-http/http-endpoint-check-fn
    "https://user-profile-service.example.com/ping"
    {:method :post
     :body   "{\"status\": \"listening\"}"}))
```

The `:body` option accepts anything supported by
[`clj-http`](https://github.com/dakrone/clj-http). Just as for `:method`, if the
`:body` function is instead a function, it will be called with the context map
at execution time in order to obtain the body to use.

##### Headers

To set headers on the request, pass the `:headers` option:

```clojure
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])

(def api-key "ffa55748904f4545de55751e9bd2c5abb45596bd")

(def check-fn
  (salutem-http/http-endpoint-check-fn
    "https://user-profile-service.example.com/ping"
    {:headers {:authorization (str "Bearer " api-key)}}))
```

The `:headers` option accepts anything supported by
[`clj-http`](https://github.com/dakrone/clj-http) and can be a function of
context as with the other request options.

##### Query parameters

To set query parameters on the endpoint URL, pass the `:query-params` option:

```clojure
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])

(def api-key "ffa55748904f4545de55751e9bd2c5abb45596bd")

(def check-fn
  (salutem-http/http-endpoint-check-fn
    "https://user-profile-service.example.com/ping"
    {:query-params {"api_key" api-key}}))
```

The `:query-params` option accepts anything supported by
[`clj-http`](https://github.com/dakrone/clj-http) and can be a function of
context as with the other request options.

#### <span id="hecf-customising-timeouts">Customising timeouts</span>

The HTTP check function supports the same three timeouts as
[`clj-http`](https://github.com/dakrone/clj-http):

* `:connection-request-timeout`: the amount of time to wait when obtaining a
  connection from the connection manager before considering the request failed;
  useful when using a pooled connection manager.
* `:connection-timeout`: the amount of time to wait when establishing an HTTP
  connection before considering the request failed.
* `:socket-timeout`: the amount of time to wait while streaming response data
  since the last data was received before considering the request failed.

To use different durations for each of the timeouts:

```clojure
(require '[salutem.core :as salutem])
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])

(def api-key "ffa55748904f4545de55751e9bd2c5abb45596bd")

(def check-fn
  (salutem-http/http-endpoint-check-fn
    "https://user-profile-service.example.com/ping"
    {:connection-request-timeout (salutem/duration 10 :seconds)
     :connection-timeout         (salutem/duration 20 :seconds)
     :socket-timeout             (salutem/duration 500 :millis)}))
```

#### Customising response success determination

By default, the check function configures 
[`clj-http`](https://github.com/dakrone/clj-http) (the underlying HTTP client)
not to throw exceptions when a response has status codes representing a failed
request. Instead, it uses a function to determine whether the response 
represents success and therefore a healthy dependency. 

The default function used to determine if the response represents success is
[[salutem.check-fns.http-endpoint.core/successful?]]. To determine success
differently, pass the `:successful-response-fn` as a function of context and
the received response:

```clojure
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])

(def check-fn
  (salutem-http/http-endpoint-check-fn
    "https://user-profile-service.example.com/ping"
    {:successful-response-fn
     (fn [context response]
       (contains? (:successful-statuses context) (:status response)))}))
```

This function is used by the function which generates results for responses
(see [below](#customising-result-generation)) so in the case that you only need
to override the statuses that constitute a healthy vs. unhealthy result, it is 
sufficient to set `:successful-response-fn` alone.

#### <span id="hecf-customising-failure-reason-determination">Customising failure reason determination</span>

When an exception occurs during check execution, `salutem` results typically
include both a `:salutem/exception` entry containing the exception and a
`:salutem/reason` entry detailing the failure reason. By default, the possible
reasons are `:timed-out` for exceptions indicating timeout and
`:threw-exception` for all other exceptions.

To use a custom function to determine the reason for a failure, pass the
`:failure-reason-fn` option as a function of context and the thrown exception:

```clojure
(require '[salutem.core :as salutem])
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])
(import '[my.corp ServiceMaintenanceException])
(import '[org.apache.http.conn ConnectTimeoutException])
(import '[java.net SocketTimeoutException ConnectException])

(def check-fn
  (salutem-http/http-endpoint-check-fn
    "https://user-profile-service.example.com/ping"
    {:failure-reason-fn
     (fn [_ exception]
       (let [exception-class (class exception)
             exception-message (ex-message exception)
             contains-timeout (re-matches #".*Timeout.*" exception-message)]
         (cond
           (isa? exception-class ServiceMaintenanceException)
           :offline-for-maintenance

           (or
             (isa? exception-class ConnectTimeoutException)
             (isa? exception-class SocketTimeoutException)
             (and (isa? exception-class ConnectException) contains-timeout))
           :timed-out

           :else
           :threw-exception)))}))
```

Note that the failure reason function is also used to determine the reason to
include in log events produced by the check function. See
[Logging during execution](#hecf-logging-during-execution)
for more details on what gets logged by the check function.

#### <span id="hecf-customising-result-generation">Customising result generation</span>

Whilst `:successful-response-fn` and `:failure-reason-fn` can influence how
results are generated for responses and exceptions, sometimes you may want to
completely override the result generation. Two options control the generation of
results in the check function, one for when a response is received and one for 
when an exception occurs.

##### Response result generation

To change how results are generated when a response is received, pass the
`:response-result-fn` option as a function of context and the received response:

```clojure
(require '[salutem.core :as salutem])
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])

(def check-fn
  (salutem-http/http-endpoint-check-fn
    "https://user-profile-service.example.com/ping"
    {:response-result-fn
     (fn [context response]
       (if (= 200 (:status response))
         (salutem/healthy
           (select-keys context [:correlation-id]))
         (salutem/unhealthy
           (merge
             (select-keys context [:correlation-id])
             {:salutem/reason :bad-status-code}))))}))
```

The default response result function uses
[[salutem.check-fns.http-endpoint.core/successful?]] to determine if the
response should be treated as healthy or unhealthy. If you wish to use the same
success semantics and instead only change the content of the result, you can use
the same function inside your response result function.

##### Exception result generation

To change how results are generated when an exception occurs, pass the
`:exception-result-fn` option as a function of context and the thrown exception:

```clojure
(require '[salutem.core :as salutem])
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])

(def check-fn
  (salutem-http/http-endpoint-check-fn
    "https://user-profile-service.example.com/ping"
    {:exception-result-fn
     (fn [context exception]
       (let [reason (get (ex-data exception) :reason :threw-exception)
             correlation-id (get context :correlation-id)]
         (salutem/unhealthy
           {:correlation-id    correlation-id
            :salutem/reason    reason
            :salutem/exception exception})))}))
```

The default exception result function uses
[[salutem.check-fns.http-endpoint.core/failure-reason]] to determine the
`:salutem/reason` to include in the result. If you wish to use the same failure
reason determination and instead only change the content of the result, you can
use the same function inside your exception result function.

#### Customising advanced HTTP client options

As previously mentioned, under the covers, the HTTP endpoint check function 
uses [`clj-http`](https://github.com/dakrone/clj-http) to perform HTTP requests.
The check function exposes an `:opts` option allowing all other 
[`clj-http`](https://github.com/dakrone/clj-http) options to be overridden, 
except for:

* the `:async?` option, which is always `true`; and
* the timeout options, which are provided directly to the check function.

For example, to use a specific connection manager:

```clojure
(require '[salutem.check-fns.http-endpoint.core :as salutem-http])
(require '[clj-http.conn-mgr :as conn-mgr])

(def connection-manager
  (conn-mgr/make-reusable-async-conn-manager {}))

(def check-fn
  (salutem-http/http-endpoint-check-fn
    "https://user-profile-service.example.com/ping"
    {:opts {:connection-manager connection-manager}}))
```

#### <span id="hecf-logging-during-execution">Logging during execution</span>

Just as for `salutem.core`, if the context map provided to the check function
includes a `:logger` entry with a
[`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
value, log events will be produced throughout execution.

The events that may be logged during execution are:

- `:salutem.check-fns.http-endpoint/check.starting{:url, :method, :body, :headers, :query-params}`
- `:salutem.check-fns.http-endpoint/check.successful{}`
- `:salutem.check-fns.http-endpoint/check.failed{:reason, :exception}`

## Data source check function

The data source check function is highly configurable and supports any
[javax.sql.DataSource](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/javax/sql/DataSource.html)
.

### <span id="dscf-installation">Installation</span>

To install the check function module, add the following to your `project.clj`
file:

```clojure
[io.logicblocks/salutem.check-fns.data-source "0.1.7"]
```

### <span id="dscf-usage">Usage</span>

To create a check, using a data source check function, of a hypothetical
H2 database instance:

```clojure
(require '[next.jdbc :as jdbc])
(require '[salutem.core :as salutem])
(require '[salutem.check-fns.data-source.core :as salutem-ds])

(def data-source 
  (jdbc/get-datasource 
    {:dbtype "h2mem"
     :dbname "datastore"}))

(def data-source-check
  (salutem/background-check
    :persistence/datastore
    (salutem-ds/data-source-check-fn data-source)))
```

By default, the check function will:

* query the datasource with `"SELECT 1 AS up;"`;
* convert the resulting result set to a map with unqualified kebab-cased keys;
* timeout the query after 5 seconds;
* treat any query result as an indication of a healthy dependency; 
* treat any exception as an indication of an unhealthy dependency; and
* include the first record in the result set in the response.

#### Customising the query

##### Query SQL parameters

Whilst the default query is adequate for many databases, it isn't supported by
all. You may also want to execute a query more specific to your context to 
include additional information in the result.

To configure the query used by the check function, pass the `:query-sql-params`
option:

```clojure
(require '[next.jdbc :as jdbc])
(require '[salutem.check-fns.data-source.core :as salutem-ds])

(def data-source 
  (jdbc/get-datasource 
    {:dbtype "h2mem"
     :dbname "datastore"}))

(def check-fn
  (salutem-ds/data-source-check-fn data-source
    {:query-sql-params ["SELECT H2VERSION() AS version FROM DUAL"]}))
```

The value for the `:query-sql-params` option is a SQL parameter vector as 
defined in [`next.jdbc`](https://github.com/seancorfield/next-jdbc), i.e., it
can contain parameters to interpolate into the query string.

The query is executed using 
[`next.jdbc`](https://github.com/seancorfield/next-jdbc)'s `execute!` function
such that the query can result in a result set with many records if required.
However, bear in mind that the default `:query-results-result-fn` includes only
the first record in the result set. In order to return a result that utilises
all records, you need to override the default result function.

If the value for `:query-sql-params` is instead a function, it will be called
with the context map in order to obtain the SQL parameter vector, allowing for
parameters to be supplied to the query at execution time.

##### Query options

The check function allows additional query options to be configured, as allowed
by [`next.jdbc`](https://github.com/seancorfield/next-jdbc)'s `execute!` 
function. To configure the query options used when executing the query and 
interpreting its results, pass the `:query-opts` option:

```clojure
(require '[next.jdbc :as jdbc])
(require '[next.jdbc.result-set :as jdbc-rs])
(require '[salutem.check-fns.data-source.core :as salutem-ds])

(def data-source 
  (jdbc/get-datasource 
    {:dbtype "h2mem"
     :dbname "datastore"}))

(def check-fn
  (salutem-ds/data-source-check-fn data-source
    {:query-opts {:builder-fn jdbc-rs/as-arrays}}))
```

By default, `:query-opts` includes a `:builder-fn` option which converts the 
result set to a vector of maps with unqualified kebab-case keys. All options
supported by [`next.jdbc`](https://github.com/seancorfield/next-jdbc) can be
provided, except `:timeout` which is always provided via the `:query-timeout`
option of the check function.

If the value for `:query-opts` is instead a function, it will be called with 
the context map in order to obtain the query options, allowing for options to be
supplied to the check function at execution time.

#### <span id="dscf-customising-timeouts">Customising timeouts</span>

By default, the check function uses a query timeout of 5 seconds. To use a
different duration, pass the `:query-timeout` option:

```clojure
(require '[next.jdbc :as jdbc])
(require '[salutem.core :as salutem])
(require '[salutem.check-fns.data-source.core :as salutem-ds])

(def data-source 
  (jdbc/get-datasource 
    {:dbtype "h2mem"
     :dbname "datastore"}))

(def check-fn
  (salutem-ds/data-source-check-fn data-source
    {:query-timeout (salutem/duration 1 :seconds)}))
```

As for other options, if the value for `:query-timeout` is instead a function, 
it will be called with the context map in order to obtain the query timeout at 
execution time.

Other timeouts, such as the connection timeout, login timeout or socket timeout,
should be configured on the data source directly. As such, the check function
doesn't provide any facility to change them.

#### <span id="dscf-customising-failure-reason-determination">Customising failure reason determination</span>

When an exception occurs during check execution, `salutem` results typically
include both a `:salutem/exception` entry containing the exception and a
`:salutem/reason` entry detailing the failure reason. By default, the possible
reasons are `:timed-out` for exceptions indicating timeout and
`:threw-exception` for all other exceptions.

To use a custom function to determine the reason for a failure, pass the
`:failure-reason-fn` option as a function of context and the thrown exception:

```clojure
(require '[next.jdbc :as jdbc])
(require '[salutem.core :as salutem])
(require '[salutem.check-fns.data-source.core :as salutem-ds])
(import '[my.corp MissingPeerException])
(import '[java.sql SQLTimeoutException])

(def data-source
  (jdbc/get-datasource
    {:dbtype "h2mem"
     :dbname "datastore"}))

(def check-fn
  (salutem-ds/data-source-check-fn data-source
    {:failure-reason-fn
     (fn [_ exception]
       (let [exception-class (class exception)]
         (cond
           (isa? exception-class MissingPeerException)
           :cluster-unhealthy

           (isa? exception-class SQLTimeoutException)
           :timed-out

           :else
           :threw-exception)))}))
```

Note that the failure reason function is also used to determine the reason to
include in log events produced by the check function. See
[Logging during execution](#dscf-logging-during-execution)
for more details on what gets logged by the check function.

#### <span id="dscf-customising-result-generation">Customising result generation</span>

Whilst the `:query-sql-params`, `:query-opts` and `:failure-reason-fn` options 
can influence how results are generated for query results and exceptions, 
sometimes you may want to completely override the result generation. Two options
control the generation of results in the check function, one for when query
results are received and one for when an exception occurs.

##### Query results result generation

To change how results are generated when query results are received, pass the
`:query-results-result-fn` option as a function of context and the received 
query results:

```clojure
(require '[next.jdbc :as jdbc])
(require '[salutem.core :as salutem])
(require '[salutem.check-fns.data-source.core :as salutem-ds])

(def data-source
  (jdbc/get-datasource
    {:dbtype "h2mem"
     :dbname "datastore"}))

(def check-fn
  (salutem-ds/data-source-check-fn data-source
    {:query-sql-params ["SELECT H2VERSION() FROM DUAL;"]
     :query-results-result-fn
     (fn [context results]
       (let [version (:h2version (first results))]
         (if (= version (:required-database-version context))
           (salutem/healthy {:version version})
           (salutem/unhealthy 
             {:salutem/reason :incorrect-database-version
              :version version}))))}))
```

##### Exception result generation

To change how results are generated when an exception occurs, pass the
`:exception-result-fn` option as a function of context and the thrown exception:

```clojure
(require '[next.jdbc :as jdbc])
(require '[salutem.core :as salutem])
(require '[salutem.check-fns.data-source.core :as salutem-ds])

(def data-source
  (jdbc/get-datasource
    {:dbtype "h2mem"
     :dbname "datastore"}))

(def check-fn
  (salutem-ds/data-source-check-fn data-source
    {:exception-result-fn
     (fn [context exception]
       (let [reason (get (ex-data exception) :reason :threw-exception)
             correlation-id (get context :correlation-id)]
         (salutem/unhealthy
           {:correlation-id    correlation-id
            :salutem/reason    reason
            :salutem/exception exception})))}))
```

The default exception result function uses
[[salutem.check-fns.data-source.core/failure-reason]] to determine the
`:salutem/reason` to include in the result. If you wish to use the same failure
reason determination and instead only change the content of the result, you can
use the same function inside your exception result function.

#### <span id="dscf-logging-during-execution">Logging during execution</span>

Just as for `salutem.core`, if the context map provided to the check function
includes a `:logger` entry with a
[`cartus.core/Logger`](https://logicblocks.github.io/cartus/cartus.core.html#var-Logger)
value, log events will be produced throughout execution.

The events that may be logged during execution are:

- `:salutem.check-fns.data-source/check.starting{:query-sql-params}`
- `:salutem.check-fns.data-source/check.successful{}`
- `:salutem.check-fns.data-source/check.failed{:reason,:exception}`

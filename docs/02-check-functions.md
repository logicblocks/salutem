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
most types of HTTP endpoint. Specifically, the HTTP endpoint check function 
allows configuration of:

* the request method, body, headers and query string;
* request timeouts (connection request, connection and socket);
* what constitutes a successful response;
* the underlying reason for any possible exception;
* the functions used to generate results; and
* many more advanced HTTP client options.

### <span id="http-endpoint-check-function-installation">Installation</span>

To install the check function module, add the following to your `project.clj` 
file:

```clojure
[io.logicblocks/salutem.check-fns.http-endpoint "0.1.7"]
```

### <span id="http-endpoint-check-function-usage">Usage</span>

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
     :body "{\"status\": \"listening\"}"}))
```

The `:body` option accepts anything supported by 
[`clj-http`](https://github.com/dakrone/clj-http). Just as for `:method`, if the
`:body` function is instead a function, it will be called with the
context map at execution time in order to obtain the body to use.

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

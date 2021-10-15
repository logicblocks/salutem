(ns salutem.check-fns.http-endpoint.core
  (:require
   [clj-http.client :as http]

   [salutem.core :as salutem]))

(defn http-endpoint-check-fn
  ([endpoint-uri] (http-endpoint-check-fn endpoint-uri {}))
  ([endpoint-uri
    {:keys [success-fn]
     :or   {success-fn
            (fn [{:keys [status]}]
              (http/unexceptional-status? status))}}]
   (fn [context result-cb]
     (try
       (let [response (http/get endpoint-uri)]
         (result-cb
           (if (success-fn response)
             (salutem/healthy)
             (salutem/unhealthy))))
       (catch Exception _
         (result-cb
           (salutem/unhealthy)))))))

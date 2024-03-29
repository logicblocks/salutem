(ns salutem.core.registry-test
  (:require
   [clojure.test :refer :all]
   [clojure.core.async :as async]

   [spy.core :as spy]
   [tick.core :as t]

   [salutem.test.support.data :as data]

   [salutem.core.time :as time]
   [salutem.core.checks :as checks]
   [salutem.core.results :as results]
   [salutem.core.registry :as registry]
   [salutem.test.support.time :as tst]))

(deftest with-check-adds-single-check-to-registry
  (let [check (checks/realtime-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy))))
        registry (registry/with-check (registry/empty-registry) check)]
    (is (= check (registry/find-check registry :thing)))))

(deftest with-check-adds-many-checks-to-registry
  (let [check-1 (checks/realtime-check :thing-1
                  (fn [_ result-cb]
                    (result-cb (results/healthy))))
        check-2 (checks/background-check :thing-2
                  (fn [_ result-callback]
                    (result-callback (results/unhealthy))))

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2))]
    (is (= #{:thing-1 :thing-2} (registry/check-names registry)))))

(deftest find-check-returns-nil-when-no-check-available
  (let [registry (registry/empty-registry)]
    (is (nil? (registry/find-check registry :no-check)))))

(deftest find-check-returns-check-when-check-in-registry
  (let [check (checks/realtime-check :check
                (fn [_ cb] (cb (results/healthy))))
        registry (-> (registry/empty-registry)
                   (registry/with-check check))]
    (is (= check (registry/find-check registry :check)))))

(deftest check-names-returns-empty-set-when-no-checks
  (let [registry (registry/empty-registry)]
    (is (= #{} (registry/check-names registry)))))

(deftest check-names-returns-set-of-check-names-when-checks-present
  (let [check-1 (checks/realtime-check :check-1
                  (fn [_ cb] (cb (results/healthy))))
        check-2 (checks/realtime-check :check-2
                  (fn [_ cb] (cb (results/unhealthy))))
        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2))]
    (is (= #{:check-1 :check-2} (registry/check-names registry)))))

(deftest outdated-checks-returns-single-outdated-check
  (let [check-1-name :thing-1
        check-2-name :thing-2

        check-1 (checks/background-check check-1-name
                  (fn [_ result-cb]
                    (result-cb (results/healthy)))
                  {:salutem/time-to-re-evaluation (time/duration 30 :seconds)})
        check-2 (checks/background-check check-2-name
                  (fn [_ result-cb]
                    (result-cb (results/unhealthy)))
                  {:salutem/time-to-re-evaluation (time/duration 5 :minutes)})

        outdated-check-1-result
        (results/healthy
          {:salutem/evaluated-at (t/<< (t/now) (t/new-duration 35 :seconds))})
        current-check-2-result
        (results/healthy
          {:salutem/evaluated-at (t/<< (t/now) (t/new-duration 10 :seconds))})

        registry
        (-> (registry/empty-registry)
          (registry/with-check check-1)
          (registry/with-check check-2)
          (registry/with-cached-result check-1-name outdated-check-1-result)
          (registry/with-cached-result check-2-name current-check-2-result))

        outdated-checks (registry/outdated-checks registry)]
    (is (= #{check-1} outdated-checks))))

(deftest outdated-checks-returns-many-outdated-checks
  (let [check-1-name :thing-1
        check-2-name :thing-2
        check-3-name :thing-3

        check-1 (checks/background-check check-1-name
                  (fn [_ result-cb]
                    (result-cb (results/healthy)))
                  {:salutem/time-to-re-evaluation (time/duration 30 :seconds)})
        check-2 (checks/background-check check-2-name
                  (fn [_ result-cb]
                    (result-cb (results/unhealthy)))
                  {:salutem/time-to-re-evaluation (time/duration 5 :minutes)})
        check-3 (checks/background-check check-3-name
                  (fn [_ result-cb]
                    (result-cb (results/healthy)))
                  {:salutem/time-to-re-evaluation (time/duration 1 :minutes)})

        outdated-check-1-result
        (results/healthy
          {:salutem/evaluated-at (t/<< (t/now) (t/new-duration 35 :seconds))})
        current-check-2-result
        (results/healthy
          {:salutem/evaluated-at (t/<< (t/now) (t/new-duration 10 :seconds))})
        outdated-check-3-result
        (results/healthy
          {:salutem/evaluated-at (t/<< (t/now) (t/new-duration 2 :minutes))})

        registry
        (-> (registry/empty-registry)
          (registry/with-check check-1)
          (registry/with-check check-2)
          (registry/with-check check-3)
          (registry/with-cached-result check-1-name outdated-check-1-result)
          (registry/with-cached-result check-2-name current-check-2-result)
          (registry/with-cached-result check-3-name outdated-check-3-result))

        outdated-checks (registry/outdated-checks registry)]
    (is (= #{check-1 check-3} outdated-checks))))

(deftest outdated-checks-treats-background-checks-without-result-as-outdated
  (let [check-1-name :thing-1
        check-2-name :thing-2
        check-3-name :thing-3

        check-1 (checks/background-check check-1-name
                  (fn [_ result-cb]
                    (result-cb (results/healthy)))
                  {:salutem/time-to-re-evaluation (time/duration 30 :seconds)})
        check-2 (checks/background-check check-2-name
                  (fn [_ result-cb]
                    (result-cb (results/unhealthy)))
                  {:salutem/time-to-re-evaluation (time/duration 5 :minutes)})
        check-3 (checks/background-check check-3-name
                  (fn [_ result-cb]
                    (result-cb (results/healthy)))
                  {:salutem/time-to-re-evaluation (time/duration 1 :minutes)})

        current-check-2-result
        (results/healthy
          {:salutem/evaluated-at (t/<< (t/now) (t/new-duration 10 :seconds))})

        registry
        (-> (registry/empty-registry)
          (registry/with-check check-1)
          (registry/with-check check-2)
          (registry/with-check check-3)
          (registry/with-cached-result check-2-name current-check-2-result))

        outdated-checks (registry/outdated-checks registry)]
    (is (= #{check-1 check-3} outdated-checks))))

(deftest resolve-check-evaluates-realtime-check-every-time
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (results/healthy))))
        check (checks/realtime-check :thing check-fn)
        registry (registry/with-check (registry/empty-registry) check)

        resolved-result-1 (registry/resolve-check registry :thing)
        _ (Thread/sleep 10)
        resolved-result-2 (registry/resolve-check registry :thing)]
    (is (= (count (spy/calls check-fn)) 2))
    (is (results/healthy? resolved-result-1))
    (is (results/healthy? resolved-result-2))
    (is (t/>
          (:salutem/evaluated-at resolved-result-2)
          (:salutem/evaluated-at resolved-result-1)))))

(deftest resolve-check-evaluates-background-check-when-no-cached-result
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (results/unhealthy))))
        check (checks/background-check :thing check-fn)
        registry (registry/with-check (registry/empty-registry) check)
        resolved-result (registry/resolve-check registry :thing)]
    (is (= (count (spy/calls check-fn)) 1))
    (is (results/unhealthy? resolved-result))))

(deftest resolve-check-resolves-background-check-as-cached-result-if-available
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (results/unhealthy))))
        check-name :thing
        check (checks/background-check check-name check-fn)
        cached-result (results/healthy)
        registry (-> (registry/empty-registry)
                   (registry/with-check check)
                   (registry/with-cached-result check-name cached-result))
        resolved-result (registry/resolve-check registry check-name)]
    (is (= (count (spy/calls check-fn)) 0))
    (is (results/healthy? resolved-result))))

(deftest
  resolve-check-resolves-realtime-check-asynchronously-when-passed-callback
  (let [context {:caller :thing-consumer}

        unique-id (data/random-uuid)

        check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb
                     (results/healthy
                       {:caller    (:caller context)
                        :unique-id unique-id})))
        check (checks/realtime-check check-name check-fn)
        registry (registry/with-check (registry/empty-registry) check)

        result-atom (atom nil)
        callback-fn (fn [result]
                      (reset! result-atom result))]
    (registry/resolve-check registry check-name context callback-fn)

    (loop [attempts 1]
      (if (not (nil? @result-atom))
        (is (= (tst/without-timings @result-atom)
              (tst/without-timings
                (results/healthy
                  {:caller    :thing-consumer
                   :unique-id unique-id}))))
        (if (< attempts 5)
          (do
            (async/<!! (async/timeout 25))
            (recur (inc attempts)))
          (throw (ex-info "Callback function was not called before timeout."
                   {:check check})))))))

(deftest resolve-check-resolves-background-check-with-cached-result-via-callback
  (let [context {}

        old-unique-id (data/random-uuid)
        fresh-unique-id (data/random-uuid)

        check-name :thing
        check-fn (fn [_ result-cb]
                   (result-cb
                     (results/healthy
                       {:unique-id fresh-unique-id})))
        check (checks/background-check check-name check-fn)
        cached-result (results/healthy
                        {:unique-id old-unique-id})
        registry (-> (registry/empty-registry)
                   (registry/with-check check)
                   (registry/with-cached-result check-name cached-result))

        result-atom (atom nil)
        callback-fn (fn [result]
                      (reset! result-atom result))]
    (registry/resolve-check registry check-name context callback-fn)

    (loop [attempts 1]
      (if (not (nil? @result-atom))
        (is (= (tst/without-timings @result-atom)
              (tst/without-timings
                (results/healthy
                  {:unique-id old-unique-id}))))
        (if (< attempts 5)
          (do
            (async/<!! (async/timeout 25))
            (recur (inc attempts)))
          (throw (ex-info "Callback function was not called before timeout."
                   {:check check})))))))

(deftest resolve-checks-resolves-all-checks-in-the-registry
  (let [result-1 (results/unhealthy)
        result-2 (results/healthy)

        check-1-name :thing-1
        check-2-name :thing-2

        check-1 (checks/background-check check-1-name
                  (fn [_ result-cb]
                    (result-cb result-1)))
        check-2 (checks/realtime-check check-2-name
                  (fn [_ result-cb]
                    (result-cb result-2)))

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2))

        results (registry/resolve-checks registry)]
    (is (= {check-1-name (tst/without-timings result-1)
            check-2-name (tst/without-timings result-2)}
          (into {}
            (map
              (fn [[name result]] [name (tst/without-timings result)])
              results))))))

(deftest resolve-checks-resolves-all-realtime-checks-every-time
  (let [check-fn-1 (spy/spy
                     (fn [_ result-cb]
                       (result-cb (results/healthy))))
        check-fn-2 (spy/spy
                     (fn [_ result-cb]
                       (result-cb (results/healthy))))

        check-1 (checks/realtime-check :thing-1 check-fn-1)
        check-2 (checks/realtime-check :thing-2 check-fn-2)

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2))

        resolved-results-1 (registry/resolve-checks registry)
        _ (Thread/sleep 10)
        resolved-results-2 (registry/resolve-checks registry)]
    (is (= (count (spy/calls check-fn-1)) 2))
    (is (results/healthy? (:thing-1 resolved-results-1)))
    (is (results/healthy? (:thing-2 resolved-results-1)))

    (is (= (count (spy/calls check-fn-2)) 2))
    (is (results/healthy? (:thing-1 resolved-results-2)))
    (is (results/healthy? (:thing-2 resolved-results-2)))

    (is (t/>
          (get-in resolved-results-2 [:thing-1 :salutem/evaluated-at])
          (get-in resolved-results-1 [:thing-1 :salutem/evaluated-at])))
    (is (t/>
          (get-in resolved-results-2 [:thing-2 :salutem/evaluated-at])
          (get-in resolved-results-1 [:thing-2 :salutem/evaluated-at])))))

(deftest resolve-checks-resolves-background-checks-when-no-cached-results
  (let [check-fn-1 (spy/spy
                     (fn [_ result-cb]
                       (result-cb (results/unhealthy))))
        check-fn-2 (spy/spy
                     (fn [_ result-cb]
                       (result-cb (results/healthy))))

        check-1-name :thing-1
        check-2-name :thing-2

        check-1 (checks/background-check check-1-name check-fn-1)
        check-2 (checks/background-check check-2-name check-fn-2)

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2))

        resolved-results (registry/resolve-checks registry)]
    (is (= (count (spy/calls check-fn-1)) 1))
    (is (= (count (spy/calls check-fn-2)) 1))

    (is (results/unhealthy? (check-1-name resolved-results)))
    (is (results/healthy? (check-2-name resolved-results)))))

(deftest resolve-checks-resolves-background-checks-as-cached-results-if-found
  (let [check-fn-1 (spy/spy
                     (fn [_ result-cb]
                       (result-cb (results/unhealthy))))
        check-fn-2 (spy/spy
                     (fn [_ result-cb]
                       (result-cb (results/healthy))))

        check-1-name :thing-1
        check-2-name :thing-2

        check-1 (checks/background-check check-1-name check-fn-1)
        check-2 (checks/background-check check-2-name check-fn-2)

        result-1 (results/healthy)
        result-2 (results/unhealthy)

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2)
                   (registry/with-cached-result check-1-name result-1)
                   (registry/with-cached-result check-2-name result-2))

        resolved-results (registry/resolve-checks registry)]
    (is (= (count (spy/calls check-fn-1)) 0))
    (is (= (count (spy/calls check-fn-2)) 0))

    (is (results/healthy? (check-1-name resolved-results)))
    (is (results/unhealthy? (check-2-name resolved-results)))))

(deftest resolve-checks-resolves-asynchronously-when-passed-callback
  (let [context {}

        check-1-name :thing-1
        check-2-name :thing-2
        check-3-name :thing-3

        cached-result-1 (results/healthy {:id (data/random-uuid)})
        fresh-result-1 (results/healthy {:id (data/random-uuid)})
        fresh-result-2 (results/healthy {:id (data/random-uuid)})
        fresh-result-3 (results/healthy {:id (data/random-uuid)})

        check-fn-1 (fn [_ result-cb] (result-cb fresh-result-1))
        check-fn-2 (fn [_ result-cb] (result-cb fresh-result-2))
        check-fn-3 (fn [_ result-cb] (result-cb fresh-result-3))

        check-1 (checks/background-check check-1-name check-fn-1)
        check-2 (checks/background-check check-2-name check-fn-2)
        check-3 (checks/realtime-check check-3-name check-fn-3)

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2)
                   (registry/with-check check-3)
                   (registry/with-cached-result check-1-name cached-result-1))

        results-atom (atom nil)
        callback-fn (fn [results]
                      (reset! results-atom results))]
    (registry/resolve-checks registry context callback-fn)

    (loop [attempts 1]
      (if (not (nil? @results-atom))
        (is (=
              (into {}
                (map
                  (fn [[name result]] [name (tst/without-timings result)])
                  @results-atom))
              {check-1-name (tst/without-timings cached-result-1)
               check-2-name (tst/without-timings fresh-result-2)
               check-3-name (tst/without-timings fresh-result-3)}))
        (if (< attempts 5)
          (do
            (async/<!! (async/timeout 25))
            (recur (inc attempts)))
          (throw (ex-info "Callback function was not called before timeout."
                   {:checks [check-1 check-2 check-3]})))))))

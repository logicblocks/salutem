(ns salutem.core.registry-test
  (:require
   [clojure.test :refer :all]

   [spy.core :as spy]
   [tick.alpha.api :as t]

   [salutem.core.time :as time]
   [salutem.core.checks :as checks]
   [salutem.core.results :as results]
   [salutem.core.registry :as registry]))

(deftest adds-single-check-to-registry
  (let [check (checks/realtime-check :thing
                (fn [_ result-cb]
                  (result-cb (results/healthy))))
        registry (registry/with-check (registry/empty-registry) check)]
    (is (= (registry/find-check registry :thing) check))))

(deftest adds-many-checks-to-registry
  (let [check-1 (checks/realtime-check :thing-1
                  (fn [_ result-cb]
                    (result-cb (results/healthy))))
        check-2 (checks/background-check :thing-2
                  (fn [_ result-callback]
                    (result-callback (results/unhealthy))))

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2))]
    (is (= (registry/check-names registry) #{:thing-1 :thing-2}))))

(deftest returns-single-outdated-check
  (let [check-1 (checks/background-check :thing-1
                  (fn [_ result-cb]
                    (result-cb (results/healthy)))
                  {:ttl (time/duration 30 :seconds)})
        check-2 (checks/background-check :thing-2
                  (fn [_ result-cb]
                    (result-cb (results/unhealthy)))
                  {:ttl (time/duration 5 :minutes)})

        outdated-check-1-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 35 :seconds))})
        current-check-2-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 10 :seconds))})

        registry
        (-> (registry/empty-registry)
          (registry/with-check check-1)
          (registry/with-check check-2)
          (registry/with-cached-result check-1 outdated-check-1-result)
          (registry/with-cached-result check-2 current-check-2-result))

        outdated-checks (registry/outdated-checks registry)]
    (is (= #{check-1} outdated-checks))))

(deftest returns-many-outdated-checks
  (let [check-1 (checks/background-check :thing-1
                  (fn [_ result-cb]
                    (result-cb (results/healthy)))
                  {:ttl (time/duration 30 :seconds)})
        check-2 (checks/background-check :thing-2
                  (fn [_ result-cb]
                    (result-cb (results/unhealthy)))
                  {:ttl (time/duration 5 :minutes)})
        check-3 (checks/background-check :thing-3
                  (fn [_ result-cb]
                    (result-cb (results/healthy)))
                  {:ttl (time/duration 1 :minutes)})

        outdated-check-1-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 35 :seconds))})
        current-check-2-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 10 :seconds))})
        outdated-check-3-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 2 :minutes))})

        registry
        (-> (registry/empty-registry)
          (registry/with-check check-1)
          (registry/with-check check-2)
          (registry/with-check check-3)
          (registry/with-cached-result check-1 outdated-check-1-result)
          (registry/with-cached-result check-2 current-check-2-result)
          (registry/with-cached-result check-3 outdated-check-3-result))

        outdated-checks (registry/outdated-checks registry)]
    (is (= #{check-1 check-3} outdated-checks))))

(deftest treats-background-checks-without-result-as-outdated
  (let [check-1 (checks/background-check :thing-1
                  (fn [_ result-cb]
                    (result-cb (results/healthy)))
                  {:ttl (time/duration 30 :seconds)})
        check-2 (checks/background-check :thing-2
                  (fn [_ result-cb]
                    (result-cb (results/unhealthy)))
                  {:ttl (time/duration 5 :minutes)})
        check-3 (checks/background-check :thing-3
                  (fn [_ result-cb]
                    (result-cb (results/healthy)))
                  {:ttl (time/duration 1 :minutes)})

        current-check-2-result
        (results/healthy
          {:evaluated-at (t/- (t/now) (t/new-duration 10 :seconds))})

        registry
        (-> (registry/empty-registry)
          (registry/with-check check-1)
          (registry/with-check check-2)
          (registry/with-check check-3)
          (registry/with-cached-result check-2 current-check-2-result))

        outdated-checks (registry/outdated-checks registry)]
    (is (= #{check-1 check-3} outdated-checks))))

(deftest resolves-realtime-check-every-time
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
          (:evaluated-at resolved-result-2)
          (:evaluated-at resolved-result-1)))))

(deftest resolves-background-check-when-no-cached-result-available
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (results/unhealthy))))
        check (checks/background-check :thing check-fn)
        registry (registry/with-check (registry/empty-registry) check)
        resolved-result (registry/resolve-check registry :thing)]
    (is (= (count (spy/calls check-fn)) 1))
    (is (results/unhealthy? resolved-result))))

(deftest resolves-background-check-as-previous-result-when-available
  (let [check-fn (spy/spy
                   (fn [_ result-cb]
                     (result-cb (results/unhealthy))))
        check (checks/background-check :thing check-fn)
        cached-result (results/healthy)
        registry (-> (registry/empty-registry)
                   (registry/with-check check)
                   (registry/with-cached-result check cached-result))
        resolved-result (registry/resolve-check registry :thing)]
    (is (= (count (spy/calls check-fn)) 0))
    (is (results/healthy? resolved-result))))

(deftest resolves-all-checks-in-the-registry
  (let [result-1 (results/unhealthy)
        result-2 (results/healthy)

        check-1 (checks/background-check :thing-1
                  (fn [_ result-cb]
                    (result-cb result-1)))
        check-2 (checks/realtime-check :thing-2
                  (fn [_ result-cb]
                    (result-cb result-2)))

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2))

        results (registry/resolve-checks registry)]
    (is (= {:thing-1 result-1 :thing-2 result-2} results))))

(deftest resolves-all-realtime-checks-every-time
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
          (get-in resolved-results-2 [:thing-1 :evaluated-at])
          (get-in resolved-results-1 [:thing-1 :evaluated-at])))
    (is (t/>
          (get-in resolved-results-2 [:thing-2 :evaluated-at])
          (get-in resolved-results-1 [:thing-2 :evaluated-at])))))

(deftest resolves-background-checks-as-previous-results-when-available
  (let [check-fn-1 (spy/spy
                     (fn [_ result-cb]
                       (result-cb (results/unhealthy))))
        check-fn-2 (spy/spy
                     (fn [_ result-cb]
                       (result-cb (results/healthy))))

        check-1 (checks/background-check :thing-1 check-fn-1)
        check-2 (checks/background-check :thing-2 check-fn-2)

        result-1 (results/healthy)
        result-2 (results/unhealthy)

        registry (-> (registry/empty-registry)
                   (registry/with-check check-1)
                   (registry/with-check check-2)
                   (registry/with-cached-result check-1 result-1)
                   (registry/with-cached-result check-2 result-2))

        resolved-results (registry/resolve-checks registry)]
    (is (= (count (spy/calls check-fn-1)) 0))
    (is (= (count (spy/calls check-fn-2)) 0))

    (is (results/healthy? (:thing-1 resolved-results)))
    (is (results/unhealthy? (:thing-2 resolved-results)))))

(deftest refreshes-realtime-check-every-time
  (let [call-counter (atom 0)
        check-fn (fn [_ result-cb]
                     (swap! call-counter inc)
                     (result-cb (results/healthy {:call @call-counter})))
        check (checks/realtime-check :thing check-fn)
        registry (registry/with-check (registry/empty-registry) check)

        updated-registry-1 (registry/refresh-result registry :thing)
        updated-registry-2 (registry/refresh-result updated-registry-1 :thing)
        
        cached-result-1 (registry/find-cached-result updated-registry-1 :thing)
        cached-result-2 (registry/find-cached-result updated-registry-2 :thing)]
    (is (= 1 (:call cached-result-1)))
    (is (= 2 (:call cached-result-2)))
    (is (t/>
          (:evaluated-at cached-result-2)
          (:evaluated-at cached-result-1)))))

(deftest refreshes-background-check-when-no-cached-result
  (let [call-counter (atom 0)
        check-fn (fn [_ result-cb]
                     (swap! call-counter inc)
                     (result-cb (results/healthy {:call @call-counter})))
        check (checks/background-check :thing check-fn)
        registry (registry/with-check (registry/empty-registry) check)

        updated-registry (registry/refresh-result registry :thing)
        
        cached-result (registry/find-cached-result updated-registry :thing)]
    (is (= 1 (:call cached-result)))))

(deftest does-not-refresh-background-check-when-cached-result-within-ttl
  (let [call-counter (atom 0)
        check-fn (fn [_ result-cb]
                     (swap! call-counter inc)
                     (result-cb (results/healthy {:call @call-counter})))
        check (checks/background-check :thing check-fn
                {:ttl (time/duration 1 :minutes)})
        result (results/healthy {:call 0})
        registry (-> (registry/empty-registry)
                   (with-check check)
                   (with-cached-result result))
                   
        updated-registry (registry/refresh/result registry :thing)
        
        cached-result (registry/find-cached-result updated-registry :thing)]
  (is (= result cached-result))))
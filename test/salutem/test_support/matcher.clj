(ns salutem.test-support.matcher
  (:require
   [clojure.test :refer :all]

   [matcher-combinators.core :as mc]
   [matcher-combinators.clj-test :as mc-test]
   [matcher-combinators.matchers :as mc-matchers]

   [cartus.test :as cartus-test])
  (:import [cartus.test TestLogger]))

(defmethod assert-expr 'logged? [msg form]
  `(let [args# (list ~@(rest form))
         [logger# log-spec-or-modifiers# log-spec#] args#
         resolved-log-spec# (if log-spec# log-spec# log-spec-or-modifiers#)
         resolved-modifiers# (if log-spec# log-spec-or-modifiers# #{})]
     (cond
       (< (count args#) 2)
       (clojure.test/do-report
         {:type     :fail
          :message  ~msg
          :expected (symbol
                      (str
                        "`logged?` accepts either a `logger` and a `log-spec` "
                        "or a `logger`, a set of `modifiers` and a `log-spec` "
                        "where `modifiers` can include any of "
                        "`#{:in-any-order :only}`."))
          :actual   (symbol (str (count args#) " were provided: " '~form))})

       (and
         (mc/matcher? resolved-log-spec#)
         (instance? TestLogger logger#))
       (let [ordered?# (not (resolved-modifiers# :in-any-order))
             subset?# (not (resolved-modifiers# :only))
             matcher# resolved-log-spec#
             matcher# (if subset?#
                        (mc-matchers/match-with [vector? mc-matchers/embeds]
                          matcher#)
                        matcher#)
             matcher# (if (not ordered?#)
                        (mc-matchers/in-any-order resolved-log-spec#)
                        matcher#)
             result# (mc/match matcher# (cartus-test/events logger#))
             match?# (mc/indicates-match? result#)]
         (clojure.test/do-report
           (if match?#
             {:type     :pass
              :message  ~msg
              :expected '~form
              :actual   (list 'logged? logger# log-spec#)}
             {:type     :fail
              :message  ~msg
              :expected '~form
              :actual   (mc-test/tagged-for-pretty-printing
                          (list '~'not (list 'logged? logger# log-spec#))
                          result#)}))
         match?#)

       :else
       (clojure.test/do-report
         {:type     :fail
          :message  ~msg
          :expected (str "The second argument to logged? needs to be a "
                      "matcher-combinators.core/Matcher")
          :actual   '~form}))))

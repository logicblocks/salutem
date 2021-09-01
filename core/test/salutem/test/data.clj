(ns salutem.test.data
  (:require
   [clojure.string :as string]))

(defn random-hex-string [length]
  (string/join
    (take length
      (repeatedly
        #(rand-nth
           ["1" "2" "3" "4" "5" "6" "7" "8" "9"
            "a" "b" "c" "d" "e" "f"])))))

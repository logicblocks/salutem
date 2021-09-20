(ns salutem.test.support.time)

(defn without-timings [result]
  (dissoc result :salutem/evaluated-at :salutem/evaluation-duration))

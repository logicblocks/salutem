(ns salutem.test.support.time)

(defn without-evaluation-date-time [result]
  (dissoc result :evaluated-at))
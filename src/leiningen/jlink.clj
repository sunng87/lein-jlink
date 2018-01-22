(ns leiningen.jlink
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.main :as l]
            [clojure.java.io :as io]))

(def config-cache ".lein-jlink")

(defn- delete-directory [f]
  (when (.isDirectory f)
    (doseq [f0 (.listFiles f)]
      (delete-directory f0)))
  (io/delete-file f true))

(def out "target/jlink")

(defn jlink-init [project]
  (let [jlink-modules (clojure.string/join "," (:jlink-modules project ["java.base"]))
        java-home (System/getenv "JAVA_HOME")
        cached-modules (try
                         (read-string (slurp config-cache))
                         (catch Exception _))]
    (when (or (not= jlink-modules cached-modules)
              (not (.exists (io/file out))))
      (delete-directory (io/file out))
      (eval/sh "jlink"
               "--module-path"
               (str java-home "/jmods")
               "--add-modules"
               jlink-modules
               "--output"
               out
               "--strip-debug"
               "--no-man-pages"
               "--no-header-files"
               "--compress=2")
      (spit config-cache (pr-str jlink-modules))
      (l/info "Created ./target/jlink/bin/java"))))

(defn jlink-clean [project]
  (delete-directory (io/file out)))

(defn jlink
  "Create Java environment using jlink"
  [project sub]
  (cond
    (= sub "init") (jlink-init project)
    (= sub "clean") (jlink-clean project)))

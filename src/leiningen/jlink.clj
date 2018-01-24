(ns leiningen.jlink
  (:refer-clojure :exclude [test])
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.main :as l]
            [leiningen.run :as run]
            [leiningen.test :as test]
            [leiningen.help :as h]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(declare jlink)

(def config-cache ".lein-jlink")

(defn- delete-directory [f]
  (when (.isDirectory f)
    (doseq [f0 (.listFiles f)]
      (delete-directory f0)))
  (io/delete-file f true))

(def out "target/jlink")

(defn- print-help []
  (h/help nil "jlink"))

(defn init
  "Initialize jlink environment"
  [project]
  (let [java-home (System/getenv "JAVA_HOME")
        jlink-module-path (s/join ":" (:jlink-module-path project [(str java-home "/jmods")]))
        jlink-modules (s/join "," (:jlink-modules project ["java.base"]))
        cached-modules (try
                         (read-string (slurp config-cache))
                         (catch Exception _))]
    (when (or (not= jlink-modules cached-modules)
              (not (.exists (io/file out))))
      (delete-directory (io/file out))
      (eval/sh "jlink"
               "--module-path"
               jlink-module-path
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

(defn clean
  "Cleanup jlink environment"
  [project]
  (delete-directory (io/file out)))

(def java-exec (str out "/bin/java"))

(defn run
  "lein run using jlink java"
  [project args]
  (jlink project "init")
  (let [project (assoc project :java-cmd java-exec)]
    (apply run/run project args)))

(defn test
  "lein test using jlink java"
  [project args]
  (jlink project "init")
  (let [project (assoc project :java-cmd java-exec)]
    (apply test/test project args)))

(defn ^{:help-arglists '[[project sub-command]]
        :subtasks (list #'init #'clean #'run #'test)}
  jlink
  "Create Java environment using jlink"
  ([project]
   (print-help))
  ([project sub & args]
   (cond
     (= sub "init") (init project)
     (= sub "clean") (clean project)
     (= sub "run") (run project args)
     (= sub "test") (test project args)
     :else (print-help))))

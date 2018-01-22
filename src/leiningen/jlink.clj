(ns leiningen.jlink
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.main :as l]
            [leiningen.help :as h]
            [clojure.java.io :as io]
            [clojure.string :as s]))

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

(defn ^{:help-arglists '[[project sub-command]]
        :subtasks (list #'init #'clean)}
  jlink
  "Create Java environment using jlink"
  ([project]
   (print-help))
  ([project sub & args]
   (cond
     (= sub "init") (init project)
     (= sub "clean") (clean project)
     :else (print-help))))

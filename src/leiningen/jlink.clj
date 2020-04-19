(ns leiningen.jlink
  (:refer-clojure :exclude [test])
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.main :as l]
            [leiningen.clean :as clean]
            [leiningen.run :as run]
            [leiningen.test :as test]
            [leiningen.uberjar :as uberjar]
            [leiningen.jar :as jar]
            [leiningen.help :as h]
            [leiningen.core.project :as p]
            [robert.hooke :as hooke]
            [clojure.java.io :as io]
            [clojure.string :as s])
  (:import [java.io File]))

(declare jlink)

(def config-cache ".lein-jlink")

(defn- delete-directory [f]
  (when (.isDirectory f)
    (doseq [f0 (.listFiles f)]
      (delete-directory f0)))
  (io/delete-file f true))

(defn out [project]
  (let [parent (.getParent (File. (:target-path project)))]
       (str parent
            (File/separator)
            "jlink")))

(defn- print-help []
  (h/help nil "jlink"))

(defn init
  "Initialize jlink environment"
  [project]
  (let [java-home (System/getenv "JAVA_HOME")
        jlink-bin-path (s/join (File/separator) [java-home "bin" "jlink"])
        jlink-modules-path (s/join (File/pathSeparator)
                                   (concat (:jlink-module-paths project)
                                           [(str java-home "/jmods")]))
        jlink-modules (s/join "," (concat (:jlink-modules project) ["java.base"]))
        cached-modules (try
                         (read-string (slurp config-cache))
                         (catch Exception _))
        jlink-path (out project)]
    (when (or (not= jlink-modules cached-modules)
              (not (.exists (io/file jlink-path))))
      (delete-directory (io/file jlink-path))
      (eval/sh "jlink"
               "--module-path"
               (str "\"" jlink-modules-path "\"")
               "--add-modules"
               jlink-modules
               "--output"
               jlink-path
               "--strip-debug"
               "--no-man-pages"
               "--no-header-files"
               "--compress=2")
      (spit config-cache (pr-str jlink-modules))
      (l/info "Created" jlink-path))))

(defn middleware [project]
  (let [java-home (System/getenv "JAVA_HOME")
        jlink-bin-path (s/join (File/separator) [java-home "bin" "jlink"])
        jlink-modules-path (str "\""
                                (s/join (File/pathSeparator)
                                        (concat (:jlink-module-paths project)
                                                [(str java-home "/jmods")]))
                                "\"")
        jlink-modules (s/join "," (concat (:jlink-modules project) ["java.base"]))
        cached-modules (try
                         (read-string (slurp config-cache))
                         (catch Exception _))
        jlink-path (out project)]
    (merge project
           {:java-cmd (s/join (File/separator) [jlink-path "bin" "java"])}
           {:javac-options ["--module-path" jlink-modules-path "--add-modules" jlink-modules]})))

(defn compile-hook [task & args]
 (apply task args))

(defn clean
  "Cleanup jlink environment"
  [project]
  (delete-directory (io/file (out project)))
  (clean/clean project))

(defn java-exec [project] (str (out project) "/bin/java"))

(defn run
  "lein run using jlink java"
  [project args]
  (init project)
  (let [project (assoc project :java-cmd (java-exec project))]
    (l/info (str (:java-cmd project)))
    (apply run/run project args)))

(defn test
  "lein test using jlink java"
  [project args]
  (init project)
  (let [project (assoc project :java-cmd (java-exec project))]
    (apply test/test project args)))

(defn assemble
  "Assemble a portable java environment"
  [project]
  (init project)
  (let [jar-path (uberjar/uberjar project)
        jlink-path (out project)
        executable (str jlink-path "/bin/" (:name project))]
    (io/copy (io/file jar-path) (io/file (str jlink-path "/" (:name project) ".jar")))
    (l/info "Copied uberjar into" jlink-path)))

(defn package
  "Create a tarball of the portable environment"
  [project]
  (assemble project)
  (l/info "Someday we'll call jpackage and really do something! :-D"))


(defn ^{:help-arglists '[[project sub-command]]
        :subtasks (list #'init #'clean #'run #'test #'assemble #'package)}
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
     (= sub "assemble") (assemble project)
     (= sub "package") (package project)
     :else (print-help))))

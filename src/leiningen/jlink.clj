(ns leiningen.jlink
  (:refer-clojure :exclude [test])
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.main :as l]
            [leiningen.run :as run]
            [leiningen.test :as test]
            [leiningen.uberjar :as uberjar]
            [leiningen.jar :as jar]
            [leiningen.help :as h]
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
  (str (:target-path project) "/jlink"))

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

(defn clean
  "Cleanup jlink environment"
  [project]
  (delete-directory (io/file (out project))))

(defn java-exec [project] (str (out project) "/bin/java"))

(defn run
  "lein run using jlink java"
  [project args]
  (jlink project "init")
  (let [project (assoc project :java-cmd (java-exec project))]
    (apply run/run project args)))

(defn test
  "lein test using jlink java"
  [project args]
  (jlink project "init")
  (let [project (assoc project :java-cmd (java-exec project))]
    (apply test/test project args)))

(defn assemble
  "Assemble a portable java environment"
  [project]
  (let [jar-path (uberjar/uberjar project)
        jlink-path (out project)
        executable (str jlink-path "/bin/" (:name project))]
    (jlink project "init")
    (eval/sh "cp" jar-path (str jlink-path "/" (:name project) ".jar"))
    (l/info "Copied uberjar into" jlink-path)
    (spit executable
          (format (slurp (io/resource "exec.sh")) (:name project)))
    (eval/sh "chmod" "a+x" executable)
    (l/info "Created executable" executable)))

(defn package
  "Create a tarball of the portable environment"
  [project]
  (assemble project)
  (let [tarball-name (str (:target-path project)
                          "/"
                          (:name project)
                          "-"
                          (:version project)
                          "-jlink"
                          ".tar.gz")]
    (eval/sh "tar" "czf" tarball-name "-C" (out project) ".")
    (l/info "Created tarball" tarball-name)))


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

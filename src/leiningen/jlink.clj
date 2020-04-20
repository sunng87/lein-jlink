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

(defn- delete-directory
  "Deletes the provided path"
  [f]
  (when (.isDirectory f)
    (doseq [f0 (.listFiles f)]
      (delete-directory f0)))
  (io/delete-file f true))

(defn out
  "Returns the path to the custom runtime image"
  [project]
  (let [parent (.getParent (File. (:target-path project)))]
       (str parent
            (File/separator)
            "image")))

(defn java-exec
  "Returns the path to the `java` command bundled with the custom runtime"
  [project]
  (str (out project) "/bin/java"))

(defn- print-help
  "Displays the help message for the plugin"
  []
  (h/help nil "jlink"))

(defn- init-runtime
  "Uses `jlink` to create a custom runtime environment"
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

(defn init
  "Creates the custom runtime environment, if required."
  [project]
  (if (:jlink-custom-jre project)
    (init-runtime project)))

(defn middleware [project]
  "Alters the `java-cmd` and `javac-options` project keys for use with a custom
  runtime image or additional Java modules and paths"
  (let [java-home (System/getenv "JAVA_HOME")
        jlink-modules-path (str "\""
                                (s/join (File/pathSeparator)
                                        (concat (:jlink-module-paths project)
                                                [(str java-home "/jmods")]))
                                "\"")
        jlink-modules (s/join "," (concat (:jlink-modules project) ["java.base"]))
        cached-modules (try
                         (read-string (slurp config-cache))
                         (catch Exception _))
        jlink-image-path (out project)]

    (merge project
           (if (:jlink-custom-jre project)

             ;; use our custom image's java
             {:java-cmd (java-exec project)}

             ;; stick with the default java
             {:java-cmd (:java-cmd project)})

           {:javac-options ["--module-path" jlink-modules-path "--add-modules" jlink-modules]})))

(defn clean
  "Deletes the custom image"
  [project]
  (delete-directory (io/file (out project)))
  (clean/clean project))

(defn assemble
  "Builds an uberjar for the project and copies into the custom runtime image"
  [project]
  (init project)
  (let [jar-path (uberjar/uberjar project)
        jlink-path (out project)
        executable (str jlink-path (File/separator) "bin" (File/separator) (:name project))]
    (io/copy (io/file jar-path) (io/file (str jlink-path "/" (:name project) ".jar")))
    (l/info "Copied uberjar into" jlink-path)))

(defn package
  "Package the project for distribution with `jpackage`"
  [project]
  (assemble project)
  (l/info "Someday we'll call jpackage and really do something! :-D"))


(defn ^{:help-arglists '[[project sub-command]]
        :subtasks (list #'init #'clean #'assemble #'package)}
  jlink
  "Create Java environment using jlink"
  ([project]
   (print-help))
  ([project sub & args]
   (cond
     (= sub "init") (init project)
     (= sub "clean") (clean project)
     (= sub "assemble") (assemble project)
     (= sub "package") (package project)
     :else (print-help))))

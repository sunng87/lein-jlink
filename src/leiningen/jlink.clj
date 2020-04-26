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
  (:import [java.io File]
           [java.io FilenameFilter]))

;; name of cache configuration file
(def config-cache ".lein-jlink")

(defn- delete-directory
  "Deletes the provided path"
  [path]
  (when (.isDirectory path)
    (doseq [file (.listFiles path)]
      (delete-directory file)))
  (io/delete-file path true))

(defn out
  "Returns the path to the custom java runtime image"
  [project]
  (if (:jlink-jre-image-path project)
    (:jlink-jre-image-path project)
    (str (.getParent (File. (:target-path project))) (File/separator) "image")))

(defn- java-exec
  "Returns the path to the `java` command bundled with the custom runtime"
  [project]
  (str (out project) "/bin/java"))

(defn- jdk-path
  "Returns the path to the JDK to use for building"
  [project]
  (if (:jlink-jdk-path project)
    (:jlink-jdk-path project)
    (System/getenv "JAVA_HOME")))

(defn- print-help
  "Displays the help message for the plugin"
  []
  (h/help nil "jlink"))

(defn middleware [project]
  "Alters the `java-cmd` and `javac-options` project keys for use with a custom
  runtime image or additional Java modules and paths"
  (let [jdk-path (jdk-path project)
        jlink-sdk-path (if (:jlink-sdk-paths project)
                           (str "\""
                                (s/join (File/pathSeparator)
                                        (:jlink-sdk-paths project))
                                "\""))
        jlink-modules-path (str "\""
                                (s/join (File/pathSeparator)
                                        (concat (:jlink-module-paths project)
                                                [(str jdk-path "/jmods")]))
                                "\"")
        jlink-modules (s/join "," (concat (:jlink-modules project) ["java.base"]))
        cached-modules (try
                         (read-string (slurp config-cache))
                         (catch Exception _))
        jlink-image-path (out project)]
    (merge project
           (if (:jlink-jre-image project)

             ;; use our custom image's java
             {:java-cmd (java-exec project)}

             ;; stick with the default java
             {:java-cmd (:java-cmd project)})

           {:javac-options ["--module-path" jlink-modules-path
                            "--add-modules" jlink-modules]
            :jvm-opts (into ["--add-modules" jlink-modules]
                            (if jlink-sdk-path
                                ["--module-path" jlink-sdk-path]))})))

(defn- init-runtime
  "Uses `jlink` to create a custom runtime environment"
  [project]
  (let [jdk-path (jdk-path project)
        jlink-bin-path (s/join (File/separator) [jdk-path "bin" "jlink"])
        jlink-modules-path (s/join (File/pathSeparator)
                                   (concat (:jlink-module-paths project)
                                           [(str jdk-path (File/separator) "jmods")]))
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
  "Creates the custom runtime environment, if required by the project."
  [project]
  (if (:jlink-jre-image project)
    (init-runtime project)))

(defn- module-info
  "Compiles the module-info.java file for the project"
  [project-in]
  (let [project (middleware project-in)
        jdk-path (jdk-path project)
        javac-bin-path (s/join (File/separator) [jdk-path "bin" "javac"])
        jlink-sdk-path (if (:jlink-sdk-paths project)
                         (str "\""
                              (s/join (File/pathSeparator)
                                      (:jlink-sdk-paths project))
                              "\""))
        jlink-modules-path (s/join (File/pathSeparator)
                                   (concat (:jlink-module-paths project)
                                           [(str jdk-path (File/separator) "jmods")
                                            (:compile-path project)]))
        jlink-modules (s/join "," (concat (:jlink-modules project) ["java.base"]))
        sh-args (concat [javac-bin-path]
                        ["--module-path"
                         jlink-modules-path
                         "--add-modules"
                         jlink-modules
                         "-classpath"
                         (:compile-path project)
                         "-d"
                         (:compile-path project)
                         (:jlink-module-info project)])]
    (l/info sh-args)
    (apply eval/sh sh-args)))

(defn clean
  "Deletes the custom image"
  [project]
  (delete-directory (io/file (out project)))
  (clean/clean project))

(defn assemble
  "Builds an uberjar for the project and copies into the custom runtime image"
  [project]
  (init project)
  (let [jdk-path (jdk-path project)
        jar-bin-path (s/join (File/separator) [jdk-path "bin" "jar"])
        uberjar-file (io/file (uberjar/uberjar project))
        jlink-path (out project)]
    (io/copy uberjar-file
             (io/file (str jlink-path
                           (File/separator) (.getName uberjar-file))))
    (l/info "Copied uberjar file into" jlink-path)))

(defn ^{:help-arglists '[[project sub-command]]
        :subtasks (list #'init #'clean #'assemble)}
  jlink
  "Create Java environment using jlink"
  ([project]
   (print-help))
  ([project sub & args]
   (cond
     (= sub "init") (init project)
     (= sub "clean") (clean project)
     (= sub "assemble") (assemble project)
     :else (print-help))))

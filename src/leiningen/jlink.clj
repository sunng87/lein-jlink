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
           [java.io FilenameFilter]
           [org.apache.commons.compress.compressors.gzip GzipCompressorOutputStream]
           [org.apache.commons.compress.archivers ArchiveStreamFactory]
           [org.apache.commons.compress.archivers.examples Archiver]))

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
  (if (:jlink-image-path project)
    (:jlink-image-path project)
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
           {:java-cmd (java-exec project)
            :javac-options ["--module-path" jlink-modules-path
                            "--add-modules" jlink-modules]
            :jvm-opts (into ["--add-modules" jlink-modules]
                            (if jlink-sdk-path
                                ["--module-path" jlink-sdk-path]))})))

(defn init
  "Creates the custom runtime environment for the project"
  [project]
  (let [jdk-path (jdk-path project)
        jlink-bin-path (s/join (File/separator) [jdk-path "bin" "jlink"])
        jlink-modules-path (s/join (File/pathSeparator)
                                   (concat (:jlink-module-paths project)
                                           [(str jdk-path (File/separator) "jmods")]))
        jlink-modules (s/join "," (concat (:jlink-modules project) ["java.base"]))
        cached-modules (try
                         (read-string (slurp config-cache))
                         (catch Exception
                             _))
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
  "Deletes the custom image"
  [project]
  (delete-directory (io/file (out project)))
  (clean/clean project))

(defn- launcher
  "Adds launcher scripts to the custom runtime image"
  [project uberjar-file launcher-path]
  (let []
    (spit (str launcher-path ".sh")
          (format (slurp (io/resource "exec.sh"))
                  (.getName uberjar-file)))
    (.setExecutable (File. (str launcher-path ".sh")) true)
    (spit (str launcher-path ".ps1")
          (format (slurp (io/resource "exec.ps1"))
                  (.getName uberjar-file)))
    (spit (str launcher-path ".bat")
          (format (slurp (io/resource "exec.bat"))
                  (.getName uberjar-file)))))

(defn assemble
  "Builds an uberjar for the project and copies into the custom runtime image"
  [project]
  (init project)
  (let [jdk-path (jdk-path project)
        jar-bin-path (s/join (File/separator) [jdk-path "bin" "jar"])
        uberjar-file (io/file (uberjar/uberjar project))
        jlink-path (out project)
        launcher-path (str jlink-path (File/separator) (:name project))]
    (io/copy uberjar-file
             (io/file (str jlink-path
                           (File/separator) (.getName uberjar-file))))
    (l/info "Copied uberjar file into" jlink-path)
    (launcher project uberjar-file launcher-path)
    (l/info "Wrote launcher scripts to" launcher-path)))

(defn- build-archiver
  "Returns an archive for the project that will write to the provided output
  stream"
  [project output-stream]
  (cond
    (= (:jlink-archive project) "zip")
    (.createArchiveOutputStream
     (ArchiveStreamFactory.)
     (ArchiveStreamFactory/ZIP)
     output-stream)

    :else
    (.createArchiveOutputStream
     (ArchiveStreamFactory.)
     (ArchiveStreamFactory/TAR)
     (GzipCompressorOutputStream. output-stream))))

(defn package
  "Packages a custom runtime image into an archive"
  [project]
  (let [source (out project)
        target-name (if (:jlink-archive-name project)
                      (str (:jlink-archive-name project) "-" (:version project))
                      (str (:name project) "-" (:version project)))
        target (if (= (:jlink-archive project) "zip")
                 (str target-name ".zip")
                 (str target-name ".tar.gz"))
        out-stream (io/output-stream (io/file target))
        archiver-stream (build-archiver project out-stream)
        archiver (Archiver.)]
    (l/info "Packaging project artifacts from" source)
    (.create archiver archiver-stream (io/file source))
    (.close archiver-stream)
    (.flush out-stream)
    (.close out-stream)
    (l/info "Packaged project to" target)))

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

(defproject lein-jlink "0.2.2-SNAPSHOT"
  :description "Package your Leiningen project as a standalone and stripped-down JVM using Java's jlink. Great for distribution and Docker images."
  :url "https://github.com/sunng87/lein-jlink"
  :dependencies [[org.apache.commons/commons-compress "1.20"]]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true
  :deploy-repositories {"releases" :clojars})

# lein-jlink

A Leiningen plugin for custom Java environment.

[![Clojars
Project](https://img.shields.io/clojars/v/lein-jlink.svg)](https://clojars.org/lein-jlink)

## Motivation

JDK 9 ships a new utility called `jlink`, which creates custom JRE
based on modules you use. As of 1.9, Clojure has no special support
for modular Java. However, we can still use customized JRE to run our
Clojure application. If you care about the size of your application
package(includes JRE), for example, is docker environment, this
feature is a pretty good news for you: The minimal JRE is only
29MB. And it's enough to run a hello world Ring application, using
[jetty adapter](https://github.com/sunng87/ring-jetty9-adapter). The
overall size (app + dependency jars + custom JRE) is only 37MB, and
22MB in gzipped tarball.

## Usage

First thing first, you need Java 9+ for `jlink`.

Put `[lein-jlink "current-version"]` into the `:plugins` vector of
your project.clj.

Create a default Java environment:

```
$ lein jlink init
```

By default, we create a basic Java environment with only base module
(the `java.base`), which is only 29MB. That's could enough for your
clojure application.

To add more modules to your jlink environment, add them to
`:jlink-modules` vector, like `["java.base" "java.sql"]`.

To add more module path, use:
`:jlink-module-path [(str (System/getProperty "java.home") "/jmods")
"lib/"]`.

### Run and test your app

You can use jlink generated JRE to run and test your clojure app, by
which you can verify functionality of your app under customized JRE.

```
lein jlink run
lein jlink test
```

A more flexible way is to create a profile in your project:

```clj
:profiles {
  :jlink {:java-cmd "./target/jlink/bin/java"}
}
```

### Assemble and package

By running `lein jlink assemble`, we create a uberjar and put it into
custom JRE directory. A launcher script is also generated in
`target/jlink/bin` that can run your application with this JRE.

`lein jlink package` creates a tarball for previously generated JRE
and can be distributed without dependencies.

## Create docker image

The jlink target directory can be put into a docker image for app
distribution. I have created a minimal base image
[alpine-jlink-base](https://github.com/sunng87/alpine-jlink-base),
which is only 12.3MB.

Assume your application is called `jlinktest`, you can create a
Dockerfile like:

```Dockerfile
FROM sunng/alpine-jlink-base

ADD target/default/jlink /opt/jlinktest
ENTRYPOINT /opt/jlinktest/bin/jlinktest
```

The result image size can be less than 50MB.

## License

Copyright © 2018 Ning Sun

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

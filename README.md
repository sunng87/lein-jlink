# lein-jlink

A Leiningen[0] plugin for custom Java environment.

[![Clojars
Project](https://img.shields.io/clojars/v/lein-jlink.svg)](https://clojars.org/lein-jlink)

## Motivation

The Java Platform Modules System (JPMS)[1], commonly referred to as just "modules",  have been a part of the platform since Java 9[2]. Modules are bigger than packages and it's likely that some of your favorite Java packages have been broken out into modules. Many modules are bundled with the standard runtime and development kits for Java but some are not. For instance, JavaFX is a module that is not packaged with Java and needs to be managed in a different way.

This plugin provides some additional tasks and middleware to Leiningen that makes it easier to interact with the Java module system. It makes it easier to include modules in your project and to interact with some of the module-specific tools like `jlink` and `jpackage`.

You might be particularly interest in these tools as it provides an easy way to build and use customized runtime that includes _only_ the modules your project needs to run. You can then package this smaller runtime with your application to provide one small, self-contained executable.

If you are packaging your application for use in a Docker[3] image, this feature is a pretty good news for you: The minimal JRE is only 29MB. And it's enough to run a hello world Ring application, using `jetty-adapter`[4]. The overall size (app + dependency jars + custom runtime) is only 37MB, and 22MB in gzipped tarball.

## Usage

First thing first, you need to migrate to Java 9 or newer. You can get the latest development kit from Adopt OpenJDK[5]:

+ [Download JDK from AdoptOpenJDK](https://adoptopenjdk.net/)

This plugin assumes that you have set a valid `JAVA_HOME` environment variable and that it will be available when Leiningen runs. If you don't have it set, go ahead and set `JAVA_HOME` now. The plugin uses this variable to find the Java modules distributed with your JDK as well as to locate tools like `jlink` and `jpackage`.

Next, add this plugin to your `project.clj` file in the `:plugins` section.

    [lein-jlink "0.2.1"]
    
Then add the middleware into the `:middleware` section.

    [leiningen.jlink/middleware]
    
Your project should look something like this...

    {defproject myorganization/myproject
      ...
      :plugins [
                ...
                [lein-jlink "0.2.1"]
               ]
      :middleware [
                   ...
                   [leiningen.jlink/middleware]
                  ]
      ...
    }
    
The middleware will alter the way Leiningen calls out to `java` and `javac` such that these calls include references to the modules that your project needs. By default only the `java.base` module is included, if you need other modules you can simply add them to the `:jlink-modules` key of your `project.clj` file.

    :jlink-modules ["java.sql"]
    
There are many modules distributed with the JDK, you can ask `java` to list them all.

    $ java --list-modules

If you are using modules that are not distributed with the JDK, you can download and add them to your project with the `:jlink-modules-paths` keyword in your `project.clj`. For instance, if you downloaded the JavaFX[6] modules from Gluon's website[7] (referred to as "jmods"), you would unpack them and then  add them like so...

    :jlink-module-paths ["/opt/java/javafx-jmods-14.0.1"]
    
If you are running on Windows, you will want to escape the `\` character in your paths with `\\`. For example:

    :jlink-module-paths ["C:\\Program Files\\Java\\javafx-jmods-14.0.1"]
    
If you are using modules that are not distributed with the JDK then you will want to create a custom runtime in order to package and distribute your application. If you are only using built-in modules, you can still use a custom runtime to build a much smaller distribution package, if that's something you are interested in. `;-)`

## Working with Custom Runtime Environments

First you need to let the plugin know that you want to use a custom runtime when interacting with your project. Set the `:jlink-custom-jre` key in your `project.clj` to `true`.

    :jlink-custom-jre true

Create a custom Java environment with the plugin's `init` task. It will call out to the `jlink` tool that is bundled with your JDK to create a new "runtime image". This image will be in the "jlink" directory at the root of your project.

    lein jlink init

By default, the plugin will create a basic Java environment with only base module (the `java.base`), which is only 29MB. This represents the minimum and it might be enough for your Clojure application, if you don't use any other modules.

If you do use other modules, you can add them through the `:jlink-modules` key in your `project.clj`.



If you have an external module, like JavaFX, you may add more module paths with the `:jlink-modules-paths` key.

    :jlink-module-paths ["/opt/java/javafx-sdk-14/lib"]

If you are running on Windows, be sure to escape the path separator like so:

    C:\\Program Files\\Java\\javafx-sdk-14\\lib

### Run and Test Your Project



You can use jlink generated JRE to run and test your clojure app, by
which you can verify functionality of your app under customized JRE.

    lein jlink run
    lein jlink test

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

[0]: "Leiningen Website" https://leiningen.org/
[1]: "Java JPMS Introduction" https://www.oracle.com/corporate/features/understanding-java-9-modules.html
[2]: "OpenJDK, Modules Quick Start" https://openjdk.java.net/projects/jigsaw/quick-start
[3]: "Docker Website" https://www.docker.com/
[4]: "Jetty Adapter" https://github.com/sunng87/ring-jetty9-adapter
[5]: "AdoptOpenJDK Website" https://adoptopenjdk.net/
[6]: "JavaFX Website" https://openjfx.io/
[7]: "Gluon JavaFX Download" https://gluonhq.com/products/javafx/

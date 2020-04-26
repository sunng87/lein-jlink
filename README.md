# lein-jlink

A [Leiningen][0] plugin for custom Java environment.

[![Clojars Project](https://img.shields.io/clojars/v/lein-jlink.svg)](https://clojars.org/lein-jlink)

## Motivation

The [Java Platform Modules System (JPMS)][1], commonly referred to as just "modules",  have been a [part of the platform since Java 9][2]. Modules are bigger than packages and it's likely that some of your favorite Java packages have been broken out into modules. Many modules are bundled with the standard runtime and development kits for Java but some are not. For instance, JavaFX is a module that is not packaged with Java and needs to be managed in a different way.

This plugin provides some additional tasks and middleware to Leiningen that makes it easier to interact with the Java module system. It makes it easier to include modules in your project and to interact with some of the module-specific tools like `jlink` and `jpackage`.

You might be particularly interest in these tools as it provides an easy way to build and use customized runtime that includes _only_ the modules your project needs to run. You can then package this smaller runtime with your application to provide one small, self-contained executable.

If you are packaging your application for use in a [Docker][3] image, this feature is a pretty good news for you: The minimal JRE is only 29MB. And it's enough to run a hello world Ring application, using [`jetty-adapter`][4]. The overall size (app + dependency jars + custom runtime) is only 37MB, and 22MB in gzipped tarball.

## Usage

First you need to migrate to Java 9 or newer. You can get the latest development kit from [Adopt OpenJDK][5]:

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
    
If you only use modules packaged with your JDK, then you can use all of the regular Leiningen commands without issue. When you compile a build a JAR file with `lein build` or execute it with `lein run`, the middleware will make sure that the modules are on the path.
    
### External Modules

If you are using modules that are not distributed with the JDK, you can download and add them to your project with the `:jlink-modules-paths` keyword in your `project.clj`. For instance, if you downloaded the [JavaFX modules][6] from [Gluon's website][7] (referred to as "jmods"), you would unpack them and then  add them like so...

    :jlink-module-paths ["/opt/java/javafx-jmods-14.0.1"]
    
If you are running on Windows, you will want to escape the `\` character in your paths with `\\`. For example:

    :jlink-module-paths ["C:\\Program Files\\Java\\javafx-jmods-14.0.1"]
    
External modules _only_ work at compile time, they cannot be passed to your default `java` command at runtime. There are two solutions:

+ You could download and install the SDK for the module. The plugin supports this but not all modules provide an SDK.
+ Create a runtime image and use the `java` from the image to execute your project. This is the recommended solution.

#### Adding a Module SDK

If you happen to have the SDK for a module installed and you don't want to build a custom runtime image, you can add the path to the SDK to your `project.clj` file on with the `:jlink-sdk-paths` key. For example...

    :jlink-sdk-paths ["C:\\Program Files\\Java\\javafx-sdk-14\\lib"]
    
That being said, building a custom image might be easier since you don't need to install anything except the modules.
    
### Buidling a Custom Runtime Environment

A custom runtime environment will build a new Java runtime for your project, including _only_ the modules your project uses. If you have external modules but lack access to an SDK, you will _need_ to create a custom runtime in order to actually run your project.

First you need to let the plugin know that you want to use a custom runtime when interacting with your project. Set the `:jlink-custom-jre` key in your `project.clj` to `true`.

    :jlink-jre-image true
    
This flag also lets the middleware know that it shouldn't use the `java` available in `JAVA_HOME` but should use the `java` in the custom runtime image instead.

Create a custom Java environment with the plugin's `init` task. It will call out to the `jlink` tool that is bundled with your JDK to create a new "runtime image". This image will be in the "jlink" directory at the root of your project.

    lein jlink init

By default, the plugin will create a basic Java environment with only base module (the `java.base`), which is only 29MB. This represents the minimum and it might be enough for your Clojure application, if you don't use any other modules. If you do use other modules, you can add them through the `:jlink-modules` key in your `project.clj`. You don't need to include `java.base`, that one is automatically pulled in by the plugin.

With your custom runtime created, you can now build and run your project with the regular Leiningen commands.

### Cleaning

If you aren't using any external modules then you can continue to use Leiningen's regular `clean` task. If you are using a image, you will have to ask the plugin to perform its clean task.

    $ lein jlink clean
    
The plugin will remove the image directory and then call out to Leiningen to perform it's regular clean task.

### Running

The plugin's middleware will take care of correctly setting the path to `java` and providing the module options. You can continue to run your project with Leiningen's run task. The only thing to keep in mind is that while the plugin can call Leiningen's task, we can't alter Leiningen's task to call the plugin. That is, if you are using an image then you need to make sure you create the image before you try to run your project.

    $ lein jlink init
    $ lein run


### Assembling and Packaging

By running `lein jlink assemble`, we create a uberjar and copy it into the custom runtime. Once this step is complete, your image will have everything it needs to run your application. You can test it out from the console.

    $ cd image
    $ bin\java -jar my-uberjar.jar
    
Your application will launch and it will have access to all of the required modules. You may customize the name of the directory that's used to hold your custom JRE image with the `:jlink-jre-image` key in your `project.clj`.

### Create a Docker Image

The custom image directory can be copied into a docker image for distribution. I have created a minimal base image
using [Alpine Linux][9], which is only 12.3MB and is suitable for running many applications. `;-)`

Assume your application is called `jlinktest`, you can create a Dockerfile that looks like this...

```Dockerfile
FROM sunng/alpine-jlink-base

ADD target/default/jlink /opt/jlinktest
ENTRYPOINT /opt/jlinktest/bin/jlinktest
```

The result image size can be less than 50MB!

## License

Copyright © 2018 Ning Sun

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[0]: https://leiningen.org/
[1]: https://www.oracle.com/corporate/features/understanding-java-9-modules.html
[2]: https://openjdk.java.net/projects/jigsaw/quick-start
[3]: https://www.docker.com/
[4]: https://github.com/sunng87/ring-jetty9-adapter
[5]: https://adoptopenjdk.net/
[6]: https://openjfx.io/
[7]: https://gluonhq.com/products/javafx/
[8]: https://vividcode.io/package-java-applications-using-jpackage-in-jdk-14/
[9]: https://github.com/sunng87/alpine-jlink-base

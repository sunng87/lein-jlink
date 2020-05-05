# lein-jlink

A [Leiningen][0] plugin for working with Java modules

[![Clojars Project](https://img.shields.io/clojars/v/lein-jlink.svg)](https://clojars.org/lein-jlink)

## Motivation

The [Java Platform Modules System (JPMS)][1], commonly referred to as just "modules",  have been a [part of the platform since Java 9][2]. Modules are bigger than packages and it's likely that some of your favorite Java packages (like "java.sql") have been broken out into modules. Many modules are bundled with the standard runtime and development kits for Java (like "java.sql"), you don't have to do much of anything special to use the. Others are now distributed separately and need to be managed specially if you want to use them, for instance, JavaFX.

This plugin provides some additional tasks and middleware for Leiningen that makes it easier to interact with the Java module system. It makes it easier to include modules in your project and to interact with the module-specific tool like `jlink`.

In addition to making it easier to interact with external modules, `jlink` also provides an way to build a customized Java runtime that includes _only_ the modules your project needs to run. You can then package this smaller runtime with your application to provide self-contained distribution package.

If you are packaging your application for use in a [Docker][3] image, this feature is a pretty good news for you: The minimal JRE is only 29MB. And it's enough to run a hello world Ring application, using [`jetty-adapter`][4]. The overall size (app + dependency jars + custom runtime) is only 37MB, and 22MB in gzipped tarball.

## Usage

First you need to migrate to Java 9 or newer. You can get the latest development kit from [Adopt OpenJDK][5]:

+ [Download JDK from AdoptOpenJDK](https://adoptopenjdk.net/)

This plugin assumes that you have set a valid `JAVA_HOME` environment variable and that it will be available when Leiningen runs. If you don't have it set, go ahead and set `JAVA_HOME` now. The plugin uses this variable to find the Java modules distributed with your JDK as well as to locate tools like `jlink` and `jpackage`. If you prefer to use another JDK, you can provide it with the `jlink-jdk-path` on your `project.clj` file.

Next, add this plugin to your `project.clj` file in the `:plugins` section.

    [lein-jlink "0.2.1"]
    
Then add the middleware into the `:middleware` section.

    [leiningen.jlink/middleware]
    
Your project should look something like this...

    {defproject myorganization/myproject
      ...
      :plugins    [...
                    [lein-jlink "0.2.1"]]
      :middleware [...
                    [leiningen.jlink/middleware]]
      ...
    }
    
The middleware will alter the way Leiningen calls out to `java` and `javac` such that it calls out to the correct ones and that these calls include references to the modules that your project needs. By default only the `java.base` module is included, if you need other modules you can simply add them to the `:jlink-modules` key of your `project.clj` file.

    :jlink-modules ["java.sql"]
    
There are many modules distributed with the JDK, you can ask `java` to list them all.

    $ java --list-modules
    
If you only use modules packaged with your JDK, then you can use all of the regular Leiningen commands without issue. When you compile a build a JAR file with `lein build` or execute it with `lein run`, the middleware will make sure that the modules are on the path.
    
### External Modules

If you are using modules that are not distributed with the JDK, you can download and add them to your project with the `:jlink-modules-paths` keyword in your `project.clj`. For instance, if you downloaded the [JavaFX modules][6] from [Gluon's website][7] (referred to as "jmods"), you would unpack them somewhere on your machine and then add a reference to them like so...

    :jlink-module-paths ["/opt/java/javafx-jmods-14.0.1"]
    
If you are running on Windows, you will want to escape the `\` character in your paths with `\\`. For example:

    :jlink-module-paths ["C:\\Program Files\\Java\\javafx-jmods-14.0.1"]
    
External modules _only_ work at compile time, they cannot be passed to your default `java` command at runtime. There are two solutions:

+ You could download and install the SDK for the module. This plugin supports this but not all modules provide an SDK.
+ Create a runtime image and use the `java` command from the image to execute your project; this is the recommended solution.

#### Adding a Module SDK

If you happen to have the SDK for a module installed and you don't want to build a custom runtime image, you can add the path to the SDK to your `project.clj` file on with the `:jlink-sdk-paths` key. For example...

    :jlink-sdk-paths ["C:\\Program Files\\Java\\javafx-sdk-14\\lib"]
    
That being said, building a custom image might be easier since you don't need to install anything except the modules.
    
### Buidling a Custom Runtime Environment

The plugin will build up a custom runtime environment (Java runtime) for your project, including _only_ the modules your project uses. If you have external modules but lack access to an SDK, you will _need_ to create a custom runtime in order to actually run your project.

Create a custom Java environment with the plugin's `init` task. It will call out to the `jlink` tool that is bundled with your JDK to create a new "runtime image". This image will be in the "image" directory at the root of your project. If you would like to store the runtime image in a different location, you can provide that location with the `:jlink-image-path` key in your `project.clj` file.

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


### Assembling

By running `lein jlink assemble`, we call out to Leiningen to create an uberjar and then move it into the custom runtime image directory and then create scripts to launch your project. Once this step is complete, your image will have everything it needs to run your application. You can test it out from the console.

    $ cd image
    $ bin\java -jar my-uberjar.jar
    
Your application will launch and it will have access to all of the required modules.

### Packaging

Lastly you may package your custom runtime, uberjar and launcher scripts into one archive for distribution.

    $ leink jlink package

The plugin will create a GZIPped TARball of the image by default, if you need a ZIP archive you can set the `:jlink-archive` key to `"zip"`.

### Create a Docker Image

The custom image directory can be copied into a docker image for distribution. We have created a minimal base image using [Alpine Linux][9], which is only 12.3MB and is suitable for running many applications. `;-)`

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

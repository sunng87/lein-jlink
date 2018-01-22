# lein-jlink

A Leiningen plugin for custom Java environment.

[![Clojars Project](https://img.shields.io/clojars/v/lein-jlink.svg)](https://clojars.org/lein-jlink)

## Usage

Put `[lein-jlink "current-version"]` into the `:plugins` vector of your project.clj.

Create a default Java environment:

```
$ lein jlink init
```

By default, we create a basic Java environment with only base module
(the `java.base`), which is only 29MB. That's could enough for your
clojure application.

To add more modules to your jlink environment, add them to
`:jlink-modules` vector, like `["java.base" "java.sql"]`.

To add more module path, use `:jlink-module-path ["lib/"]`.

## License

Copyright © 2018 Ning Sun

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

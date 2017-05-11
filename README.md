This sbt-appengine is a sbt 0.13.x port of sbt/sbt-appengine.
Improvements are:
- Automatic download of correct appengine sdk
- No more deprecated code

Changes:
- Removed dependency on spray.revolver
- Removed data nucleus enhancer (as its use is discouraged by google)

requirements
------------

use sbt 0.13+

setup
-----

Download or clone this repository and then execute
```
sbt -batch publishLocal
```
in its root directory.

Then put the following in `project/appengine.sbt` or `project/plugins.sbt` in your project:

```scala
addSbtPlugin("com.cornim" % "sbt-appengine" % "0.7.2")
```

put the following in your `build.sbt`:

```scala
enablePlugins(AppenginePlugin)
```

Note that I cannot make this plugin auto-trigger since it depends on 
the WarPlugin of xsbt-web-plugin which is not auto-triggering.


usage
-----

### deploy

you can deploy your application like this:

    > gaeDeploy

### development server

to (re)start the development server in the background:

    > gaeDevServer

by default development server runs in debug mode. IDE can connect to it via port 1044.

### backend support

you can deploy your backend application(s) like this:

    > gaeDeployBackends
    
to start a backend instance in the cloud:

    > gaeStartBackend <backend-name>
    
to stop a backend instance:

    > gaeStopBackend <backend-name>

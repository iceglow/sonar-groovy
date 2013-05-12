Sonar Groovy
============

This is an updated Sonar Groovy plugin. It uses Jacoco for Java 7 support, but expects you to generate the Jacoco report, it doesn't attempt to instrument your application.

It is now Grails-aware.

It uses 0.18.1 of the CodeNarc plugin, GMetrics is retained for its obvious uses,

Building it
-----------

Just use:

    mvn clean package

And then copy the resulting jar file into your  $SONAR/extensions/plugin directory


Description
-----------

It works around Sonar's problem of only supporting the source directories defined in Maven itself - which is specifically a problem of projects like Grails.

The Source Importer runs first and collects source directories and files. It holds onto these and these are used (and Sonar's ones from its Project are ignored).

For Groovy, it will automatically look for

 * src/main/groovy
 * src/test/groovy

For Grails, it will automatically look for all of the standard directories (src/groovy, src/java, grails-app/*)

Overriding
----------

You can add extra directories and override directories:

* -Dsonar.grails.testDirs is a ":" separated list of test directories to use instead of test/unit, test/integration for Grails. If you have Spock or Easyb tests elsewhere, this can be useful.
* -Dsonar.grails.dirs is a ":" separated list which replaces completely the list of directories it will look in. Use
* -Dsonar.grails.extraDirs to provide a ":" separated list for extra directories on top of the standard Grails ones

Grails
------

A few useful things:

* Make sure you set the Surefire directory to target/test-reports if you are using Grails.
* If you are wanting to use Jacoco and you use Maven like we do, Jacoco has no dynamic agent loading capability, it only has a Pre-Main in its definition, no Agent-Main. This means you really need to instrument at the Maven level,
and Sonar/Groovy will just drop any instrumentation for files it can't use. To do this, we set on our build server:

    export MAVEN_OPTS="-Xmx1024M -XX:MaxPermSize=512M -javaagent:$HOME/jacoco/lib/jacocoagent.jar=destfile=target/jacoco.exec"

And then removed it for the next stage of the build cycle - when you do sonar:sonar, the Hibernate code spits exceptions everywhere.

License
-------
 To get Jacoco working with Groovy, I had to grab the source from Maven Central as it has disappeared from source repos. Its core problem is that it does not recognize non-Java files (it is hard coded to only accept JavaFile objects),
 so it has now been generalized. However there are two caveats:

 * I had to change the license header so that the Sonar parent would accept it. The license is essentially the same, I just copied it from the other files, its still LGPL3, but this is in no way an attempt to relicense code I don't
 own. Just to get a package out, and I'm sharing my changes.
 * The AbstractAnalyzer has had to be changed to get around the stupid problem of Sonar when using Maven only supporting the internal Maven directories (you can't even override it with sonar.source)



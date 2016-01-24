# Introduction #
Java logging gives me a considerable headache. This page is an attempt to provide detailed information on The Fascinator's use of the various logging libraries available, and how the end-user can make use of it all.

Some of the 'opinions' below are unsupported by references, but represent a considerable amount of practical usage and research into logging solutions. If your opinions differ, [please let us know](http://groups.google.com/group/the-fascinator-dev), and help us make the software even better.

First, some brief information about the libraries in question:
  * [Log4j](http://logging.apache.org/log4j/1.2/): Ye 'olde popular logging framework. Historical predecessor to logback, but many libraries still use it, including of course, older versions of libraries that have now migrated away.
  * [JCL](http://commons.apache.org/logging/): Jakarta Commons Logging, also known as Apache Commons Logging, or more frequently, just 'commons logging' or JCL. This is a wrapper for other frameworks, such as log4j. It has mostly (broad generalisation) fallen out of favour amongst developers to make way for the more modern SLF4J. Again though, it is still in fairly broad use amongst code libraries.
  * [SLF4J](http://www.slf4j.org/): The Simple Logging Facade for Java; similar to JCL it is a wrapper for other logging implementations, although it was purpose built to be such, whereas JCL just [ended up](http://radio-weblogs.com/0122027/2003/08/15.html) becoming a pseudo-framework.
    * Remember that this is an API, so it will always need to be accompanied by an implementation (or binding). Ideally, you only ever want a [single binding on your classpath](http://www.slf4j.org/codes.html#multiple_bindings), which is where a lot of our work is focused.
    * The project does make available several viable implementations, such as the core JDK implementation and several bridges (detailed later). It is very popular, and considered to be more efficient and robust then most alternatives.
  * [Logback](http://logback.qos.ch/): is the successor to log4j and is the native implementation of SLF4J (all three projects have the [same founder](http://en.wikipedia.org/wiki/Ceki_G%C3%BClc%C3%BC)). This is The Fascinator's logging implementation of choice.

## All Roads Lead to Logback ##
The biggest challenge faced when bringing together a wide variety of JARs and WARS to make a single application server is that they all bring their own dependencies to the table. We need to route all of this logging activity into a single framework if we want to see log entries from every part of the code.

In the [olden days](http://ant.apache.org/) you'd distribute you applications with specifically chosen JARs and WARs that you could modify to your hearts content to ensure that they all played nicely together.

[These days](http://maven.apache.org/) of course, we don't want to go carting around binary JAR files with each piece of software, but it also means that there is a lot of legacy code out there that just assumes if you don't want to do things their way you will manually hack the JARs/WARs apart yourself. We need some solutions for this, and thankfully we mostly have them (below).

### Logging Bridges ###
Before we go into gory details however... what are we actually trying to achieve? Well first, we need to remove all references to logging frameworks we don't want on our classpath and replace them with logging bridges.

These bridges are valid implementations of each framework, but they have all their real logging gutted and redirect to SLF4J, and from there it all goes to logback.

This lets us provide a single point of configuration for the entire application (in theory).

The bridges themselves are easy, since we just add them as a dependency at the top level of the application:
```
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>1.6.1</version>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-core</artifactId>
    <version>0.9.29</version>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>0.9.29</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>jcl-over-slf4j</artifactId>
    <version>1.6.1</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>log4j-over-slf4j</artifactId>
    <version>1.6.1</version>
</dependency>
```

So this config gets us the SLF4J API (but not an implementation), followed by the two logback JARs (our implementation) and bridges for JCL (`jcl-over-slf4j`) and log4j (`log4j-over-slf4j`) logging.

### Excluding Dependencies ###
More annoying then the bridges however, is the need to remove other logging implementations from the classpath. If we leave commons logging JARs sitting around we can't be sure that the Java class loader will find our bridge JAR over the commons logging one.

#### The Easy Way ####
The easiest way to avoid bringing in these JARs is with some top-level declarations for the project indicating that these JARs will provided, like so:
```
<dependency>
  <groupId>commons-logging</groupId>
  <artifactId>commons-logging</artifactId>
  <version>1.1.1</version>
  <scope>provided</scope>
</dependency>
```

So when Maven goes to resolve the commons-logging dependency for any lower POMs it will already have a reference telling it that it doesn't need to go get this JAR.

#### The Slow Way ####
Alternative to this, you can also exclude a dependency from being loaded when you go to load a library, like so:
```
<dependency>
    <groupId>commons-httpclient</groupId>
    <artifactId>commons-httpclient</artifactId>
    <exclusions>
        <exclusion>
            <groupId>commons-logging</groupId>
            <artifactId>commons-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

Here we are saying that we do need the Commons HTTP library, but we don't want it to fetch the commons-logging JAR on which HTTP depends. At runtime, we expect it will instead make use of our jcl-over-slf4j bridge.

Why would you do it this way? I touch on this a little further in 'Multi-module Projects' below, but I was also somewhat attracted to the idea that it encapsulated the configuration in the locations that necessitated its inclusion (ie. we are not just excluding commons-logging, we excluding it because library XYZ is trying to bring it in) and it also forces awareness of where the logging 'plumbing' is going throughout the application.

That all said, I'm constantly arguing with myself over this, and could be convinced that 'easier is better' if enough people agree.

### How to 'Un-Bundle' a WAR ###
This problem is somewhat trickier. A good example is Solr (v3.3.0 at time of writing) which is distributed as a WAR file containing all dependencies (including slf4j-jdk14, the JDK implementation of SLF4J).

Before we addressed our logging problems we would deploy the Solr WAR directly to Jetty and letting nature run its course:
```
<dependency>
    <groupId>org.apache.solr</groupId>
    <artifactId>solr</artifactId>
    <type>war</type>
    <version>3.3.0</version>
</dependency>
...
<plugin>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>2.1</version>
    <executions>
        <execution>
            <id>copy-webapp</id>
            <phase>package</phase>
            <goals>
                <goal>copy</goal>
            </goals>
            <configuration>
                <outputDirectory>${dir.server}/webapps</outputDirectory>
                <artifactItems>
                    <artifactItem>
                        <groupId>org.apache.solr</groupId>
                        <artifactId>solr</artifactId>
                        <type>war</type>
                    </artifactItem>
                </artifactItems>
            </configuration>
        </execution>
    </executions>
</plugin>
```

With this 'solution' of course, we are leaving the WAR sitting on the classpath, and the JDK JAR would get found and continually throw warnings in logs (SLF4J works like this, but it warns you you have two implementations). On some systems it would even go further, because the Java class loader found this JAR first and diverted our logging away from logback. Mucho Bad.

We instead shifted to unpacking the WAR and leaving a '`/solr`' directory in the same location (replace the '`<execution>`' node from above):
```
        <execution>
            <id>unpack-solr-war</id>
            <phase>package</phase>
            <goals>
                <goal>unpack</goal>
            </goals>
            <configuration>
                <!-- Make sure we don't unpack the bundled logging JAR.
                     Fascinator will provide logback instead. -->
                <excludes>WEB-INF/lib/slf4j-jdk14-1.6.1.jar</excludes>
                <outputDirectory>${dir.server}/webapps/solr</outputDirectory>
                <artifactItems>
                    <artifactItem>
                        <groupId>org.apache.solr</groupId>
                        <artifactId>solr</artifactId>
                        <type>war</type>
                    </artifactItem>
                </artifactItems>
            </configuration>
        </execution>
```

The line of particular importance is here:
```
<excludes>WEB-INF/lib/slf4j-jdk14-1.6.1.jar</excludes>
```
Where we told Maven to exclude that one JAR during the unpack process, thus removing it from the classpath.

## Multi-module Projects ##
Something to keep in mind with regards to logging is The Fascinator's [Maven build structure](https://sites.google.com/site/fascinatorhome/home/documentation/technical/details/maven-structure). The main reason I opted for the more time consuming implementation above was because I wanted each component building block to be able to stand on its own.

For example, an individual plugin should be able to be picked up out-of-context and it would still be clear what its logging expectations are, both above and below it in an application stack.

I'm somewhat hopeful as well, that if developers are building their own plugins modeled off core plugins as an example, that they will be more aware of logging and their own requirements in that regard.

## Tiered Configuration ##
With regards to runtime configuration of logback you'll find there are several tiers of configuration in the application.

### Command Line ###
The Core Library has an embedded resource ('`logback.xml`') with fairly basic configuration. This is only here so that the command line utilities like '`HarvestClient`' are configured.

This default configuration is very basic though, just setting some levels and routing everything to STDOUT. This allows your control scripts to redirect to a log file from the command line.

If you wanted to override this configuration for some reasons, the most likely solution would be to provide a '`logback.groovy`' file (as below) on the classpath.

### Developer's Build ###
The Portal WAR has an embedded resource ('`logback.xml`'), primarily to provide meaningful logging to developer's when they start the server using Maven's embedded Jetty server.

If you don't provide some overiding configuration for your project (see below, and that is the recommended situation) then this configuration will probably also be used by your deployed server.

### Deploying a Server ###
Most likely when you deploy a finished server you will want to provide your own logging configuration. The easiest solution is to provide a '[logback.groovy](http://logback.qos.ch/manual/groovy.html)' file somewhere on your classpath.

These groovy scripts load as a higher priority then '`logback.xml`' files, so it will override the other two files on the classpath and ensure your configuration is used instead.

It is possible to do the same thing with a '`logback.xml`' file, but there are two drawbacks here:
  1. This relies on your classpath finding the XML file you provided before the others. In our experience is isn't too difficult to achieve, but it becomes something extra you have to be aware of in configuring your servlet container and overall system.
  1. If cleans logs tickle your fancy, using '`logback.groovy`' stops logback from even looking for your XML files. When it finds multiple XML files in the classpath you will get several warnings in your logs, even if the correct one is chosen.

## Debugging ##
If you are trying to work out why a particular JAR keeps ending up deployed to your server, it is helpful to know how to track down Maven dependencies.

Try running this in your project:
```
mvn dependency:tree
```

It will evaluate all dependencies for your project, back through each dependency and their dependencies (and so on). The result will be pushed to STDOUT as a textual tree structure, eg.:
```
[INFO] au.edu.usq.adfi.geonames:server:war:1.0-SNAPSHOT
[INFO] +- org.apache.solr:solr-core:jar:3.3.0:compile
[INFO] |  +- org.apache.solr:solr-noggit:jar:3.3.0:compile
[INFO] |  +- org.apache.lucene:lucene-analyzers:jar:3.3.0:compile
[INFO] |  +- org.apache.lucene:lucene-highlighter:jar:3.3.0:compile
[INFO] |  +- org.apache.lucene:lucene-memory:jar:3.3.0:compile
[INFO] |  +- org.apache.lucene:lucene-misc:jar:3.3.0:compile
[INFO] |  +- org.apache.lucene:lucene-queries:jar:3.3.0:compile
[INFO] |  |  \- jakarta-regexp:jakarta-regexp:jar:1.4:compile
[INFO] |  +- org.apache.lucene:lucene-spatial:jar:3.3.0:compile
[INFO] |  +- org.apache.lucene:lucene-spellchecker:jar:3.3.0:compile
[INFO] |  +- org.apache.lucene:lucene-grouping:jar:3.3.0:compile
[INFO] |  +- org.apache.solr:solr-commons-csv:jar:3.3.0:compile
[INFO] |  +- org.apache.geronimo.specs:geronimo-stax-api_1.0_spec:jar:1.0.1:compile
[INFO] |  +- commons-codec:commons-codec:jar:1.4:compile
[INFO] |  +- commons-fileupload:commons-fileupload:jar:1.2.1:compile
[INFO] |  +- commons-httpclient:commons-httpclient:jar:3.1:compile
[INFO] |  |  \- commons-logging:commons-logging:jar:1.0.4:compile
[INFO] |  +- commons-io:commons-io:jar:1.4:compile
[INFO] |  +- commons-lang:commons-lang:jar:2.4:compile
[INFO] |  +- org.apache.velocity:velocity:jar:1.6.4:compile
[INFO] |  |  +- commons-collections:commons-collections:jar:3.2.1:compile
[INFO] |  |  \- oro:oro:jar:2.0.8:compile
[INFO] |  +- org.apache.velocity:velocity-tools:jar:2.0:compile
[INFO] |  |  +- commons-beanutils:commons-beanutils:jar:1.7.0:compile
[INFO] |  |  +- commons-digester:commons-digester:jar:1.8:compile
[INFO] |  |  +- commons-chain:commons-chain:jar:1.1:compile
[INFO] |  |  +- commons-validator:commons-validator:jar:1.3.1:compile
[INFO] |  |  +- dom4j:dom4j:jar:1.1:compile
[INFO] |  |  +- sslext:sslext:jar:1.2-0:compile
[INFO] |  |  +- org.apache.struts:struts-core:jar:1.3.8:compile
[INFO] |  |  |  \- antlr:antlr:jar:2.7.2:compile
[INFO] |  |  +- org.apache.struts:struts-taglib:jar:1.3.8:compile
[INFO] |  |  \- org.apache.struts:struts-tiles:jar:1.3.8:compile
[INFO] |  +- org.slf4j:slf4j-api:jar:1.6.1:compile
[INFO] |  \- org.slf4j:slf4j-jdk14:jar:1.6.1:compile
[INFO] +- org.apache.solr:solr-solrj:jar:3.3.0:compile
[INFO] |  +- org.apache.lucene:lucene-core:jar:3.3.0:compile
[INFO] |  +- org.apache.zookeeper:zookeeper:jar:3.3.1:compile
[INFO] |  |  +- log4j:log4j:jar:1.2.15:compile
[INFO] |  |  |  \- javax.mail:mail:jar:1.4:compile
[INFO] |  |  |     \- javax.activation:activation:jar:1.1:compile
[INFO] |  |  \- jline:jline:jar:0.9.94:compile
[INFO] |  |     \- junit:junit:jar:3.8.1:compile
[INFO] |  \- org.codehaus.woodstox:wstx-asl:jar:3.2.7:compile
[INFO] |     \- stax:stax-api:jar:1.0.1:compile
[INFO] +- ch.qos.logback:logback-core:jar:0.9.17:compile
[INFO] +- ch.qos.logback:logback-classic:jar:0.9.17:compile
[INFO] \- javax.servlet:servlet-api:jar:2.5:compile
```

In a large project you can get overwhelmed by the amount of information that comes through here, but you can always ask Maven to filter the output with something like '`-Dincludes=commons-logging`'.

Personally I prefer to see the whole picture, but redirect the output to a file and use tools like '`grep`' or a log file browser to look for all the different logging options. My personal favourite is [BareTail](http://www.baremetalsoft.com/baretail/), which I configured to highlight the different logging JARs, each with their own colour, making it very easy to see problems, as well as 'good' JARs like the bridges. YMMV.

The net result is that you want to have some idea of where the logging JARs are coming from:
```
[INFO] au.edu.usq.adfi.geonames:server:war:1.0-SNAPSHOT
[INFO] +- org.apache.solr:solr-core:jar:3.3.0:compile
[INFO] |  +- commons-httpclient:commons-httpclient:jar:3.1:compile
[INFO] |  |  \- commons-logging:commons-logging:jar:1.0.4:compile
[INFO] |  +- org.slf4j:slf4j-api:jar:1.6.1:compile
[INFO] |  \- org.slf4j:slf4j-jdk14:jar:1.6.1:compile
[INFO] +- org.apache.solr:solr-solrj:jar:3.3.0:compile
[INFO] |  +- org.apache.zookeeper:zookeeper:jar:3.3.1:compile
[INFO] |  |  +- log4j:log4j:jar:1.2.15:compile
[INFO] +- ch.qos.logback:logback-core:jar:0.9.17:compile
[INFO] +- ch.qos.logback:logback-classic:jar:0.9.17:compile
```

And from there you can return to the earlier suggestions on how to deal with them. ie. In this case we'd need to:
  * exclude the '`log4j`' JAR when loading the '`solr-solrj`' dependency.
  * exclude the '`slf4j-jdk14`' JAR and '`commons-logging`' when loading the '`solr-core`' dependency.
  * Depending on where the implementation will be in your application is might be worth adding '`log4j-over-slf4j`' and '`jcl-over-slf4j`' bridges to this project to replace '`log4j`' and '`commons-logging`'.
  * The '`slf4j-api`' JAR is fine, and the '`logback`' JARs... although it is worth checking versions as well and deciding whether you want to be explicit, or let Maven handle version clashes (I leave it to Maven, unless it is causing a problem).

## Outstanding Problems ##
The world is not yet perfect, and there are some annoyances yet to tracked down and eliminated.

Take Jetty for example, which has an internal logging implementation unrelated to the others. There are some resources available for solutions though:
  * [logback-access](http://logback.qos.ch/access.html) can be used to get Jetty configured via a non-standard logback config file. I could never get it to work, and Jetty crashes without any errors or logs when I try, so debugging is tedious, blind work.
  * There are also some (possibly old) [indications](http://www.qos.ch/pipermail/logback-user/2010-August/001741.html) that this will not work with groovy scripts or context selectors (see below).

With multiple applications deployed inside Jetty, or any servlet container, logging can get a bit convoluted. In [Mint](http://code.google.com/p/redbox-mint/) for example, we have a '`/solr`' context deployed for The Fascinator, and a '`/geonames`' context deployed for [geo-spatial lookups](http://code.google.com/p/solr-geonames/). This second context is running its own embedded Solr index, causing a mess if you are trying to separate them via logging.

Jetty and Logback do have some solutions, using [JNDI and Context Selectors](http://logback.qos.ch/manual/contextSelector.html), which I tinkered with for a while. The problem is that to implement them completely you have to modify the '`web.xml`' file for each context, which is somewhat 'hacky' again. Remember that we are deploying this server from a Maven build script, not from our own hacked repository of JARs/WARs.

I suspect there is a solution here, but as yet have not had time to get all the planets to align. Ideally I would like a programmatic way of setting '`web.xml`' values, or anything similar, so that JNDI and '`logback-access`' can see them. This would allow me finer grained control over logging for each context. For now '`/solr`' and '`/geonames`' log to the same file, and on !ReDBox (which doesn't have '`/geonames`') the same configuration generates no Solr logs at all... go figure.

I'd also like to get Jetty shifted to '`logback-access`', but this is a minor annoyance. Separate or not, Jetty's logging works well and lives peacefully alongside the core Fascinator logs.
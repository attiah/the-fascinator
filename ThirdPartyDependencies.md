# Introduction #
This list contains information on all current third party dependencies in use by The Fascinator. Developers who add new dependencies should try to keep this list up to date.

For clarity, a 'third party' dependency is one that is not typically available via the standard Maven dependency mechanisms, so we need to create entries in a Maven repository somewhere (in this case Sonatype) on their behalf.

## Build process ##
Unless otherwise noted, the build process for each bundle simply follows the Sonatype documentation.

```
mvn source:jar javadoc:jar package gpg:sign repository:bundle-create
```

Upload and release the resulting bundle [as per documentation](https://docs.sonatype.org/display/Repository/Uploading+3rd-party+Artifacts+to+Maven+Central).


---

### OAI4J ###
**Web page:** http://oai4j-client.sourceforge.net/<br />
**Used by:** OAI-PMH Harvester<br />
**Build process:**<br />
Download the source, currently version 0.6 SNAPSHOT:
```
https://oai4j-client.svn.sourceforge.net/svnroot/oai4j-client
```

Inside the `trunk` folder modify `pom.xml` to add the required elements for a Sonatype upload. Most is new, although `<url>` has been modified to a more relevant location and the version has been modified to match the [beta 1 JAR](http://sourceforge.net/projects/oai4j-client/files/oai4j-client/OAI4J%200.6%20Beta%201/) from the sourceforge site:
```
    <version>0.6b1</version>
    ...
    <url>http://oai4j-client.sourceforge.net/</url>
    <description>OAI4J is a Java library that implements a client API for the OAI-PMH standard specification from the Open Archives Initiative. It also has support for the upcoming OAI-ORE specification.</description>
    <licenses>
        <license>
            <name>Apache License V2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
    </licenses>
    <scm>
        <url>http://oai4j-client.svn.sourceforge.net/viewvc/oai4j-client/</url>
        <connection>scm:svn:https://oai4j-client.svn.sourceforge.net/svnroot/oai4j-client</connection>
    </scm>

    <developers>
        <developer>
            <id>bobcat_zed</id>
            <name>Oskar</name>
            <url>http://bobcat_zed.users.sourceforge.net/</url>
            <roles>
                <role>Admin</role>
                <role>Developer</role>
            </roles>
        </developer>
        <developer>
            <id>pangloss</id>
            <name>E S</name>
            <url>http://pangloss.users.sourceforge.net/</url>
            <roles>
                <role>Admin</role>
            </roles>
        </developer>
    </developers>
```
**Maven Central:** http://search.maven.org/#browse|-907833221


---

### Jython Standalone 2.5.1 ###
**Web page:** http://www.jython.org/<br />
**Used by:** Solr Indexer, Jython Transformer<br />
**Build process:**<br />
Be forewarned, this process is not painless. The Standalone JAR is only available via the install process (ie. cannot be built directly) but does not include source or JavaDoc JARs (ie. must be built directly).

#### Installing the base JAR ####
Follow the [installation instructions](http://wiki.python.org/jython/InstallationInstructions) for [Jython 2.5.1](http://sourceforge.net/projects/jython/files/jython/2.5.1/jython_installer-2.5.1.jar/download) in 'Standalone Mode'.

I renamed the resulting JAR to `jython-standalone-2.5.1.jar`. Now you also need a POM to go with this JAR (same directory is fine... we'll move them both later):
```
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.python</groupId>
    <artifactId>jython-standalone</artifactId>
    <version>2.5.1</version>
    <packaging>jar</packaging>

    <name>Jythone Standalone 2.5.1</name>
    <url>http://www.jython.org/</url>
    <description>The Jython Standalone installation is a Jython JAR also containing all Python standard libraries. The JAR can be recreated by following the 'Standalone mode' installation instructions: http://wiki.python.org/jython/InstallationInstructions.</description>

    <licenses>
        <license>
            <name>The Jython License</name>
            <url>http://www.jython.org/license.html</url>
        </license>
    </licenses>

    <scm>
        <url>http://hg.python.org/jython</url>
        <connection>scm:hg:http://hg.python.org/jython</connection>
    </scm>

    <developers>
        <developer>
            <name>Various</name>
            <url>http://wiki.python.org/jython/WhosDoingWhat</url>
            <roles>
                <role>Admin</role>
                <role>Developer</role>
            </roles>
        </developer>
    </developers>

    <reporting>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
                <configuration>
                    <links>
                        <value>http://java.sun.com/javase/6/docs/api/</value>
                    </links>
                </configuration>
            </plugin>
        </plugins>
    </reporting>

</project>
```

#### Building from Source ####
Checkout the 2.5.1 tagged release from here: https://jython.svn.sourceforge.net/svnroot/jython/tags/Release_2_5_1

All the work we are concerned with is in the `jython` directory. Start by creating a `maven-bundle` sub-directory there. You'll note that there is already a `maven` folder from earlier work in the community but I've ignored this for several reasons:
  * The bundle JARs aren't signed (trivial to fix).
  * No JavaDoc (trivial to fix).
  * Incomplete POM (trivial to fix).
  * **Not for the Standalone JAR** (bugger!).

Perhaps someone from the Jython community could fix this more easily, but after struggling with the problem for more then a day I just took the easiest road... a new bundle using JavaDoc and Source JARs from a build, combined with the officially **installed** binary for the same version.

  1. In the `maven-bundle` sub-directory, copy the installed `jython-standalone-2.5.1.jar` and `pom.xml` we had from earlier.
  1. In the base `jython` directory, create a new `ant.properties` file (contents below).
```
informix.jar=${basedir}/extlibs/ifxjdbc.jar
oracle.jar=${basedir}/extlibs/ojdbc14.jar
svn.revision=6813
svn.main.dir=tags/Release_2_5_1
jython.version=2.5.1
jython.version.noplus=2.5.1 
```
  1. You need to grab a JAR file from IBM (registration required) after installing the [Informix JDBC driver](http://www14.software.ibm.com/webapp/download/search.jsp?go=y&rs=ifxjdbc). I just picked the latest version. Remember we aren't using the resultant binary, but the installed version, so this is just to let the build process function. Copy `ifxjdbc.jar` into the `extlibs` sub-directory.
  1. Direct download [ojdbc14.jar](http://download.oracle.com/otn/utilities_drivers/jdbc/10205/ojdbc14.jar), also into `extlibs`... same reasoning as above.
  1. Modify `build.xml` to add a new target (and dependencies). The `init` target is a modification (in case that's not obvious):
```
    <target name="init">
        ...
        <!-- Standalone Bundling -->
        <property name="jython.source.jar" value="jython-dev-sources.jar" />
        <property name="jython.javadoc.jar" value="jython-dev-javadoc.jar" />
        <property name="maven.bundle.dir" value="${jython.base.dir}/maven-bundle" />
        <property name="maven.bundle.jar" value="${maven.bundle.dir}/jython-standalone-2.5.1-bundle.jar" />
    </target>

    <target name="sources-jar" description="Bundle Java source in a JAR">
        <jar basedir="${source.dir}" destfile="${dist.dir}/${jython.source.jar}"/>
    </target>
    <target name="javadoc-jar" depends="javadoc" description="Bundle JavaDoc in a JAR">
        <jar basedir="${apidoc.dir}" destfile="${dist.dir}/${jython.javadoc.jar}"/>
    </target>

    <target name="maven-bundle" depends="jar,sources-jar,javadoc-jar">
        <copy file="${dist.dir}/${jython.source.jar}" tofile="${maven.bundle.dir}/jython-standalone-2.5.1-sources.jar" overwrite="true"></copy>
        <copy file="${dist.dir}/${jython.javadoc.jar}" tofile="${maven.bundle.dir}/jython-standalone-2.5.1-javadoc.jar" overwrite="true"></copy>

        <exec executable="gpg"><arg value="--yes" /><arg value="-ab" /><arg value="${maven.bundle.dir}/jython-standalone-2.5.1.jar" /></exec>
        <exec executable="gpg"><arg value="--yes" /><arg value="-ab" /><arg value="${maven.bundle.dir}/pom.xml" /></exec>
        <exec executable="gpg"><arg value="--yes" /><arg value="-ab" /><arg value="${maven.bundle.dir}/jython-standalone-2.5.1-sources.jar" /></exec>
        <exec executable="gpg"><arg value="--yes" /><arg value="-ab" /><arg value="${maven.bundle.dir}/jython-standalone-2.5.1-javadoc.jar" /></exec>

        <jar destfile="${maven.bundle.jar}">
            <fileset file="${maven.bundle.dir}/pom.xml" />
            <fileset file="${maven.bundle.dir}/*jar" />
            <fileset file="${maven.bundle.dir}/*asc" />
        </jar>
    </target>
```

After all this you should finally be ready to run:
```
ant maven-bundle
```

This will create Source and JavaDoc JARs from the source code and copy them beside the binary JAR and POM we already copied. Then it will sign all of the files (prompting you for a passphrase if appropriate) and bundle the results together.

The bundle can be directly upload to Sonatype, no Maven executions required.

**Background Information:** I found [many](http://old.nabble.com/Please-deploy-version-2.5.2-to-Maven-repository-td31369024.html) [different](http://old.nabble.com/Jython-2.5.1-missing-from-Maven-td26857910.html) [requests](http://old.nabble.com/2.5.1-Maven-Central-upload-td28037871.html#a28037871) to have up-to-date versions of Jython artifacts hosted somewhere... they all appear to have been ignored. No mention of standalone JARs.

**Maven Central:** http://search.maven.org/#browse|1471889018


---

### Jython Standalone 2.5.2 ###
**Web page:** http://www.jython.org/<br />
**Used by:** Portal<br />
**Build process:**<br />
Fairly simple after doing v2.5.1... ~~no idea why we have two different version. Perhaps we should look into that?~~ _Greg: v2.5.2 was required for `threadLocalStateInterpreter()`, and I vaguely recall a conversation with Oliver about this. Something about memory leaks and/or non-thread-safe behaviour in v2.5.1 when Oliver was addressing the caching of Python objects in the Java layer._

#### Installing the base JAR ####
Follow the [installation instructions](http://wiki.python.org/jython/InstallationInstructions) for Jython 2.5.2 in 'Standalone Mode'.

I renamed the resulting JAR to `jython-standalone-2.5.2.jar`. Now you also need a POM to go with this JAR (same directory is fine... we'll move them both later):
```
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.python</groupId>
    <artifactId>jython-standalone</artifactId>
    <version>2.5.2</version>
    <packaging>jar</packaging>

    <name>Jythone Standalone 2.5.2</name>
    <url>http://www.jython.org/</url>
    <description>The Jython Standalone installation is a Jython JAR also containing all Python standard libraries. The JAR can be recreated by following the 'Standalone mode' installation instructions: http://wiki.python.org/jython/InstallationInstructions.</description>

    <licenses>
        <license>
            <name>The Jython License</name>
            <url>http://www.jython.org/license.html</url>
        </license>
    </licenses>

    <scm>
        <url>http://hg.python.org/jython</url>
        <connection>scm:hg:http://hg.python.org/jython</connection>
    </scm>

    <developers>
        <developer>
            <name>Various</name>
            <url>http://wiki.python.org/jython/WhosDoingWhat</url>
            <roles>
                <role>Admin</role>
                <role>Developer</role>
            </roles>
        </developer>
    </developers>

    <reporting>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
                <configuration>
                    <links>
                        <value>http://java.sun.com/javase/6/docs/api/</value>
                    </links>
                </configuration>
            </plugin>
        </plugins>
    </reporting>

</project>
```

#### Building from Source ####
Checkout the 2.5.2 tagged release from here: https://jython.svn.sourceforge.net/svnroot/jython/tags/Release_2_5_2

All the work we are concerned with is in the `jython` directory. Start by creating a `maven-bundle` sub-directory there.

  1. In the `maven-bundle` sub-directory, copy the installed `jython-standalone-2.5.2.jar` and `pom.xml` we had from earlier.
  1. In the base `jython` directory, create a new `ant.properties` file (contents below).
```
informix.jar=${basedir}/extlibs/ifxjdbc.jar
oracle.jar=${basedir}/extlibs/ojdbc14.jar
svn.revision=7206
svn.main.dir=tags/Release_2_5_2
jython.version=2.5.2
jython.version.noplus=2.5.2
```
  1. You need to grab a JAR file from IBM (registration required) after installing the [Informix JDBC driver](http://www14.software.ibm.com/webapp/download/search.jsp?go=y&rs=ifxjdbc). I just picked the latest version. Remember we aren't using the resultant binary, but the installed version, so this is just to let the build process function. Copy `ifxjdbc.jar` into the `extlibs` sub-directory.
  1. Direct download [ojdbc14.jar](http://download.oracle.com/otn/utilities_drivers/jdbc/10205/ojdbc14.jar), also into `extlibs`... same reasoning as above.
  1. Modify `build.xml` to add a new target (and dependencies). The `init` target is a modification (in case that's not obvious):
```
    <target name="init">
        ...
        <!-- Standalone Bundling -->
        <property name="jython.source.jar" value="jython-dev-sources.jar" />
        <property name="jython.javadoc.jar" value="jython-dev-javadoc.jar" />
        <property name="maven.bundle.dir" value="${jython.base.dir}/maven-bundle" />
        <property name="maven.bundle.jar" value="${maven.bundle.dir}/jython-standalone-2.5.2-bundle.jar" />
    </target>

    <target name="sources-jar" description="Bundle Java source in a JAR">
        <jar basedir="${source.dir}" destfile="${dist.dir}/${jython.source.jar}"/>
    </target>
    <target name="javadoc-jar" depends="javadoc" description="Bundle JavaDoc in a JAR">
        <jar basedir="${apidoc.dir}" destfile="${dist.dir}/${jython.javadoc.jar}"/>
    </target>

    <target name="maven-bundle" depends="jar,sources-jar,javadoc-jar">
        <copy file="${dist.dir}/${jython.source.jar}" tofile="${maven.bundle.dir}/jython-standalone-2.5.2-sources.jar" overwrite="true"></copy>
        <copy file="${dist.dir}/${jython.javadoc.jar}" tofile="${maven.bundle.dir}/jython-standalone-2.5.2-javadoc.jar" overwrite="true"></copy>

        <exec executable="gpg"><arg value="--yes" /><arg value="-ab" /><arg value="${maven.bundle.dir}/jython-standalone-2.5.2.jar" /></exec>
        <exec executable="gpg"><arg value="--yes" /><arg value="-ab" /><arg value="${maven.bundle.dir}/pom.xml" /></exec>
        <exec executable="gpg"><arg value="--yes" /><arg value="-ab" /><arg value="${maven.bundle.dir}/jython-standalone-2.5.2-sources.jar" /></exec>
        <exec executable="gpg"><arg value="--yes" /><arg value="-ab" /><arg value="${maven.bundle.dir}/jython-standalone-2.5.2-javadoc.jar" /></exec>

        <jar destfile="${maven.bundle.jar}">
            <fileset file="${maven.bundle.dir}/pom.xml" />
            <fileset file="${maven.bundle.dir}/*jar" />
            <fileset file="${maven.bundle.dir}/*asc" />
        </jar>
    </target>
```

After all this you should finally be ready to run:
```
ant maven-bundle
```

This will create Source and JavaDoc JARs from the source code and copy them beside the binary JAR and POM we already copied. Then it will sign all of the files (prompting you for a passphrase if appropriate) and bundle the results together.

The bundle can be directly upload to Sonatype, no Maven executions required.

**Maven Central:** TBA
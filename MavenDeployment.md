# Introduction #

This page is a work-in-progress. Based on the [ReDBox documentation](http://code.google.com/p/redbox-mint/wiki/DeployingMaven) for the same process.

The intention of this page is to show developers what needs to be deployed (and how) to Sonatype's Nexus repository, both for developer snapshots, as well as for migration to Maven Central.

# What gets deployed? #

Each heading below focuses on the parts of the application that need to be separately deployed, and they are listed in order of dependency.

## Organisational POM ##
The organisational POM defines top-level project information, and lists Sonatype's OSS Project POM as its parent. Trunk and tagged releases are available in their own area of subversion:

http://the-fascinator.googlecode.com/svn/project_meta

## Vocabulary Library (OPTIONAL) ##
As per [the documentation](https://sites.google.com/site/fascinatorhome/home/documentation/technical/details/maven-structure), the Vocabulary is in an odd spot in our architecture. In terms of build/release processes this isn't a big deal, but it is worth noting that the POM contains a commented out section for using the Schemagen Plugin. The Schemagen plugin has it's own codebase and documentation (TODO: deploy the site build and link to this somewhere).

**IMPORTANT:** _The Vocabulary Library does not need to be built (or released) independently unless you are altering the underlying vocabularies via the Schemagen Plugin. The VAST majority of developers will never need to do this._

## Third Party Dependencies ##
Any parts of the core platform that depend on external code libraries not currently available via Maven will need to uploaded to Sonatype. We maintain a list on [a separate page](ThirdPartyDependencies.md). This list should be kept up-to-date, so if you are adding a new dependency not only the the upload is required before you release, **but please update the list too**.

## The Fascinator ##
The core system will deploy as one large task, given that it is configured as a multi-module Maven project. There is a small 'gotcha' associated with this that is covered later (following the 'release:prepare' step).


---


# How does deployment work? #

So the nitty-gritty required for a developer to deploy artifacts to Sonatype's Maven repository is fairly basic, although it looks daunting at first, particularly if you aren't that familiar with Maven. This section should highlight the process.

## Background stuff ##
Please refer to [OSS Maven Repository Usage Guide](https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide) for an overview of the Sonatype OSS Maven Repository infrastructure.
  * Steps 1 & 2 are the focus, you need to register and get a login.
  * If you are to release anything you will also need a GPG key (Step 5). Deploying snapshots for basic dev work does not require this.
  * Once you have a login for https://issues.sonatype.org you need to visit https://issues.sonatype.org/browse/OSSRH-1683 and ask a Sonatype staff member to give you access to the project.

## Configuration ##
To make your credentials available to Maven add something like this to your settings.xml file:
```
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>sonatype-nexus-snapshots</id>
            <username>XXXXX</username>
            <password>XXXXX</password>
        </server>
        <server>
            <id>sonatype-nexus-staging</id>
            <username>XXXXX</username>
            <password>XXXXX</password>
        </server>
    </servers>
</settings>
```

## Deploying Snapshots ##
This is pretty easy:
```
mvn clean
mvn deploy
```

If you're working on a SNAPSHOT and that's all you want to deploy, STOP HERE. The rest if for a tagged release.

## Staging a Release ##
You MUST ensure that any dependencies are for released versions and not SNAPSHOTS. This may mean you have to release artifacts from other code bases first.

When you've sorted out dependencies you can start the release procedure below:

```
mvn release:clean
mvn release:prepare
mvn release:perform
```

The prepare step in particular will commit changes through subversion to migrate the current SNAPSHOT to a tagged release and roll forward the SNAPSHOT number on trunk. For this reason you shouldn't ever need to manually alter the version numbers in POMs; the release process will do this for you.

A number of prompts will confirm details regarding the release and it is generally advised to leave this as is. You will also be prompted for you GPG passphrase during the release if you have one (generally a good idea).

**Fascinator**: When deploy the core Fascinator you will run into a problem with releasing multi-module POMs, namely that some of the JARs won't build because their dependencies were never built. To workaround this, got to the '`target/checkout`' directory and run a '`tf rebuild`' to build the version you are about to release. Then re-run '`mvn release:perform`'.

**Another problem**: Depending on your system's default JVM configuration you may run into memory issues during the Maven release process. Try setting this environment variable at the beginning to alleviate problems:
```
set MAVEN_OPTS=-XX:MaxPermSize=128m -Xmx512m
```

## Releasing Artifacts ##
The public release is not completed yet, you've just released to Sonatype's staging repository, so you need to follow [Step 8](https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide#SonatypeOSSMavenRepositoryUsageGuide-8.ReleaseIt) from their release guide, and:
  1. close the [staging repository](https://oss.sonatype.org/index.html#stagingRepositories).
  1. **review** the contents of what you are about to release. **You cannot undo a release** so make sure you don't skip this step.
  1. **release** your closed repository, to have it deployed to Maven Central (~~happens hourly~~; it might be hourly according to the Sonatype doco, but I usually have to wait 5+ hours to see the artifacts in Central).

In between you closing the staging repository and the synch to Maven central, you will see the artifact(s) in the staging area URL below.

## Finding Deployed Artifacts ##
Snapshots: https://oss.sonatype.org/content/repositories/snapshots/com/googlecode/the-fascinator/

Release staging area: https://oss.sonatype.org/content/groups/staging/com/googlecode/the-fascinator/

Maven Central: http://search.maven.org/#browse|-214281614
# Introduction #
I'm starting to look at building a tool similar to the HarvestClient (in that it can be run outside of the server) that would grab a backed up storage directory/plugin and rebuild a system on top of it. There are some old skeletons in Fascinator's closet that will cause problems here depending on the scope however, so I thought I'd put together some thoughts on how to proceed.

# Background #
This is an issue that has [come up before](https://fascinator.usq.edu.au/trac/wiki/tf2/DeveloperNotes/investigations/BackupRestore), and it was very messy then as well. Mostly, it was messy because there are several feature requests converging here that muddy the waters, such as replication, synchronization and migration between multiple instances of The Fascinator that are running at the same time.

The reality however at this stage, is that all deployed instances of the Fascinator (that I am aware of) do not make use of this sort of functionality (just basic, single server deployments)... it was all design work to build towards future ideas.

### Problem 1: No Solr Index ###
Existing in-system scripts that re-index or re-harvest the entire system are critically flawed. They rely on the existence of the Solr index to return a list of OIDs that are stored in the system.

This can causes problem not during a backup, but sometimes when and index event fails because you are working on the python script invovled you are suddenly left with an orphan record in storage that is invisible to the user interface.

The Solr index has always been the preferred option simply because it allows the process to proceed in an efficient fashion, paging through page after page of search results.

The only alternative is avoided inside the system because the Storage API exposes a general '`getObjectIdList()`', but this is primitive and tries to return a list of every ID in storage inside a single Object. This solution will not scale to large datasets without causing significant memory problems.

However, '`getObjectIdList()`' is the only choice at this stage for a storage based restore, simply because the Solr index does not exist.

### Problem 2: Object ID Generation ###
The Storage API allows you to use any Object ID (OID) you desire, but most harvesters make use of a common utility library provided by the system core which takes several strings and hashes them together:
  * The complete path to the ingested file.
  * The server's host name.
  * The System's username running the server.

As mentioned earlier, there is history there, relating to desktop deployments of The Fascinator talking to each other and/or a server, so those Strings were picked as an initial attempt to ensure unique OIDs were all but guaranteed.

Server's such as Mint/ReDBox have partially addressed this issue by providing different OID generation algorithms. For example, the CSV Harvester (used by Mint to ingest most data) instead hashes:
  * The file name holding the CSV data (but not the file path).
  * A configured String (`recordIDPrefix`).
  * The ID column (or row number if absent).

This method allows each data source (combination of file and '`recordIDPrefix`') that bothers to provide it's own identifier (or some unique data column) to generate the same OID every time, no matter which server ingests the data. This is important if you are going to (for example) reload your institutional Party data periodically with updates, since you don't want to be creating new duplicate entries for each person.

Where this issue hasn't been addressed however is in the creation of internally used OIDs, such as harvest files and packages. So if you were to move server, path or username all of your harvest files will suddenly change OID. Leading to...

### Problem 3: Linking to Harvest Files ###
Each object has the OID of both the configuration and rules files from its original data source embedded in its metadata. More detail on this setup makes sense if you are familiar with the [general make up of an object](https://sites.google.com/site/fascinatorhome/home/documentation/technical/details/object-life-cycle).

The problem arises however if/when the OID for the harvest file changes because the job of restoring the system becomes much more complicated. First you would need to find all of the harvest files, a job made more difficult by the fact that we don't currently identify them as such. You could at least look for what is absent... in that harvest files don't have links to harvest files embedded in their metadata.

Next you need to resolve these harvest files to the new server's harvest files on disk. You'll probably have to ingest them and do name matching... possibly messy, given that this is meant to occur as part of house keeping inside the system.

From there you can start migrating and altering all of the other objects in Storage to point to the new harvest files before they get indexed.

_A possible alternative is to simply ingest and index the entire storage layer into a staging index, and find the harvest files (and groups of associated objects) via the index. Then you can migrate to your real index as required. This might be a better solution for migrations across wildly different application versions, or when choosing to deprecate certain data sources, since you could do this as a two stage process with user input in the middle._

### Problem 4: Plugin/Version Compatibility ###
This is a longer term consideration, but some sort of thought needs to be given to how you would handle a migration across different versions of the Storage API and/or data model and/or Storage Plugins changing entirely (such as switching to Fedora).

# Proposal #
Before receiving any feedback, here's my thoughts on the way forward:

### Stage 1: Trivial backup/restore ###
This stage focuses on the tool being able to rebuild a system on the same server, with the same name, on the same path. It would expect to have all OIDs be the same both before and after the process and no migration work would be undertaken.

Possibly it could accommodate minor migration work as a manual process to accompany the execution afterwards, but it would not be part of the automated tool.

This tool would be executed from the command, similar to the HarvestClient, but it would expect the server to be running to provide message and indexing services... just like the HarvestClient does now.

The basic process would be something like:
  * Unpack and install the server using identical configuration.
  * Copy the Storage directory to be in the correct location.
  * Start the server, and visit the home page to ensure it is running correctly (it will look empty).
  * Execute the restore tool to populate your Solr index and security model.

Note, that the last point highlights the issue of security. For some systems this won't matter, eg. Mint/ReDBox derive all security data from workflows (in the harvest files) and the re-index process will rebuild an entirely accurate security model. Some systems that allow manual administration of security will need to also backup their security datastore... but this should just be an issue of appropriate documentation on a per-system basis.

### Stage 2: Solve OID Issues ###
Modify the Fascinator core to allow the default OID algorithm to be overridden on a system-wide basis, in addition to the current accommodation for each data source.

This would allow individual implementations to specify some way to ensure harvest files generate consistent OIDs no matter which server/environment they are housed in.

This will resolve the more annoying problems involved in a system migration, but still hasn't addessed huge migrations where underlying APIs might change. The tool should probably execute almost identically to Stage 1.

### Stage 3: Version/Plugin Migrations ###
This would ideally be the final version of the tool, solving the complication migration issues associated with the APIs themselves changing. The 'tool' would have to split into two tools joined by scripts and processes.

I've got a few different ideas for how this could be done, but a lot of detailed planning isn't particularly useful at this stage, since it is a fair way off.
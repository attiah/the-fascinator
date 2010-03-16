import md5, os, time
from au.edu.usq.fascinator.indexer.rules import AddField, New
from org.dom4j.io import SAXReader

from au.edu.usq.fascinator.common.nco import Contact
from au.edu.usq.fascinator.common.nfo import PaginatedTextDocument
from au.edu.usq.fascinator.common.nid3 import ID3Audio
from au.edu.usq.fascinator.common.nie import InformationElement

from au.edu.usq.fascinator.common.ctag import Tag, TaggedContent

from au.edu.usq.fascinator.api.storage import StorageException
#
# Available objects:
#    indexer    : Indexer instance
#    jsonConfig : JsonConfigHelper of our harvest config file
#    rules      : RuleManager instance
#    object     : DigitalObject to index
#    payload    : Payload to index
#    params     : Metadata Properties object
#

def indexPath(name, path, includeLastPart=True):
    parts = path.split("/")
    length = len(parts)
    if includeLastPart:
        length +=1
    for i in range(1, length):
        part = "/".join(parts[:i])
        if part != "":
            if part.startswith("/"):
                part = part[1:]
            rules.add(AddField(name, part))

def indexList(name, values):
    for value in values:
        rules.add(AddField(name, value))

def getNodeValues (doc, xPath):
    nodes = doc.selectNodes(xPath)
    valueList = []
    if nodes:
        for node in nodes:
            #remove duplicates:
            nodeValue = node.getText()
            if nodeValue not in valueList:
                valueList.append(node.getText())
    return valueList

#start with blank solr document
rules.add(New())

#common fields
oid = object.getId()
pid = payload.getId()
metaPid = params.getProperty("metaPid", "DC")
if pid == metaPid:
    itemType = "object"
else:
    oid += "/" + pid
    itemType = "datastream"
    rules.add(AddField("identifier", pid))

rules.add(AddField("id", oid))
rules.add(AddField("storage_id", oid))
rules.add(AddField("item_type", itemType))
rules.add(AddField("last_modified", time.strftime("%Y-%m-%dT%H:%M:%SZ")))

# Security
roles = indexer.getRolesWithAccess(oid)
if roles is not None:
    for role in roles:
        rules.add(AddField("security_filter", role))
else:
    # Default to guest access if Null object returned
    schema = indexer.getAccessSchema("simple");
    schema.setRecordId(oid)
    schema.set("role", "guest")
    indexer.setAccessSchema(schema, "simple")
    rules.add(AddField("security_filter", "guest"))

if pid == metaPid:
    #only need to index metadata for the main object
    rules.add(AddField("repository_name", params["repository.name"]))
    rules.add(AddField("repository_type", params["repository.type"]))

    titleList = []
    descriptionList = []
    creatorList = []
    creationDate = []
    contributorList = []
    approverList = []
    formatList = []
    fulltext = []
    relationDict = {}

    ### Check if dc.xml returned from ice is exist or not. if not... process the dc-rdf
    try:
        dcPayload = object.getPayload("dc.xml")
        indexer.registerNamespace("dc", "http://purl.org/dc/elements/1.1/")
        dcXml = indexer.getXmlDocument(dcPayload)
        if dcXml is not None:
            #get Title
            titleList = getNodeValues(dcXml, "//dc:title")
            #get abstract/description
            descriptionList = getNodeValues(dcXml, "//dc:description")
            #get creator
            creatorList = getNodeValues(dcXml, "//dc:creator")
            #get contributor list
            contributorList = getNodeValues(dcXml, "//dc:contributor")
            #get creation date
            creationDate = getNodeValues(dcXml, "//dc:issued")
            #ice metadata stored in dc:relation as key::value
            relationList = getNodeValues(dcXml, "//dc:relation")
            for relation in relationList:
                key, value = relation.split("::")
                value = value.strip()
                key = key.replace("_5f","") #ICE encoding _ as _5f?
                if relationDict.has_key(key):
                    relationDict[key].append(value)
                else:
                    relationDict[key] = [value]
    except StorageException, e:
        print " storage exception", str(e)

    try:
        rdfPayload = object.getPayload("aperture.rdf")
        rdfModel = indexer.getRdfModel(rdfPayload)

        #Seems like aperture only encode the spaces. Tested against special characters file name
        #and it's working
        safeOid = oid.replace(" ", "%20")
        #under windows we need to add a slash
        if not safeOid.startswith("/"):
            safeOid = "/" + safeOid
        rdfId = "file:%s" % safeOid

        #Set write to False so it won't write to the model
        paginationTextDocument = PaginatedTextDocument(rdfModel, rdfId, False)
        informationElement = InformationElement(rdfModel, rdfId, False)
        id3Audio = ID3Audio(rdfModel, rdfId, False)

        #1. get title only if no title returned by ICE
        if titleList == []:
            allTitles = informationElement.getAllTitle();
            while (allTitles.hasNext()):
                title = allTitles.next().strip()
                if title != "":
                    titleList.append(title)
            allTitles = id3Audio.getAllTitle()
            while (allTitles.hasNext()):
                title = allTitles.next().strip()
                if title != "":
                    titleList.append(title)

        #use id/filename if no title
        if titleList == []:
           title = os.path.split(oid)[1]
           titleList.append(title)

        #2. get creator only if no creator returned by ICE
        if creatorList == []:
            allCreators = paginationTextDocument.getAllCreator();
            while (allCreators.hasNext()):
                thing = allCreators.next()
                contacts = Contact(rdfModel, thing.getResource(), False)
                allFullnames = contacts.getAllFullname()
                while (allFullnames.hasNext()):
                     creatorList.append(allFullnames.next())

        #3. getFullText: only aperture has this information
        if informationElement.hasPlainTextContent():
            allPlainTextContents = informationElement.getAllPlainTextContent()
            while(allPlainTextContents.hasNext()):
                fulltextString = allPlainTextContents.next()
                fulltext.append(fulltextString)

                #4. description/abstract will not be returned by aperture, so if no description found
                # in dc.xml returned by ICE, put first 100 characters
                if descriptionList == []:
                    descriptionString = fulltextString
                    if len(fulltextString)>100:
                        descriptionString = fulltextString[:100] + "..."
                    descriptionList.append(descriptionString)

        if id3Audio.hasAlbumTitle():
            albumTitle = id3Audio.getAllAlbumTitle().next().strip()
            descriptionList.append("Album: " + albumTitle)

        #5. mimeType: only aperture has this information
        if informationElement.hasMimeType():
            allMimeTypes = informationElement.getAllMimeType()
            while(allMimeTypes.hasNext()):
                formatList.append(allMimeTypes.next())

        #6. contentCreated
        if creationDate == []:
            if informationElement.hasContentCreated():
                creationDate.append(informationElement.getContentCreated().getTime().toString())
    except StorageException, e:
        print " storage exception", str(e)

    indexList("dc_title", titleList)
    indexList("dc_creator", creatorList)  #no dc_author in schema.xml, need to check
    indexList("dc_contributor", contributorList)
    indexList("dc_description", descriptionList)
    indexList("dc_format", formatList)
    indexList("dc_date", creationDate)

    for key in relationDict:
        indexList(key, relationDict[key])

    indexList("full_text", fulltext)
    baseFilePath = params["base.file.path"]
    filePath = oid
    if baseFilePath:
        #NOTE: need to change again if the json file accept forward slash in window
        #get the base folder
        baseDir = baseFilePath.rstrip("/")
        baseDir = "/%s/" % baseDir[baseDir.rfind("/")+1:]
        filePath = filePath.replace("\\", "/").replace(baseFilePath, baseDir)
    indexPath("file_path", filePath, includeLastPart=False)

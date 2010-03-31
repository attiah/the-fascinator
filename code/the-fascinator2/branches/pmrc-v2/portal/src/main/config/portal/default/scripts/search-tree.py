import md5
from au.edu.usq.fascinator.api.indexer import SearchRequest
from au.edu.usq.fascinator.common import JsonConfigHelper
from java.io import ByteArrayInputStream, ByteArrayOutputStream
from java.net import URLEncoder
from java.util import ArrayList, HashMap, HashSet
from authentication import Authentication

class Facet:
    def __init__(self, key, value, count):
        self.__name = value[value.rfind("/") + 1:]
        fq = '%s:"%s"' % (key, value)
        self.__facetQuery = URLEncoder.encode(fq, "UTF-8")
        self.__id = md5.new(fq).hexdigest()
        self.__count = count
        self.__subFacets = ArrayList()
    
    def getId(self):
        return self.__id
    
    def getName(self):
        return self.__name
    
    def getCount(self):
        return self.__count
    
    def getFacetQuery(self):
        return self.__facetQuery
    
    def addSubFacet(self, facet):
        self.__subFacets.add(facet)
    
    def getSubFacets(self):
        return self.__subFacets

class FacetList:
    def __init__(self, name, json):
        self.__facetMap = HashMap()
        self.__facetList = ArrayList()
        entries = json.getList("facet_counts/facet_fields/" + name)
        for i in range(0, len(entries), 2):
            value = entries[i]
            count = entries[i+1]
            if count > 0:
                facet = Facet(name, value, count)
                self.__facetMap.put(value, facet)
                slash = value.rfind("/")
                if slash == -1:
                    self.__facetList.add(facet)
                else:
                    parent = self.getFacet(value[:slash])
                    if parent is not None:
                        parent.addSubFacet(facet)
    
    def getFacets(self):
        return self.__facetList
    
    def getFacet(self, name):
        return self.__facetMap.get(name)

class SearchTreeData:
    def __init__(self):
        self.authentication = Authentication()
        self.authentication.session_init()
        self.__search()
    
    def __search(self):
        query = formData.get("query")
        if query is None or query == "":
            query = "*:*"
        facetField = formData.get("facet.field")
        
        # find objects with annotations matching the query
        if query != "*:*":
			anotarQuery = query
			annoReq = SearchRequest(anotarQuery)
			annoReq.setParam("facet", "false")
			annoReq.setParam("rows", str(99999))
			annoReq.setParam("sort", "dateCreated asc")
			annoReq.setParam("start", str(0))        
			anotarOut = ByteArrayOutputStream()
			Services.indexer.annotateSearch(annoReq, anotarOut)
			resultForAnotar = JsonConfigHelper(ByteArrayInputStream(anotarOut.toByteArray()))
			resultForAnotar = resultForAnotar.getJsonList("response/docs")
			ids = HashSet()
			for annoDoc in resultForAnotar:
				annotatesUri = annoDoc.get("annotatesUri")
				ids.add(annotatesUri)
				print "Found annotation for %s" % annotatesUri
			# add annotation ids to query
			query += ' OR id:("' + '" OR "'.join(ids) + '")'
        
        req = SearchRequest(query)
        req.setParam("facet", "true")
        req.setParam("fl", "id")
        req.setParam("rows", "0")
        req.setParam("facet.limit", "-1")
        req.setParam("facet.field", facetField)
        
        fq = sessionState.get("fq")
        if fq is not None:
            req.setParam("fq", fq)
        req.addParam("fq", 'item_type:"object"')
        
        # Make sure 'fq' has already been set in the session
        security_roles = self.authentication.get_roles_list();
        security_query = 'security_filter:("' + '" OR "'.join(security_roles) + '")'
        req.addParam("fq", security_query)

        out = ByteArrayOutputStream()
        indexer = Services.getIndexer()
        indexer.search(req, out)
        result = JsonConfigHelper(ByteArrayInputStream(out.toByteArray()))
        
        self.__facetList = FacetList(facetField, result)
    
    def getFacetList(self):
        return self.__facetList
    
    def getFacet(self, value):
        return self.__facetList.get(value)

scriptObject = SearchTreeData()

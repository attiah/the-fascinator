from au.edu.usq.fascinator.portal import Portal
from ch.qos.logback.classic.html import  HTMLLayout
from org.slf4j import Logger, LoggerFactory

class BackupActions:
    def __init__(self):
        print "formData=%s" % formData
        result = "{}"
        resultType = "text/plain; charset=UTF-8"
        portalManager = Services.getPortalManager()
        func = formData.get("func")
        if func == "backup-view":
            print "Backup view %s" % portalId
            portal = portalManager.get(portalId)
            if portal:
                portalManager.backup(portal)
                sessionState.set("backup/lastResult", "success")
                result = '{ status: "ok" }'
            else:
                sessionState.set("backup/lastResult", "failed")
                result = '{ status: "failed" }'
        elif func == "get-state":
            result = '{ running: "%s", lastResult: "%s" }' % \
                (sessionState.get("backup/running"),
                 sessionState.get("backup/lastResult"))
        elif func == "get-log":
            context = LoggerFactory.getILoggerFactory()
            logger = context.getLogger("au.edu.usq.fascinator.BackupClient")
            appender = logger.getAppender("CYCLIC")
            layout = HTMLLayout()
            layout.setContext(context)
            layout.setPattern("%d%msg")
            layout.setTitle("Backup log")
            layout.start()
            result = "<table>"
            count = appender.getLength()
            if count == -1:
                result += "<tr><td>Failed</td></tr>"
            elif count == 0:
                result += "<tr><td>No logging events</td></tr>"
            else:
                for i in range(0, count):
                    event = appender.get(i)
                    result += layout.doLayout(event)
            result += "</table>"
            resultType = "text/html; charset=UTF-8"
        writer = response.getPrintWriter(resultType)
        writer.println(result)
        writer.close()

scriptObject = BackupActions()

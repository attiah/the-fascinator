/* 
 * The Fascinator - Core
 * Copyright (C) 2009 University of Southern Queensland
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package au.edu.usq.fascinator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.usq.fascinator.api.PluginManager;
import au.edu.usq.fascinator.api.indexer.Indexer;
import au.edu.usq.fascinator.api.indexer.IndexerException;
import au.edu.usq.fascinator.api.indexer.SearchRequest;
import au.edu.usq.fascinator.api.storage.DigitalObject;
import au.edu.usq.fascinator.api.storage.Payload;
import au.edu.usq.fascinator.api.storage.PayloadType;
import au.edu.usq.fascinator.api.storage.Storage;
import au.edu.usq.fascinator.common.JsonConfig;
import au.edu.usq.fascinator.common.JsonConfigHelper;

/**
 * To backup the DigitalObject indexed in Solr
 * <p>
 * Rely on configuration either set in:
 * </p>
 * <ul>
 * <li>system-config.json (Default) "email": "email@usq.edu.au", "backupDir":
 * "${user.home}/.fascinator-backup"</li>
 * <li>portal.xml <backup-email>email@usq.edu.au</backup-email> <backup-paths>
 * <field name="/home/octalina/.fascinator-backup">default</field>
 * </backup-paths></li>
 * </ul>
 * 
 * @author Linda octalina
 * 
 */

public class BackupClient {

    public static final String DATE_FORMAT = "yyyy-MM-dd";

    public static final String DATETIME_FORMAT = DATE_FORMAT + "'T'hh:mm:ss'Z'";

    /** Default storage type will be used if none defined **/
    private static final String DEFAULT_STORAGE_TYPE = "file-system";

    /** Default indexer type if none defined **/
    private static final String DEFAULT_INDEXER_TYPE = "solr";

    private static final String DEFAULT_BACKUP_CONFIG = "src/test/resources/backup.json";

    private static Logger log = LoggerFactory.getLogger(BackupClient.class);

    /** Json configuration file **/
    private JsonConfig config;

    /** Json system config configuration file **/
    private JsonConfig systemConfig;

    /** Email used to define user space **/
    private String email = null;

    /** Backup location list **/
    private Map<String, Map<String, Object>> backupDirList = new HashMap<String, Map<String, Object>>();

    /** Storage **/
    private String realStorageType;

    private Storage realStorage;

    /** Indexer **/
    private String indexerType;

    /** Portal query **/
    private String portalQuery = null;

    /** Portal directory **/
    private File portalDir;

    /**
     * Backup Client Constructor
     * 
     * @throws IOException
     * 
     * @throws IOException
     */
    public BackupClient() throws IOException {
        setDefaultSetting(null);
    }

    /**
     * Backup Client Constructor
     * 
     * @param jsonFile
     * @throws IOException
     * @throws IOException
     */
    public BackupClient(File jsonFile) throws IOException {
        setDefaultSetting(jsonFile);
    }

    @SuppressWarnings("unchecked")
    public void setDefaultSetting(File jsonFile) throws IOException {
        if (jsonFile != null) {
            config = new JsonConfig(jsonFile);
            systemConfig = new JsonConfig(config.getSystemFile());
        } else {
            config = new JsonConfig();
            systemConfig = new JsonConfig(config.getSystemFile());
            File defaultConfig = new File(systemConfig.get("fascinator-home")
                    + "/core", DEFAULT_BACKUP_CONFIG);
            config = new JsonConfig(defaultConfig);
        }
        indexerType = systemConfig.get("indexer/type", DEFAULT_INDEXER_TYPE);
        realStorageType = systemConfig
                .get("storage/type", DEFAULT_STORAGE_TYPE);

        // Set default email, backupDirList
        setEmail(config.get("backup-email"));

        Map<String, Object> backupPaths = config
                .getMapWithChild("backup-paths");
        Map<String, Map<String, Object>> backupPathsDict = new HashMap<String, Map<String, Object>>();
        for (String key : backupPaths.keySet()) {
            Map<String, Object> newObj = (Map<String, Object>) backupPaths
                    .get(key);
            backupPathsDict.put(key, newObj);
        }
        setBackupDir(backupPathsDict);
    }

    /**
     * Backup Client Constructor
     * 
     * @param email
     * @param backupDir
     * @param portalQuery
     * @throws IOException
     */
    public BackupClient(File portalDir, String email,
            Map<String, Map<String, Object>> backupDirs, String portalQuery)
            throws IOException {
        this.portalDir = portalDir;
        setDefaultSetting(null);
        setEmail(email);
        if (backupDirs != null) {
            backupDirList = new HashMap<String, Map<String, Object>>();
            setBackupDir(backupDirs);
        }
        setPortalQuery(portalQuery);
    }

    /**
     * Create the md5 of the email for the user space
     * 
     * @param email
     */
    public void setEmail(String email) {
        if (email != null && email != "") {
            this.email = DigestUtils.md5Hex(email);
        }
    }

    /**
     * Get the email
     * 
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Set Backup location being used
     * 
     * @param backupDir
     */
    public void setBackupDir(Map<String, Map<String, Object>> backupDirs) {
        if (backupDirs != null) {
            for (String backupPath : backupDirs.keySet()) {
                Map<String, Object> backupPathProps = backupDirs
                        .get(backupPath);
                backupDirList.put(backupPath, backupPathProps);
            }
        }
    }

    /**
     * Return backup location
     * 
     * @return backupDir
     */
    public Map<String, Map<String, Object>> getBackupDir() {
        return backupDirList;
    }

    /**
     * Set the portal Query
     * 
     * @param portalQuery
     */
    public void setPortalQuery(String portalQuery) {
        if (portalQuery != null && portalQuery != "") {
            this.portalQuery = portalQuery;
        }
    }

    /**
     * Get the portal Query
     * 
     * @return portalQuery
     */
    public String getPortalQuery() {
        return portalQuery;
    }

    /**
     * Run the backup code
     * 
     */
    public void run() {
        DateFormat df = new SimpleDateFormat(DATETIME_FORMAT);
        String now = df.format(new Date());
        long start = System.currentTimeMillis();
        log.info("Started at " + now);

        Indexer indexer;
        try {
            realStorage = PluginManager.getStorage(realStorageType);
            indexer = PluginManager.getIndexer(indexerType);
            realStorage.init(config.getSystemFile());
            indexer.init(config.getSystemFile());
            log.info("Loaded {} and {}", realStorage.getName(), indexer
                    .getName());
        } catch (Exception e) {
            log.error("Failed to initialise storage", e);
            return;
        }

        // Get all the records from solr
        int startRow = 0;
        int numPerPage = 5;
        int numFound = 0;
        do {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            SearchRequest request = new SearchRequest("*:*");
            request.addParam("rows", String.valueOf(numPerPage));
            request.addParam("fq", "item_type:\"object\"");
            request.setParam("start", String.valueOf(startRow));

            // Check if the portal has it's own query
            if (portalQuery != "" && portalQuery != null) {
                request.addParam("fq", portalQuery);
            }

            try {
                indexer.search(request, result);
                JsonConfigHelper js;

                js = new JsonConfigHelper(result.toString());
                startBackup(js);

                startRow += numPerPage;
                numFound = Integer.parseInt(js.get("response/numFound"));

            } catch (IndexerException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } while (startRow < numFound);

        log.info("Completed in "
                + ((System.currentTimeMillis() - start) / 1000.0) + " seconds");
    }

    /**
     * Start backup files from from the result returned by solr
     * 
     * @param js
     * @throws IOException
     */
    public void startBackup(JsonConfigHelper js) throws IOException {
        // Backup all the digital object returned by indexer
        for (Object oid : js.getList("response/docs/id")) {
            String fileName = oid.toString();
            File originalFile = new File(fileName);
            DigitalObject digitalObject = realStorage.getObject(fileName);
            fileName = isWindowFile(fileName);

            // Backup to "active" backup directory
            for (String backupPath : backupDirList.keySet()) {
                Map<String, Object> backupProps = backupDirList.get(backupPath);
                IgnoreFilter ignoreFilter = new IgnoreFilter(backupProps.get(
                        "ignoreFilter").toString().split("\\|"));
                if (ignoreFilter.accept(originalFile) == true) {
                    String includeMeta = String.valueOf(backupProps
                            .get("include-rendition-meta"));
                    String active = String.valueOf(backupProps.get("active"));
                    String includePortal = String.valueOf(backupProps
                            .get("include-portal-view"));

                    // Only active path then do backup
                    if (active == "true") {
                        File backupDirectory = new File(backupPath.toString()
                                + File.separator + email + File.separator
                                + "files");
                        if (backupDirectory.exists() == false) {
                            backupDirectory.getParentFile().mkdirs();
                        }
                        // Process the source data first
                        String outputFileName = backupDirectory
                                .getAbsolutePath()
                                + fileName;
                        File outputFile = new File(outputFileName);
                        outputFile.getParentFile().mkdirs();
                        OutputStream output = new FileOutputStream(outputFile);
                        log.info("Backup file: {} to {}", fileName, outputFile
                                .getAbsolutePath());
                        Payload payload = digitalObject.getSource();
                        if (payload != null) {
                            IOUtils.copy(payload.getInputStream(), output);
                        }

                        // Process the rest of the metadata if
                        // includeMeta is true, This will be in the zip file
                        List<Payload> payloadList = digitalObject
                                .getPayloadList();
                        if (includeMeta == "true"
                                && payloadList.isEmpty() == false) {
                            String zipFileName = backupDirectory
                                    .getAbsolutePath()
                                    + fileName + ".zip";
                            includeMetadata(zipFileName, payloadList);
                        }

                        // Process the view/portal files if included
                        if (includePortal == "true") {
                            File portalFolder;
                            if (portalDir == null) {
                                portalDir = new File(config
                                        .get("fascinator-home")
                                        + "/portal", systemConfig
                                        .get("portal/home"));
                                portalFolder = new File(backupPath.toString()
                                        + File.separator + email
                                        + File.separator + "config/portal");
                            } else {
                                portalFolder = new File(backupPath.toString()
                                        + File.separator + email
                                        + File.separator + "config/portal"
                                        + File.separator + portalDir.getName());
                            }
                            portalFolder.getParentFile().mkdirs();
                            includePortalDir(portalDir, portalFolder);
                        }
                    }
                }
            }
        }
    }

    /**
     * Zipping payload & metadata
     * 
     * @param zipFileName
     * @param payloadList
     * @throws IOException
     */
    private void includeMetadata(String zipFileName, List<Payload> payloadList)
            throws IOException {
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(
                zipFileName));
        for (Payload metaPayload : payloadList) {
            if (metaPayload.getType() == PayloadType.Enrichment) {
                out.putNextEntry(new ZipEntry(metaPayload.getId()));
                IOUtils.copy(metaPayload.getInputStream(), out);
                out.closeEntry();
            }
        }
        out.close();
    }

    /**
     * Copy portal config directory
     * 
     * @param portalFolder
     * @throws IOException
     */
    private void includePortalDir(File portalSrc, File portalDest)
            throws IOException {
        if (portalSrc.isDirectory()) {
            if (!portalDest.exists()) {
                portalDest.mkdir();
            }
            for (String file : portalSrc.list()) {
                includePortalDir(new File(portalSrc, file), new File(
                        portalDest, file));
            }
        } else {
            InputStream in = new FileInputStream(portalSrc);
            OutputStream out = new FileOutputStream(portalDest);
            IOUtils.copy(in, out);
            in.close();
            out.close();
        }
    }

    /**
     * Change the name if it's a window file
     * 
     * @param fileName
     * @return
     */
    private String isWindowFile(String fileName) {
        // For window C: will cause error. so fix it as C_
        // NOTE: remember to change it back when doing restore
        if (System.getProperty("os.name").toLowerCase().indexOf("windows") > -1) {
            fileName = fileName.replace("C:", File.separator + "C_");
        }
        return fileName;
    }

    /**
     * File filter used to ignore specified files
     */
    private class IgnoreFilter implements FileFilter {

        /** wildcard patterns of files to ignore */
        private String[] patterns;

        public IgnoreFilter(String[] patterns) {
            this.patterns = patterns;
        }

        public boolean accept(File path) {
            for (String pattern : patterns) {
                if (FilenameUtils.wildcardMatch(path.getName(), pattern)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            log.info("Usage: harvest <json-config>");
        } else {
            File jsonFile = new File(args[0]);
            try {
                BackupClient backup = new BackupClient(jsonFile);
                backup.run();
            } catch (IOException ioe) {
                log.error("Failed to initialise client: {}", ioe.getMessage());
            }
        }
    }
}

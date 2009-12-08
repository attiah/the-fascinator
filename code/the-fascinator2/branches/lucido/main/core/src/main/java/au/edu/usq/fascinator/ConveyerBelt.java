package au.edu.usq.fascinator;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.edu.usq.fascinator.api.PluginException;
import au.edu.usq.fascinator.api.PluginManager;
import au.edu.usq.fascinator.api.storage.DigitalObject;
import au.edu.usq.fascinator.api.transformer.Transformer;
import au.edu.usq.fascinator.api.transformer.TransformerException;
import au.edu.usq.fascinator.common.JsonConfig;

//package au.edu.usq.fascinator.extractor.aperture.Extractor;

public class ConveyerBelt {
    private JsonConfig config;
    private File jsonFile;
    private String type;

    private String configString;

    private static Logger log = LoggerFactory.getLogger(ConveyerBelt.class);

    public ConveyerBelt(File jsonFile, String type) {
        this.jsonFile = jsonFile;
        this.type = type;
        try {
            config = new JsonConfig(jsonFile);
        } catch (IOException ioe) {
            log.warn("Failed to load config from {}", jsonFile);
        }
    }

    public ConveyerBelt(String json, String type) {
        try {
            configString = json;
            config = new JsonConfig(json);
            this.type = type;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            log.warn("Failed to load config from {}", jsonFile);
        }
    }

    public DigitalObject transform(DigitalObject object)
            throws TransformerException {
        List<Object> pluginList = config.getList("transformer/" + type);
        DigitalObject result = object;
        if (pluginList != null) {
            for (Object pluginName : pluginList) {
                Transformer transPlugin = PluginManager
                        .getTransformer(pluginName.toString().trim());
                try {
                    if (jsonFile == null) {
                        // transPlugin.init(config.toString());
                        transPlugin.init(configString);
                    } else {
                        transPlugin.init(jsonFile);
                    }
                    result = transPlugin.transform(result);
                } catch (PluginException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        return result;
    }
}

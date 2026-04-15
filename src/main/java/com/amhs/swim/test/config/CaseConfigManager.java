package com.amhs.swim.test.config;

import org.json.JSONObject;
import com.amhs.swim.test.util.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manages per-case payload configurations.
 * Allows users to customize payloads for each test case message via a JSON config file.
 * Supports reverting to default values.
 */
public class CaseConfigManager {
    
    private static final String CONFIG_FILE = "config/case_payloads.json"; // user customizations
    private static final String DEFAULTS_FILE_XML = "config/default_case_payloads.xml"; // standardized defaults
    private static final String DEFAULTS_FILE_TXT = "config/default_case_payloads.txt"; // fallback plaintext defaults
    private static CaseConfigManager instance;
    private JSONObject caseConfigs;
    private Map<String, Map<Integer, String>> defaultPayloads;
    
    private CaseConfigManager() {
        caseConfigs = new JSONObject();
        defaultPayloads = new HashMap<>();
        loadConfig();
        loadStandardDefaults();
    }
    
    public static synchronized CaseConfigManager getInstance() {
        if (instance == null) {
            instance = new CaseConfigManager();
        }
        return instance;
    }
    
    /**
     * Load case payload configurations from file.
     */
    private void loadConfig() {
        Path path = Paths.get(CONFIG_FILE);
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path);
                caseConfigs = new JSONObject(content);
                Logger.logCase("CONFIG", "INFO", "Loaded case payload config from: " + CONFIG_FILE);
            } catch (IOException e) {
                Logger.logCase("CONFIG", "WARN", "Failed to read case payload config, using defaults: " + e.getMessage());
                caseConfigs = new JSONObject();
            }
        } else {
            Logger.logCase("CONFIG", "INFO", "No case payload config found, using defaults");
            caseConfigs = new JSONObject();
        }
    }

    /**
     * Load standardized default payloads from a text or XML file.
     * Supported formats:
     *  - config/default_case_payloads.xml (preferred)
     *    <defaults>
     *      <case id="CTSW106">
     *        <msg idx="1"><![CDATA[Payload text...]]></msg>
     *      </case>
     *    </defaults>
     *  - config/default_case_payloads.txt (fallback) with lines:
     *    CTSW106|1|Payload text (payload may contain '|' characters)
     */
    private void loadStandardDefaults() {
        try {
            Path xmlPath = Paths.get(DEFAULTS_FILE_XML);
            Path txtPath = Paths.get(DEFAULTS_FILE_TXT);
            if (Files.exists(xmlPath)) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(xmlPath.toFile());
                doc.getDocumentElement().normalize();
                NodeList caseNodes = doc.getElementsByTagName("case");
                for (int i = 0; i < caseNodes.getLength(); i++) {
                    Element caseElem = (Element) caseNodes.item(i);
                    String caseId = caseElem.getAttribute("id");
                    NodeList msgNodes = caseElem.getElementsByTagName("msg");
                    for (int j = 0; j < msgNodes.getLength(); j++) {
                        Element msgElem = (Element) msgNodes.item(j);
                        String idxStr = msgElem.getAttribute("idx");
                        try {
                            int idx = Integer.parseInt(idxStr);
                            String payload = msgElem.getTextContent();
                            defaultPayloads.computeIfAbsent(caseId, k -> new HashMap<>()).put(idx, payload);
                        } catch (NumberFormatException nfe) {
                            // skip invalid index
                        }
                    }
                }
                Logger.logCase("CONFIG", "INFO", "Loaded standard defaults from: " + DEFAULTS_FILE_XML);
            } else if (Files.exists(txtPath)) {
                try (Stream<String> lines = Files.lines(txtPath)) {
                    lines.forEach(line -> {
                        String l = line.trim();
                        if (l.isEmpty() || l.startsWith("#")) return;
                        int p1 = l.indexOf('|');
                        if (p1 < 0) return;
                        int p2 = l.indexOf('|', p1 + 1);
                        if (p2 < 0) return;
                        String caseId = l.substring(0, p1).trim();
                        try {
                            int idx = Integer.parseInt(l.substring(p1 + 1, p2).trim());
                            String payload = l.substring(p2 + 1);
                            defaultPayloads.computeIfAbsent(caseId, k -> new HashMap<>()).put(idx, payload);
                        } catch (NumberFormatException nfe) {
                            // skip
                        }
                    });
                }
                Logger.logCase("CONFIG", "INFO", "Loaded standard defaults from: " + DEFAULTS_FILE_TXT);
            } else {
                Logger.logCase("CONFIG", "INFO", "No standard default payload file found");
            }
        } catch (Exception e) {
            Logger.logCase("CONFIG", "WARN", "Failed to load standard defaults: " + e.getMessage());
        }
    }
    
    /**
     * Save current case payload configurations to file.
     */
    public void saveConfig() {
        try {
            Path path = Paths.get(CONFIG_FILE);
            Files.createDirectories(path.getParent());
            Files.writeString(path, caseConfigs.toString(4));
            Logger.logCase("CONFIG", "INFO", "Saved case payload config to: " + CONFIG_FILE);
        } catch (IOException e) {
            Logger.logCase("CONFIG", "ERROR", "Failed to save case payload config: " + e.getMessage());
        }
    }
    
    /**
     * Register default payload for a specific case message.
     * @param caseId Case ID (e.g., "CTSW101")
     * @param msgIndex Message index (1-based)
     * @param defaultPayload The default payload value
     */
    public void registerDefault(String caseId, int msgIndex, String defaultPayload) {
        defaultPayloads.computeIfAbsent(caseId, k -> new HashMap<>()).put(msgIndex, defaultPayload);
    }
    
    /**
     * Get the configured or default payload for a case message.
     * @param caseId Case ID
     * @param msgIndex Message index
     * @return Configured payload if exists, otherwise default
     */
    public String getPayload(String caseId, int msgIndex) {
        // Check if user has customized this
        if (caseConfigs.has(caseId)) {
            JSONObject caseConfig = caseConfigs.getJSONObject(caseId);
            String key = "msg_" + msgIndex;
            if (caseConfig.has(key)) {
                return caseConfig.getString(key);
            }
        }
        
        // Return default if registered
        if (defaultPayloads.containsKey(caseId) && defaultPayloads.get(caseId).containsKey(msgIndex)) {
            return defaultPayloads.get(caseId).get(msgIndex);
        }
        
        return "";
    }
    
    /**
     * Set a custom payload for a case message.
     * @param caseId Case ID
     * @param msgIndex Message index
     * @param payload The payload to store
     */
    public void setPayload(String caseId, int msgIndex, String payload) {
        JSONObject caseConfig;
        if (caseConfigs.has(caseId)) {
            caseConfig = caseConfigs.getJSONObject(caseId);
        } else {
            caseConfig = new JSONObject();
            caseConfigs.put(caseId, caseConfig);
        }
        caseConfig.put("msg_" + msgIndex, payload);
    }
    
    /**
     * Revert a specific case message to its default payload.
     * @param caseId Case ID
     * @param msgIndex Message index
     * @return The default payload value
     */
    public String revertToDefault(String caseId, int msgIndex) {
        // Remove from config if present
        if (caseConfigs.has(caseId)) {
            JSONObject caseConfig = caseConfigs.getJSONObject(caseId);
            String key = "msg_" + msgIndex;
            if (caseConfig.has(key)) {
                caseConfig.remove(key);
                // Clean up empty case config
                if (caseConfig.length() == 0) {
                    caseConfigs.remove(caseId);
                }
            }
        }
        
        // Return registered default
        if (defaultPayloads.containsKey(caseId) && defaultPayloads.get(caseId).containsKey(msgIndex)) {
            return defaultPayloads.get(caseId).get(msgIndex);
        }
        
        return "";
    }
    
    /**
     * Revert all messages in a case to their defaults.
     * @param caseId Case ID
     */
    public void revertCaseToDefaults(String caseId) {
        if (caseConfigs.has(caseId)) {
            caseConfigs.remove(caseId);
        }
    }
    
    /**
     * Revert all cases to their defaults.
     */
    public void revertAllToDefaults() {
        caseConfigs = new JSONObject();
    }
    
    /**
     * Check if a case has any custom configurations.
     * @param caseId Case ID
     * @return true if customized
     */
    public boolean hasCustomConfig(String caseId) {
        return caseConfigs.has(caseId) && caseConfigs.getJSONObject(caseId).length() > 0;
    }
    
    /**
     * Get all message configs for a case.
     * @param caseId Case ID
     * @return Map of message index to payload
     */
    public Map<Integer, String> getCaseConfig(String caseId) {
        Map<Integer, String> result = new HashMap<>();
        if (caseConfigs.has(caseId)) {
            JSONObject caseConfig = caseConfigs.getJSONObject(caseId);
            for (String key : caseConfig.keySet()) {
                if (key.startsWith("msg_")) {
                    int idx = Integer.parseInt(key.substring(4));
                    result.put(idx, caseConfig.getString(key));
                }
            }
        }
        return result;
    }
}

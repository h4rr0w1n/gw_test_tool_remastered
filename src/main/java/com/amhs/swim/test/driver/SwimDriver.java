package com.amhs.swim.test.driver;

import com.amhs.swim.test.config.TestConfig;
import com.amhs.swim.test.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Main driver for interacting with the SWIM environment via AMQP 1.0 and REST APIs.
 * Compliant with ICAO EUR Doc 047, AMHS-SWIM Gateway Testing Plan V3.0.
 * 
 * This class implements the Adapter Pattern to automatically detect and select the messaging protocol:
 * - Solace JCSMP (proprietary) is prioritized if libraries are present and the broker is reachable.
 * - Apache Qpid Proton-J (standard AMQP 1.0) serves as the industry-standard fallback.
 * 
 * Protocol Auto-Detection: During connection initialization, the driver probes for Solace API 
 * availability. If not found or unreachable, it falls back to the Qpid AMQP 1.0 adapter.
 */
public class SwimDriver {
    private SwimMessagingAdapter activeAdapter;
    private boolean isConnected = false;
    private boolean traceEnabled = false;
    
    // REST API components (shared regardless of messaging adapter)
    private String authToken;
    
    /**
     * Auto-detect and select the appropriate messaging adapter.
     * Priority: Solace JCSMP (if available) -> Qpid AMQP 1.0 (fallback)
     */
    private void detectAndSelectAdapter() {
        Logger.log("INFO", "Detecting available SWIM messaging adapters...");
        
        // Try Solace first (legacy/proprietary)
        SolaceSwimAdapter solaceAdapter = new SolaceSwimAdapter();
        if (solaceAdapter.isAvailable() && solaceAdapter.canConnect()) {
            this.activeAdapter = solaceAdapter;
            Logger.log("SUCCESS", "Selected Solace JCSMP adapter (proprietary API detected & reachable).");
            return;
        }
        
        // Fallback to Qpid (standard AMQP 1.0)
        QpidSwimAdapter qpidAdapter = new QpidSwimAdapter();
        if (qpidAdapter.isAvailable() && qpidAdapter.canConnect()) {
            this.activeAdapter = qpidAdapter;
            Logger.log("SUCCESS", "Selected Qpid AMQP 1.0 adapter (standard AMQP 1.0 & reachable).");
            return;
        }

        // If we reach here, either the libraries are missing OR the brokers are offline
        if (solaceAdapter.isAvailable() || qpidAdapter.isAvailable()) {
            throw new IllegalStateException(
                "AMQP software/driver is missing: Connection refused to configured broker hosts. " +
                "Please ensure your AMQP broker (Solace, Qpid, etc.) is running."
            );
        }
        
        // No adapter available in classpath
        throw new IllegalStateException(
            "No SWIM messaging adapter libraries found in classpath. " +
            "Please ensure either Solace JCSMP or Apache Qpid Proton-J is included."
        );
    }
    
    /**
     * Connects to the SWIM Message Broker.
     * Automatically selects the appropriate adapter (Solace or Qpid) based on availability checks.
     */
    public void connect() throws Exception {
        if (activeAdapter == null) {
            detectAndSelectAdapter();
        }
        
        activeAdapter.connect();
        isConnected = true;
    }

    /**
     * Tests the connection to the SWIM Broker (invoked by the "Check Connection" UI button).
     */
    public void testConnection() {
        try {
            if (activeAdapter != null) {
                activeAdapter.close();
            }
            activeAdapter = null;
            isConnected = false;
            
            connect();
        } catch (Exception e) {
            Logger.log("ERROR", "Connection test failed: " + e.getMessage());
        }
    }
    
    /**
     * Publishes a message to SWIM.
     * Delegates delivery to the currently active adapter (Solace or Qpid).
     * 
     * @param topic Destination address (e.g., MET.METAR.VVTS or a queue name)
     * @param payload Binary message payload
     * @param properties AMQP 1.0 application properties mapped per AMHS/SWIM specification
     */
    public void publishMessage(String topic, byte[] payload, Map<String, Object> properties) throws Exception {
        if (!isConnected) {
            connect();
        }
        
        if (traceEnabled) {
            Logger.logAMQPDeepTrace(null, properties);
        }
        
        activeAdapter.publishMessage(topic, payload, properties);
    }
    
    /**
     * Gửi message với AMQPProperties helper object.
     * Chuyển đổi sang Map trước khi delegate cho adapter.
     */
    public void publishMessage(String topic, byte[] payload, AMQPProperties properties) throws Exception {
        Map<String, Object> propsMap = properties.toMap();
        publishMessage(topic, payload, propsMap);
    }

    public void publishToTopic(String topic, byte[] payload, Map<String, Object> properties) throws Exception {
        if (!isConnected) connect();
        if (traceEnabled) {
            Logger.logAMQPDeepTrace(null, properties);
        }
        activeAdapter.publishToTopic(topic, payload, properties);
    }
    
    public void publishToTopic(String topic, byte[] payload, AMQPProperties properties) throws Exception {
        publishToTopic(topic, payload, properties.toMap());
    }

    public void publishToQueue(String queue, byte[] payload, Map<String, Object> properties) throws Exception {
        if (!isConnected) connect();
        if (traceEnabled) {
            Logger.logAMQPDeepTrace(null, properties);
        }
        activeAdapter.publishToQueue(queue, payload, properties);
    }
    
    public void publishToQueue(String queue, byte[] payload, AMQPProperties properties) throws Exception {
        publishToQueue(queue, payload, properties.toMap());
    }

    
    /**
     * Subscribes to and consumes a message from SWIM.
     * Delegates to the active messaging adapter.
     * 
     * @param address Destination address/queue/topic to subscribe to
     * @param timeoutMs Consumption timeout in milliseconds
     * @return The received binary payload
     */
    public byte[] consumeMessage(String address, long timeoutMs) throws Exception {
        if (!isConnected) {
            connect();
        }
        
        return activeAdapter.consumeMessage(address, timeoutMs);
    }
    
    /**
     * Overload với default timeout 5000ms.
     */
    public byte[] consumeMessage(String address) throws Exception {
        return consumeMessage(address, 5000);
    }
    
    /**
     * Obtains a JWT token from the Keycloak OIDC endpoint.
     * Per EUR Doc 047: REST API authentication is handled via OAuth2/OIDC.
     * This is a shared component independent of the messaging adapter.
     */
    public String obtainTokenFromKeycloak(String keycloakUrl, String realm, String clientId, 
                                         String username, String password) throws IOException {
        Logger.log("INFO", "Đang lấy JWT token từ Keycloak: " + keycloakUrl);
        
        String tokenEndpoint = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        URL url = new URL(tokenEndpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        
        String postData = "grant_type=password" +
                         "&client_id=" + clientId +
                         "&username=" + username +
                         "&password=" + password;
        
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = postData.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int statusCode = conn.getResponseCode();
        if (statusCode == 200) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                authToken = extractAccessToken(response.toString());
                Logger.log("SUCCESS", "Lấy JWT token thành công.");
                return authToken;
            }
        } else {
            Logger.log("ERROR", "Lỗi lấy token: HTTP " + statusCode);
            throw new IOException("Failed to obtain token: HTTP " + statusCode);
        }
    }
    
    /**
     * Extract access_token từ JSON response.
     */
    private String extractAccessToken(String jsonResponse) {
        int start = jsonResponse.indexOf("\"access_token\":\"") + 16;
        if (start < 16) return null;
        int end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }
    
    /**
     * Performs a service lookup in the SWIM Registry via REST API.
     * Per EUR Doc 047: Service discovery is performed via RESTful registry queries.
     */
    public String lookupServiceInRegistry(String registryUrl, String serviceName, 
                                          String serviceType) throws IOException {
        Logger.log("INFO", "Đang lookup service trong registry: " + registryUrl);
        
        String lookupEndpoint = registryUrl + "/services?name=" + serviceName + "&type=" + serviceType;
        URL url = new URL(lookupEndpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        
        int statusCode = conn.getResponseCode();
        if (statusCode == 200) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                Logger.log("SUCCESS", "Lookup service thành công.");
                return response.toString();
            }
        } else {
            Logger.log("ERROR", "Lỗi lookup service: HTTP " + statusCode);
            throw new IOException("Failed to lookup service: HTTP " + statusCode);
        }
    }
    
    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    /**
     * Đóng kết nối.
     */
    public void disconnect() {
        if (activeAdapter != null) {
            activeAdapter.close();
        }
        isConnected = false;
        Logger.log("SUCCESS", "Disconnected from SWIM.");
    }
    
    /**
     * Get the currently active adapter name (for logging/debugging).
     */
    public String getActiveAdapterName() {
        return activeAdapter != null ? activeAdapter.getAdapterName() : "None";
    }
    
    /**
     * Helper class để chứa các AMQP 1.0 properties theo EUR Doc 047.
     * Có thể chuyển đổi sang Map<String, Object> để sử dụng với adapter.
     */
    public static class AMQPProperties {
        private String atsPri;           // amhs_ats_pri: SS/DD/FF/GG/KK
        private Short amqpPriority;      // standard amqp priority: 0-9
        private String recipients;       // amhs_recipients: list of recipients
        private String bodyPartType;     // amhs_bodypart_type: ia5-text, utf8-text, etc.
        private String contentType;      // content-type AMQP property (e.g. application/octet-stream)
        private String originator;       // amhs_originator
        private String subject;          // subject AMQP property
        private String messageId;        // amhs_message_id
        private Long creationTime;       // amqp creation-time
        private String filingTime;       // amhs_ats_ft (ATS filing time DDhhmm)
        private String dlHistory;        // amhs_dl_history
        private String secEnvelope;      // amhs_sec_envelope (signed messages)
        private String replyTo;          // amqp reply-to
        // Extra arbitrary application properties (persistent, safe from toMap() discard bug)
        private Map<String, Object> extraProps;
        
        // Advanced AMQP 1.0 Sections for Multi-Broker Support
        private Map<String, Object> messageAnnotations;
        private Map<String, Object> deliveryAnnotations;
        private Map<String, Object> footer;
        private BrokerProfile brokerProfile = BrokerProfile.STANDARD;
        private BodyType bodyType = BodyType.DATA;

        public enum BrokerProfile {
            STANDARD, AZURE_SERVICE_BUS, IBM_MQ, RABBITMQ, SOLACE
        }

        public enum BodyType {
            DATA, AMQP_VALUE, AMQP_SEQUENCE
        }
        
        public AMQPProperties() {
            this.messageAnnotations = new HashMap<>();
            this.deliveryAnnotations = new HashMap<>();
            this.footer = new HashMap<>();
            this.extraProps = new HashMap<>();
        }

        /**
         * Set an arbitrary extra AMQP application property.
         * This is the correct way to set properties not covered by typed setters.
         * Unlike toMap().put(...), this persists across toMap() calls.
         */
        public void setExtraProp(String key, Object value) {
            this.extraProps.put(key, value);
        }

        public Map<String, Object> getExtraProps() { return extraProps; }
        
        // Getters and Setters
        public Map<String, Object> getMessageAnnotations() { return messageAnnotations; }
        public void setMessageAnnotations(Map<String, Object> annotations) { this.messageAnnotations = annotations; }
        
        public Map<String, Object> getDeliveryAnnotations() { return deliveryAnnotations; }
        public void setDeliveryAnnotations(Map<String, Object> annotations) { this.deliveryAnnotations = annotations; }
        
        public BrokerProfile getBrokerProfile() { return brokerProfile; }
        public void setBrokerProfile(BrokerProfile profile) { this.brokerProfile = profile; }

        public BodyType getBodyType() { return bodyType; }
        public void setBodyType(BodyType type) { this.bodyType = type; }
        
        public Short getAmqpPriority() { return amqpPriority; }
        public void setAmqpPriority(Short amqpPriority) { this.amqpPriority = amqpPriority; }
        
        public String getAtsPri() { return atsPri; }
        public void setAtsPri(String atsPri) { this.atsPri = atsPri; }
        
        public String getRecipients() { return recipients; }
        public void setRecipients(String recipients) { this.recipients = recipients; }
        
        public String getBodyPartType() { return bodyPartType; }
        public void setBodyPartType(String bodyPartType) { this.bodyPartType = bodyPartType; }
        
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        
        public String getOriginator() { return originator; }
        public void setOriginator(String originator) { this.originator = originator; }
        
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        
        public Long getCreationTime() { return creationTime; }
        public void setCreationTime(Long creationTime) { this.creationTime = creationTime; }
        
        public String getFilingTime() { return filingTime; }
        public void setFilingTime(String filingTime) { this.filingTime = filingTime; }
        
        public String getDlHistory() { return dlHistory; }
        public void setDlHistory(String dlHistory) { this.dlHistory = dlHistory; }
        
        public String getSecEnvelope() { return secEnvelope; }
        public void setSecEnvelope(String secEnvelope) { this.secEnvelope = secEnvelope; }
        
        public String getReplyTo() { return replyTo; }
        public void setReplyTo(String replyTo) { this.replyTo = replyTo; }
        
        /**
         * Convert to Map<String, Object> for use with SwimMessagingAdapter.
         * Extra properties set via setExtraProp() are included.
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            // Merge extra properties first so typed setters take precedence
            if (extraProps != null) map.putAll(extraProps);
            if (amqpPriority != null) map.put("amqp_priority", amqpPriority);
            if (atsPri != null) map.put("amhs_ats_pri", atsPri);
            if (recipients != null) map.put("amhs_recipients", recipients);
            if (bodyPartType != null) map.put("amhs_bodypart_type", bodyPartType);
            if (contentType != null) map.put("content_type", contentType);
            if (originator != null) map.put("amhs_originator", originator);
            if (subject != null) map.put("amhs_subject", subject);
            if (messageId != null) map.put("amhs_message_id", messageId);
            if (creationTime != null) map.put("creation_time", creationTime);
            if (filingTime != null) map.put("amhs_ats_ft", filingTime);
            if (dlHistory != null) map.put("amhs_dl_history", dlHistory);
            if (secEnvelope != null) map.put("amhs_sec_envelope", secEnvelope);
            if (replyTo != null) map.put("amhs_reply_to", replyTo);
            if (messageAnnotations != null && !messageAnnotations.isEmpty()) map.put("amqp_message_annotations", messageAnnotations);
            if (deliveryAnnotations != null && !deliveryAnnotations.isEmpty()) map.put("amqp_delivery_annotations", deliveryAnnotations);
            if (footer != null && !footer.isEmpty()) map.put("amqp_footer", footer);
            map.put("amqp_broker_profile", brokerProfile.name());
            map.put("amqp_body_type", bodyType.name());
            return map;
        }
        
        /**
         * Create from Map<String, Object>.
         */
        public static AMQPProperties fromMap(Map<String, Object> map) {
            AMQPProperties props = new AMQPProperties();
            if (map.containsKey("amhs_ats_pri")) props.setAtsPri((String) map.get("amhs_ats_pri"));
            if (map.containsKey("amhs_recipients")) props.setRecipients((String) map.get("amhs_recipients"));
            if (map.containsKey("amhs_bodypart_type")) props.setBodyPartType((String) map.get("amhs_bodypart_type"));
            if (map.containsKey("amhs_content_type")) props.setContentType((String) map.get("amhs_content_type"));
            if (map.containsKey("amhs_originator")) props.setOriginator((String) map.get("amhs_originator"));
            if (map.containsKey("amhs_subject")) props.setSubject((String) map.get("amhs_subject"));
            if (map.containsKey("amhs_message_id")) props.setMessageId((String) map.get("amhs_message_id"));
            if (map.containsKey("creation_time")) props.setCreationTime((Long) map.get("creation_time"));
            if (map.containsKey("amhs_filing_time")) props.setFilingTime((String) map.get("amhs_filing_time"));
            if (map.containsKey("amhs_dl_history")) props.setDlHistory((String) map.get("amhs_dl_history"));
            if (map.containsKey("amhs_sec_envelope")) props.setSecEnvelope((String) map.get("amhs_sec_envelope"));
            if (map.containsKey("amhs_reply_to")) props.setReplyTo((String) map.get("amhs_reply_to"));
            if (map.containsKey("amqp_message_annotations")) props.setMessageAnnotations((Map<String, Object>) map.get("amqp_message_annotations"));
            if (map.containsKey("amqp_delivery_annotations")) props.setDeliveryAnnotations((Map<String, Object>) map.get("amqp_delivery_annotations"));
            if (map.containsKey("amqp_footer")) props.setFooter((Map<String, Object>) map.get("amqp_footer"));
            if (map.containsKey("amqp_broker_profile")) props.setBrokerProfile(BrokerProfile.valueOf((String) map.get("amqp_broker_profile")));
            if (map.containsKey("amqp_body_type")) props.setBodyType(BodyType.valueOf((String) map.get("amqp_body_type")));
            return props;
        }

        public Map<String, Object> getFooter() { return footer; }
        public void setFooter(Map<String, Object> footer) { this.footer = footer; }
    }
}

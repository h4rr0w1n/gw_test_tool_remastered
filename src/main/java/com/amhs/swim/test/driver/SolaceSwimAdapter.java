package com.amhs.swim.test.driver;

import com.amhs.swim.test.config.TestConfig;
import com.amhs.swim.test.util.Logger;
import com.solacesystems.jcsmp.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/**
 * Solace JCSMP implementation of the {@link SwimMessagingAdapter}.
 * 
 * This adapter uses the proprietary Solace JCSMP API to interact with Solace PubSub+ 
 * event brokers. It is used primarily for legacy deployments where SMF (Solace Message 
 * Format) connectivity is required over standard AMQP 1.0.
 * 
 * Features specialized handling for mapping AMQP-style host/port combinations 
 * to Solace-specific SMF connection schemes.
 */
public class SolaceSwimAdapter implements SwimMessagingAdapter {
    
    private JCSMPSession session;
    private XMLMessageProducer producer;
    private XMLMessageConsumer consumer;
    private boolean isConnected = false;
    
    private static final String SOLACE_JCSMP_CLASS = "com.solacesystems.jcsmp.JCSMPSession";
    
    @Override
    public boolean isAvailable() {
        try {
            // Check if Solace JCSMP classes are present
            Class.forName(SOLACE_JCSMP_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    @Override
    public String getAdapterName() {
        return "Solace-JCSMP";
    }
    
    @Override
    public boolean canConnect() {
        if (!isAvailable()) return false;
        
        TestConfig config = TestConfig.getInstance();
        String host = config.getProperty("swim.broker.host", "localhost");
        String port = config.getProperty("swim.broker.port", "55555");
        
        String[] normalized = normalizeSolaceConnection(host, port);
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(normalized[1], Integer.parseInt(normalized[2])), 2000); // 2 second timeout
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void connect() throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Solace JCSMP library not available");
        }
        
        TestConfig config = TestConfig.getInstance();
        String host = config.getProperty("swim.broker.host", "localhost");
        String port = config.getProperty("swim.broker.port", "55555");
        String user = config.getProperty("swim.broker.user", "default");
        String pass = config.getProperty("swim.broker.password", "default");
        String vpn = config.getProperty("swim.broker.vpn", "default");
        
        String[] normalized = normalizeSolaceConnection(host, port);
        String connectionUrl = normalized[0] + "://" + normalized[1] + ":" + normalized[2];
        
        Logger.log("INFO", "Connecting to SWIM Broker via Solace JCSMP at: " + connectionUrl);
        
        // Create Solace properties
        JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, connectionUrl);
        properties.setProperty(JCSMPProperties.USERNAME, user);
        properties.setProperty(JCSMPProperties.PASSWORD, pass);
        properties.setProperty(JCSMPProperties.VPN_NAME, vpn);
        
        // Create session
        session = JCSMPFactory.onlyInstance().createSession(properties);
        session.connect();
        
        // Create producer
        producer = session.getMessageProducer(new JCSMPStreamingPublishEventHandler() {
            @Override
            public void responseReceived(String messageID) {}
            @Override
            public void handleError(String messageID, JCSMPException e, long timestamp) {
                Logger.log("ERROR", "Solace Publish Error: " + e.getMessage());
            }
        });
        
        isConnected = true;
        Logger.log("SUCCESS", "Solace JCSMP connection established.");
    }
    
    @Override
    public void publishMessage(String topic, byte[] payload, Map<String, Object> properties) throws Exception {
        publishToTopic(topic, payload, properties);
    }

    @Override
    public void publishToTopic(String topic, byte[] payload, Map<String, Object> properties) throws Exception {
        publishInternal(JCSMPFactory.onlyInstance().createTopic(topic), payload, properties);
    }

    @Override
    public void publishToQueue(String queue, byte[] payload, Map<String, Object> properties) throws Exception {
        publishInternal(JCSMPFactory.onlyInstance().createQueue(queue), payload, properties);
    }

    private void publishInternal(Destination dest, byte[] payload, Map<String, Object> properties) throws Exception {
        if (!isConnected) {
            connect();
        }
        
        Logger.log("INFO", "Publishing message via Solace JCSMP to: " + dest.getName());
        
        // Create message
        XMLMessage msg;
        String bodyPartType = (String) properties.get("amhs_bodypart_type");
        
        if (bodyPartType != null && (bodyPartType.contains("text") || bodyPartType.contains("ia5"))) {
            TextMessage textMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
            String ct = (String) properties.get("content_type");
            String charset = "UTF-8";
            if (ct != null && ct.contains("charset=")) {
                charset = ct.substring(ct.indexOf("charset=") + 8).trim().split("[; ]")[0].replace("\"", "");
            }
            try {
                textMsg.setText(new String(payload, charset));
            } catch (java.io.UnsupportedEncodingException e) {
                textMsg.setText(new String(payload));
            }
            msg = textMsg;
        } else {
            BytesMessage bytesMsg = JCSMPFactory.onlyInstance().createMessage(BytesMessage.class);
            bytesMsg.setData(payload);
            msg = bytesMsg;
        }
        
        // Set User Properties — all amhs_* keys + message_id + creation_time
        SDTMap userProps = JCSMPFactory.onlyInstance().createMap();
        
        // Validate and use correct originator per EUR Doc 047 §4.5.2.12
        String validatedOriginator = getValidatedOriginator(properties);
        
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Skip amhs_originator as it's handled separately with validation
            if (key.equals("amhs_originator")) continue;

            // We only send amhs_*, swim_*, amqp_* and content_type properties to Solace
            if (key.startsWith("amhs_") || key.startsWith("amqp_") || 
                key.startsWith("swim_") || key.equals("content_type")) {
                
                // Solace WebUI fix: sanitize strings to be URI-safe to prevent 'Malformed URI sequence' errors.
                // CRITICAL: We EXEMPT technical metadata like swim_compression or FTBP attributes 
                // because sanitization (replacing % with _pct_) would corrupt these fields for the IUT.
                if (value instanceof String && !isTechnicalProperty(key)) {
                    value = sanitizeForSolace((String) value);
                }
                
                // Use typed methods for SDTMap to avoid unnecessary string conversions 
                // and to help the Solace Management Console render types correctly.
                if (value instanceof String) {
                    userProps.putString(key, (String) value);
                } else if (value instanceof Long) {
                    userProps.putLong(key, (Long) value);
                } else if (value instanceof Integer) {
                    userProps.putInteger(key, (Integer) value);
                } else if (value instanceof Boolean) {
                    userProps.putBoolean(key, (Boolean) value);
                } else if (value instanceof Double) {
                    userProps.putDouble(key, (Double) value);
                } else {
                    // Fallback to string for unknown types
                    userProps.putString(key, String.valueOf(value));
                }
            }
        }
        
        // Always set the validated originator
        userProps.putString("amhs_originator", validatedOriginator);
        
        // amhs_message_id: empty string signals "no message-id" for rejection testing (EUR Doc 047 §4.5.1.2)
        // amhs_message_id and amqp_message_id handling
        String msgIdKey = properties.containsKey("amhs_message_id") ? "amhs_message_id" : 
                         (properties.containsKey("amqp_message_id") ? "amqp_message_id" : null);
        if (msgIdKey != null) {
            String msgId = String.valueOf(properties.get(msgIdKey));
            // Message IDs are technical and should be preserved, but they are also a common source of WebUI crashes.
            // We'll keep them intact for now as they are critical for rejection testing.
            userProps.putString(msgIdKey, msgId);
        }

        // creation_time: value 0 signals epoch/zero timestamp for rejection testing
        if (properties.containsKey("creation_time")) {
            Object ct = properties.get("creation_time");
            if (ct instanceof Long) {
                msg.setSenderTimestamp((Long) ct);
                userProps.putLong("creation_time", (Long) ct);
            } else {
                userProps.putString("creation_time", String.valueOf(ct));
            }
        }
        msg.setProperties(userProps);
        
        // Set Priority (0-9, or 10+ for rejection testing)
        // Per EUR Doc 047 §4.5.2.2: amhs_ats_pri takes precedence over amqp_priority
        if (properties.containsKey("amhs_ats_pri")) {
            msg.setPriority(mapAmhsPriorityToInt((String) properties.get("amhs_ats_pri")));
        } else if (properties.containsKey("amqp_priority")) {
            msg.setPriority(((Number) properties.get("amqp_priority")).intValue());
        }
        
        // Send message
        producer.send(msg, dest);
        
        Logger.log("SUCCESS", "Message published via Solace JCSMP.");

    }
    
    @Override
    public byte[] consumeMessage(String address, long timeoutMs) throws Exception {
        if (!isConnected) {
            connect();
        }
        
        Logger.log("INFO", "Consuming message via Solace JCSMP from: " + address);
        
        Topic solaceTopic = JCSMPFactory.onlyInstance().createTopic(address);
        consumer = session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage msg) {}
            @Override
            public void onException(JCSMPException e) {
                Logger.log("ERROR", "Solace Consumer Error: " + e.getMessage());
            }
        });
        
        session.addSubscription(solaceTopic);
        consumer.start();
        
        BytesXMLMessage rxMsg = consumer.receive((int)timeoutMs);
        
        if (rxMsg != null) {
            Logger.log("SUCCESS", "Message received via Solace JCSMP.");
            
            if (rxMsg instanceof TextMessage) {
                return ((TextMessage) rxMsg).getText().getBytes();
            } else if (rxMsg instanceof BytesMessage) {
                return ((BytesMessage) rxMsg).getData();
            } else {
                byte[] attachment = new byte[rxMsg.getAttachmentContentLength()];
                rxMsg.readAttachmentBytes(attachment);
                return attachment;
            }
        }
        
        return null;
    }
    
    @Override
    public void close() {
        Logger.log("INFO", "Closing Solace JCSMP connection...");
        if (consumer != null) consumer.close();
        if (producer != null) producer.close();
        if (session != null) session.closeSession();
        isConnected = false;
        Logger.log("SUCCESS", "Solace JCSMP connection closed.");
    }
    
    /**
     * Normalizes the connection parameters for Solace.
     * This method translates standard AMQP 1.0 host and port configurations into 
     * SMF-compliant connection strings. Specifically, it maps standard AMQP 
     * ports (5672/5671) to Solace SMF ports (55555/55443) to ensure 
     * interoperability in multi-broker environments.
     */
    private String[] normalizeSolaceConnection(String host, String port) {
        String finalHost = host != null ? host.trim() : "";
        String finalPort = port != null ? port.trim() : "";
        String scheme = "tcp";

        if (finalHost.contains("://")) {
            String[] parts = finalHost.split("://");
            scheme = parts[0];
            String address = parts[1];
            
            if (address.contains(":")) {
                String[] addrParts = address.split(":");
                finalHost = addrParts[0];
                finalPort = addrParts[1];
            } else {
                finalHost = address;
            }
        }
        
        // Map AMQP schemes to SMF schemes since JCSMP only uses SMF
        if (scheme.equalsIgnoreCase("amqps") || scheme.equalsIgnoreCase("tcps") || scheme.equalsIgnoreCase("https")) {
            scheme = "tcps";
        } else {
            scheme = "tcp";
        }
        
        // Map standard AMQP ports to Solace standard SMF ports
        if ("5672".equals(finalPort)) {
            finalPort = "55555";
        } else if ("5671".equals(finalPort)) {
            finalPort = "55443";
        }
        
        return new String[] { scheme, finalHost, finalPort };
    }

    /**
     * Validate and get originator address per EUR Doc 047 §4.5.2.12.
     * If amhs_originator is not a valid 8-letter AFTN address, use default originator and log warning.
     * 
     * @param properties Map of AMQP properties
     * @return Valid 8-letter AFTN originator address (guaranteed non-null)
     */
    private String getValidatedOriginator(Map<String, Object> properties) {
        String originator = (String) properties.get("amhs_originator");
        
        // Check if originator is valid 8-letter AFTN address
        if (originator != null && originator.length() == 8 && originator.matches("[A-Z0-9]+")) {
            return originator;
        }
        
        // Use default originator and log warning
        TestConfig config = TestConfig.getInstance();
        String defaultOriginator = config.getProperty("gateway.default_originator", "LFRCZZZZ");
        
        if (originator != null && !originator.isEmpty()) {
            Logger.log("WARNING", "Invalid amhs_originator format '" + originator + 
                "': must be 8-letter AFTN address. Using default originator: " + defaultOriginator);
        } else {
            Logger.log("INFO", "No amhs_originator provided. Using default originator: " + defaultOriginator);
        }
        
        return defaultOriginator;
    }

    private int mapAmhsPriorityToInt(String amhsPriority) {
        if (amhsPriority == null) return 4;
        switch (amhsPriority.toUpperCase()) {
            case "SS": return 9;
            case "DD": return 7;
            case "FF": return 4;
            case "GG": return 2;
            case "KK": return 0;
            default: return 4;
        }
    }

    /**
     * Determines if a property is technical metadata that should not be sanitized.
     */
    private boolean isTechnicalProperty(String key) {
        return key.equals("swim_compression") || 
               key.startsWith("amhs_ftbp_") || 
               key.equals("amhs_ipm_id") ||
               key.equals("amhs_registered_identifier") ||
               key.equals("amhs_bodypart_type") ||
               key.equals("content_type") ||
               key.startsWith("amqp_");
    }

    private String sanitizeForSolace(String input) {
        if (input == null) return null;
        // Solace WebUI throws 'Malformed URI sequence' if it sees a stray % 
        // We replace it to be safe for display purposes in the manager console.
        return input.replace("%", "_pct_");
    }
}

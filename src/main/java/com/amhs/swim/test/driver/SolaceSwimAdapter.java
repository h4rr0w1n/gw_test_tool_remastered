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
        
        // Set User Properties
        SDTMap userProps = JCSMPFactory.onlyInstance().createMap();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("amhs_")) {
                userProps.putString(key, String.valueOf(entry.getValue()));
            }
        }
        msg.setProperties(userProps);
        
        // Set Priority (0-9, or 10+ for rejection testing)
        if (properties.containsKey("amqp_priority")) {
            msg.setPriority(((Number) properties.get("amqp_priority")).intValue());
        } else if (properties.containsKey("amhs_ats_pri")) {
            msg.setPriority(mapAmhsPriorityToInt((String) properties.get("amhs_ats_pri")));
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
}

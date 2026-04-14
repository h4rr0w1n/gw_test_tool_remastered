package com.amhs.swim.test.driver;

import com.amhs.swim.test.config.TestConfig;
import com.amhs.swim.test.util.Logger;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Binary;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.engine.*;
import org.apache.qpid.proton.message.Message;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.reactor.Handshaker;
import org.apache.qpid.proton.reactor.Reactor;
import org.apache.qpid.proton.reactor.FlowController;
import org.apache.qpid.proton.engine.BaseHandler;
import org.apache.qpid.proton.engine.Event;
import org.apache.qpid.proton.engine.Sasl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Apache Qpid Proton-J implementation of the SwimMessagingAdapter.
 * 
 * This adapter provides a standards-compliant AMQP 1.0 transport layer as required 
 * by ICAO EUR Doc 047. It utilizes the Proton-J Reactor pattern to manage 
 * high-performance asynchronous networking, handshaking, and link state management.
 * 
 * Key Features:
 * - Reactive event-driven transport (TCP).
 * - SASL Plain/Anonymous authentication support.
 * - Explicit Link settlement control (Unsettled Sender/First Receiver).
 * - Automatic flow control (credit) management.
 */
public class QpidSwimAdapter implements SwimMessagingAdapter {
    
    private Connection connection;
    private Session session;
    private Sender sender;
    private Receiver receiver;
    private Reactor reactor;
    private boolean isConnected = false;
    private String authToken;
    private BlockingQueue<Message> receivedMessages;
    private static long deliveryCounter = 0;
    private final Object connectionLock = new Object();
    private boolean connectionFailed = false;
    
    // AMQP 1.0 Application Property Keys per EUR Doc 047
    public static final Symbol AMHS_ATS_PRI = Symbol.valueOf("amhs_ats_pri");
    public static final Symbol AMHS_RECIPIENTS = Symbol.valueOf("amhs_recipients");
    public static final Symbol AMHS_BODY_PART_TYPE = Symbol.valueOf("amhs_bodypart_type");
    public static final Symbol AMHS_CONTENT_TYPE = Symbol.valueOf("amhs_content_type");
    public static final Symbol AMHS_ORIGINATOR = Symbol.valueOf("amhs_originator");
    public static final Symbol AMHS_SUBJECT = Symbol.valueOf("amhs_subject");
    public static final Symbol AMHS_MESSAGE_ID = Symbol.valueOf("amhs_message_id");
    public static final Symbol AMHS_FILING_TIME = Symbol.valueOf("amhs_filing_time");
    public static final Symbol AMHS_DL_HISTORY = Symbol.valueOf("amhs_dl_history");
    public static final Symbol AMHS_SEC_ENVELOPE = Symbol.valueOf("amhs_sec_envelope");
    
    private static final String QPID_PROTON_CLASS = "org.apache.qpid.proton.Proton";
    
    @Override
    public boolean isAvailable() {
        try {
            Class.forName(QPID_PROTON_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    @Override
    public String getAdapterName() {
        return "Qpid-AMQP1.0";
    }
    
    @Override
    public boolean canConnect() {
        if (!isAvailable()) return false;
        
        TestConfig config = TestConfig.getInstance();
        String host = config.getProperty("swim.broker.host", "localhost");
        int port = Integer.parseInt(config.getProperty("swim.broker.port", "5672"));
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000); // 2 second timeout
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Connects to the SWIM Broker using the Proton-J Reactor.
     * Starts a background event-loop thread and waits for the connection handshake to complete.
     */
    @Override
    public void connect() throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Apache Qpid Proton library not available");
        }
        
        TestConfig config = TestConfig.getInstance();
        String host = config.getProperty("swim.broker.host", "localhost");
        String port = config.getProperty("swim.broker.port", "5672");
        String user = config.getProperty("swim.broker.user", "default");
        String pass = config.getProperty("swim.broker.password", "default");
        String containerId = config.getProperty("swim.container.id", "amhs-swim-gateway-test");
        
        Logger.log("INFO", "Connecting to SWIM Broker via AMQP 1.0 Reactor at: " + host + ":" + port);
        
        receivedMessages = new LinkedBlockingQueue<>();
        
        // Start Reactor in a background thread
        try {
            reactor = Proton.reactor();
            
            new Thread(() -> {
                try {
                    // connectionToHost handles transport creation and binding automatically
                    connection = reactor.connectionToHost(host, Integer.parseInt(port), new ProtonHandler(user, pass));
                    connection.setContainer(containerId);
                    
                    reactor.run();
                } catch (Exception e) {
                    Logger.log("ERROR", "AMQP Reactor error: " + e.getMessage());
                    synchronized (connectionLock) {
                        connectionFailed = true;
                        connectionLock.notifyAll();
                    }
                } finally {
                    isConnected = false;
                }
            }, "AMQP-Reactor-Thread").start();
        } catch (IOException e) {
            throw new Exception("Could not initialize AMQP Reactor", e);
        }
        
        // Wait for connection to be established or fail
        synchronized (connectionLock) {
            long waitTime = 10000; // 10s timeout
            long start = System.currentTimeMillis();
            while (!isConnected && !connectionFailed && (System.currentTimeMillis() - start < waitTime)) {
                connectionLock.wait(100);
            }
            
            if (connectionFailed) {
                throw new IOException("Failed to establish AMQP connection (Transport error)");
            }
            if (!isConnected) {
                throw new IOException("Connection timeout (10s)");
            }
        }
        
        Logger.log("SUCCESS", "AMQP 1.0 connection established.");
    }
    
    /**
     * Inner class implementing the Proton logic for handling AMQP 1.0 protocol events.
     * Manages the lifecycle of connections, sessions, and links according to ICAO specs.
     */
    private class ProtonHandler extends BaseHandler {
        private final String user;
        private final String pass;
        
        public ProtonHandler(String user, String pass) {
            this.user = user;
            this.pass = pass;
            add(new Handshaker());
            add(new FlowController());
        }
        
        @Override
        public void onConnectionInit(Event event) {
            Connection conn = event.getConnection();
            conn.open();
        }
        
        @Override
        public void onConnectionBound(Event event) {
            Transport transport = event.getTransport();
            Sasl sasl = transport.sasl();
            if (sasl != null) {
                sasl.setMechanisms("PLAIN", "ANONYMOUS");
                sasl.plain(user, pass);
            }
        }
        
        @Override
        public void onConnectionRemoteOpen(Event event) {
            synchronized (connectionLock) {
                isConnected = true;
                connectionLock.notifyAll();
            }
        }
        
        @Override
        public void onSessionInit(Event event) {
            event.getSession().open();
        }
        
        @Override
        public void onLinkRemoteOpen(Event event) {
            // Links are opened by the publish/consume methods
        }
        
        @Override
        public void onDelivery(Event event) {
            Delivery delivery = event.getDelivery();
            if (delivery.isReadable() && !delivery.isPartial()) {
                Receiver recv = (Receiver) delivery.getLink();
                int size = delivery.pending();
                byte[] buffer = new byte[size];
                int read = recv.recv(buffer, 0, size);
                
                Message msg = Proton.message();
                msg.decode(buffer, 0, read);
                
                onMessageReceived(msg);
                delivery.settle();
            }
            
            if (delivery.isUpdated()) {
                // Handled in send loop for synchronous settlement wait if needed
            }
        }
        
        @Override
        public void onTransportError(Event event) {
            Logger.log("ERROR", "Transport error: " + event.getTransport().getCondition());
            synchronized (connectionLock) {
                connectionFailed = true;
                isConnected = false;
                connectionLock.notifyAll();
            }
        }
    }
    
    @Override
    public void publishToTopic(String topic, byte[] payload, Map<String, Object> properties) throws Exception {
        // For AMQP 1.0 (Qpid), topic vs queue routing is determined by the broker address.
        // The address is set as the message target in publishMessage.
        publishMessage(topic, payload, properties);
    }

    @Override
    public void publishToQueue(String queue, byte[] payload, Map<String, Object> properties) throws Exception {
        publishMessage(queue, payload, properties);
    }

    /**
     * Encapsulates AMQP 1.0 message preparation including headers, application properties, 
     * and body sections (Data/AmqpValue) as defined in the AMHS-SWIM Gateway mapping.
     */
    @Override
    public void publishMessage(String topic, byte[] payload, Map<String, Object> properties) throws Exception {
        if (!isConnected) {
            connect();
        }
        
        // Suppress redundant INFO if needed, or keep it short.
        // log("DEBUG", "Adapter publishing to: " + topic);
        
        // Create AMQP 1.0 message per spec
        Message message = Proton.message();
        
        // Set message annotations
        Map<Symbol, Object> annotations = new HashMap<>();
        // Add broker-specific annotations
        applyBrokerProfileAnnotations(annotations, properties);
        // Add user-provided annotations
        if (properties.containsKey("amqp_message_annotations")) {
            Map<String, Object> userAnn = (Map<String, Object>) properties.get("amqp_message_annotations");
            for (Map.Entry<String, Object> entry : userAnn.entrySet()) {
                annotations.put(Symbol.valueOf(entry.getKey()), entry.getValue());
            }
        }
        if (!annotations.isEmpty()) {
            message.setMessageAnnotations(new MessageAnnotations(annotations));
        }

        // Set delivery annotations
        if (properties.containsKey("amqp_delivery_annotations")) {
            Map<Symbol, Object> delAnn = new HashMap<>();
            Map<String, Object> userDelAnn = (Map<String, Object>) properties.get("amqp_delivery_annotations");
            for (Map.Entry<String, Object> entry : userDelAnn.entrySet()) {
                delAnn.put(Symbol.valueOf(entry.getKey()), entry.getValue());
            }
            message.setDeliveryAnnotations(new org.apache.qpid.proton.amqp.messaging.DeliveryAnnotations(delAnn));
        }
        
        // Set properties per AMQP 1.0 spec
        message.setAddress(topic);
        if (properties.containsKey("amhs_message_id")) {
            message.setMessageId(properties.get("amhs_message_id"));
        }
        if (properties.containsKey("amhs_subject")) {
            message.setSubject((String) properties.get("amhs_subject"));
        }
        if (properties.containsKey("amhs_reply_to")) {
            message.setReplyTo((String) properties.get("amhs_reply_to"));
        }
        if (properties.containsKey("content_type")) {
            message.setContentType((String) properties.get("content_type"));
        }
        
        // Set creation time if present
        if (properties.containsKey("creation_time")) {
            message.setCreationTime((Long) properties.get("creation_time"));
        }
        
        // Set priority (AMQP 1.0 uses 0-9, AMHS uses SS/DD/FF/GG/KK)
        if (properties.containsKey("amqp_priority")) {
            message.setPriority(((Number) properties.get("amqp_priority")).shortValue());
        } else if (properties.containsKey("amhs_ats_pri")) {
            message.setPriority(mapAmhsPriorityToAmqp((String) properties.get("amhs_ats_pri")));
        } else {
            message.setPriority((short) 4); // Default AMQP 1.0 priority
        }
        
        // Set application properties per EUR Doc 047
        Map<String, Object> appProperties = new HashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("amhs_") || key.equals("swim_compression")) {
                appProperties.put(key, entry.getValue());
            }
        }
        message.setApplicationProperties(new ApplicationProperties(appProperties));
        
        // Set body - Data, AmqpValue, or AmqpSequence
        String bodyType = (String) properties.getOrDefault("amqp_body_type", "DATA");
        Section body = createBodySection(payload, 
            (String) properties.getOrDefault("amhs_bodypart_type", "ia5-text"),
            bodyType);
        message.setBody(body);

        // Set footer
        if (properties.containsKey("amqp_footer")) {
            Map<Symbol, Object> footerMap = new HashMap<>();
            Map<String, Object> userFooter = (Map<String, Object>) properties.get("amqp_footer");
            for (Map.Entry<String, Object> entry : userFooter.entrySet()) {
                footerMap.put(Symbol.valueOf(entry.getKey()), entry.getValue());
            }
            message.setFooter(new org.apache.qpid.proton.amqp.messaging.Footer(footerMap));
        }
        
        // Send message via AMQP 1.0 sender link
        sendAmqpMessage(message);
    }
    
    private Session getActiveSession() {
        if (session == null) {
            session = connection.session();
            session.open();
        }
        return session;
    }
    
    /**
     * Create body section based on content type.
     */
    private Section createBodySection(byte[] payload, String bodyPartType, String bodyType) {
        if (payload == null || payload.length == 0) {
            // Scenario for empty body sections
            if ("AMQP_VALUE".equalsIgnoreCase(bodyType)) return new AmqpValue(null);
            if ("AMQP_SEQUENCE".equalsIgnoreCase(bodyType)) return new org.apache.qpid.proton.amqp.messaging.AmqpSequence(new java.util.ArrayList<>());
            return new Data(null);
        }

        if ("AMQP_VALUE".equalsIgnoreCase(bodyType)) {
            if ("ia5-text".equalsIgnoreCase(bodyPartType) || "utf8-text".equalsIgnoreCase(bodyPartType)) {
                return new AmqpValue(new String(payload, StandardCharsets.UTF_8));
            } else {
                return new AmqpValue(new Binary(payload));
            }
        } else if ("AMQP_SEQUENCE".equalsIgnoreCase(bodyType)) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            list.add(new Binary(payload));
            return new org.apache.qpid.proton.amqp.messaging.AmqpSequence(list);
        } else {
            // Default to DATA
            return new Data(new Binary(payload));
        }
    }

    private void applyBrokerProfileAnnotations(Map<Symbol, Object> annotations, Map<String, Object> properties) {
        String profileStr = (String) properties.getOrDefault("amqp_broker_profile", "STANDARD");
        SwimDriver.AMQPProperties.BrokerProfile profile = SwimDriver.AMQPProperties.BrokerProfile.valueOf(profileStr);
        
        switch (profile) {
            case AZURE_SERVICE_BUS:
                annotations.put(Symbol.valueOf("x-opt-enqueued-time"), System.currentTimeMillis());
                annotations.put(Symbol.valueOf("x-opt-partition-key"), "amhs-partition");
                break;
            case IBM_MQ:
                annotations.put(Symbol.valueOf("x-opt-ibm-mq-priority"), properties.getOrDefault("amhs_ats_pri", "FF"));
                break;
            case RABBITMQ:
                annotations.put(Symbol.valueOf("x-amqp-0-9-1-routing-key"), properties.get("amhs_recipients"));
                break;
            default:
                break;
        }
    }
    
    /**
     * Send message via AMQP 1.0 sender link.
     */
    private void sendAmqpMessage(Message message) throws Exception {
        if (sender == null) {
            sender = getActiveSession().sender("test-sender");
            // Set settlement modes per EUR Doc 047 interoperability requirements
            sender.setSenderSettleMode(SenderSettleMode.UNSETTLED);
            sender.setReceiverSettleMode(ReceiverSettleMode.FIRST);
            sender.open();
            
            // Allow reactor thread to process the link open
            Thread.sleep(100);
        }
        
        // Encode message with dynamic buffer sizing
        int bufferSize = 65536;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        while (true) {
            try {
                buffer.clear();
                message.encode(new org.apache.qpid.proton.codec.WritableBuffer.ByteBufferWrapper(buffer));
                break;
            } catch (BufferOverflowException e) {
                bufferSize *= 2;
                if (bufferSize > 100 * 1024 * 1024) { // 100MB limit for safety
                    throw new Exception("Message too large to encode even with 100MB buffer", e);
                }
                buffer = ByteBuffer.allocate(bufferSize);
            }
        }
        buffer.flip();
        
        // Create delivery and send
        byte[] tag = (System.currentTimeMillis() + "-" + (deliveryCounter++)).getBytes();
        Delivery delivery = sender.delivery(tag);
        
        int encodedSize = buffer.remaining();
        byte[] data = new byte[encodedSize];
        buffer.get(data);
        
        sender.send(data, 0, encodedSize);
        sender.advance();
        
        // Wait for settlement
        while (!delivery.isSettled()) {
            Thread.sleep(10);
        }
    }
    
    @Override
    public byte[] consumeMessage(String address, long timeoutMs) throws Exception {
        if (!isConnected) {
            connect();
        }
        
        Logger.log("INFO", "Consuming message via AMQP 1.0 from: " + address);
        
        // Create receiver link
        if (receiver == null) {
            receiver = getActiveSession().receiver(address);
            receiver.setSource(new org.apache.qpid.proton.amqp.messaging.Source());
            ((org.apache.qpid.proton.amqp.messaging.Source)receiver.getSource()).setAddress(address);
            receiver.open();
            
            // Grant flow credits to the sender
            receiver.flow(100);
            
            // Allow reactor thread to process the link open
            Thread.sleep(100);
        }
        
        // Receive message with timeout
        Message message = receiveAmqpMessage(timeoutMs);
        
        if (message != null) {
            Logger.log("SUCCESS", "Message received via AMQP 1.0.");
            
            // Extract body
            Section body = message.getBody();
            if (body instanceof Data) {
                Binary binary = ((Data) body).getValue();
                return binary.getArray();
            } else if (body instanceof AmqpValue) {
                Object value = ((AmqpValue) body).getValue();
                if (value instanceof String) {
                    return ((String) value).getBytes(StandardCharsets.UTF_8);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Receive message with timeout.
     */
    private Message receiveAmqpMessage(long timeoutMs) throws Exception {
        Message msg = receivedMessages.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (msg != null) {
            return msg;
        }
        return null;
    }
    
    /**
     * Handle message received from AMQP 1.0 reactor.
     */
    public void onMessageReceived(Message message) {
        if (receivedMessages != null) {
            receivedMessages.offer(message);
        }
    }
    
    /**
     * Get JWT token from Keycloak OIDC endpoint.
     */
    public String obtainTokenFromKeycloak(String keycloakUrl, String realm, String clientId, 
                                         String username, String password) throws IOException {
        Logger.log("INFO", "Getting JWT token from Keycloak: " + keycloakUrl);
        
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
                Logger.log("SUCCESS", "JWT token obtained successfully.");
                return authToken;
            }
        } else {
            Logger.log("ERROR", "Failed to get token: HTTP " + statusCode);
            throw new IOException("Failed to obtain token: HTTP " + statusCode);
        }
    }
    
    /**
     * Extract access_token from JSON response.
     */
    private String extractAccessToken(String jsonResponse) {
        int start = jsonResponse.indexOf("\"access_token\":\"") + 16;
        if (start < 16) return null;
        int end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }
    
    /**
     * Lookup service in SWIM Registry via REST API.
     */
    public String lookupServiceInRegistry(String registryUrl, String serviceName, 
                                          String serviceType) throws IOException {
        Logger.log("INFO", "Looking up service in registry: " + registryUrl);
        
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
                Logger.log("SUCCESS", "Service lookup successful.");
                return response.toString();
            }
        } else {
            Logger.log("ERROR", "Failed to lookup service: HTTP " + statusCode);
            throw new IOException("Failed to lookup service: HTTP " + statusCode);
        }
    }
    
    @Override
    public void close() {
        Logger.log("INFO", "Closing AMQP 1.0 connection...");
        
        if (sender != null) {
            sender.close();
        }
        if (receiver != null) {
            receiver.close();
        }
        if (session != null) {
            session.close();
        }
        if (connection != null) {
            connection.close();
        }
        
        isConnected = false;
        Logger.log("SUCCESS", "AMQP 1.0 connection closed.");
    }
    
    /**
     * Map AMHS priority to AMQP 1.0 priority (0-9).
     * Per EUR Doc 047 Appendix A Table 9:
     * - SS (Storm/Safety) -> 6
     * - DD (Distress/Urgency) -> 4
     * - FF (Flight Regularity) -> 0
     * - GG (Government) -> 0
     * - KK (Company) -> 0
     * 
     * Rational: SS/DD are mapped to higher priority (6/4) to ensure preferred 
     * queueing across standard AMQP 1.0 brokers (Azure, IBM MQ, RabbitMQ).
     */
    private byte mapAmhsPriorityToAmqp(String amhsPriority) {
        if (amhsPriority == null) return 0;
        switch (amhsPriority.toUpperCase()) {
            case "SS": return 6;
            case "DD": return 4;
            case "FF": return 0;
            case "GG": return 0;
            case "KK": return 0;
            default: return 0;
        }
    }
}

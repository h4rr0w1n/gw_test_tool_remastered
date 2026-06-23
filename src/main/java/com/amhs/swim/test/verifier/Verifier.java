package com.amhs.swim.test.verifier;

import com.amhs.swim.test.util.Utils;
import com.solacesystems.jcsmp.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class Verifier {
    private static final List<String> ALL_APP_PROPS = Arrays.asList(
            "amhs_originator", "amhs_recipients", "amhs_subject", "amhs_ats_pri",
            "amhs_ipm_id", "amhs_registered_identifier", "amhs_user_visible_string",
            "amhs_ats_ft", "amhs_ats_ohi", "amhs_bodypart_type", "amhs_content_encoding",
            "amhs_dl_history", "amhs_sec_envelope", "amhs_reply_to", "amhs_notification_request",
            "amhs_ftbp_file_name", "amhs_ftbp_object_size", "amhs_ftbp_uncompressed_size",
            "amhs_ftbp_last_mod", "swim_compression", "amqp_broker_profile", "amqp_body_type"
    );

    private static java.util.Properties loadDefaultConfig() {
        java.util.Properties props = new java.util.Properties();
        String configPath = "config/test.properties";
        try (InputStreamReader is = new InputStreamReader(new java.io.FileInputStream(configPath), StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(is)) {
            props.load(br);
        } catch (IOException e) {
            try (InputStreamReader is = new InputStreamReader(new java.io.FileInputStream("../config/test.properties"), StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(is)) {
                props.load(br);
            } catch (IOException ignored) {
            }
        }
        return props;
    }

    private static String[] normalizeSolaceConnection(String host, String port) {
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

    private static String sanitizeForSolace(String input) {
        if (input == null) return null;
        return input.replace("%", "_pct_");
    }

    public static void main(String[] args) {
        java.util.Properties defaultProps = loadDefaultConfig();

        String defHost = defaultProps.getProperty("swim.broker.host", "localhost");
        String defPortStr = defaultProps.getProperty("swim.broker.port", "55555");
        String defUser = defaultProps.getProperty("swim.broker.user", "default");
        String defPass = defaultProps.getProperty("swim.broker.password", "default");
        String defVpn = defaultProps.getProperty("swim.broker.vpn", "default");
        String defQueue = defaultProps.getProperty("gateway.default_queue", "TEST.QUEUE");

        String targetAddress, serverUrl, vpnName;
        String user = defUser, pass = defPass, host = defHost;
        String portStr = defPortStr;

        if (args.length < 1) {
            System.out.println("--- Universal AMQP Verifier ---");
            System.out.println("Usage: Verifier <queue-address> [amqp-url] [vpn-name]");
            System.out.println("Defaults (from config):");
            System.out.println("  Queue:    " + defQueue);
            System.out.println("  URL:      tcp://" + defHost + ":55555");
            System.out.println("  VPN:      " + defVpn);
            System.out.println("-----------------------------------");
            targetAddress = defQueue;
            vpnName = defVpn;
        } else {
            targetAddress = args[0];
            serverUrl = args.length > 1 ? args[1] : "";
            vpnName = args.length > 2 ? args[2] : defVpn;

            // Parse server URL to extract user/pass/host/port if needed
            if (serverUrl.startsWith("amqp://") || serverUrl.startsWith("tcp://")) {
                String rest = serverUrl.substring(serverUrl.indexOf("://") + 3);
                int atIndex = rest.indexOf('@');
                if (atIndex != -1) {
                    String userInfo = rest.substring(0, atIndex);
                    int colonIndex = userInfo.indexOf(':');
                    if (colonIndex != -1) {
                        user = userInfo.substring(0, colonIndex);
                        pass = userInfo.substring(colonIndex + 1);
                    } else {
                        user = userInfo;
                        pass = "";
                    }
                    rest = rest.substring(atIndex + 1);
                }
                int colonIndex = rest.indexOf(':');
                int slashIndex = rest.indexOf('/');
                if (colonIndex != -1) {
                    host = rest.substring(0, colonIndex);
                    portStr = slashIndex != -1 ? rest.substring(colonIndex + 1, slashIndex) : rest.substring(colonIndex + 1);
                } else if (slashIndex != -1) {
                    host = rest.substring(0, slashIndex);
                } else {
                    host = rest;
                }
            }
        }

        String[] normalized = normalizeSolaceConnection(host, portStr);
        String connectionUrl = normalized[0] + "://" + normalized[1] + ":" + normalized[2];

        // Print all parsed values for debug
        System.out.println("[DEBUG] Parsed values:");
        System.out.println("[DEBUG]   host = " + normalized[1]);
        System.out.println("[DEBUG]   port = " + normalized[2]);
        System.out.println("[DEBUG]   user = " + user);
        System.out.println("[DEBUG]   pass = " + (pass != null && !pass.isEmpty() ? "***" : "(empty)"));
        System.out.println("[DEBUG]   vpn  = " + vpnName);
        System.out.println("[DEBUG]   addr = " + targetAddress);

        try {
            // Create Solace properties
            JCSMPProperties properties = new JCSMPProperties();
            properties.setProperty(JCSMPProperties.HOST, connectionUrl);
            properties.setProperty(JCSMPProperties.USERNAME, user);
            properties.setProperty(JCSMPProperties.PASSWORD, pass);
            properties.setProperty(JCSMPProperties.VPN_NAME, vpnName);

            // Create session
            JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
            session.connect();

            System.out.println("[*] Connected successfully!");

            String sanitizedAddress = sanitizeForSolace(targetAddress);
            CountDownLatch latch = new CountDownLatch(1);

            // Create message consumer
            XMLMessageConsumer consumer = session.getMessageConsumer(new XMLMessageListener() {
                @Override
                public void onReceive(BytesXMLMessage msg) {
                    printMessage(msg);
                }

                @Override
                public void onException(JCSMPException e) {
                    System.err.println("[-] Consumer error: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            // Subscribe to address (supports both queues and topics)
            Topic topic = JCSMPFactory.onlyInstance().createTopic(sanitizedAddress);
            session.addSubscription(topic);

            consumer.start();

            System.out.println("[*] Listening for address/queue: " + targetAddress);
            System.out.println("[*] Waiting for messages. Press Ctrl+C to stop.");

            // Keep the program running
            latch.await();
        } catch (Exception e) {
            System.err.println("[-] Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printMessage(BytesXMLMessage msg) {
        System.out.println("\n" + Utils.repeat("=", 60));
        System.out.println("--- Message Received ---");

        System.out.println("Standard Properties:");
        printStandardProperty("id", msg.getApplicationMessageId());
        printStandardProperty("user_id", ""); // Not directly available in JCSMP
        printStandardProperty("address", msg.getDestination() != null ? msg.getDestination().getName() : "");
        printStandardProperty("subject", msg.getApplicationMessageType());
        printStandardProperty("reply_to", msg.getReplyTo() != null ? msg.getReplyTo().getName() : "");
        printStandardProperty("correlation_id", msg.getCorrelationId());
        printStandardProperty("content_type", msg.getHTTPContentType());
        printStandardProperty("content_encoding", "");
        printStandardProperty("expiry_time", "");
        printStandardProperty("creation_time", msg.getSenderTimestamp() != null ? String.valueOf(msg.getSenderTimestamp()) : "");
        printStandardProperty("group_id", "");
        printStandardProperty("group_sequence", "");
        printStandardProperty("reply_to_group_id", "");
        printStandardProperty("priority", String.valueOf(msg.getPriority()));

        System.out.println("\nApplication Properties:");
        SDTMap userProps = msg.getProperties();
        Map<String, Object> properties = new HashMap<>();
        if (userProps != null) {
            for (String key : userProps.keySet()) {
                try {
                    properties.put(key, userProps.get(key));
                } catch (JCSMPException e) {
                    // Ignore
                }
            }
        }

        Set<String> printedProps = new HashSet<>();
        for (String key : ALL_APP_PROPS) {
            Object value = properties.get(key);
            printedProps.add(key);
            printAppProperty(key, value);
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!printedProps.contains(entry.getKey())) {
                printAppProperty(entry.getKey(), entry.getValue());
            }
        }

        System.out.println("\nMessage Annotations:");
        System.out.println("  None");

        System.out.println("\nDelivery Annotations:");
        System.out.println("  None");

        // Extract and show payload
        System.out.println("\n--- Payload ---");
        byte[] bodyBytes = null;
        String bodyStr = null;

        if (msg instanceof TextMessage) {
            bodyStr = ((TextMessage) msg).getText();
            bodyBytes = bodyStr != null ? bodyStr.getBytes(StandardCharsets.UTF_8) : new byte[0];
        } else if (msg instanceof BytesMessage) {
            bodyBytes = ((BytesMessage) msg).getData();
            bodyStr = bodyBytes != null ? new String(bodyBytes, StandardCharsets.UTF_8) : "";
        } else {
            int len = msg.getAttachmentContentLength();
            bodyBytes = new byte[len];
            msg.readAttachmentBytes(bodyBytes);
            bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
        }

        int totalBytes = bodyBytes != null ? bodyBytes.length : 0;
        System.out.println("Payload Type: " + (msg != null ? msg.getClass().getSimpleName() : "null"));

        List<String> lines = Arrays.asList(bodyStr.split("\n"));

        if (lines.size() > 67) {
            List<String> truncatedLines = new ArrayList<>();
            for (int i = 0; i < 67; i++) {
                String line = lines.get(i);
                truncatedLines.add(line.length() > 300 ? line.substring(0, 297) + "..." : line);
            }
            String truncatedBody = String.join("\n", truncatedLines);
            System.out.println("Payload Data:\n" + truncatedBody + "\n\n... [Truncated " + (lines.size() - 67) + " lines]");
            System.out.println("Total payload size: " + totalBytes + " bytes");
        } else {
            List<String> trimmedLines = new ArrayList<>();
            for (String line : lines) {
                trimmedLines.add(line.length() > 300 ? line.substring(0, 297) + "..." : line);
            }
            System.out.println("Payload Data: " + String.join("\n", trimmedLines));
            System.out.println("Total payload size: " + totalBytes + " bytes");
        }

        System.out.println(Utils.repeat("=", 60) + "\n");
    }

    private static void printStandardProperty(String name, Object value) {
        if (value == null) {
            System.out.println("  " + name + ": ");
        } else {
            System.out.println("  " + name + ": " + value);
        }
    }

    private static void printAppProperty(String key, Object value) {
        if (value == null) {
            System.out.println("  " + key + ": ");
        } else if (value instanceof List || value instanceof Object[]) {
            List<Object> list;
            if (value instanceof Object[]) {
                list = Arrays.asList((Object[]) value);
            } else {
                list = (List<Object>) value;
            }
            System.out.println("  " + key + ":");
            for (int i = 0; i < list.size(); i += 8) {
                int end = Math.min(i + 8, list.size());
                List<Object> chunk = list.subList(i, end);
                System.out.println("    " + String.join(" ", listToString(chunk)));
            }
        } else if (value instanceof String && (key.equals("amhs_recipients") || key.equals("amhs_address")) &&
                (((String) value).contains(",") || ((String) value).contains(" ")) && ((String) value).length() > 30) {
            String[] split = ((String) value).split(((String) value).contains(",") ? "," : " ");
            List<String> vList = new ArrayList<>();
            for (String s : split) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    vList.add(trimmed);
                }
            }
            System.out.println("  " + key + ":");
            for (int i = 0; i < vList.size(); i += 8) {
                int end = Math.min(i + 8, vList.size());
                List<String> chunk = vList.subList(i, end);
                System.out.println("    " + String.join(" ", chunk));
            }
        } else {
            String val = String.valueOf(value);
            if (val.length() > 300) {
                val = val.substring(0, 297) + "...";
            }
            System.out.println("  " + key + ": " + val);
        }
    }

    private static List<String> listToString(List<Object> list) {
        List<String> strList = new ArrayList<>();
        for (Object obj : list) {
            strList.add(String.valueOf(obj));
        }
        return strList;
    }
}

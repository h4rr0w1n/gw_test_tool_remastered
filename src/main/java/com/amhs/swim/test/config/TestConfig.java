package com.amhs.swim.test.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for the AMHS/SWIM Gateway Test Tool.
 * 
 * Implements a Singleton pattern to manage centralized configuration properties.
 * It handles loading default properties from the classpath and provides a mechanism 
 * for external file-based overrides (config/test.properties) to allow persistent 
 * environment-specific settings.
 */
public class TestConfig {
    private static TestConfig instance;
    private Properties props;

    private TestConfig() {
        props = new Properties();
        loadConfig();
    }

    public static synchronized TestConfig getInstance() {
        if (instance == null) {
            instance = new TestConfig();
        }
        return instance;
    }

    private void loadConfig() {
        // 1. Load default configuration from classpath resources
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config/test.properties")) {
            if (input != null) {
                props.load(input);
            } else {
                setDefaults();
            }
        } catch (Exception ex) {
            setDefaults();
        }

        // 2. Load external overrides from config/test.properties if present
        File file = new File("config/test.properties");
        if (file.exists()) {
            try (InputStream input = new FileInputStream(file)) {
                props.load(input);
                System.out.println("Loaded external configuration from: " + file.getAbsolutePath());
            } catch (Exception ex) {
                System.err.println("Error loading external configuration: " + ex.getMessage());
            }
        }
    }

    public void setDefaults() {
        // Default fallback values for sandbox environment
        props.setProperty("amhs.mta.host", "localhost");
        props.setProperty("amhs.mta.port", "10000");
        props.setProperty("swim.broker.host", "localhost");
        props.setProperty("swim.broker.port", "5672");
        props.setProperty("swim.broker.user", "default");
        props.setProperty("swim.broker.password", "default");
        props.setProperty("swim.broker.vpn", "default");
        props.setProperty("swim.container.id", "amhs-swim-gateway-test");
        props.setProperty("directory.host", "ldap://localhost:389");
        props.setProperty("gateway.max_recipients", "512");
        props.setProperty("gateway.max_size", "1000000");
        props.setProperty("gateway.default_topic", "TEST.TOPIC");
        props.setProperty("gateway.test_recipient", "VVTSYMYX");
        
        // AMQP 1.0 Security (Placeholder for later TLS/SASL configuration)
        props.setProperty("swim.amqp.sasl.mechanism", "PLAIN");
        props.setProperty("swim.amqp.tls.enabled", "false");
        props.setProperty("swim.amqp.truststore.path", "config/truststore.jks");
        props.setProperty("swim.amqp.truststore.password", "password");
        props.setProperty("swim.amqp.keystore.path", "config/keystore.jks");
        props.setProperty("swim.amqp.keystore.password", "password");
    }

    public void setProperty(String key, String value) {
        props.setProperty(key, value);
    }

    public void saveConfig() {
        File file = new File("config/test.properties");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
            props.store(output, "AMHS/SWIM Gateway Test Tool Configuration");
            System.out.println("Config saved to: " + file.getAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Error saving configuration: " + ex.getMessage());
        }
    }

    public String getProperty(String key) {
        String value = System.getProperty(key);
        if (value != null) return value;
        return props.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null) return value;
        return props.getProperty(key, defaultValue);
    }

    public int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
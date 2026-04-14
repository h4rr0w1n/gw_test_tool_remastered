package com.amhs.swim.test.config;

import java.io.InputStream;
import java.util.Properties;

/**
 * Cấu hình bảo mật cho AMHS/SWIM Gateway.
 * Tuân thủ EUR Doc 047 v3.0, Chương 3.4 (AMHS Security).
 * Quản lý các tham số PKI, chữ ký số và hành động khi xác thực thất bại.
 */
public class SecurityConfig {
    private static SecurityConfig instance;
    private Properties props;

    private SecurityConfig() {
        props = new Properties();
        loadConfig();
    }

    public static synchronized SecurityConfig getInstance() {
        if (instance == null) {
            instance = new SecurityConfig();
        }
        return instance;
    }

    private void loadConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config/test.properties")) {
            if (input == null) {
                System.out.println("Không tìm thấy file cấu hình, sử dụng giá trị mặc định cho bảo mật.");
                setDefaults();
                return;
            }
            props.load(input);
        } catch (Exception ex) {
            ex.printStackTrace();
            setDefaults();
        }
    }

    private void setDefaults() {
        // Cấu hình mặc định theo Spec v3.0 3.4
        props.setProperty("gateway.security.enabled", "false");
        props.setProperty("gateway.security.keystore.path", "security/keystore.p12");
        props.setProperty("gateway.security.trusted.ca.path", "security/ca_certs/");
        props.setProperty("gateway.security.passphrase", "changeit");
        props.setProperty("gateway.security.sign.all", "false");
        props.setProperty("gateway.security.action.unsigned", "convert"); // convert, reject, mark
        props.setProperty("gateway.security.action.invalid", "reject"); // reject, mark
    }

    public boolean isSecurityEnabled() {
        return Boolean.parseBoolean(props.getProperty("gateway.security.enabled"));
    }

    public String getKeystorePath() {
        return props.getProperty("gateway.security.keystore.path");
    }

    public String getTrustedCaPath() {
        return props.getProperty("gateway.security.trusted.ca.path");
    }

    public String getPassphrase() {
        return props.getProperty("gateway.security.passphrase");
    }

    public boolean isSignAllMessages() {
        return Boolean.parseBoolean(props.getProperty("gateway.security.sign.all"));
    }

    public String getActionOnUnsigned() {
        return props.getProperty("gateway.security.action.unsigned");
    }

    public String getActionOnInvalidSignature() {
        return props.getProperty("gateway.security.action.invalid");
    }
}
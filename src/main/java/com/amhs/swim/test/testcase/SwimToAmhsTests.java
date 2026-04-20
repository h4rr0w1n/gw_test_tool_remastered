package com.amhs.swim.test.testcase;

import com.amhs.swim.test.driver.SwimDriver;
import com.amhs.swim.test.util.Logger;
import com.amhs.swim.test.config.TestConfig;
import com.amhs.swim.test.config.CaseConfigManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * All 16 SWIM→AMHS test cases (CTSW101–CTSW116).
 * Compliant with ICAO EUR Doc 047 Appendix A — AMHS/SWIM Gateway Testing Plan v3.0.
 *
 * Each case implements:
 *  - getMessages()     → per-message rows shown in the panel
 *  - getCriteria()     → required metrics/criteria header shown in log
 *  - executeSingle()   → single-message execution dispatched by panel
 *  - execute(inputs)   → full batch (used by Batch Execute button)
 */
public class SwimToAmhsTests {

    private SwimDriver swimDriver;

    public SwimToAmhsTests() {
        this.swimDriver = new SwimDriver();
    }

    public SwimDriver getSwimDriver() { return swimDriver; }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String topic(Map<String, String> in) {
        return in != null ? in.getOrDefault("topic",
            TestConfig.getInstance().getProperty("gateway.default_topic", "TEST.TOPIC"))
            : TestConfig.getInstance().getProperty("gateway.default_topic", "TEST.TOPIC");
    }

    private String recip(Map<String, String> in) {
        return in != null ? in.getOrDefault("recipient", "") : "";
    }

    private void dual(Map<String, String> in, byte[] payload, SwimDriver.AMQPProperties props) throws Exception {
        swimDriver.publishToQueue(
            in != null ? in.getOrDefault("queue", TestConfig.getInstance().getProperty("gateway.default_queue", "TEST.QUEUE"))
                       : TestConfig.getInstance().getProperty("gateway.default_queue", "TEST.QUEUE"),
            payload, props);
        swimDriver.publishToTopic(topic(in), payload, props);
    }

    /**
     * Load and validate an AMHS address file.
     * @param path         File path
     * @param caseId       For logging
     * @param required     Required address count (-1 = no validation)
     * @return Array of address lines, or empty if file not found
     */
    private String[] loadAddressFile(String path, String caseId, int required) {
        if (path == null || path.isBlank()) {
            Logger.logCase(caseId, "ERROR", "No address file specified.");
            return new String[0];
        }
        try {
            List<String> lines = Files.readAllLines(Paths.get(path));
            // Filter blank lines
            List<String> addresses = new ArrayList<>();
            for (String l : lines) {
                String trimmed = l.trim();
                if (!trimmed.isEmpty()) addresses.add(trimmed);
            }
            int count = addresses.size();
            Logger.logCase(caseId, "INFO",
                String.format("Address file loaded: %s | Found: %d addresses", path, count));
            if (required > 0 && count != required) {
                Logger.logCase(caseId, "WARN",
                    String.format("Address count mismatch: required %d, found %d. " +
                                  "Test may not match testbook intent.", required, count));
            }
            return addresses.toArray(new String[0]);
        } catch (IOException e) {
            Logger.logCase(caseId, "ERROR", "Failed to read address file: " + e.getMessage());
            return new String[0];
        }
    }

    /**
     * Read a named field from the inputs map.
     * Returns the inputs value if present and non-blank, otherwise returns defaultValue.
     */
    private String getInput(Map<String, String> inputs, String key, String defaultValue) {
        if (inputs == null) return defaultValue;
        String v = inputs.get(key);
        return (v != null && !v.trim().isEmpty()) ? v.trim() : defaultValue;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW101
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW101: Convert AMQP unaware message to AMHS (Text & Binary).
     * Objective: Validate that basic AMQP messages without application-specific metadata 
     * are correctly converted using default gateway settings (e.g., priority mapping, 
     * creation time interpolation).
     */
    public BaseTestCase CTSW101 = new BaseTestCase("CTSW101",
            "Convert AMQP unaware message to AMHS (Text & Binary)") {

        @Override
        public String getCriteria() {
            return
                "• Payload matches text/binary sent from AMQP interface\n" +
                "• AMQP default priority (4) correctly translated to DD/Normal\n" +
                "• Filing time (creation-time) inserted in DDhhmm in ATS-message-header\n" +
                "• amhs_recipients correctly translated to primary-recipient AMHS O/R address\n" +
                "• trace-information compliant with EUR Doc 047 §4.5.4.27–4.5.4.31\n" +
                "• global-domain-identifier sub-components match gateway AMHS management domain\n" +
                "• per-message-indicators: alternate-recipient-allowed, disclosure-prohibited, etc.\n" +
                "• per-recipient-indicators: responsible, non-delivery-report (Ref: §4.5.4.15)";
        }

        @Override
        public String getManualGuide() {
            return 
                "Please verify that the AMHS Test Tool receives the message correctly. " +
                "Confirm that the payload contains the original text or binary content and that the AMQP priority level of 4 has been accurately translated. " +
                "Additionally, ensure that the filing time in the ATS header matches the AMQP creation time.";
        }

        @Override
        public List<TestMessage> getMessages() {
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            
            // Load payloads from default config (config/default_case_payloads.xml)
            // ICAO EUR Doc 047 Appendix A compliant defaults
            return List.of(
                new TestMessage(1,
                    "Text message | priority=4 | content-type: text/plain;charset=utf-8 | body: amqp-value",
                    configMgr.getPayload("CTSW101", 1), true, false, "p1"),
                new TestMessage(2,
                    "Binary message | priority=4 | content-type: application/octet-stream | body: data",
                    configMgr.getPayload("CTSW101", 2), true, false, "binFile", true)
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            
            switch (idx) {
                case 1: {
                    SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
                    p.setRecipients(r);
                    // Get priority from inputs or default to 4
                    String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
                    short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(priority);
                    p.setContentType("text/plain; charset=utf-8");
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
                    
                    // Mandatory properties per EUR Doc 047 Appendix A
                    // Load IPM-ID pattern from config, replace {ts} with timestamp
                    String ipmPattern = configMgr.getConfig("CTSW101", "ipm_id_pattern", "IPM.CTSW101.{ts}");
                    p.setIpmId(ipmPattern.replace("{ts}", String.valueOf(System.currentTimeMillis())));
                    // Load Registered-ID pattern, replace {idx} with message index
                    String regPattern = configMgr.getConfig("CTSW101", "reg_id_pattern", "REG.CTSW101.{idx}");
                    p.setRegisteredId(regPattern.replace("{idx}", "1"));
                    p.setUserVisibleStr("Standard Text Message");
                    
                    // Get payload from config or input
                    String payloadText = configMgr.getPayload("CTSW101", 1);
                    if (inputs != null && inputs.containsKey("p1")) {
                        payloadText = inputs.get("p1");
                    }
                    byte[] payload = payloadText.getBytes();
                    
                    dual(inputs, payload, p);
                    Logger.logTransmission(testCaseId, 1, attempt, topic(inputs),
                        "SENT", "text/plain | priority=" + priority + " | len=" + payload.length);
                    Logger.logPayloadDetail(testCaseId, 1, p.toMap(), new String(payload));
                    return true;
                }
                case 2: {
                    SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
                    p.setRecipients(r);
                    // Get priority from inputs or default to 4
                    String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
                    short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(priority);
                    p.setContentType("application/octet-stream");
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA);
                    
                    // Mandatory properties per EUR Doc 047 Appendix A
                    String ipmPattern = configMgr.getConfig("CTSW101", "ipm_id_pattern", "IPM.CTSW101.{ts}");
                    p.setIpmId(ipmPattern.replace("{ts}", String.valueOf(System.currentTimeMillis())));
                    String regPattern = configMgr.getConfig("CTSW101", "reg_id_pattern", "REG.CTSW101.{idx}");
                    p.setRegisteredId(regPattern.replace("{idx}", "2"));
                    p.setUserVisibleStr("Standard Binary Message");
                    
                    // Get file path from config or input
                    String path = configMgr.getPayload("CTSW101", 2);
                    if (inputs != null && inputs.containsKey("binFile")) {
                        path = inputs.get("binFile");
                    }
                    if (inputs != null && inputs.containsKey("file_path")) {
                        path = inputs.get("file_path");
                    }
                    
                    byte[] payload;
                    if (path != null && !path.trim().isEmpty() && Files.exists(Paths.get(path))) {
                        payload = Files.readAllBytes(Paths.get(path));
                        Logger.logCase(testCaseId, "INFO", "Loaded binary file: " + path + " (" + payload.length + " bytes)");
                    } else {
                        payload = "CTSW101 Binary Dummy Payload".getBytes();
                        Logger.logCase(testCaseId, "WARN", "Binary file not found, using dummy string payload.");
                    }
                    
                    dual(inputs, payload, p);
                    Logger.logTransmission(testCaseId, 2, attempt, topic(inputs),
                        "SENT", "application/octet-stream | priority=" + priority + " | len=" + payload.length);
                    Logger.logPayloadDetail(testCaseId, 2, p.toMap(), "[binary] " + payload.length + " bytes from " + path);
                    return true;
                }
            }
            return false;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW102
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW102: Reject AMQP messages missing required information.
     * Objective: Verify the gateway's validation logic. Messages lacking mandatory 
     * fields (e.g., recipient, payload) or containing invalid values must be rejected 
     * to prevent malformed AMHS traffic.
     */
    public BaseTestCase CTSW102 = new BaseTestCase("CTSW102",
            "Reject AMQP messages missing required information") {

        @Override
        public String getManualGuide() {
            return 
                "Verify that all twelve erroneous messages are rejected by the system under test. " +
                "The gateway must not convert these messages to AMHS; instead, it should log each rejection event and report the situation to the Control Position.";
        }

        @Override
        public String getCriteria() {
            return
                "All injected messages MUST be rejected by the IUT:\n" +
                "• Msg 1/6: priority=10 (invalid, max is 9) → REJECT\n" +
                "• Msg 2/7: empty message-id → REJECT (optional)\n" +
                "• Msg 3/8: creation-time=0 → REJECT (optional)\n" +
                "• Msg 4/9: empty amqp-value / empty data → REJECT\n" +
                "• Msg 5/10: empty amhs_recipients (text) / >8-char recipient (binary) → REJECT\n" +
                "• Msg 6/11: empty content-type → REJECT (optional)\n" +
                "• Each rejection must be logged and reported to Control Position\n" +
                "Ref: EUR Doc 047 §4.5.1.1–4.5.1.5, §4.5.2.2, §4.5.2.10";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1,  "TEXT | priority=10 (invalid) → REJECT",                   "CTSW102 Rejection", true,  false, "payload"),
                new TestMessage(2,  "TEXT | empty message-id → REJECT (optional)",              "CTSW102 Rejection", false, true,  "payload"),
                new TestMessage(3,  "TEXT | creation-time=0 → REJECT (optional)",               "CTSW102 Rejection", false, true,  "payload"),
                new TestMessage(4,  "TEXT | empty amqp-value → REJECT",                         "",                  true,  false, "payload"),
                new TestMessage(5,  "TEXT | empty amhs_recipients → REJECT",                    "CTSW102 Rejection", true,  false, "payload"),
                new TestMessage(6,  "TEXT | empty content-type → REJECT (optional)",            "CTSW102 Rejection", false, true,  "payload"),
                new TestMessage(7,  "BINARY | priority=10 (invalid) → REJECT",                  "src/main/resources/sample.pdf", true,  false, "binPayload_7", true),
                new TestMessage(8,  "BINARY | empty message-id → REJECT (optional)",            "src/main/resources/sample.pdf", false, true,  "binPayload_8", true),
                new TestMessage(9,  "BINARY | creation-time=0 → REJECT (optional)",             "src/main/resources/sample.pdf", false, true,  "binPayload_9", true),
                new TestMessage(10, "BINARY | empty data element → REJECT",                     "",                              true,  false, "binPayload_10", true),
                new TestMessage(11, "BINARY | amhs_recipients >8 chars → REJECT",               "src/main/resources/sample.pdf", true,  false, "binPayload_11", true),
                new TestMessage(12, "BINARY | empty content-type → REJECT (optional)",          "src/main/resources/sample.pdf", false, true,  "binPayload_12", true)
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            String r = recip(inputs);
            byte[] payload;
            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            String desc;
            
            // Get priority from inputs or use default based on test case
            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            
            // For indices 7-12, load binary file bytes if available.
            // Msg 11: binPayload_11 key holds the file path (first pipe-segment from config).
            if (idx >= 7 && idx <= 12) {
                String key = "binPayload_" + idx;
                // For msg 11 the config payload is pipe-separated: filePath|recip_11
                // The file upload button puts the path in binPayload_11 directly.
                String rawDefault = CaseConfigManager.getInstance().getPayload("CTSW102", idx);
                String defaultPath = (rawDefault != null && rawDefault.contains("|"))
                    ? rawDefault.split("\\|", 2)[0].trim() : rawDefault;
                String path = (inputs != null ? inputs.getOrDefault(key, defaultPath != null ? defaultPath : "src/main/resources/sample.pdf") : (defaultPath != null ? defaultPath : "src/main/resources/sample.pdf"));
                if (path != null && !path.trim().isEmpty() && Files.exists(Paths.get(path))) {
                    payload = Files.readAllBytes(Paths.get(path));
                } else {
                    payload = (idx == 10 ? new byte[0] : "Dummy Binary".getBytes());
                }
            } else {
                String configDefault = CaseConfigManager.getInstance().getPayload("CTSW102", idx);
                String defPayload = (configDefault != null && !configDefault.isEmpty()) ? configDefault : "Content";
                payload = (inputs != null ? inputs.getOrDefault("payload", defPayload) : defPayload).getBytes();
            }

            switch (idx) {
                // ---- TEXT messages ----
                case 1:
                    p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
                    p.setRecipients(r);
                    short pri1 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 10;
                    p.setAmqpPriority(pri1);
                    desc = "TEXT priority=" + pri1 + " (INVALID)";
                    break;
                case 2:
                    p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
                    p.setRecipients(r);
                    short pri2 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(pri2); p.setMessageId("");
                    desc = "TEXT empty message-id";
                    break;
                case 3:
                    p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
                    p.setRecipients(r);
                    short pri3 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(pri3); p.setCreationTime(0L);
                    desc = "TEXT creation-time=0";
                    break;
                case 4:
                    p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
                    p.setRecipients(r);
                    short pri4 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(pri4); payload = new byte[0];
                    desc = "TEXT empty amqp-value";
                    break;
                case 5:
                    p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
                    p.setRecipients("");
                    short pri5 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(pri5);
                    desc = "TEXT empty amhs_recipients";
                    break;
                case 6:
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
                    p.setRecipients(r);
                    short pri6 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(pri6);
                    desc = "TEXT empty content-type";
                    break;
                // ---- BINARY messages ----
                case 7:
                    p.setContentType("application/octet-stream"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA);
                    p.setRecipients(r);
                    short pri7 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 10;
                    p.setAmqpPriority(pri7);
                    desc = "BINARY priority=" + pri7 + " (INVALID)";
                    break;
                case 8:
                    p.setContentType("application/octet-stream"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA);
                    p.setRecipients(r);
                    short pri8 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(pri8); p.setMessageId("");
                    desc = "BINARY empty message-id";
                    break;
                case 9:
                    p.setContentType("application/octet-stream"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA);
                    p.setRecipients(r);
                    short pri9 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(pri9); p.setCreationTime(0L);
                    desc = "BINARY creation-time=0";
                    break;
                case 10:
                    p.setContentType("application/octet-stream"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA);
                    p.setRecipients(r);
                    short pri10 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(pri10); payload = new byte[0];
                    desc = "BINARY empty data element";
                    break;
                case 11: {
                    p.setContentType("application/octet-stream"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA);
                    // recip_11 loaded from config (default: LONGADDRESSXXXXX per EUR Doc 047 §4.5.1.4);
                    // editable per-session via config screen field "recip_11".
                    String defaultRecip11Raw = CaseConfigManager.getInstance().getPayload("CTSW102", 11);
                    String defaultRecip11 = (defaultRecip11Raw != null && defaultRecip11Raw.contains("|"))
                        ? defaultRecip11Raw.split("\\|", 2)[1].trim() : "LONGADDRESSXXXXX";
                    String recip11 = (inputs != null && inputs.containsKey("recip_11") && !inputs.get("recip_11").trim().isEmpty())
                        ? inputs.get("recip_11") : defaultRecip11;
                    p.setRecipients(recip11);
                    short pri11 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(pri11);
                    desc = "BINARY amhs_recipients=" + recip11 + " (>8 chars → REJECT)";
                    break;
                }
                case 12:
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA);
                    p.setRecipients(r);
                    short pri12 = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                    p.setAmqpPriority(pri12);
                    desc = "BINARY empty content-type";
                    break;
                default: return false;
            }
            dual(inputs, payload, p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs),
                "SENT (expect REJECT by IUT)", desc);
            String loggedPayload = (idx >= 7 && idx <= 12) 
                    ? "[binary] " + payload.length + " bytes" 
                    : new String(payload);
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(), loggedPayload);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW103
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW103: ATSMHS Service Level conversion.
     * Objective: Test the conversion behavior across different ATSMHS service levels 
     * (Basic, Extended, Content-based, Recipient-based). Ensures that binary attachments 
     * are rejected in Basic mode while handled correctly in Extended mode.
     */
    public BaseTestCase CTSW103 = new BaseTestCase("CTSW103",
            "ATSMHS Service Level conversion (Basic/Extended/Content-based/Recipient-based)") {

        @Override
        public String getManualGuide() {
            return 
                "Verify the conversion output based on the specific Service Level of each message. " +
                "For Basic cases, check the Optional Heading Information and Filing Time; " +
                "for Extended cases, verify the IPM heading and precedence mapping. " +
                "Finally, ensure that binary messages are correctly rejected when the gateway is in Basic mode.";
        }

        @Override
        public String getCriteria() {
            return
                "• Msg 1 (Basic/Text): amhs_ats_ohi → ATS-message-OptionalHeadingInfo; amhs_ats_ft → ATS-Filing-Time;\n" +
                "  amhs_ats_pri → priority-indicator per Table 9; amqp-value → ATS-message-text\n" +
                "• Msg 2 (Basic/Binary): REJECT — log and report to Control Position\n" +
                "• Msg 3 (Extended/Text): amhs_ats_ohi → originators-reference; amhs_ats_ft → Authorization-time DDhhmm;\n" +
                "  amhs_ats_pri → precedence per Table 9; precedence-policy-identifier = 1.3.27.8.0.0\n" +
                "• Msg 4 (ContentBased/Binary→Ext): same as Extended mapping\n" +
                "• Msg 5 (ContentBased/Text→Basic): same as Basic mapping\n" +
                "• Msg 6 (RecipBased/AllExt): all recipients support extended → Extended mapping\n" +
                "• Msg 7 (RecipBased/Mixed): one recipient not ext-capable → Basic mapping fallback\n" +
                "Ref: EUR Doc 047 §3.3.3, §4.5.3.7–4.5.3.9, Table 9";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "amhs_service_level=basic | text/plain | amqp-value | ACCEPT",       "CTSW103 Basic",         true, false, "p1"),
                new TestMessage(2, "amhs_service_level=basic | application/octet-stream | REJECT",       "src/main/resources/sample.pdf", true, false, "binFile_2", true),
                new TestMessage(3, "amhs_service_level=extended | text/plain | amqp-value | ACCEPT",     "CTSW103 Extended",      true, false, "p3"),
                new TestMessage(4, "amhs_service_level=content-based | binary → Extended mapping",       "src/main/resources/sample.pdf", true, false, "binFile_4", true),
                new TestMessage(5, "amhs_service_level=content-based | text → Basic mapping",            "CTSW103 CText",         true, false, "p5"),
                new TestMessage(6, "amhs_service_level=recipient-based | all recipients ext | → Ext",   "CTSW103 RBAllExt",      true, false, "p6"),
                new TestMessage(7, "amhs_service_level=recipient-based | mixed recipients | → Basic",    "CTSW103 RBMixed",       true, false, "p7")
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            
            // Get priority from inputs or default to 4 (per ICAO testbook defaults)
            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short defaultPriority = (short) 4;
            short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : defaultPriority;
            
            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r);
            byte[] payload;
            String desc;
            switch (idx) {
                case 1: {
                    p.setContentType("text/plain; charset=utf-8"); 
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE); 
                    p.setExtraProp("amhs_service_level","basic"); 
                    p.setAmqpPriority(priority); 
                    
                    // Mandatory properties per EUR Doc 047 - loaded from config
                    String ipmPattern = configMgr.getConfig("CTSW103", "ipm_id_pattern", "IPM.CTSW103.{ts}");
                    String ipmId = ipmPattern.replace("{ts}", String.valueOf(System.currentTimeMillis())).replace("{idx}", "1");
                    p.setIpmId(ipmId);
                    
                    String regPattern = configMgr.getConfig("CTSW103", "reg_id_pattern", "REG.CTSW103.{idx}");
                    String regId = regPattern.replace("{ts}", String.valueOf(System.currentTimeMillis())).replace("{idx}", "1");
                    p.setRegisteredId(regId);
                    
                    p.setUserVisibleStr("Basic Mode Text Test");

                    desc="Basic/Text"; 
                    String payloadText = configMgr.getPayload("CTSW103", 1);
                    if (inputs != null && inputs.containsKey("p1")) payloadText = inputs.get("p1");
                    payload = payloadText.getBytes();
                    break;
                }
                case 2: {
                    p.setContentType("application/octet-stream"); 
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA); 
                    p.setExtraProp("amhs_service_level","basic"); 
                    p.setAmqpPriority(priority); 
                    desc="Basic/Binary→REJECT"; 
                    String path = (inputs != null ? inputs.getOrDefault("binFile_2", configMgr.getPayload("CTSW103", 2)) : configMgr.getPayload("CTSW103", 2));
                    payload = (path != null && !path.trim().isEmpty() && Files.exists(Paths.get(path)) ? Files.readAllBytes(Paths.get(path)) : "Dummy".getBytes());
                    break;
                }
                case 3: {
                    p.setContentType("text/plain; charset=utf-8"); 
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE); 
                    p.setExtraProp("amhs_service_level","extended"); 
                    p.setAmqpPriority(priority); 

                    // Mandatory properties per EUR Doc 047 - loaded from config
                    String ipmPattern = configMgr.getConfig("CTSW103", "ipm_id_pattern", "IPM.CTSW103.{ts}");
                    String ipmId = ipmPattern.replace("{ts}", String.valueOf(System.currentTimeMillis())).replace("{idx}", "3");
                    p.setIpmId(ipmId);
                    
                    String regPattern = configMgr.getConfig("CTSW103", "reg_id_pattern", "REG.CTSW103.{idx}");
                    String regId = regPattern.replace("{ts}", String.valueOf(System.currentTimeMillis())).replace("{idx}", "3");
                    p.setRegisteredId(regId);
                    
                    p.setUserVisibleStr("Extended Mode Text Test");

                    desc="Extended/Text"; 
                    String payloadText = configMgr.getPayload("CTSW103", 3);
                    if (inputs != null && inputs.containsKey("p3")) payloadText = inputs.get("p3");
                    payload = payloadText.getBytes();
                    break;
                }
                case 4: {
                    p.setContentType("application/octet-stream"); 
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA); 
                    p.setExtraProp("amhs_service_level","content-based"); 
                    p.setAmqpPriority(priority); 
                    desc="ContentBased/Binary→Ext"; 
                    String path = (inputs != null ? inputs.getOrDefault("binFile_4", configMgr.getPayload("CTSW103", 4)) : configMgr.getPayload("CTSW103", 4));
                    payload = (path != null && !path.trim().isEmpty() && Files.exists(Paths.get(path)) ? Files.readAllBytes(Paths.get(path)) : "Dummy".getBytes());
                    break;
                }
                case 5: {
                    p.setContentType("text/plain; charset=utf-8"); 
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE); 
                    p.setExtraProp("amhs_service_level","content-based"); 
                    p.setAmqpPriority(priority); 
                    desc="ContentBased/Text→Basic"; 
                    String payloadText = configMgr.getPayload("CTSW103", 5);
                    if (inputs != null && inputs.containsKey("p5")) payloadText = inputs.get("p5");
                    payload = payloadText.getBytes();
                    break;
                }
                case 6: {
                    p.setContentType("text/plain; charset=utf-8"); 
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE); 
                    p.setExtraProp("amhs_service_level","recipient-based"); 
                    p.setAmqpPriority(priority); 
                    desc="RecipBased/AllExt"; 
                    String payloadText = configMgr.getPayload("CTSW103", 6);
                    if (inputs != null && inputs.containsKey("p6")) payloadText = inputs.get("p6");
                    payload = payloadText.getBytes();
                    break;
                }
                case 7: {
                    p.setRecipients(r + ",VVTSNONEXT"); 
                    p.setContentType("text/plain; charset=utf-8"); 
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE); 
                    p.setExtraProp("amhs_service_level","recipient-based"); 
                    p.setAmqpPriority(priority); 

                    // Mandatory properties per EUR Doc 047 - loaded from config
                    String ipmPattern = configMgr.getConfig("CTSW103", "ipm_id_pattern", "IPM.CTSW103.{ts}");
                    String ipmId = ipmPattern.replace("{ts}", String.valueOf(System.currentTimeMillis())).replace("{idx}", "7");
                    p.setIpmId(ipmId);
                    
                    String regPattern = configMgr.getConfig("CTSW103", "reg_id_pattern", "REG.CTSW103.{idx}");
                    String regId = regPattern.replace("{ts}", String.valueOf(System.currentTimeMillis())).replace("{idx}", "7");
                    p.setRegisteredId(regId);
                    
                    p.setUserVisibleStr("Mixed Recipients Test");

                    desc="RecipBased/Mixed→Basic"; 
                    String payloadText = configMgr.getPayload("CTSW103", 7);
                    if (inputs != null && inputs.containsKey("p7")) payloadText = inputs.get("p7");
                    payload = payloadText.getBytes();
                    break;
                }
                default: return false;
            }
            dual(inputs, payload, p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs), "SENT", desc);
            String loggedPayload;
            if (idx == 2 || idx == 4) {
                loggedPayload = "[binary] " + payload.length + " bytes";
            } else {
                loggedPayload = new String(payload);
            }
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(), loggedPayload);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW104
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW104: AMQP to AMHS Priority Mapping.
     * Objective: Verify that AMQP priority levels (0-9) are correctly mapped to ATS 
     * priority indicators (SS, DD, FF, GG, KK) according to EUR Doc 047 Table 9. 
     * Also tests the precedence of 'amhs_ats_pri' property over standard AMQP priority.
     */
    public BaseTestCase CTSW104 = new BaseTestCase("CTSW104",
            "Convert AMQP messages with various priority values to AMHS") {

        @Override
        public String getCriteria() {
            return
                "• 10 msgs (priority 0–9): each correctly mapped per Table 9 of EUR Doc 047\n" +
                "• 5 msgs (default prio=4 + amhs_ats_pri SS/DD/FF/GG/KK): amhs_ats_pri overrides AMQP priority\n" +
                "• 4 msgs (prio=1 + amhs_ats_pri SS/DD/FF/GG): amhs_ats_pri takes precedence\n" +
                "• 1 msg  (prio=9 + amhs_ats_pri=KK): confirm amhs_ats_pri precedence over prio\n" +
                "Ref: EUR Doc 047 §4.5.2.2, Table 9 (Priority Mapping)";
        }

        @Override
        public String getManualGuide() {
            return 
                "Verify that all twenty messages, covering various priority levels and ATS priority overrides, are delivered successfully. " +
                "Confirm that the 'amhs_ats_pri' property takes precedence over the standard AMQP priority and that the resulting AMHS priority indicators match EUR Doc 047 Table 9 exactly.";
        }

        @Override
        public List<TestMessage> getMessages() {
            List<TestMessage> msgs = new ArrayList<>();
            for (int i = 0; i <= 9; i++)
                msgs.add(new TestMessage(i+1, "priority=" + i + " | amqp-value | text/plain", "P" + i, true, false, "p" + (i+1)));
            String[] ats = {"SS","DD","FF","GG","KK"};
            for (int i = 0; i < 5; i++)
                msgs.add(new TestMessage(11+i, "priority=4 + amhs_ats_pri=" + ats[i] + " | amhs_ats_pri takes precedence", "Pri4+ATS_"+ats[i], true, false, "p" + (11+i)));
            String[] ats2 = {"SS","DD","FF","GG"};
            for (int i = 0; i < 4; i++)
                msgs.add(new TestMessage(16+i, "priority=1 + amhs_ats_pri=" + ats2[i], "Pri1+ATS_"+ats2[i], true, false, "p" + (16+i)));
            msgs.add(new TestMessage(20, "priority=9 + amhs_ats_pri=KK | amhs_ats_pri must win", "Pri9+ATS_KK", true, false, "p20"));
            return msgs;
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            String r = recip(inputs);
            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r); p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
            String payload;
            String desc;
            
            // Get priority from inputs or use default based on test case
            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short defaultPriority = (short) 4;

            if (idx >= 1 && idx <= 10) {
                int pri = idx - 1;
                String dft = "P" + pri;
                payload = inputs != null ? inputs.getOrDefault("p" + idx, dft) : dft;
                short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) pri;
                p.setAmqpPriority(priority); desc = "priority=" + pri;
            } else if (idx >= 11 && idx <= 15) {
                String[] ats = {"SS","DD","FF","GG","KK"};
                String at = ats[idx-11];
                String dft = "Pri4+ATS_" + at;
                payload = inputs != null ? inputs.getOrDefault("p" + idx, dft) : dft;
                short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;
                p.setAmqpPriority(priority); p.setAtsPri(at);
                desc = "priority=4 + amhs_ats_pri=" + at;
            } else if (idx >= 16 && idx <= 19) {
                String[] ats = {"SS","DD","FF","GG"};
                String at = ats[idx-16];
                String dft = "Pri1+ATS_" + at;
                payload = inputs != null ? inputs.getOrDefault("p" + idx, dft) : dft;
                short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 1;
                p.setAmqpPriority(priority); p.setAtsPri(at);
                desc = "priority=1 + amhs_ats_pri=" + at;
            } else if (idx == 20) {
                String dft = "Pri9+ATS_KK";
                payload = inputs != null ? inputs.getOrDefault("p20", dft) : dft;
                short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 9;
                p.setAmqpPriority(priority); p.setAtsPri("KK");
                desc = "priority=9 + amhs_ats_pri=KK";
            } else return false;

            dual(inputs, payload.getBytes(), p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs), "SENT", desc);
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(), payload);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW105
    // ─────────────────────────────────────────────────────────────────────────

    public BaseTestCase CTSW105 = new BaseTestCase("CTSW105",
            "Convert AMHS-aware AMQP messages containing filing time") {

        @Override
        public String getCriteria() {
            return
                "• Msg 1: empty amhs_ats_ft → filing time derived from AMQP creation-time, formatted DDhhmm\n" +
                "  → inserted in ATS-message-Filing-Time or Authorization-time per ATSMHS Service Level\n" +
                "• Msg 2: amhs_ats_ft='250102' → conveyed as value of ATS-message-Filing-Time\n" +
                "Ref: EUR Doc 047 §4.5.2.10, §4.5.2.10.1.b";
        }

        @Override
        public String getManualGuide() {
            return 
                "Verify the filing time conversion by checking two scenarios: first, confirm that a missing filing time is correctly derived from the AMQP creation time in DDhhmm format; " +
                "second, ensure that an explicit filing time of '250102' is accurately conveyed in the AMHS output.";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "amhs_ats_ft=empty → use creation-time → DDhhmm format", "CTSW105 Default FT",   true, false, "p1"),
                new TestMessage(2, "amhs_ats_ft='250102' → explicit ATS-Filing-Time=250102",  "CTSW105 Explicit FT",  true, false, "p2")
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            
            // Get priority from inputs or default to 4 (per ICAO testbook defaults)
            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short defaultPriority = (short) 4;
            short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : defaultPriority;
            
            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r); p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
            
            // Mandatory properties per EUR Doc 047 - loaded from config
            CaseConfigManager configMgrCTSW105 = CaseConfigManager.getInstance();
            String ipmPattern105 = configMgrCTSW105.getConfig("CTSW105", "ipm_id_pattern", "IPM.CTSW105.{ts}");
            String ipmId105 = ipmPattern105.replace("{ts}", String.valueOf(System.currentTimeMillis()));
            p.setIpmId(ipmId105);
            
            String regPattern105 = configMgrCTSW105.getConfig("CTSW105", "reg_id_pattern", "REG.CTSW105");
            String regId105 = regPattern105.replace("{ts}", String.valueOf(System.currentTimeMillis()));
            p.setRegisteredId(regId105);
            
            p.setUserVisibleStr("Filing Time Test");

            byte[] payload; String desc;
            if (idx == 1) {
                String payloadText = configMgr.getPayload("CTSW105", 1);
                if (inputs != null && inputs.containsKey("p1")) payloadText = inputs.get("p1");
                payload = payloadText.getBytes();
                desc = "empty amhs_ats_ft → creation-time DDhhmm";
            } else if (idx == 2) {
                p.setFilingTime("250102");
                String payloadText = configMgr.getPayload("CTSW105", 2);
                if (inputs != null && inputs.containsKey("p2")) payloadText = inputs.get("p2");
                payload = payloadText.getBytes();
                desc = "amhs_ats_ft=250102";
            } else return false;
            p.setAmqpPriority(priority);
            dual(inputs, payload, p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs), "SENT", desc);
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(), new String(payload));
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW106
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW106: Handling of Optional Heading Information (OHI).
     * Objective: Validate that the 'amhs_ats_ohi' property is correctly mapped and 
     * trimmed based on message priority (53 chars for high/normal, 48 for low) 
     * according to EUR Doc 047 §4.5.2.11.
     */
    public BaseTestCase CTSW106 = new BaseTestCase("CTSW106",
            "Convert AMHS-aware AMQP message containing optional heading information (OHI)") {

        @Override
        public String getManualGuide() {
            return 
                "Confirm that the gateway enforces length limits on the Optional Heading Information (OHI) according to message priority. " +
                "Verify that OHI is limited to 53 characters for SS/DD/FF priorities and 48 characters for GG/KK, ensuring that any content exceeding these limits is correctly trimmed.";
        }

        @Override
        public String getCriteria() {
            return
                "• Msgs 1–3 (priority=4, limit=53 chars): amhs_ats_ohi → originators-reference\n" +
                "  Msg 3: text trimmed to 53 chars\n" +
                "• Msgs 4–6 (priority=6, limit=48 chars): amhs_ats_ohi → originators-reference\n" +
                "  Msg 6: text trimmed to 48 chars\n" +
                "• Optional: repeat with basic ATSMHS level → ATS-Message-Header\n" +
                "Ref: EUR Doc 047 §4.5.2.11";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "priority=4 | amhs_ats_ohi < 53 chars",           "OHI-SHORT | OHI Content",   true, false, "p1"),
                new TestMessage(2, "priority=4 | amhs_ats_ohi = 53 chars exactly",   "A".repeat(53) + " | OHI Content", true, false, "p2"),
                new TestMessage(3, "priority=4 | amhs_ats_ohi > 53 chars → trim 53", "A".repeat(60) + " | OHI Content", true, false, "p3"),
                new TestMessage(4, "priority=6 | amhs_ats_ohi < 48 chars",           "OHI-HI-SHORT | OHI Content", true, false, "p4"),
                new TestMessage(5, "priority=6 | amhs_ats_ohi = 48 chars exactly",   "B".repeat(48) + " | OHI Content", true, false, "p5"),
                new TestMessage(6, "priority=6 | amhs_ats_ohi > 48 chars → trim 48", "B".repeat(60) + " | OHI Content", true, false, "p6")
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short defaultPriority = (idx <= 3) ? (short) 4 : (short) 6;
            short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : defaultPriority;

            String[] ohiDefaults = {"OHI-SHORT", "A".repeat(53), "A".repeat(60), "OHI-HI-SHORT", "B".repeat(48), "B".repeat(60)};
            if (idx < 1 || idx > 6) return false;
            int i = idx - 1;

            // New field keys from buildExtraFieldSpecs: amhs_ats_ohi_<N>, body_<N>
            // Fall back to pipe-split of the XML config default for compatibility
            String configDefault = configMgr.getPayload("CTSW106", idx);
            String[] cfgParts = (configDefault != null && configDefault.contains("|"))
                ? configDefault.split("\\|", 2) : new String[]{configDefault, "OHI Content"};
            String defOhi  = cfgParts[0] != null ? cfgParts[0].trim() : ohiDefaults[i];
            String defBody = cfgParts.length > 1 ? cfgParts[1].trim() : "OHI Content";

            String ohi  = (inputs != null && inputs.containsKey("amhs_ats_ohi_" + idx) && !inputs.get("amhs_ats_ohi_" + idx).trim().isEmpty())
                ? inputs.get("amhs_ats_ohi_" + idx) : defOhi;
            String body = (inputs != null && inputs.containsKey("body_" + idx) && !inputs.get("body_" + idx).trim().isEmpty())
                ? inputs.get("body_" + idx) : defBody;

            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r);
            p.setAmqpPriority(priority);
            p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);

            // Mandatory properties per EUR Doc 047 - loaded from config
            CaseConfigManager configMgrCTSW106 = CaseConfigManager.getInstance();
            String ipmPattern106 = configMgrCTSW106.getConfig("CTSW106", "ipm_id_pattern", "IPM.CTSW106.{ts}");
            String ipmId106 = ipmPattern106.replace("{ts}", String.valueOf(System.currentTimeMillis()));
            p.setIpmId(ipmId106);
            
            String regPattern106 = configMgrCTSW106.getConfig("CTSW106", "reg_id_pattern", "REG.CTSW106");
            String regId106 = regPattern106.replace("{ts}", String.valueOf(System.currentTimeMillis()));
            p.setRegisteredId(regId106);
            
            p.setUserVisibleStr("OHI Data Test");
            p.setExtraProp("amhs_ats_ohi", ohi);
            dual(inputs, body.getBytes(), p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs), "SENT",
                "priority=" + priority + " | amhs_ats_ohi len=" + ohi.length() + " (" + ohi.substring(0, Math.min(20, ohi.length())) + (ohi.length() > 20 ? "..." : "") + ")");
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(), body);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW107
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW107: Subject field mapping.
     * Objective: Verify that the AMQP 'subject' property and the 'amhs_subject' 
     * application property are mapped to the IPM Subject element, with proper length 
     * enforcement (128 chars) and precedence rules.
     */
    public BaseTestCase CTSW107 = new BaseTestCase("CTSW107",
            "Convert AMQP message containing subject field") {

        @Override
        public String getManualGuide() {
            return 
                "Verify the mapping of the subject field into the IPM heading. " +
                "Confirm that subjects exceeding 128 characters are trimmed appropriately, that the standard AMQP subject property is mapped correctly, " +
                "and that the 'amhs_subject' application property takes precedence over the standard subject field when both are present.";
        }

        @Override
        public String getCriteria() {
            return
                "• Msg 1: no subject mapping → IPM element 'subject' MUST be absent\n" +
                "• Msg 2: AMQP subject >128 chars → trimmed to 128 → IPM element 'subject'\n" +
                "• Msg 3: AMQP subject (normal) → mapped to IPM element 'subject'\n" +
                "• Msg 4: empty AMQP subject + amhs_subject app prop → amhs_subject maps to IPM 'subject'\n" +
                "• Msg 5: AMQP subject + amhs_subject present → amhs_subject TAKES PRECEDENCE\n" +
                "Ref: EUR Doc 047 §4.5.2.3";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "no subject properties → deliver message with EMPTY/ABSENT subject",        " | Empty Subject Msg", true, false, "body_1"),
                new TestMessage(2, "AMQP subject > 128 chars → trim to 128 → IPM subject",            "S".repeat(150) + " | Msg1 Payload", true, false, "p2"),
                new TestMessage(3, "AMQP subject (normal) → IPM subject",                             "Normal Subject | Msg2 Payload", true, false, "p3"),
                new TestMessage(4, "empty AMQP subject + amhs_subject app prop → IPM subject",        "AMHS App Prop Subject | Msg3 Payload", true, false, "p4"),
                new TestMessage(5, "AMQP subject + amhs_subject both present → amhs_subject wins",    "subject props | amhs app prop | Msg4 Payload", true, false, "p5")
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;

            String configDefault = configMgr.getPayload("CTSW107", idx);
            String[] cfgParts = configDefault != null ? configDefault.split("\\|", -1) : new String[]{""};

            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r); p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
            p.setAmqpPriority(priority);

            String body; String desc;
            if (idx == 5) {
                String defSubject    = cfgParts.length > 0 ? cfgParts[0].trim() : "subject props";
                String defAmhsSubj   = cfgParts.length > 1 ? cfgParts[1].trim() : "amhs app prop";
                String defBody       = cfgParts.length > 2 ? cfgParts[2].trim() : "Msg5";
                String subject       = getInput(inputs, "subject_" + idx, defSubject);
                String amhsSubject   = getInput(inputs, "amhs_subject_" + idx, defAmhsSubj);
                body                 = getInput(inputs, "body_" + idx, defBody);
                p.setSubject(subject);
                p.setExtraProp("amhs_subject", amhsSubject);
                desc = "AMQP-subject='" + subject + "' + amhs_subject='" + amhsSubject + "' (amhs_subject WINS)";
            } else if (idx == 4) {
                String defAmhsSubj = cfgParts.length > 0 ? cfgParts[0].trim() : "AMHS App Prop";
                String defBody     = cfgParts.length > 1 ? cfgParts[1].trim() : "Msg4";
                String amhsSubject = getInput(inputs, "amhs_subject_" + idx, defAmhsSubj);
                body               = getInput(inputs, "body_" + idx, defBody);
                p.setExtraProp("amhs_subject", amhsSubject);
                desc = "amhs_subject=" + amhsSubject + " (no AMQP subject)";
            } else if (idx == 1) {
                String defBody = cfgParts.length > 1 ? cfgParts[1].trim() : "Empty Subject Msg";
                body = getInput(inputs, "body_" + idx, defBody);
                // No subject property set
                desc = "EMPTY/ABSENT subject field mapping";
            } else {
                // idx 2 (was 1: long) or 3 (was 2: normal)
                String defSubject = cfgParts.length > 0 ? cfgParts[0].trim() : "";
                if (defSubject.isEmpty()) {
                    defSubject = (idx == 2 ? "S".repeat(150) : "Normal Subject");
                }
                String defBody    = cfgParts.length > 1 ? cfgParts[1].trim() : "Msg" + idx;
                String subject    = getInput(inputs, "subject_" + idx, defSubject);
                body              = getInput(inputs, "body_" + idx, defBody);
                p.setSubject(subject);
                desc = "AMQP-subject='" + subject + "' (len=" + subject.length() + (subject.length() > 128 ? " → trim to 128" : "") + ")";
            }

            dual(inputs, body.getBytes(), p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs), "SENT", desc);
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(), body);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW108
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW108: Known originator address conversion.
     * Objective: Validate that an 8-character originator address known to the gateway 
     * is correctly expanded into a full AMHS MF-address.
     */
    public BaseTestCase CTSW108 = new BaseTestCase("CTSW108",
            "AMQP message with known originator indicator") {

        @Override
        public String getManualGuide() {
            return 
                "Verify the conversion of an 8-character originator address into a full AMHS MF-address. " +
                "Confirm that the originator address appears correctly in both the AMHS envelope and the IPM heading as required by the conversion specifications.";
        }

        @Override
        public String getCriteria() {
            return
                "• amhs_originator (8-letter address known in IUT) → converted to MF-address per AFTN/AMHS mapping\n" +
                "• Conveyed as 'originator' in this-IPM heading and 'originator-name' in envelope\n" +
                "Ref: EUR Doc 047 §4.5.2.12a, §4.5.3.2, §4.5.3.5–4.5.3.6, §4.5.4.6";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "amhs_originator=VVTSYMYX (known 8-char) → converted to AMHS MF-address | ACCEPT", "VVTSYMYX | Known Orig Body", true, false, "p1")
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            if (idx != 1) return false;
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;

            // New field keys: originator_108, body_108
            String configDefault = configMgr.getPayload("CTSW108", 1);
            String[] cfgParts = (configDefault != null && configDefault.contains("|"))
                ? configDefault.split("\\|", 2) : new String[]{configDefault, "Known Orig Body"};
            String defOrig = cfgParts[0] != null ? cfgParts[0].trim() : "VVTSYMYX";
            String defBody = cfgParts.length > 1 ? cfgParts[1].trim() : "Known Orig Body";

            String orig = getInput(inputs, "originator_108", defOrig);
            String body = getInput(inputs, "body_108", defBody);

            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r); p.setOriginator(orig);
            p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
            p.setAmqpPriority(priority);
            dual(inputs, body.getBytes(), p);
            Logger.logTransmission(testCaseId, 1, attempt, topic(inputs), "SENT",
                "amhs_originator=" + orig + " (known → expanded to MF-address)");
            Logger.logPayloadDetail(testCaseId, 1, p.toMap(), body);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW109
    // ─────────────────────────────────────────────────────────────────────────

    public BaseTestCase CTSW109 = new BaseTestCase("CTSW109",
            "AMQP message with unknown originator indicator") {

        @Override
        public String getManualGuide() {
            return 
                "Verify that the gateway correctly handles unknown originator addresses by falling back to the configured default originator. " +
                "Confirm that this fallback event is recorded in the system logs and reported to the Control Position for manual follow-up.";
        }

        @Override
        public String getCriteria() {
            return
                "• amhs_originator unknown in IUT → conversion FAILS\n" +
                "• IUT MUST use the configured 'default originator'\n" +
                "• Situation must be logged and reported to Control Position\n" +
                "Ref: EUR Doc 047 §4.5.2.12 (default originator)";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "amhs_originator=UNKNOWN1 (unknown in IUT) → default originator used | logged+reported", "UNKNOWN1 | Unknown Orig Body", true, false, "p1")
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            if (idx != 1) return false;
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;

            // New field keys: originator_109, body_109
            String configDefault = configMgr.getPayload("CTSW109", 1);
            String[] cfgParts = (configDefault != null && configDefault.contains("|"))
                ? configDefault.split("\\|", 2) : new String[]{configDefault, "Unknown Orig Body"};
            String defOrig = cfgParts[0] != null ? cfgParts[0].trim() : "UNKNOWN1";
            String defBody = cfgParts.length > 1 ? cfgParts[1].trim() : "Unknown Orig Body";

            String orig = getInput(inputs, "originator_109", defOrig);
            String body = getInput(inputs, "body_109", defBody);

            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r); p.setOriginator(orig);
            p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
            p.setAmqpPriority(priority);
            dual(inputs, body.getBytes(), p);
            Logger.logTransmission(testCaseId, 1, attempt, topic(inputs), "SENT",
                "amhs_originator=" + orig + " (UNKNOWN → IUT must use default originator per §4.5.2.12)");
            Logger.logPayloadDetail(testCaseId, 1, p.toMap(), body);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW110
    // ─────────────────────────────────────────────────────────────────────────

    public BaseTestCase CTSW110 = new BaseTestCase("CTSW110",
            "Reject AMQP messages with unsupported content-type combinations") {

        @Override
        public String getManualGuide() {
            return 
                "Verify that the gateway strictly enforces content-type combinations. " +
                "Confirm that messages with mismatched or unsupported types (Messages 1, 2, 5, and 6) are rejected, while standard binary and text messages (Messages 3 and 4) are accepted for delivery.";
        }

        @Override
        public String getCriteria() {
            return
                "• Msg 1: content-type=application/octet-stream | amqp-value empty | data empty → REJECT\n" +
                "• Msg 2: content-type=text/plain;charset=utf-8 | amqp-value empty | data present → REJECT\n" +
                "• Msg 3: content-type=application/octet-stream | amqp-value empty | data present → ACCEPT\n" +
                "• Msg 4: content-type=text/plain;charset=utf-8 | amqp-value present | data empty → ACCEPT\n" +
                "• Msg 5 (opt.): content-type=text/plain;charset=utf-16 → REJECT\n" +
                "• Msg 6 (opt.): content-type=text/plain;charset=utf-8 | amqp-value+data both present → REJECT\n" +
                "Rejections must be logged and reported to Control Position\n" +
                "Ref: EUR Doc 047 §4.5.1.6";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "application/octet-stream | data=EMPTY | amqp-value=EMPTY → REJECT",           "N/A",   true,  false, "dummy"),
                new TestMessage(2, "text/plain;charset=utf-8 | amqp-value=EMPTY | data=PRESENT → REJECT",         "src/main/resources/sample.pdf", true,  false, "binFile_2", true),
                new TestMessage(3, "application/octet-stream | amqp-value=EMPTY | data=PRESENT → ACCEPT",         "src/main/resources/sample.pdf", true,  false, "binFile_3", true),
                new TestMessage(4, "text/plain;charset=utf-8 | amqp-value=PRESENT | data=EMPTY → ACCEPT",         "text-accept", true, false, "text_4"),
                new TestMessage(5, "(opt.) text/plain;charset=utf-16 → REJECT",                                   "utf16",  false, true,  "text_5"),
                new TestMessage(6, "(opt.) text/plain;charset=utf-8 | amqp-value+data both present → REJECT",     "both",   false, true,  "text_6")
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            
            // Get priority from inputs or default to 4 (per ICAO testbook defaults)
            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short defaultPriority = (short) 4;
            short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : defaultPriority;
            
            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r);
            p.setAmqpPriority(priority);
            byte[] payload; String desc;
            switch (idx) {
                case 1: p.setContentType("application/octet-stream"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA); payload=new byte[0]; desc="octet-stream|data EMPTY → REJECT"; break;
                case 2: {
                    p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA); 
                    String confDefault = configMgr.getPayload("CTSW110", 2);
                    String path = (inputs != null ? inputs.getOrDefault("binFile_2", confDefault) : confDefault);
                    payload = (path != null && !path.trim().isEmpty() && Files.exists(Paths.get(path)) ? Files.readAllBytes(Paths.get(path)) : "BinaryData".getBytes());
                    desc="text/utf-8|data PRESENT → REJECT"; 
                    break;
                }
                case 3: {
                    p.setContentType("application/octet-stream"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA); 
                    String confDefault = configMgr.getPayload("CTSW110", 3);
                    String path = (inputs != null ? inputs.getOrDefault("binFile_3", confDefault) : confDefault);
                    payload = (path != null && !path.trim().isEmpty() && Files.exists(Paths.get(path)) ? Files.readAllBytes(Paths.get(path)) : "BinaryData".getBytes());
                    desc="octet-stream|data PRESENT → ACCEPT"; 
                    break;
                }
                case 4: {
                    p.setContentType("text/plain; charset=utf-8"); 
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE); 
                    String confDefault = configMgr.getPayload("CTSW110", 4);
                    String payloadText = (inputs!=null?inputs.getOrDefault("text_4", confDefault):confDefault);
                    payload = payloadText.getBytes(); 
                    desc="text/utf-8|amqp-value PRESENT → ACCEPT"; 
                    break;
                }
                case 5: {
                    p.setContentType("text/plain; charset=utf-16"); 
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE); 
                    String confDefault = configMgr.getPayload("CTSW110", 5);
                    String payloadText = (inputs!=null?inputs.getOrDefault("text_5", confDefault):confDefault);
                    payload = payloadText.getBytes("UTF-16"); 
                    desc="text/utf-16 → REJECT"; 
                    break;
                }
                case 6: {
                    p.setContentType("text/plain; charset=utf-8"); 
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE); 
                    String confDefault = configMgr.getPayload("CTSW110", 6);
                    String payloadText = (inputs!=null?inputs.getOrDefault("text_6", confDefault):confDefault);
                    payload = payloadText.getBytes(); 
                    desc="amqp-value+data both → REJECT"; 
                    break;
                }
                default: return false;
            }
            dual(inputs, payload, p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs), "SENT", desc);
            String loggedPayload;
            if (idx == 1 || idx == 2 || idx == 3) {
                loggedPayload = "[binary] " + payload.length + " bytes";
            } else {
                try {
                    loggedPayload = new String(payload, p.getContentType().contains("utf-16") ? "UTF-16" : "UTF-8");
                } catch(Exception e) {
                    loggedPayload = new String(payload);
                }
            }
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(), loggedPayload);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW111: Message size limit enforcement.
     * Objective: Verify that the gateway rejects messages (text or binary) exceeding 
     * the configured maximum size limit (e.g., 1 MB).
     */
    public BaseTestCase CTSW111 = new BaseTestCase("CTSW111",
            "Reject AMQP message if payload exceeds configured maximum size") {

        @Override
        public String getCriteria() {
            return
                "• Msg (a) text: amqp-value size ≤ configured max → ACCEPT, received at AMHS test interface\n" +
                "• Msg (b) binary: data size ≤ configured max → ACCEPT, received at AMHS test interface\n" +
                "• Msg (c) text: amqp-value size > configured max → REJECT, logged, reported to Control Position\n" +
                "• Msg (d) binary: data size > configured max → REJECT, logged, reported to Control Position\n" +
                "Ref: EUR Doc 047 §4.5.1.7 'Maximum message data size'";
        }

        @Override
        public String getManualGuide() {
            return 
                "Verify that the gateway enforces the configured maximum message size limit. " +
                "Messages within the size limit must be accepted and delivered to the AMHS test interface, " +
                "while those exceeding the limit must be rejected, with the event being logged and reported to the Control Position.";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "(a) text/amqp-value size ≤ max → ACCEPT",   "1 KB text",   true, false, "maxSizeText"),
                new TestMessage(2, "(b) binary/data size ≤ max → ACCEPT",        "src/main/resources/small_payload.bin", true, false, "maxSizeBin", true),
                new TestMessage(3, "(c) text/amqp-value size > max → REJECT",   "OVER max",    true, false, "maxSizeTextOver"),
                new TestMessage(4, "(d) binary/data size > max → REJECT",        "src/main/resources/large_payload.bin", true, false, "maxSizeBinOver", true)
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            int max = 1048576; // 1 MB — must match IUT gateway config

            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;

            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r);
            p.setAmqpPriority(priority);
            byte[] payload; String desc;
            switch (idx) {
                case 1: {
                    // Text payload ≤ max — user can supply custom text; pad to 1 KB minimum
                    p.setContentType("text/plain; charset=utf-8");
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
                    String defText = configMgr.getPayload("CTSW111", 1);
                    String userText = getInput(inputs, "maxSizeText", defText != null ? defText : "");
                    byte[] rawBytes = userText.getBytes();
                    // If user text is shorter than 1 KB, pad with 'A' so it is a real 1-KB payload
                    if (rawBytes.length < 1024) {
                        byte[] padded = new byte[1024];
                        System.arraycopy(rawBytes, 0, padded, 0, rawBytes.length);
                        Arrays.fill(padded, rawBytes.length, padded.length, (byte) 'A');
                        payload = padded;
                        Logger.logCase(testCaseId, "INFO",
                            "[MSG-1] User text (" + rawBytes.length + "B) padded to 1024B to meet test requirement.");
                    } else {
                        payload = rawBytes;
                    }
                    desc = "text/plain | " + payload.length + "B ≤ max → ACCEPT";
                    break;
                }
                case 2: {
                    // Binary ≤ max — binary file provided by user
                    p.setContentType("application/octet-stream");
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA);
                    String defPath = configMgr.getPayload("CTSW111", 2);
                    String path = getInput(inputs, "maxSizeBin", defPath != null ? defPath : "");
                    if (!path.isEmpty() && Files.exists(Paths.get(path))) {
                        payload = Files.readAllBytes(Paths.get(path));
                        Logger.logCase(testCaseId, "INFO",
                            "[MSG-2] Loaded binary: " + path + " (" + payload.length + "B)");
                    } else {
                        payload = new byte[1024];
                        Arrays.fill(payload, (byte) 0xAB);
                        Logger.logCase(testCaseId, "WARN",
                            "[MSG-2] No binary file found at \"" + path + "\"; using 1KB dummy payload.");
                    }
                    desc = "binary | " + payload.length + "B ≤ max → ACCEPT";
                    break;
                }
                case 3: {
                    // Text payload > max — user can supply custom text; pad to max+1 KB if shorter
                    p.setContentType("text/plain; charset=utf-8");
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
                    String defText = configMgr.getPayload("CTSW111", 3);
                    String userText = getInput(inputs, "maxSizeTextOver", defText != null ? defText : "");
                    byte[] rawBytes = userText.getBytes();
                    int overSize = max + 1024;
                    if (rawBytes.length <= max) {
                        byte[] padded = new byte[overSize];
                        System.arraycopy(rawBytes, 0, padded, 0, Math.min(rawBytes.length, overSize));
                        Arrays.fill(padded, rawBytes.length, padded.length, (byte) 'X');
                        payload = padded;
                        Logger.logCase(testCaseId, "INFO",
                            "[MSG-3] User text (" + rawBytes.length + "B) padded to " + overSize + "B to exceed max.");
                    } else {
                        payload = rawBytes;
                    }
                    desc = "text/plain | " + payload.length + "B > max → REJECT";
                    break;
                }
                case 4: {
                    // Binary > max — binary file provided by user
                    p.setContentType("application/octet-stream");
                    p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA);
                    String defPath = configMgr.getPayload("CTSW111", 4);
                    String path = getInput(inputs, "maxSizeBinOver", defPath != null ? defPath : "");
                    if (!path.isEmpty() && Files.exists(Paths.get(path))) {
                        payload = Files.readAllBytes(Paths.get(path));
                        Logger.logCase(testCaseId, "INFO",
                            "[MSG-4] Loaded binary: " + path + " (" + payload.length + "B)");
                    } else {
                        payload = new byte[max + 1024];
                        Arrays.fill(payload, (byte) 0xFF);
                        Logger.logCase(testCaseId, "WARN",
                            "[MSG-4] No binary file found at \"" + path + "\"; using " + (max + 1024) + "B dummy payload.");
                    }
                    desc = "binary | " + payload.length + "B > max → REJECT";
                    break;
                }
                default: return false;
            }
            dual(inputs, payload, p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs), "SENT", desc);
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(), "Payload size: " + payload.length + " bytes");
            return true;
        }


        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW112  — file-based address loading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW112: Recipient quantity limit enforcement.
     * Objective: Validate that messages addressing more recipients than allowed by 
     * the gateway configuration (e.g., 512) are correctly rejected.
     */
    public BaseTestCase CTSW112 = new BaseTestCase("CTSW112",
            "Reject AMQP message addressing more recipients than configured maximum") {

        @Override
        public String getManualGuide() {
            return 
                "Verify that the gateway enforces recipient number limits by loading external address files. " +
                "Confirm that a message with 512 recipients is accepted for delivery, while a message with 513 recipients is rejected; " +
                "all rejections should be verified against the system logs and reported status.";
        }

        @Override
        public String getCriteria() {
            return
                "• Msg (a): 512 AMHS recipients ('Maximum message number of recipients' = 512) → ACCEPT\n" +
                "  → Verify message received at AMHS test interface\n" +
                "• Msg (b): 513 AMHS recipients ('Maximum message number of recipients' = 512) → REJECT\n" +
                "  → Verify IUT does NOT deliver, logs event, reports to Control Position\n" +
                "Address lists MUST be loaded from external files (one AMHS address per line).\n" +
                "Ref: EUR Doc 047 §4.5.1.8 'Maximum message number of recipients'";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "(a) Load file with 512 AMHS addresses → send → ACCEPT\n    Required: exactly 512 addresses (one per line)", 
                                "src/main/resources/address_512.txt", true, false, "addressFile_a", true),
                new TestMessage(2, "(b) Load file with 513 AMHS addresses → send → REJECT\n    Required: exactly 513 addresses (one per line)\n    [Sync] copies file from message 1", 
                                "src/main/resources/address_513.txt", true, false, "addressFile_b", true)
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            int required = (idx == 1) ? 512 : 513;
            String fileKey = (idx == 1) ? "addressFile_a" : "addressFile_b";
            String path = inputs != null ? inputs.getOrDefault(fileKey, "") : "";

            // For sync: if msg 2 file is same as msg 1 / empty, check for synced key
            if (idx == 2 && (path == null || path.isBlank())) {
                path = inputs != null ? inputs.getOrDefault("addressFile_a", "") : "";
                if (!path.isBlank()) {
                    Logger.logCase(testCaseId, "INFO", "[MSG-2] Synced address file from Message 1: " + path);
                }
            }

            String r = recip(inputs);
            String[] addresses = new String[0];
            
            // If the user used the standard AMHS RECIPIENTS upload/textbox
            if (r != null && !r.trim().isEmpty()) {
                addresses = r.split(",");
                for(int i=0; i<addresses.length; i++) addresses[i] = addresses[i].trim();
            }

            // Fallback to loading from file if the standard textbox wasn't used or doesn't have enough addresses
            if (addresses.length == 0 && !path.isBlank()) {
                addresses = loadAddressFile(path, testCaseId, required);
            }

            if (addresses.length == 0) {
                Logger.logCase(testCaseId, "ERROR",
                    "[MSG-" + idx + "] No addresses loaded. Please upload an address file either in AMHS RECIPIENTS or PAYLOAD field.");
                return false;
            }

            // Build comma-separated recipient string
            String recipients = String.join(",", addresses);
            Logger.logCase(testCaseId, "INFO",
                String.format("[MSG-%d] Loaded %d addresses. Required: %d. Status: %s",
                    idx, addresses.length, required,
                    addresses.length == required ? "COUNT OK ✓" : "COUNT MISMATCH ✗ (testbook intent may not be met)"));

            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(recipients);
            p.setContentType("text/plain; charset=utf-8");
            p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);

            // Mandatory properties per EUR Doc 047 - loaded from config
            CaseConfigManager configMgrCTSW112 = CaseConfigManager.getInstance();
            String ipmPattern112 = configMgrCTSW112.getConfig("CTSW112", "ipm_id_pattern", "IPM.CTSW112.{ts}");
            String ipmId112 = ipmPattern112.replace("{ts}", String.valueOf(System.currentTimeMillis()));
            p.setIpmId(ipmId112);
            
            String regPattern112 = configMgrCTSW112.getConfig("CTSW112", "reg_id_pattern", "REG.CTSW112");
            String regId112 = regPattern112.replace("{ts}", String.valueOf(System.currentTimeMillis()));
            p.setRegisteredId(regId112);
            
            p.setUserVisibleStr("Multiple Recipients Test");

            String dft = "Msg " + (idx == 1 ? "512" : "513");
            String body = inputs != null ? inputs.getOrDefault("p" + idx + "_body", dft) : dft;
            dual(inputs, body.getBytes(), p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs),
                "SENT",
                addresses.length + " recipients | expect " + (idx == 1 ? "ACCEPT" : "REJECT"));
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(),
                "First 3 addresses: " + String.join(", ",
                    Arrays.copyOfRange(addresses, 0, Math.min(3, addresses.length))) +
                (addresses.length > 3 ? " ... [+" + (addresses.length-3) + " more]" : ""));
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW113
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW113: Incoming receipt and non-receipt notification (RN and NRN).
     * Objective: Validate that the gateway correctly processes and conveys requests 
     * for Receipt Notifications (RN) and Non-Receipt Notifications (NRN) per EUR Doc 047 Table 4.
     */
    public BaseTestCase CTSW113 = new BaseTestCase("CTSW113",
            "Incoming receipt and non-receipt notification (RN and NRN)") {

        @Override
        public String getManualGuide() {
            return 
                "Verify the processing of both receipt and non-receipt notifications (RN and NRN). " +
                "For the first message, please delete it manually in the AMHS Test Tool to trigger an NRN; " +
                "for the second message, accept it to trigger an RN. " +
                "Finally, confirm that the gateway logs the results and reports them according to standard protocols.";
        }

        @Override
        public String getCriteria() {
            return
                "• 2 messages sent with priority=6 (→ SS/Flash) to AMQP consumer\n" +
                "• notification-requests element must contain BOTH 'rn' and 'nrn'\n" +
                "• Msg 1: AMHS Tool removes/deletes → NRN returned to IUT\n" +
                "  IUT behavior: log + report to Control Position + store for manual processing\n" +
                "• Msg 2: AMHS Tool accepts → RN returned to IUT\n" +
                "  IUT behavior: log + report to Control Position + store for manual processing\n" +
                "Ref: EUR Doc 047 §4.5.3.4, §4.4.7.3";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "priority=6 | amhs_notification_request=rn,nrn | → NRN expected from AMHS Tool (delete message)", "NotifRequest NRN", true, false, "p1"),
                new TestMessage(2, "priority=6 | amhs_notification_request=rn,nrn | → RN expected from AMHS Tool (accept message)",  "NotifRequest RN", true, false, "p2")
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            if (idx < 1 || idx > 2) return false;
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            
            // Config default is pipe-separated: payload | amhs_notification_request
            // Standard per EUR Doc 047 §4.4.7.3: both rn and nrn must be requested.
            // The user may override either field from the config screen.
            String configDefault = configMgr.getPayload("CTSW113", idx);
            String[] cfgParts = (configDefault != null && configDefault.contains("|"))
                ? configDefault.split("\\|", 2) : new String[]{configDefault, "rn,nrn"};
            String defPayload = cfgParts[0] != null ? cfgParts[0].trim() : "";
            String defNotifReq = cfgParts.length > 1 ? cfgParts[1].trim() : "rn,nrn";

            String payload = (inputs != null ? inputs.getOrDefault("p" + idx, defPayload) : defPayload);
            // notification_request editable via config screen field; default from XML
            String notifReq = (inputs != null && inputs.containsKey("amhs_notification_request") && !inputs.get("amhs_notification_request").trim().isEmpty())
                ? inputs.get("amhs_notification_request") : defNotifReq;

            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            
            // Get priority from inputs or default to 6 (→ SS, per testbook)
            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 6;
            
            p.setRecipients(r); p.setAmqpPriority(priority);
            p.setContentType("text/plain; charset=utf-8"); p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
            p.setExtraProp("amhs_notification_request", notifReq);
            dual(inputs, payload.getBytes(), p);
            String expected = idx == 1 ? "NRN" : "RN";
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs), "SENT",
                "priority=" + priority + " (→SS) | notification_request=" + notifReq + " | expect " + expected + " from AMHS Tool");
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(), payload);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW114
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * CTSW114: Incoming non-delivery reports (NDR).
     * Objective: Verify that non-delivery reports generated by the AMHS system 
     * are correctly received and logged by the gateway's SWIM interface.
     *
     * NDR triggering requires (per EUR Doc 047 §4.4.1.3):
     *  1. amhs_notification_request must include 'nrn' so the AMHS tool generates an NDR
     *     when the message is deleted (unable-to-transfer scenario)
     *  2. amhs_originator must be set so the AMHS knows where to send the NDR
     *  3. A valid AMHS recipient (populated from the AMHS RECIPIENTS standard field)
     *  The NDR is NOT sent by this tool — it is returned BY the AMHS system after
     *  the operator deletes the received message in the AMHS Test Tool.
     */
    public BaseTestCase CTSW114 = new BaseTestCase("CTSW114",
            "Incoming non-delivery reports (NDR)") {

        @Override
        public String getManualGuide() {
            return
                "Step 1: Send this message (priority=4, notification_request=nrn, originator set).\n" +
                "Step 2: In the AMHS Test Tool, DELETE the received message.\n" +
                "        This triggers an NDR back to the originator (the gateway).\n" +
                "Step 3: Verify the gateway receives the NDR containing:\n" +
                "  - non-delivery-reason-code = 'unable-to-transfer'\n" +
                "  - non-delivery-diagnostic-code = EMPTY\n" +
                "Step 4: Confirm the gateway logs the event and reports to Control Position.\n" +
                "Ref: EUR Doc 047 §4.4.1.3";
        }

        @Override
        public String getCriteria() {
            return
                "• 1 message sent with amhs_notification_request=nrn " +
                    "(so AMHS generates NDR on unable-to-transfer)\n" +
                "• amhs_originator set (AMHS returns NDR to this address)\n" +
                "• AMHS Test Tool operator DELETES the message to trigger NDR\n" +
                "• IUT receives NDR with:\n" +
                "  - non-delivery-reason-code = 'unable-to-transfer'\n" +
                "  - non-delivery-diagnostic-code = EMPTY field\n" +
                "• IUT MUST log the NDR event and report to Control Position\n" +
                "Ref: EUR Doc 047 §4.4.1.3";
        }

        @Override
        public List<TestMessage> getMessages() {
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            String def = configMgr.getPayload("CTSW114", 1);
            return List.of(
                new TestMessage(1,
                    "Send message to AMHS Tool with amhs_notification_request=nrn + amhs_originator set.\n" +
                    "After receipt: DELETE it in AMHS Tool → triggers NDR with 'unable-to-transfer'",
                    def != null ? def : "Trigger NDR Payload", true, false, "p1")
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            if (idx != 1) return false;
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();

            String priorityStr = inputs != null ? inputs.get("amqp_priority") : null;
            short priority = (priorityStr != null && !priorityStr.isEmpty()) ? Short.parseShort(priorityStr) : (short) 4;

            // Payload — user-editable via config screen
            String defPayload = configMgr.getPayload("CTSW114", 1);
            String payload = getInput(inputs, "p1", defPayload != null ? defPayload : "CTSW114 NDR Trigger");

            // amhs_originator — REQUIRED so AMHS can return the NDR to the gateway.
            // Must be a known 8-char AFTN address or full AMHS MF-address (per §4.4.1.3).
            // Loaded from config with fallback to default value in XML
            String defOrig = configMgr.getConfig("CTSW114", "amhs_originator", "VVTSYMYX");
            String originator = getInput(inputs, "amhs_originator", defOrig);

            // amhs_notification_request — REQUIRED to instruct AMHS to generate NDR.
            // Must include 'nrn' (non-receipt notification) per EUR Doc 047 §4.4.7.3.
            // Loaded from config with fallback to default value in XML
            String defNotif = configMgr.getConfig("CTSW114", "amhs_notification_request", "nrn");
            String notifReq = getInput(inputs, "amhs_notification_request_114", defNotif);

            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r);
            p.setAmqpPriority(priority);
            p.setContentType("text/plain; charset=utf-8");
            p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
            p.setOriginator(originator);
            
            // Mandatory properties per EUR Doc 047 - loaded from config
            String ipmPattern114 = configMgr.getConfig("CTSW114", "ipm_id_pattern", "IPM.CTSW114.{ts}");
            String ipmId114 = ipmPattern114.replace("{ts}", String.valueOf(System.currentTimeMillis()));
            p.setIpmId(ipmId114);
            
            String regPattern114 = configMgr.getConfig("CTSW114", "reg_id_pattern", "REG.CTSW114");
            String regId114 = regPattern114.replace("{ts}", String.valueOf(System.currentTimeMillis()));
            p.setRegisteredId(regId114);
            
            p.setUserVisibleStr("NDR Trigger Message");

            p.setExtraProp("amhs_notification_request", notifReq);

            dual(inputs, payload.getBytes(), p);
            Logger.logTransmission(testCaseId, 1, attempt, topic(inputs), "SENT",
                "priority=" + priority + " | notification_request=" + notifReq
                + " | originator=" + originator
                + " | Next step: DELETE message in AMHS Tool → NDR: unable-to-transfer");
            Logger.logPayloadDetail(testCaseId, 1, p.toMap(), payload);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW115
    // ─────────────────────────────────────────────────────────────────────────

    public BaseTestCase CTSW115 = new BaseTestCase("CTSW115",
            "Convert AMQP messages with body-part type and content encoding") {

        @Override
        public String getManualGuide() {
            return 
                "Confirm the accurate conversion of various body part types and content encodings according to EUR Doc 047 Table 10. " +
                "Verify that IA5 text and general text body parts are correctly mapped to ATS-message-text and that the original encoded information types are preserved in the conversion.";
        }

        @Override
        public String getCriteria() {
            return
                "All messages: content-type=text/plain;charset=utf-8\n" +
                "• Msg 1: amhs_bodypart_type=ia5-text | amhs_content_encoding=IA5 | amqp-value='Lorem ipsum'\n" +
                "• Msg 2: amhs_bodypart_type=ia5_text_body_part | amhs_content_encoding=IA5 | amqp-value='Lorem ipsum i5bpt'\n" +
                "• Msg 3: amhs_bodypart_type=general-text-body-part | amhs_content_encoding=ISO-646 | amqp-value='Lorem ipsum 646'\n" +
                "• Msg 4: amhs_bodypart_type=general-text-body-part | amhs_content_encoding=ISO-8859-1 | amqp-value='Lorem ipsum 8859'\n" +
                "Verify: ATS-message-text == amqp-value for each message\n" +
                "Verify: original-encoded-information-types per EUR Doc 047 §4.5.4.7\n" +
                "Ref: EUR Doc 047 §4.5.2.4–4.5.2.5, §4.5.2.14a, §4.5.4.7, Table 10–11";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "amhs_bodypart_type=ia5-text | encoding=IA5 | amqp-value='Lorem ipsum'",                "Lorem ipsum",      true, false, "p1"),
                new TestMessage(2, "amhs_bodypart_type=ia5_text_body_part | encoding=IA5 | amqp-value='Lorem ipsum i5bpt'","Lorem ipsum i5bpt", true, false, "p2"),
                new TestMessage(3, "amhs_bodypart_type=general-text-body-part | encoding=ISO-646 | value='Lorem ipsum 646'","Lorem ipsum 646",  true, false, "p3"),
                new TestMessage(4, "amhs_bodypart_type=general-text-body-part | encoding=ISO-8859-1 | value='Lorem ipsum 8859'","Lorem ipsum 8859", true, false, "p4")
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();
            
            // Config default is pipe-separated: payload | amhs_bodypart_type | amhs_content_encoding
            // Mandated per EUR Doc 047 §4.5.2.4-4.5.2.5, Table 10-11. Editable per-session.
            String[] specBodyParts = {"ia5-text","ia5_text_body_part","general-text-body-part","general-text-body-part"};
            String[] specEncodings = {"IA5","IA5","ISO-646","ISO-8859-1"};
            String[] keys = {"p1","p2","p3","p4"};
            if (idx < 1 || idx > 4) return false;
            int i = idx - 1;

            String configDefault = configMgr.getPayload("CTSW115", idx);
            String[] cfgParts = (configDefault != null && configDefault.contains("|"))
                ? configDefault.split("\\|", 3) : new String[]{configDefault, specBodyParts[i], specEncodings[i]};
            String defPayload    = cfgParts[0] != null ? cfgParts[0].trim() : "";
            String defBodyPart   = cfgParts.length > 1 ? cfgParts[1].trim() : specBodyParts[i];
            String defEncoding   = cfgParts.length > 2 ? cfgParts[2].trim() : specEncodings[i];

            String payload   = (inputs != null ? inputs.getOrDefault(keys[i], defPayload) : defPayload);
            String bodyPart  = (inputs != null && inputs.containsKey("amhs_bodypart_type") && !inputs.get("amhs_bodypart_type").trim().isEmpty())
                ? inputs.get("amhs_bodypart_type") : defBodyPart;
            String encoding  = (inputs != null && inputs.containsKey("amhs_content_encoding") && !inputs.get("amhs_content_encoding").trim().isEmpty())
                ? inputs.get("amhs_content_encoding") : defEncoding;

            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r); p.setContentType("text/plain; charset=utf-8");
            p.setBodyType(SwimDriver.AMQPProperties.BodyType.AMQP_VALUE);
            p.setBodyPartType(bodyPart);
            p.setExtraProp("amhs_content_encoding", encoding);
            dual(inputs, payload.getBytes(), p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs), "SENT",
                bodyPart + " | encoding=" + encoding);
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(), "amqp-value: '" + payload + "'");
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // CTSW116
    // ─────────────────────────────────────────────────────────────────────────

    public BaseTestCase CTSW116 = new BaseTestCase("CTSW116",
            "Convert AMQP binary message with FTBP attributes and GZIP") {

        @Override
        public String getManualGuide() {
            return 
                "Verify the conversion of AMQP binary messages containing File Transfer Body Part (FTBP) attributes. " +
                "Confirm that the file name, object size, and last modification attributes are correctly mapped to the FTBP structure " +
                "and verify that the gateway successfully decompresses GZIP-encoded payloads.";
        }

        @Override
        public String getCriteria() {
            return
                "• Msg 1: content-type=application/octet-stream | data=binary file | amqp-value ABSENT\n" +
                "  App properties: amhs_ftbp_file_name, amhs_ftbp_object_size, amhs_ftbp_last_mod\n" +
                "  → AMHS message must contain file-transfer-body-part (FTBP)\n" +
                "• Msg 2: same as Msg 1 + swim_compression='gzip' (binary payload is GZIP-compressed)\n" +
                "  → IUT must decompress; AMHS FTBP must contain original uncompressed data\n" +
                "Verify: incomplete-pathname = amhs_ftbp_file_name\n" +
                "Verify: actual-values = amhs_ftbp_object_size\n" +
                "Verify: date-and-time-of-last-modification = amhs_ftbp_last_mod (if present)\n" +
                "Verify: ATS-message-text element NOT present (§4.5.2.14b)\n" +
                "Ref: EUR Doc 047 §4.5.2.6–4.5.2.8, §4.5.2.13–4.5.2.14b";
        }

        @Override
        public List<TestMessage> getMessages() {
            return List.of(
                new TestMessage(1, "binary file + FTBP attributes (file_name, object_size, last_mod) | no compression",
                    "src/main/resources/sample.pdf", true, false, "binFile", true),
                new TestMessage(2, "same file + swim_compression=gzip | IUT must decompress | FTBP contains original data",
                    "src/main/resources/sample.pdf", true, false, "binFile2", true)
            );
        }

        @Override
        public boolean executeSingle(int idx, int attempt, Map<String, String> inputs) throws Exception {
            if (idx < 1 || idx > 2) return false;
            String r = recip(inputs);
            CaseConfigManager configMgr = CaseConfigManager.getInstance();

            // Config default is pipe-separated: filePath | amhs_ftbp_last_mod
            // Mandated per EUR Doc 047 §4.5.2.6-4.5.2.8. Editable per-session.
            String configDefault = configMgr.getPayload("CTSW116", idx);
            String[] cfgParts = (configDefault != null && configDefault.contains("|"))
                ? configDefault.split("\\|", 2) : new String[]{configDefault, "240101120000Z"};
            String defFilePath = cfgParts[0] != null ? cfgParts[0].trim() : "src/main/resources/sample.pdf";
            String defLastMod  = cfgParts.length > 1 ? cfgParts[1].trim() : "240101120000Z";

            String fileKey = idx == 1 ? "binFile" : "binFile2";
            String filePath = (inputs != null ? inputs.getOrDefault(fileKey, defFilePath) : defFilePath);
            // amhs_ftbp_last_mod editable via config screen
            String lastMod = (inputs != null && inputs.containsKey("amhs_ftbp_last_mod") && !inputs.get("amhs_ftbp_last_mod").trim().isEmpty())
                ? inputs.get("amhs_ftbp_last_mod") : defLastMod;

            byte[] binPayload;
            java.nio.file.Path fp = Paths.get(filePath);
            if (filePath != null && !filePath.trim().isEmpty() && Files.exists(fp)) {
                binPayload = Files.readAllBytes(fp);
            } else {
                Logger.logCase(testCaseId, "WARN",
                    "[MSG-" + idx + "] File not found: " + filePath + " — using 1KB dummy payload.");
                binPayload = new byte[1024];
            }
            long fileSize = binPayload.length;

            SwimDriver.AMQPProperties p = new SwimDriver.AMQPProperties();
            p.setRecipients(r); p.setContentType("application/octet-stream");
            p.setBodyType(SwimDriver.AMQPProperties.BodyType.DATA);
            p.setExtraProp("amhs_ftbp_file_name", Paths.get(filePath).getFileName().toString());
            p.setExtraProp("amhs_ftbp_object_size", fileSize); // Set as Long for AMQP 1.0 compliance
            p.setExtraProp("amhs_ftbp_last_mod", lastMod);

            byte[] sendPayload = binPayload;
            String desc;
            if (idx == 2) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) { gzip.write(binPayload); }
                sendPayload = baos.toByteArray();
                p.setExtraProp("swim_compression", "gzip");
                desc = "FTBP + GZIP | original=" + fileSize + "B compressed=" + sendPayload.length + "B | last_mod=" + lastMod;
            } else {
                desc = "FTBP | size=" + fileSize + "B | last_mod=" + lastMod;
            }

            dual(inputs, sendPayload, p);
            Logger.logTransmission(testCaseId, idx, attempt, topic(inputs), "SENT", desc);
            Logger.logPayloadDetail(testCaseId, idx, p.toMap(),
                "file=" + filePath + " | size=" + fileSize +
                (idx==2 ? " | compressed=" + sendPayload.length + "B (gzip)" : "") +
                " | ftbp_last_mod=" + lastMod);
            return true;
        }

        @Override public boolean execute() throws Exception { return false; }
    };
}

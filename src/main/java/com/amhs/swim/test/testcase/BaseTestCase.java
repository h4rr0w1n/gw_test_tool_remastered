package com.amhs.swim.test.testcase;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base abstract class for all AMHS/SWIM Gateway test cases.
 * Fully compliant with ICAO EUR Doc 047, Appendix A — AMHS/SWIM Gateway Testing Plan v3.0.
 *
 * This class provides the core lifecycle and metadata management for individual test 
 * scenarios. Each test case defines its own message sequence, acceptance criteria, 
 * and execution logic.
 *
 * Each test case defines:
 * - A list of TestMessage objects (one per message in the testbook scenario).
 * - A criteria/metrics string shown for audit and verification purposes.
 * - Specialized execution methods for single and batch message injection.
 */
public abstract class BaseTestCase {
    protected String testCaseId;
    protected String testCaseName;

    /**
     * Constructs a new test case.
     * @param id The formal ID (e.g., CTSW101) matching EUR Doc 047.
     * @param name A descriptive name for the test scenario.
     */
    public BaseTestCase(String id, String name) {
        this.testCaseId = id;
        this.testCaseName = name;
    }

    public String getTestCaseId() { return testCaseId; }
    public String getTestCaseName() { return testCaseName; }

    // ─────────────────────────────────────────────────────────────────────────
    // Execution API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Execute a SINGLE message by its testbook index.
     * The implementation dispatches to the appropriate per-message logic.
     *
     * @param messageIndex  1-based message number matching getMessages()
     * @param attemptNumber The current attempt/retry count (for logging)
     * @param inputs        User-supplied overrides (custom payload text, file path, etc.)
     * @return true if the message was injected without error
     */
    public boolean executeSingle(int messageIndex, int attemptNumber, Map<String, String> inputs) throws Exception {
        // Default: subclasses override this for per-message dispatch
        return execute(inputs);
    }

    /**
     * Execute ALL messages (batch). Default iterates executeSingle() for each message.
     */
    public boolean executeAll(Map<String, String> inputs) throws Exception {
        List<TestMessage> messages = getMessages();
        boolean allOk = true;
        for (TestMessage msg : messages) {
            allOk &= executeSingle(msg.getIndex(), 1, inputs);
        }
        return allOk;
    }

    /**
     * Legacy execution — kept for compatibility.
     */
    public abstract boolean execute() throws Exception;

    /**
     * Parameterised execution — delegates to executeAll by default.
     */
    public boolean execute(Map<String, String> inputs) throws Exception {
        return executeAll(inputs);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test case metadata
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the list of messages defined in the testbook for this case.
     * Each message includes its index, minimum required description, and default payload.
     */
    public List<TestMessage> getMessages() {
        return Collections.emptyList();
    }

    /**
     * Returns the required metrics/criteria text to be shown at the top of
     * the execution log panel. Sourced directly from EUR Doc 047 Appendix A.
     */
    public abstract String getCriteria();

    /**
     * Optional manual verification guide for the tester.
     */
    public String getManualGuide() {
        return "";
    }

    /**
     * Returns legacy required parameters (kept for backward compat).
     */
    public List<TestParameter> getRequiredParameters() {
        return Collections.emptyList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner: TestMessage
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Describes one message in a test case scenario as defined in the testbook.
     */
    public static class TestMessage {
        private final int index;           // 1-based message number in the testbook
        private final String minText;      // Minimum required description (read-only in UI)
        private final String defaultData;  // Default custom payload / user data key hint
        private final boolean mandatory;   // Pre-checked in the UI (required by testbook)
        private final boolean optional;    // True if testbook marks this message as "Optional"
        private final String customKey;    // Key into user inputs map for this message's data
        private final boolean isFile;      // Whether a file picker should be shown

        /**
         * @param index       Message number in testbook (1, 2, 3 …)
         * @param minText     Minimum required text from testbook (shown read-only)
         * @param defaultData Default custom payload
         * @param mandatory   Whether the message is pre-enabled in the UI
         * @param optional    Whether the testbook marks this as optional
         * @param customKey   Key for retrieving user-overridden data from inputs map
         */
        public TestMessage(int index, String minText, String defaultData,
                           boolean mandatory, boolean optional, String customKey) {
            this(index, minText, defaultData, mandatory, optional, customKey, false);
        }

        /**
         * Full constructor.
         */
        public TestMessage(int index, String minText, String defaultData,
                           boolean mandatory, boolean optional, String customKey, boolean isFile) {
            this.index = index;
            this.minText = minText;
            this.defaultData = defaultData;
            this.mandatory = mandatory;
            this.optional = optional;
            this.customKey = customKey;
            this.isFile = isFile;
        }

        public int getIndex()         { return index; }
        public String getMinText()    { return minText; }
        public String getDefaultData(){ return defaultData; }
        public boolean isMandatory()  { return mandatory; }
        public boolean isOptional()   { return optional; }
        public String getCustomKey()  { return customKey; }
        public boolean isFile()       { return isFile; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner: TestParameter (legacy – kept for backward compat)
    // ─────────────────────────────────────────────────────────────────────────

    public static class TestParameter {
        private String key;
        private String label;
        private String defaultValue;
        private boolean isLargeText;

        public TestParameter(String key, String label, String defaultValue, boolean isLargeText) {
            this.key = key;
            this.label = label;
            this.defaultValue = defaultValue;
            this.isLargeText = isLargeText;
        }

        public String getKey()          { return key; }
        public String getLabel()        { return label; }
        public String getDefaultValue() { return defaultValue; }
        public boolean isLargeText()    { return isLargeText; }
    }
}
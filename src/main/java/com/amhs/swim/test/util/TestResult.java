package com.amhs.swim.test.util;

/**
 * Data model representing a single execution result for a test case message.
 * 
 * This model holds metadata about a message injection, including its status 
 * in the SWIM environment and placeholder fields for manual AMHS verification. 
 * This data is used to populate the session report during export.
 */
public class TestResult {
    private String caseCode;
    private int attempt;
    private int messageIndex;
    private String payloadSummary;
    private String autoResult;

    // Attempt-specific manual validation
    private Boolean msgPass;
    private String msgNote = "";
    private boolean locked = false;

    /**
     * Constructs a result record for a specific message attempt.
     */
    public TestResult(String caseCode, int attempt, int messageIndex, String payloadSummary, String autoResult) {
        this.caseCode = caseCode;
        this.attempt = attempt;
        this.messageIndex = messageIndex;
        this.payloadSummary = payloadSummary;
        this.autoResult = autoResult;
    }

    // Getters for Excel exporter
    public String getCaseCode() { return caseCode; }
    public int getAttempt() { return attempt; }
    public int getMessageIndex() { return messageIndex; }
    public String getPayloadSummary() { return payloadSummary; }
    public String getAutoResult() { return autoResult; }

    public Boolean getMsgPass() { return msgPass; }
    public void setMsgPass(Boolean msgPass) { this.msgPass = msgPass; }

    public String getMsgNote() { return msgNote; }
    public void setMsgNote(String msgNote) { this.msgNote = (msgNote == null) ? "" : msgNote; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
}

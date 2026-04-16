package com.amhs.swim.test.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized manager for tracking and storing test execution results per session.
 * 
 * Implements a Singleton pattern to ensure a consistent state across different 
 * GUI components and test drivers. Results stored here are used for final 
 * report generation (e.g., Excel export).
 */
public class ResultManager {
    private static ResultManager instance;
    private final List<TestResult> results = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, CaseSessionState> sessionStates = new HashMap<>();
    private final Map<String, java.util.concurrent.atomic.AtomicInteger> attemptCounters = new HashMap<>();

    private ResultManager() {}

    public static synchronized ResultManager getInstance() {
        if (instance == null) {
            instance = new ResultManager();
        }
        return instance;
    }

    public void addResult(TestResult result) {
        results.add(result);
    }

    public List<TestResult> getResults() {
        return new ArrayList<>(results);
    }

    public void clear() {
        results.clear();
        sessionStates.clear();
        attemptCounters.clear();
    }

    public int getNextAttempt(String caseCode, int messageIndex) {
        String key = caseCode + "_" + messageIndex;
        return attemptCounters.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
    }

    public CaseSessionState getState(String caseCode) {
        return sessionStates.computeIfAbsent(caseCode, k -> new CaseSessionState());
    }

    /**
     * Gets the latest TestResult for a specific case and message index.
     * Searches backwards since newer results are added at the end.
     */
    public TestResult getLatestMessageResult(String caseCode, int messageIndex) {
        List<TestResult> syncResults = getResults(); // thread-safe copy
        for (int i = syncResults.size() - 1; i >= 0; i--) {
            TestResult r = syncResults.get(i);
            if (r.getCaseCode().equals(caseCode) && r.getMessageIndex() == messageIndex) {
                return r;
            }
        }
        return null;
    }

    // ──────────────────────────────────────────────
    // Row DTO for the Results popup table
    // ──────────────────────────────────────────────

    /** Flat row used to populate the "Display Result" popup and the XLSX export. */
    public static class ResultRow {
        public final String caseCode;
        public final int    attempt;
        public final String messageLabel;
        public final String payloadSummary;
        public final String result;

        public ResultRow(String caseCode, int attempt, String messageLabel,
                         String payloadSummary, String result) {
            this.caseCode       = caseCode;
            this.attempt        = attempt;
            this.messageLabel   = messageLabel;
            this.payloadSummary = payloadSummary;
            this.result         = result;
        }
    }
}

package com.amhs.swim.test.util;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Specialized logging utility for the AMHS/SWIM Gateway Test Tool.
 * 
 * This class manages log distribution across three planes:
 * 1. Standard Output: Real-time console tracking.
 * 2. File Persistence: Long-term audit logs in 'test_results.log'.
 * 3. GUI Feedback: Event-driven updates to the global console and per-test execution panels.
 * 
 * Designed to provide the "Deep Inspection" capability required for manual 
 * verification of AMQP 1.0 message headers and application properties.
 */
public class Logger {
    public static final String LOG_FILE = "test_results.log";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static LogListener globalListener;
    private static final Map<String, LogListener> caseListeners = new ConcurrentHashMap<>();

    public interface LogListener {
        void onLog(String message);
    }

    /** Register/replace the global GUI log listener (bottom strip). */
    public static void setLogListener(LogListener l) {
        globalListener = l;
    }

    /**
     * Register a per-case log listener for the TestCasePanel.
     * When a message is logged with caseId, it is routed to this listener IN ADDITION to the global one.
     */
    public static void setCaseLogListener(String caseId, LogListener l) {
        if (l == null) {
            caseListeners.remove(caseId);
        } else {
            caseListeners.put(caseId, l);
        }
    }

    /** Deregister a per-case log listener. */
    public static void clearCaseLogListener(String caseId) {
        caseListeners.remove(caseId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core log methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generic log — goes to file, console, and global listener only.
     */
    public static void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format("[%s] [%s] %s", timestamp, level, message);
        System.out.println(logEntry);
        writeToFile(logEntry + "\n");

        if (globalListener != null) {
            globalListener.onLog(logEntry);
        }
    }

    /**
     * Case-scoped log — goes to file, console, global listener, AND the per-case panel listener.
     * Use this for all test-execution messages.
     */
    public static void logCase(String caseId, String level, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logEntry = String.format("[%s] [%s] [%s] %s", timestamp, caseId, level, message);
        System.out.println(logEntry);
        writeToFile(logEntry + "\n");

        if (globalListener != null) {
            globalListener.onLog(logEntry);
        }

        LogListener caseListener = caseListeners.get(caseId);
        if (caseListener != null) {
            caseListener.onLog(logEntry);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Structured log helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static void logVerification(String caseId, String details) {
        logCase(caseId, "SUCCESS",
            "\n" + "-".repeat(60) + "\n" +
            " [" + caseId + "] VERIFICATION SUMMARY\n" +
            "  " + details.replace("\n", "\n  ") + "\n" +
            "-".repeat(60) + "\n");
    }

    /**
     * Log the start banner for a case in the requested format:
     * ════════════════════════════════════════════════════════════
     * CASE CTSWxxx
     * (requirements/criteria)
     * 
     * MANUAL VERIFICATION GUIDE:
     * (guide)
     * 
     * Ref: ...
     * ════════════════════════════════════════════════════════════
     */
    public static void logCaseStart(String caseId, String criteria, String manualGuide) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("════════════════════════════════════════════════════════════════════════\n");
        sb.append("CASE ").append(caseId).append("\n");
        
        String requirements = criteria;
        String ref = "";
        if (criteria != null && criteria.contains("Ref:")) {
            int idx = criteria.lastIndexOf("Ref:");
            requirements = criteria.substring(0, idx).trim();
            ref = criteria.substring(idx).replace("Ref:", "").trim();
        }

        if (requirements != null && !requirements.isEmpty()) {
            sb.append(requirements).append("\n");
        }
        
        if (!ref.isEmpty()) {
            sb.append("(Ref: ").append(ref).append(")\n");
        }
        
        sb.append("════════════════════════════════════════════════════════════════════════");
        logCase(caseId, "INFO", sb.toString());
    }

    /** Legacy overload. */
    public static void logCaseStart(String caseId, String criteria) {
        logCaseStart(caseId, criteria, "");
    }

    /**
     * Marks the completion of a test case in the log.
     * Generates a structural boundary in the log file for auditability.
     */
    public static void logCaseEnd(String caseId) {
        String banner =
            "\n════════════════════════════════════════════════════════════════════════\n" +
            "END OF " + caseId + ".\n" +
            "════════════════════════════════════════════════════════════════════════\n";
        logCase(caseId, "INFO", banner);
    }

    /**
     * Log a single message transmission attempt in the ICAO-compliant format.
     * Simulates what the AMQP broker itself would emit.
     */
    public static void logTransmission(String caseId, int msgIndex, int attempt,
                                       String destination, String status, String payloadSummary) {
        String line = String.format(
            "[MSG-%d | Attempt #%d] >> %s | Status: %s | Payload Summary: %s",
            msgIndex, attempt, destination, status, payloadSummary
        );
        logCase(caseId, "AMQP", line);
    }

    /**
     * Log detailed payload for cross-checking.
     * Standardized to "DEEP INSPECTION" style with box-drawing sections for maximum clarity.
     */
    public static void logPayloadDetail(String caseId, int msgIndex, Map<String, Object> props, String bodySummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n  ╔" + "═".repeat(68) + "╗\n");
        sb.append("  ║ [DEEP INSPECTION] AMQP 1.0 MESSAGE METADATA" + " ".repeat(25) + "║\n");
        sb.append("  ╠" + "═".repeat(68) + "╣\n");
        
        if (props != null) {
            props.forEach((k, v) -> {
                if (v != null) {
                    sb.append(String.format("  ║ %-25s : %-40s ║\n", k, v));
                }
            });
        }
        
        if (bodySummary != null && !bodySummary.isEmpty()) {
            sb.append("  ╠" + "═".repeat(68) + "╣\n");
            sb.append("  ║ [PAYLOAD CONTENT] " + " ".repeat(48) + "║\n");
            sb.append("  ╠" + "═".repeat(68) + "╣\n");
            
            // Handle multiple lines if any
            String[] lines = bodySummary.split("\n");
            for (String line : lines) {
                if (line.length() > 64) {
                    sb.append(String.format("  ║ %-66s ║\n", line.substring(0, 63) + "…"));
                } else {
                    sb.append(String.format("  ║ %-66s ║\n", line));
                }
            }
        }
        
        sb.append("  ╚" + "═".repeat(68) + "╝\n");
        logCase(caseId, "PAYLOAD", sb.toString());
    }

    /** Legacy version for simple strings. */
    public static void logPayloadDetail(String caseId, int msgIndex, String detail) {
        logPayloadDetail(caseId, msgIndex, null, detail);
    }

    /**
     * Log a manual verification guideline.
     */
    public static void logManualAction(String caseId, String step) {
        logCase(caseId, "GUIDELINE",
            "\n>>> [MANUAL STEP] " + caseId + ":\n" + step + "\n");
    }

    /**
     * Records a deep trace of AMQP 1.0 properties for compliance verification.
     */
    public static void logAMQPDeepTrace(String caseId, java.util.Map<String, Object> props) {
        logPayloadDetail(caseId, 0, props, "[TRACE ONLY]");
    }

    /**
     * Records traffic log for a test request (as per EUR Doc 047).
     */
    public static void logTraffic(String direction, String content) {
        log("TRAFFIC", "Direction: " + direction);
        log("TRAFFIC", "Content: " + content);
    }

    private static synchronized void writeToFile(String text) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
            fw.write(text);
        } catch (IOException e) {
            System.err.println("Error writing log file: " + e.getMessage());
        }
    }
}
package com.amhs.swim.test.util;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility to load the original ICAO Testbook descriptions from cases.json.
 */
public class TestbookLoader {

    private static final String CASES_FILE_PATH = "cases.json";
    private static JSONObject casesData = null;

    private static void loadData() {
        try {
            Path path = Paths.get(CASES_FILE_PATH);
            if (Files.exists(path)) {
                String content = Files.readString(path);
                casesData = new JSONObject(content);
            } else {
                casesData = new JSONObject();
                Logger.logCase("MAIN", "WARN", "cases.json not found at " + path.toAbsolutePath());
            }
        } catch (IOException e) {
            casesData = new JSONObject();
            Logger.logCase("MAIN", "ERROR", "Failed to read cases.json: " + e.getMessage());
        }
    }

    /**
     * Gets the full description text for a specific Case ID from the JSON data.
     * @param caseId the ID of the case (e.g., "CTSW101")
     * @return the description text or a default message if not found.
     */
    public static String getDescription(String caseId) {
        if (casesData == null) {
            loadData();
        }
        if (casesData.has(caseId)) {
            String raw = casesData.getString(caseId);
            return cleanDescription(raw);
        }
        return "No description available for " + caseId;
    }

    /**
     * Cleans the raw description by removing irrelevant and messy parts.
     */
    private static String cleanDescription(String raw) {
        if (raw == null) return "";
        // Split into lines
        String[] lines = raw.split("\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            // Skip lines that are just page numbers, dates, or references
            if (trimmed.matches("^\\d+$") || // page numbers like "39"
                trimmed.matches("^\\d{2}/\\d{2}/\\d{4}$") || // dates like "06/05/2025"
                trimmed.startsWith("EUR Doc 047") ||
                trimmed.startsWith("Ref Doc 047") ||
                trimmed.startsWith("Test class") ||
                trimmed.startsWith("Normal communications") ||
                trimmed.startsWith("Erroneous AMQP messages") ||
                trimmed.startsWith("Procedural errors") ||
                trimmed.startsWith("\f") || // form feed
                trimmed.equals("AMHS/SWIM Gateway Testing Plan") ||
                trimmed.equals("AST PG") ||
                trimmed.startsWith("Appendix A") ||
                trimmed.matches("^\\d+\\.\\d+.*$") // section numbers like "4.5.2.2"
               ) {
                continue;
            }
            // Skip empty lines if previous was also empty
            if (trimmed.isEmpty() && cleaned.length() > 0 && cleaned.charAt(cleaned.length() - 1) == '\n') {
                continue;
            }
            cleaned.append(line).append("\n");
        }
        return cleaned.toString().trim();
    }
}

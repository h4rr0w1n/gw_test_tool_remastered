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
            return casesData.getString(caseId);
        }
        return "No description available for " + caseId;
    }
}

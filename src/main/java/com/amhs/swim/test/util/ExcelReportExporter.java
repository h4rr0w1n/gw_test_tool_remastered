package com.amhs.swim.test.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Utility for generating standardized Excel (.xlsx) session reports.
 * 
 * Compliant with ICAO-style audit requirements, this class extracts test 
 * results from the {@link ResultManager} and formats them into a structured 
 * spreadsheet containing message payloads, statuses, and manual verification fields.
 */
public class ExcelReportExporter {

    /**
     * Exports all results stored in the current session to the specified Excel file.
     * @param filePath The destination path for the .xlsx file.
     * @throws IOException If the file cannot be written.
     */
    public static void exportToExcel(String filePath) throws IOException {
        List<TestResult> results = ResultManager.getInstance().getResults();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("AMHS-SWIM Test Results");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headFont = workbook.createFont();
            headFont.setBold(true);
            headerStyle.setFont(headFont);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Columns as per Requirement 4
            String[] headers = {
                "CASE CODE (CTSWxxx)", 
                "Attempts", 
                "Messages", 
                "Detailed Message Payloads", 
                "Result"
            };

            // Date Range Row
            Row dateRow = sheet.createRow(0);
            Cell dateCell = dateRow.createCell(0);
            dateCell.setCellValue("DATE: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            
            // Note: Poi requires adding merging regions for spanning if wanted, but simpler to just place it in cell 0.
            org.apache.poi.ss.util.CellRangeAddress mergedRegion = new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 4);
            sheet.addMergedRegion(mergedRegion);
            
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setFont(headFont);
            dateCell.setCellStyle(dateStyle);

            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 2;
            for (TestResult res : results) {
                Row row = sheet.createRow(rowIdx++);
                
                String combinedResult = res.getAutoResult();
                Boolean msgPass = res.getMsgPass();
                if (msgPass != null) {
                    combinedResult += msgPass ? " [Manual: PASS]" : " [Manual: FAIL]";
                }
                String note = res.getMsgNote();
                if (note != null && !note.isEmpty()) {
                    combinedResult += " (Note: " + note + ")";
                }

                row.createCell(0).setCellValue(res.getCaseCode());
                row.createCell(1).setCellValue(res.getAttempt());
                row.createCell(2).setCellValue("Msg-" + res.getMessageIndex());
                row.createCell(3).setCellValue(res.getPayloadSummary());
                row.createCell(4).setCellValue(combinedResult);
            }

            // Auto-size columns for Sheet 1
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // ─────────────────────────────────────────────────────────────────
            // Sheet 2: Case Summaries
            // ─────────────────────────────────────────────────────────────────
            Sheet summarySheet = workbook.createSheet("Case Summaries");
            
            Row summaryHeaderRow = summarySheet.createRow(0);
            Cell hCell0 = summaryHeaderRow.createCell(0);
            hCell0.setCellValue("CASE");
            hCell0.setCellStyle(headerStyle);
            
            Cell hCell1 = summaryHeaderRow.createCell(1);
            hCell1.setCellValue("RESULT");
            hCell1.setCellStyle(headerStyle);
            
            int sumRowIdx = 1;
            for (int i = 1; i <= 16; i++) {
                String caseCode = String.format("CTSW%03d", 100 + i);
                Row row = summarySheet.createRow(sumRowIdx++);
                row.createCell(0).setCellValue(caseCode);
                
                CaseSessionState state = ResultManager.getInstance().getState(caseCode);
                String caseStatus = "UNKNOWN";
                if (state != null && state.casePass != null) {
                    caseStatus = state.casePass ? "PASS" : "FAIL";
                }
                row.createCell(1).setCellValue(caseStatus);
            }
            
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                workbook.write(out);
            }
        }
    }
}

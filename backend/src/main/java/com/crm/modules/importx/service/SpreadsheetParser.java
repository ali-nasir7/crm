package com.crm.modules.importx.service;

import com.crm.common.api.ApiException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses CSV (RFC-4180 via commons-csv) and XLSX (Apache POI) into rows of string maps.
 * Header detection: first non-empty row. Values are stringified without scientific notation.
 */
@Component
public class SpreadsheetParser {

    public record Parsed(List<String> headers, List<Map<String, Object>> rows) {}

    private static final int MAX_ROWS = 50_000;
    private static final int MAX_COLUMNS = 60;

    public Parsed parse(String fileName, byte[] content) {
        boolean isXlsx = fileName.toLowerCase().endsWith(".xlsx")
            || (content.length > 4 && content[0] == 'P' && content[1] == 'K'); // zip magic
        if (isXlsx) return parseXlsx(content);
        return parseCsv(content);
    }

    private Parsed parseCsv(byte[] content) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            Iterable<org.apache.commons.csv.CSVRecord> records = org.apache.commons.csv.CSVFormat.DEFAULT
                .builder().setIgnoreEmptyLines(true).setTrim(true).build().parse(reader);
            List<String> headers = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            boolean headerDone = false;
            int rowIdx = 0;
            for (org.apache.commons.csv.CSVRecord record : records) {
                if (!headerDone) {
                    for (int i = 0; i < record.size() && i < MAX_COLUMNS; i++) {
                        String h = record.get(i);
                        headers.add(h != null && !h.isBlank() ? h.trim() : "Column " + (i + 1));
                    }
                    headerDone = true;
                    continue;
                }
                if (rowIdx++ >= MAX_ROWS) throw ApiException.badRequest("File exceeds the " + MAX_ROWS + " row limit");
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < record.size() ? record.get(i) : "");
                }
                rows.add(row);
            }
            if (headers.isEmpty()) throw ApiException.badRequest("Could not detect a header row in the file");
            return new Parsed(headers, rows);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Could not parse CSV file: " + e.getMessage());
        }
    }

    private Parsed parseXlsx(byte[] content) {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheetAt(0);
            List<String> headers = new ArrayList<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            int firstRow = 0;
            Row headerRow = sheet.getRow(firstRow);
            if (headerRow == null) throw ApiException.badRequest("Could not detect a header row in the file");
            int colCount = Math.min(headerRow.getLastCellNum(), MAX_COLUMNS);
            for (int c = 0; c < colCount; c++) {
                String h = stringVal(headerRow.getCell(c));
                headers.add(h != null && !h.isBlank() ? h.trim() : "Column " + (c + 1));
            }
            for (int r = firstRow + 1; r <= sheet.getLastRowNum(); r++) {
                if (rows.size() >= MAX_ROWS) throw ApiException.badRequest("File exceeds the " + MAX_ROWS + " row limit");
                Row row = sheet.getRow(r);
                if (row == null || isEmpty(row, colCount)) continue;
                Map<String, Object> map = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    map.put(headers.get(c), stringVal(row.getCell(c)));
                }
                rows.add(map);
            }
            return new Parsed(headers, rows);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Could not parse Excel file: " + e.getMessage());
        }
    }

    private boolean isEmpty(Row row, int cols) {
        for (int c = 0; c < cols; c++) {
            String v = stringVal(row.getCell(c));
            if (v != null && !v.isBlank()) return false;
        }
        return true;
    }

    private String stringVal(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) && !Double.isInfinite(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }
}

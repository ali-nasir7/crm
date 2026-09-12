package com.crm.common.util;

import java.util.List;

/** Minimal RFC-4180 CSV writer used for exports and error reports. */
public final class CsvUtil {
    private CsvUtil() {}

    public static String write(List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        writeRow(sb, header.stream().map(h -> (Object) h).toList());
        for (List<Object> row : rows) writeRow(sb, row);
        return sb.toString();
    }

    private static void writeRow(StringBuilder sb, List<Object> row) {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) sb.append(',');
            Object v = row.get(i);
            String s = v == null ? "" : String.valueOf(v);
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                sb.append('"').append(s.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(s);
            }
        }
        sb.append('\n');
    }
}

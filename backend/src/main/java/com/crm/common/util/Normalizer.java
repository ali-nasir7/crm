package com.crm.common.util;

import java.util.regex.Pattern;

/** Deterministic normalization used for duplicate detection and lookups. */
public final class Normalizer {
    private Normalizer() {}

    private static final Pattern NON_DIGITS = Pattern.compile("\\D+");

    public static String email(String v) { return v == null ? null : v.trim().toLowerCase(); }

    public static String phone(String v) {
        if (v == null) return null;
        String digits = NON_DIGITS.matcher(v).replaceAll("");
        // compare on last 10 digits (country-code agnostic) when long enough
        if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
        // Gulf local dialing format: 0501234567 == +971 50 123 4567 -> same subscriber number
        if (digits.length() == 10 && digits.startsWith("0")) digits = digits.substring(1);
        return digits;
    }

    public static String website(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().toLowerCase();
        s = s.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash);
        return s.isBlank() ? null : s;
    }

    public static String linkedin(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().toLowerCase();
        s = s.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        int cut = s.indexOf('?');
        if (cut >= 0) s = s.substring(0, cut);
        cut = s.indexOf('#');
        if (cut >= 0) s = s.substring(0, cut);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.isBlank() ? null : s;
    }

    public static String name(String v) { return v == null ? null : v.trim().toLowerCase().replaceAll("\\s+", " "); }
}

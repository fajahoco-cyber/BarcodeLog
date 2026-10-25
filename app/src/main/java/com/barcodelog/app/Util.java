package com.barcodelog.app;

/** توابع کمکی خالص (بدون وابستگی به اندروید). */
public final class Util {
    private Util() {}

    /** ارقام فارسی و عربی را به انگلیسی تبدیل می‌کند. */
    public static String normalizeDigits(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '۰' && c <= '۹') sb.append((char) ('0' + (c - '۰')));
            else if (c >= '٠' && c <= '٩') sb.append((char) ('0' + (c - '٠')));
            else sb.append(c);
        }
        return sb.toString();
    }

    /** نام امن برای فایل: حروف/ارقام (از جمله فارسی)، فاصله، خط تیره و زیرخط. */
    public static String safeFileName(String s) {
        if (s == null) return "file";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') sb.append(c);
            else if (c == ' ' || c == '/' || c == ':' || c == '‌') sb.append('_');
        }
        String r = sb.toString().replaceAll("_+", "_");
        r = r.replaceAll("^_+|_+$", "");
        if (r.isEmpty()) r = "file";
        if (r.length() > 60) r = r.substring(0, 60);
        return r;
    }
}

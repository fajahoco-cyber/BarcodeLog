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
            if (c >= '\u06F0' && c <= '\u06F9') sb.append((char) ('0' + (c - '\u06F0')));
            else if (c >= '\u0660' && c <= '\u0669') sb.append((char) ('0' + (c - '\u0660')));
            else sb.append(c);
        }
        return sb.toString();
    }

    /**
     * شمارهٔ موبایل ایران را بررسی و به قالب استاندارد 09xxxxxxxxx تبدیل می‌کند.
     * قالب‌های پذیرفته‌شده: 09123456789 ، 9123456789 ، 989123456789 ،
     * +989123456789 ، 00989123456789 (با فاصله، خط تیره و پرانتز هم مشکلی نیست).
     * شمارهٔ نامعتبر یا ساختگی (مثل 09111111111) null برمی‌گرداند.
     */
    public static String normalizeMobile(String raw) {
        String s = normalizeDigits(raw).replaceAll("[\\s\\-()]", "");
        if (s.startsWith("+98")) s = "0" + s.substring(3);
        else if (s.startsWith("0098")) s = "0" + s.substring(4);
        else if (s.startsWith("98") && s.length() == 12) s = "0" + s.substring(2);
        else if (s.startsWith("9") && s.length() == 10) s = "0" + s;
        if (!s.matches("09\\d{9}")) return null;
        // رد شمارهٔ ساختگی: ۹ رقم آخر همه یکی باشند (مثل 09111111111)
        if (s.substring(2).matches("(\\d)\\1{8}")) return null;
        return s;
    }

    /** نام امن برای فایل: حروف/ارقام (از جمله فارسی)، فاصله، خط تیره و زیرخط. */
    public static String safeFileName(String s) {
        if (s == null) return "file";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') sb.append(c);
            else if (c == ' ' || c == '/' || c == ':' || c == '\u200c') sb.append('_');
        }
        String r = sb.toString().replaceAll("_+", "_");
        r = r.replaceAll("^_+|_+$", "");
        if (r.isEmpty()) r = "file";
        if (r.length() > 60) r = r.substring(0, 60);
        return r;
    }
}

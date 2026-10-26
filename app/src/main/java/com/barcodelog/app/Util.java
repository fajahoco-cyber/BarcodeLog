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

    /**
     * شمارهٔ موبایل را فقط وقتی می‌پذیرد که دقیقاً ۱۱ رقم باشد و با 09 شروع شود.
     * شمارهٔ ساختگی (مثل 09111111111) هم رد می‌شود.
     *
     * @return شمارهٔ استاندارد (ارقام انگلیسی) یا null اگر نامعتبر باشد
     */
    public static String normalizeMobile(String raw) {
        String s = normalizeDigits(raw).trim();
        if (!s.matches("09\\d{9}")) return null;
        // رد شمارهٔ ساختگی: ۹ رقم آخر همه یکی باشند
        if (s.substring(2).matches("(\\d)\\1{8}")) return null;
        return s;
    }

    /**
     * متن گفتاری را به رقم تبدیل می‌کند. هم رقم (۰۹۱۲ یا 0912) و هم کلمه
     * (صفر، یک، دو، ... نه) را می‌فهمد و بقیه را نادیده می‌گیرد.
     * مثال: «صفر نه یک دو ۳۴۵ 6789» ← 09123456789
     */
    public static String spokenToDigits(String raw) {
        if (raw == null) return "";
        String s = normalizeDigits(raw)
                .replace('ي', 'ی')   // ي عربی -> ی
                .replace('ك', 'ک');  // ك عربی -> ک
        StringBuilder out = new StringBuilder();
        for (String token : s.split("[\\s,.\\-،]+")) {
            if (token.isEmpty()) continue;
            if (token.matches("\\d+")) {
                out.append(token);
                continue;
            }
            int d = wordToDigit(token);
            if (d >= 0) out.append(d);
        }
        return out.toString();
    }

    private static int wordToDigit(String w) {
        switch (w) {
            case "صفر": return 0;                    // صفر
            case "یک": return 1;                          // یک
            case "دو": return 2;                          // دو
            case "سه": return 3;                          // سه
            case "چهار": return 4;              // چهار
            case "پنج": return 5;                    // پنج
            case "شش": return 6;                          // شش
            case "هفت": return 7;                    // هفت
            case "هشت": return 8;                    // هشت
            case "نه": return 9;                          // نه
            default: return -1;
        }
    }

    /**
     * از متن گفتاری نوع بسته را تشخیص می‌دهد.
     *
     * @return «بسته» یا «پاکت»، یا null اگر تشخیص داده نشد
     */
    public static String matchPackage(String raw) {
        if (raw == null) return null;
        String s = raw.replace('ي', 'ی').replace('ك', 'ک');
        if (s.contains("پاکت")) return "پاکت"; // پاکت
        if (s.contains("بسته")) return "بسته"; // بسته
        return null;
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

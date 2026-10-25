package com.barcodelog.app;

import java.util.Calendar;
import java.util.Locale;

/** تبدیل تاریخ میلادی به شمسی (جلالی) و قالب‌بندی. */
public final class Jalali {
    private Jalali() {}

    /** @return {سال، ماه، روز} شمسی */
    public static int[] fromGregorian(int gy, int gm, int gd) {
        int[] gdm = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int jy;
        if (gy > 1600) {
            jy = 979;
            gy -= 1600;
        } else {
            jy = 0;
            gy -= 621;
        }
        int gy2 = (gm > 2) ? (gy + 1) : gy;
        int days = (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400)
                - 80 + gd + gdm[gm - 1];
        jy += 33 * (days / 12053);
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm;
        int jd;
        if (days < 186) {
            jm = 1 + days / 31;
            jd = 1 + days % 31;
        } else {
            jm = 7 + (days - 186) / 30;
            jd = 1 + (days - 186) % 30;
        }
        return new int[]{jy, jm, jd};
    }

    /** مثال: 1405/08/03 */
    public static String formatDate(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        int[] j = fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
        return String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]);
    }

    /** مثال: 1405/08/03 23:24 */
    public static String formatDateTime(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return formatDate(millis) + String.format(Locale.US, " %02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }
}

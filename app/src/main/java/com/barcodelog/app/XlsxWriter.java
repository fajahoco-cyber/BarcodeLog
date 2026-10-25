package com.barcodelog.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * نویسندهٔ ساده و بدون وابستگی برای فایل اکسل (xlsx).
 * ستون اول (ردیف) عددی است و بقیه متنی‌اند تا صفرهای ابتدای بارکد و تلفن حفظ شود.
 * شیت راست‌به‌چپ است و ردیف سرستون ثابت (freeze) می‌ماند.
 */
public final class XlsxWriter {
    private XlsxWriter() {}

    /**
     * @param rows هر ردیف آرایه‌ای از رشته‌ها؛ ستون اول به‌صورت عدد نوشته می‌شود
     *             اگر عدد صحیح باشد.
     */
    public static void write(OutputStream out, String sheetName, String[] headers,
                             List<String[]> rows, double[] widths) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        put(zip, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>");
        put(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>");
        put(zip, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + esc(sheetName(sheetName)) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>");
        put(zip, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>");
        put(zip, "xl/styles.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F5FBF\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"3\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\">"
                + "<alignment horizontal=\"center\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\">"
                + "<alignment vertical=\"top\" wrapText=\"1\"/></xf>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>");

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\">");
        sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>");
        sb.append("<selection pane=\"bottomLeft\"/>");
        sb.append("</sheetView></sheetViews>");
        sb.append("<sheetFormatPr defaultRowHeight=\"15\"/>");
        if (widths != null && widths.length > 0) {
            sb.append("<cols>");
            for (int i = 0; i < widths.length; i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                        .append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        // سرستون
        sb.append("<row r=\"1\" ht=\"22\" customHeight=\"1\">");
        for (int c = 0; c < headers.length; c++) {
            appendText(sb, ref(c, 1), headers[c], 1);
        }
        sb.append("</row>");
        // داده‌ها
        int r = 2;
        for (String[] row : rows) {
            sb.append("<row r=\"").append(r).append("\">");
            for (int c = 0; c < row.length; c++) {
                String v = row[c] == null ? "" : row[c];
                if (c == 0 && isInteger(v)) {
                    sb.append("<c r=\"").append(ref(c, r)).append("\"><v>").append(v).append("</v></c>");
                } else {
                    appendText(sb, ref(c, r), v, 2);
                }
            }
            sb.append("</row>");
            r++;
        }
        sb.append("</sheetData></worksheet>");
        put(zip, "xl/worksheets/sheet1.xml", sb.toString());
        zip.finish();
        zip.flush();
    }

    private static void appendText(StringBuilder sb, String ref, String text, int style) {
        sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"inlineStr\">")
                .append("<is><t xml:space=\"preserve\">").append(esc(text)).append("</t></is></c>");
    }

    private static boolean isInteger(String s) {
        if (s.isEmpty() || s.length() > 9) return false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        }
        return true;
    }

    /** A1 .. Z1 ، AA1 ... */
    private static String ref(int col, int row) {
        StringBuilder name = new StringBuilder();
        int n = col;
        do {
            name.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return name.append(row).toString();
    }

    private static String sheetName(String s) {
        if (s == null) return "Sheet1";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("[]:*?/\\".indexOf(c) < 0 && c >= 0x20) sb.append(c);
        }
        String r = sb.toString().trim();
        if (r.isEmpty()) r = "Sheet1";
        if (r.length() > 31) r = r.substring(0, 31);
        return r;
    }

    /** escape کردن XML و حذف نویسه‌های کنترلی غیرمجاز */
    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c != 0xFFFE && c != 0xFFFF)) {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

package com.barcodelog.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** پایگاه دادهٔ محلی (SQLite): فایل‌ها و رکوردهای هر فایل. */
public class Db extends SQLiteOpenHelper {

    public static class FileItem {
        public long id;
        public String name;
        public long created;
        public int count;
        /** نوع فایل: TYPE_DETAILED یا TYPE_SCAN_ONLY */
        public int type;
    }

    /** فایل با اطلاعات (بارکد + فرستنده، تلفن، نوع مرسوله، توضیحات) */
    public static final int TYPE_DETAILED = 0;
    /** فایل «فقط اسکن»: فقط لیست بارکدها */
    public static final int TYPE_SCAN_ONLY = 1;

    public static class Record {
        public long id;
        public long fileId;
        public long created;
        public String barcode = "";
        public String format = "";
        public String firstName = "";
        public String lastName = "";
        public String packageType = "";
        public String description = "";
        public String phone = "";

        /** نام کامل؛ رکوردهای قدیمی که نام و فامیل جدا داشتند هم پشتیبانی می‌شوند. */
        public String fullName() {
            return (firstName + " " + lastName).trim();
        }
    }

    private static final String DB_NAME = "barcodelog.db";
    private static final int DB_VERSION = 2;
    private static final String REC_COLS =
            "id, file_id, barcode, format, first_name, last_name, package_type, description, phone, created";

    private static Db instance;

    public static synchronized Db get(Context context) {
        if (instance == null) instance = new Db(context.getApplicationContext());
        return instance;
    }

    private Db(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE files (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, created INTEGER NOT NULL, "
                + "type INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "file_id INTEGER NOT NULL, barcode TEXT NOT NULL, format TEXT, "
                + "first_name TEXT, last_name TEXT, package_type TEXT, description TEXT, "
                + "phone TEXT, created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_records_file ON records(file_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // نسخهٔ ۲: نوع فایل (با اطلاعات / فقط اسکن). فایل‌های قبلی همه «با اطلاعات» می‌مانند.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE files ADD COLUMN type INTEGER NOT NULL DEFAULT 0");
        }
    }

    // ---------------------------------------------------------------- فایل‌ها

    public long addFile(String name) {
        return addFile(name, TYPE_DETAILED);
    }

    public long addFile(String name, int type) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("created", System.currentTimeMillis());
        v.put("type", type);
        return getWritableDatabase().insert("files", null, v);
    }

    public int getFileType(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT type FROM files WHERE id=?", new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? c.getInt(0) : TYPE_DETAILED;
        } finally {
            c.close();
        }
    }

    public void renameFile(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        getWritableDatabase().update("files", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteFile(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("records", "file_id=?", new String[]{String.valueOf(id)});
            db.delete("files", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<FileItem> listFiles() {
        List<FileItem> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT f.id, f.name, f.created, "
                        + "(SELECT COUNT(*) FROM records r WHERE r.file_id = f.id), f.type "
                        + "FROM files f ORDER BY f.created DESC, f.id DESC", null);
        try {
            while (c.moveToNext()) {
                FileItem f = new FileItem();
                f.id = c.getLong(0);
                f.name = str(c, 1);
                f.created = c.getLong(2);
                f.count = c.getInt(3);
                f.type = c.getInt(4);
                out.add(f);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public String getFileName(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT name FROM files WHERE id=?", new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? str(c, 0) : "";
        } finally {
            c.close();
        }
    }

    // ---------------------------------------------------------------- رکوردها

    public List<Record> listRecords(long fileId) {
        List<Record> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + REC_COLS + " FROM records WHERE file_id=? ORDER BY id ASC",
                new String[]{String.valueOf(fileId)});
        try {
            while (c.moveToNext()) out.add(readRecord(c));
        } finally {
            c.close();
        }
        return out;
    }

    public Record getRecord(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + REC_COLS + " FROM records WHERE id=?",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readRecord(c) : null;
        } finally {
            c.close();
        }
    }

    public long insertRecord(Record r) {
        ContentValues v = values(r);
        v.put("file_id", r.fileId);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert("records", null, v);
    }

    public void updateRecord(Record r) {
        getWritableDatabase().update("records", values(r), "id=?",
                new String[]{String.valueOf(r.id)});
    }

    public void deleteRecord(long id) {
        getWritableDatabase().delete("records", "id=?", new String[]{String.valueOf(id)});
    }

    public boolean barcodeExists(long fileId, String barcode, long excludeId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM records WHERE file_id=? AND barcode=? AND id<>? LIMIT 1",
                new String[]{String.valueOf(fileId), barcode, String.valueOf(excludeId)});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    /** انواع بسته‌ای که قبلاً وارد شده‌اند (جدیدترین اول). */
    public List<String> recentPackageTypes() {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT package_type FROM records WHERE package_type IS NOT NULL "
                        + "AND package_type <> '' GROUP BY package_type "
                        + "ORDER BY MAX(id) DESC LIMIT 30", null);
        try {
            while (c.moveToNext()) out.add(str(c, 0));
        } finally {
            c.close();
        }
        return out;
    }

    // ---------------------------------------------------------------- کمکی

    private static ContentValues values(Record r) {
        ContentValues v = new ContentValues();
        v.put("barcode", r.barcode);
        v.put("format", r.format);
        v.put("first_name", r.firstName);
        v.put("last_name", r.lastName);
        v.put("package_type", r.packageType);
        v.put("description", r.description);
        v.put("phone", r.phone);
        return v;
    }

    private static Record readRecord(Cursor c) {
        Record r = new Record();
        r.id = c.getLong(0);
        r.fileId = c.getLong(1);
        r.barcode = str(c, 2);
        r.format = str(c, 3);
        r.firstName = str(c, 4);
        r.lastName = str(c, 5);
        r.packageType = str(c, 6);
        r.description = str(c, 7);
        r.phone = str(c, 8);
        r.created = c.getLong(9);
        return r;
    }

    private static String str(Cursor c, int i) {
        return c.isNull(i) ? "" : c.getString(i);
    }
}

package com.barcodelog.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** رکوردهای یک فایل + ثبت رکورد جدید + خروجی اکسل. */
public class RecordsActivity extends BaseActivity {

    public static final String EXTRA_FILE_ID = "file_id";
    /** اگر true باشد، به‌محض باز شدن صفحه منوی اشتراک اکسل خودکار باز می‌شود (پایان اسکن سریع) */
    public static final String EXTRA_AUTO_SHARE = "auto_share";
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int REQ_SAVE_EXCEL = 4001;

    private long fileId;
    private boolean autoShare;
    /** فایل «فقط اسکن»: فقط لیست بارکدها، بدون فرم و اطلاعات جانبی */
    private boolean scanOnly;
    private MaterialButton btnAdd;
    private String fileName = "";
    private final List<Db.Record> items = new ArrayList<>();
    private RecordAdapter adapter;
    private ListView listView;
    private View emptyView;
    private TextView txtCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        fileId = getIntent().getLongExtra(EXTRA_FILE_ID, 0);
        autoShare = savedInstanceState == null
                && getIntent().getBooleanExtra(EXTRA_AUTO_SHARE, false);

        listView = findViewById(R.id.listRecords);
        emptyView = findViewById(R.id.emptyView);
        txtCount = findViewById(R.id.txtCount);
        btnAdd = findViewById(R.id.btnAdd);
        MaterialButton btnShare = findViewById(R.id.btnShare);
        MaterialButton btnSaveExcel = findViewById(R.id.btnSaveExcel);

        adapter = new RecordAdapter();
        listView.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> {
            if (scanOnly) openContinuousScan();
            else openForm(0);
        });
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (!scanOnly) openForm(items.get(position).id); // در «فقط اسکن» فرم ویرایش ندارد
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(items.get(position));
            return true;
        });
        btnShare.setOnClickListener(v -> shareExcel());
        btnSaveExcel.setOnClickListener(v -> saveExcel());
    }

    @Override
    protected void onResume() {
        super.onResume();
        fileName = Db.get(this).getFileName(fileId);
        scanOnly = Db.get(this).getFileType(fileId) == Db.TYPE_SCAN_ONLY;
        btnAdd.setText(scanOnly ? R.string.continue_scan : R.string.add_record);
        setTitle(fileName);
        reload();
        if (autoShare) {
            autoShare = false; // فقط یک بار
            listView.post(this::shareExcel);
        }
    }

    private void reload() {
        items.clear();
        items.addAll(Db.get(this).listRecords(fileId));
        adapter.notifyDataSetChanged();
        txtCount.setText(getString(R.string.records_count, items.size()));
        boolean empty = items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openForm(long recordId) {
        Intent i = new Intent(this, RecordFormActivity.class);
        i.putExtra(RecordFormActivity.EXTRA_FILE_ID, fileId);
        i.putExtra(RecordFormActivity.EXTRA_RECORD_ID, recordId);
        startActivity(i);
    }

    /** ادامهٔ اسکن پیوسته روی همین فایل («فقط اسکن») */
    private void openContinuousScan() {
        Intent i = new Intent(this, ContinuousScanActivity.class);
        i.putExtra(ContinuousScanActivity.EXTRA_FILE_ID, fileId);
        startActivity(i);
    }

    private void confirmDelete(Db.Record r) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_record_title)
                .setMessage(R.string.delete_record_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Db.get(this).deleteRecord(r.id);
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------ خروجی اکسل

    private String exportFileName() {
        return Util.safeFileName(fileName) + ".xlsx";
    }

    private void writeWorkbook(OutputStream os) throws IOException {
        List<Db.Record> recs = Db.get(this).listRecords(fileId);
        List<String[]> rows = new ArrayList<>();
        int n = 1;
        for (Db.Record r : recs) {
            String fmt = r.format == null || r.format.isEmpty() ? getString(R.string.format_manual) : r.format;
            if (scanOnly) {
                // «فقط اسکن»: ردیف، نوع بارکد، بارکد
                rows.add(new String[]{String.valueOf(n++), fmt, r.barcode});
            } else {
                // ردیف، نوع بارکد، بارکد، فرستنده، شماره تماس، نوع مرسوله، توضیحات
                rows.add(new String[]{
                        String.valueOf(n++),
                        fmt,
                        r.barcode,
                        r.fullName(),
                        r.phone,
                        r.packageType,
                        r.description
                });
            }
        }
        String[] headers = getResources().getStringArray(
                scanOnly ? R.array.excel_headers_scan_only : R.array.excel_headers);
        double[] widths = scanOnly ? new double[]{6, 14, 40}
                : new double[]{6, 14, 30, 26, 16, 14, 40};
        XlsxWriter.write(os, fileName, headers, rows, widths);
    }

    private boolean hasRecords() {
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_export, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /** فایل را می‌سازد و از طریق منوی اشتراک (تلگرام، واتساپ، ایمیل و ...) می‌فرستد. */
    private void shareExcel() {
        if (!hasRecords()) return;
        try {
            File dir = new File(getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs failed");
            File f = new File(dir, exportFileName());
            try (FileOutputStream fos = new FileOutputStream(f)) {
                writeWorkbook(fos);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(XLSX_MIME);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, fileName);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.share_chooser)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** از کاربر محل ذخیره را می‌پرسد (پوشهٔ دانلود، حافظهٔ گوشی، درایو و ...). */
    private void saveExcel() {
        if (!hasRecords()) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(XLSX_MIME);
        i.putExtra(Intent.EXTRA_TITLE, exportFileName());
        startActivityForResult(i, REQ_SAVE_EXCEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SAVE_EXCEL && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                if (os == null) throw new IOException("no stream");
                writeWorkbook(os);
                Toast.makeText(this, R.string.saved_ok, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------ لیست

    private class RecordAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(RecordsActivity.this).inflate(R.layout.item_record, parent, false);
            Db.Record r = items.get(position);

            ((TextView) v.findViewById(R.id.txtIndex)).setText(String.valueOf(position + 1));

            if (scanOnly) {
                // «فقط اسکن»: خود بارکد تیتر است، زیرش نوع بارکد؛ بدون نام، بسته و توضیحات
                TextView title = v.findViewById(R.id.txtName);
                title.setText(r.barcode);
                title.setTextDirection(View.TEXT_DIRECTION_LTR);
                title.setGravity(android.view.Gravity.START);
                ((TextView) v.findViewById(R.id.txtBarcode)).setText(
                        r.format == null || r.format.isEmpty() ? getString(R.string.format_manual) : r.format);
                v.findViewById(R.id.txtDetails).setVisibility(View.GONE);
                v.findViewById(R.id.txtPack).setVisibility(View.GONE);
                return v;
            }
            // حالت با اطلاعات (ممکن است ویوی بازیافتی از حالت قبل باشد؛ جهت متن را برگردان)
            ((TextView) v.findViewById(R.id.txtName)).setTextDirection(View.TEXT_DIRECTION_INHERIT);

            String name = r.fullName();
            ((TextView) v.findViewById(R.id.txtName)).setText(name.isEmpty() ? getString(R.string.no_name) : name);
            ((TextView) v.findViewById(R.id.txtBarcode)).setText(r.barcode);

            StringBuilder d = new StringBuilder();
            if (!r.phone.isEmpty()) d.append(r.phone);
            if (!r.description.isEmpty()) {
                if (d.length() > 0) d.append("  •  ");
                d.append(r.description.replace('\n', ' '));
            }
            TextView details = v.findViewById(R.id.txtDetails);
            details.setText(d.toString());
            details.setVisibility(d.length() == 0 ? View.GONE : View.VISIBLE);

            // برچسب رنگی نوع بسته
            TextView pack = v.findViewById(R.id.txtPack);
            if (r.packageType.isEmpty()) {
                pack.setVisibility(View.GONE);
            } else {
                pack.setVisibility(View.VISIBLE);
                pack.setText(r.packageType);
                if (r.packageType.equals(getString(R.string.package_envelope))) {
                    pack.setBackgroundResource(R.drawable.bg_pill_env);
                    pack.setTextColor(getResources().getColor(R.color.env_text));
                } else if (r.packageType.equals(getString(R.string.package_parcel))) {
                    pack.setBackgroundResource(R.drawable.bg_pill_pack);
                    pack.setTextColor(getResources().getColor(R.color.pack_text));
                } else {
                    pack.setBackgroundResource(R.drawable.bg_pill_neutral);
                    pack.setTextColor(getResources().getColor(R.color.neutral_text));
                }
            }
            return v;
        }
    }
}

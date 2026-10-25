package com.barcodelog.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
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
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int REQ_SAVE_EXCEL = 4001;

    private long fileId;
    private String fileName = "";
    private final List<Db.Record> items = new ArrayList<>();
    private RecordAdapter adapter;
    private ListView listView;
    private TextView txtEmpty;
    private TextView txtCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        fileId = getIntent().getLongExtra(EXTRA_FILE_ID, 0);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        listView = findViewById(R.id.listRecords);
        txtEmpty = findViewById(R.id.txtEmpty);
        txtCount = findViewById(R.id.txtCount);
        MaterialButton btnAdd = findViewById(R.id.btnAdd);
        MaterialButton btnShare = findViewById(R.id.btnShare);
        MaterialButton btnSaveExcel = findViewById(R.id.btnSaveExcel);

        adapter = new RecordAdapter();
        listView.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> openForm(0));
        listView.setOnItemClickListener((parent, view, position, id) -> openForm(items.get(position).id));
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
        setTitle(fileName);
        reload();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        items.clear();
        items.addAll(Db.get(this).listRecords(fileId));
        adapter.notifyDataSetChanged();
        txtCount.setText(getString(R.string.records_count, items.size()));
        boolean empty = items.isEmpty();
        txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openForm(long recordId) {
        Intent i = new Intent(this, RecordFormActivity.class);
        i.putExtra(RecordFormActivity.EXTRA_FILE_ID, fileId);
        i.putExtra(RecordFormActivity.EXTRA_RECORD_ID, recordId);
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
            rows.add(new String[]{
                    String.valueOf(n++),
                    r.barcode,
                    fmt,
                    r.firstName,
                    r.lastName,
                    r.packageType,
                    r.description,
                    r.phone,
                    Jalali.formatDateTime(r.created)
            });
        }
        String[] headers = getResources().getStringArray(R.array.excel_headers);
        double[] widths = {6, 26, 14, 16, 18, 14, 36, 16, 18};
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

            String name = (r.firstName + " " + r.lastName).trim();
            if (name.isEmpty()) name = getString(R.string.no_name);
            String title = (position + 1) + ".  " + name;
            if (!r.packageType.isEmpty()) title += "  •  " + r.packageType;
            ((TextView) v.findViewById(R.id.txtTitle)).setText(title);
            ((TextView) v.findViewById(R.id.txtBarcode)).setText(r.barcode);

            StringBuilder d = new StringBuilder();
            if (!r.phone.isEmpty()) d.append(r.phone);
            if (!r.description.isEmpty()) {
                if (d.length() > 0) d.append("  —  ");
                d.append(r.description.replace('\n', ' '));
            }
            TextView details = v.findViewById(R.id.txtDetails);
            details.setText(d.toString());
            details.setVisibility(d.length() == 0 ? View.GONE : View.VISIBLE);
            return v;
        }
    }
}

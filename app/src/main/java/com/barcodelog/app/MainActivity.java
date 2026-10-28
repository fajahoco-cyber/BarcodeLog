package com.barcodelog.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/** صفحهٔ اول: فهرست فایل‌ها + ساخت فایل جدید. */
public class MainActivity extends BaseActivity {

    private final List<Db.FileItem> items = new ArrayList<>();
    private FileAdapter adapter;
    private ListView listView;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.listFiles);
        emptyView = findViewById(R.id.emptyView);
        ExtendedFloatingActionButton btnNew = findViewById(R.id.btnNewFile);

        adapter = new FileAdapter();
        listView.setAdapter(adapter);

        btnNew.setOnClickListener(v -> askFileName(null, false));
        ExtendedFloatingActionButton btnQuick = findViewById(R.id.btnQuickScan);
        btnQuick.setOnClickListener(v -> askFileName(null, true));
        listView.setOnItemClickListener((parent, view, position, id) -> openFile(items.get(position)));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            showFileMenu(items.get(position));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        items.clear();
        items.addAll(Db.get(this).listFiles());
        adapter.notifyDataSetChanged();
        boolean empty = items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openFile(Db.FileItem f) {
        // فایل «فقط اسکن» هم با همان لیست باز می‌شود (لیست ساده + ادامهٔ اسکن)
        Intent i = new Intent(this, RecordsActivity.class);
        i.putExtra(RecordsActivity.EXTRA_FILE_ID, f.id);
        startActivity(i);
    }

    /**
     * @param existing اگر null باشد فایل جدید ساخته می‌شود، وگرنه تغییر نام
     * @param quick    اگر true باشد، بعد از ساخت فایل مستقیم وارد اسکن سریع و پیوسته می‌شود
     */
    private void askFileName(Db.FileItem existing, boolean quick) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setHint(R.string.file_name_hint);
        String prefix = getString(quick ? R.string.quick_default_prefix : R.string.default_file_prefix);
        String initial = existing != null ? existing.name
                : prefix + " " + Jalali.formatDate(System.currentTimeMillis());
        input.setText(initial);
        input.selectAll();

        int titleRes = existing != null ? R.string.rename_file
                : (quick ? R.string.quick_scan_title : R.string.new_file_title);
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setView(dialogInput(input))
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String name = text(input).trim();
                    if (name.isEmpty()) name = initial;
                    Db db = Db.get(this);
                    if (existing != null) {
                        db.renameFile(existing.id, name);
                        reload();
                    } else {
                        long id = db.addFile(name, quick ? Db.TYPE_SCAN_ONLY : Db.TYPE_DETAILED);
                        Intent i = new Intent(this,
                                quick ? ContinuousScanActivity.class : RecordsActivity.class);
                        i.putExtra(RecordsActivity.EXTRA_FILE_ID, id); // همان کلید "file_id"
                        startActivity(i);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showFileMenu(Db.FileItem f) {
        String[] options = {getString(R.string.rename_file), getString(R.string.delete)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(f.name)
                .setItems(options, (d, which) -> {
                    if (which == 0) askFileName(f, false);
                    else confirmDelete(f);
                })
                .show();
    }

    private void confirmDelete(Db.FileItem f) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_file_title)
                .setMessage(getString(R.string.delete_file_msg, f.name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Db.get(this).deleteFile(f.id);
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private class FileAdapter extends BaseAdapter {
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
                    : LayoutInflater.from(MainActivity.this).inflate(R.layout.item_file, parent, false);
            Db.FileItem f = items.get(position);
            ((TextView) v.findViewById(R.id.txtName)).setText(f.name);
            String info = Jalali.formatDateTime(f.created);
            if (f.type == Db.TYPE_SCAN_ONLY) info += "  •  " + getString(R.string.scan_only_tag);
            ((TextView) v.findViewById(R.id.txtInfo)).setText(info);
            ((TextView) v.findViewById(R.id.txtCount)).setText(getString(R.string.file_count, f.count));
            return v;
        }
    }
}

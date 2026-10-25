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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** صفحهٔ اول: فهرست فایل‌ها + ساخت فایل جدید. */
public class MainActivity extends BaseActivity {

    private final List<Db.FileItem> items = new ArrayList<>();
    private FileAdapter adapter;
    private ListView listView;
    private TextView txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        listView = findViewById(R.id.listFiles);
        txtEmpty = findViewById(R.id.txtEmpty);
        MaterialButton btnNew = findViewById(R.id.btnNewFile);

        adapter = new FileAdapter();
        listView.setAdapter(adapter);

        btnNew.setOnClickListener(v -> askFileName(null));
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
        txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openFile(Db.FileItem f) {
        Intent i = new Intent(this, RecordsActivity.class);
        i.putExtra(RecordsActivity.EXTRA_FILE_ID, f.id);
        startActivity(i);
    }

    /** @param existing اگر null باشد فایل جدید ساخته می‌شود، وگرنه تغییر نام */
    private void askFileName(Db.FileItem existing) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setHint(R.string.file_name_hint);
        String initial = existing != null ? existing.name
                : getString(R.string.default_file_prefix) + " " + Jalali.formatDate(System.currentTimeMillis());
        input.setText(initial);
        input.selectAll();

        new MaterialAlertDialogBuilder(this)
                .setTitle(existing != null ? R.string.rename_file : R.string.new_file_title)
                .setView(dialogInput(input))
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String name = text(input).trim();
                    if (name.isEmpty()) name = initial;
                    Db db = Db.get(this);
                    if (existing != null) {
                        db.renameFile(existing.id, name);
                        reload();
                    } else {
                        long id = db.addFile(name);
                        Intent i = new Intent(this, RecordsActivity.class);
                        i.putExtra(RecordsActivity.EXTRA_FILE_ID, id);
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
                    if (which == 0) askFileName(f);
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
            ((TextView) v.findViewById(R.id.txtInfo)).setText(
                    getString(R.string.file_info, f.count, Jalali.formatDateTime(f.created)));
            return v;
        }
    }
}

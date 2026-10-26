package com.barcodelog.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

/** فرم ثبت/ویرایش یک رکورد: اسکن بارکد + نام و نام خانوادگی، نوع بسته، تلفن، توضیحات. */
public class RecordFormActivity extends BaseActivity {

    public static final String EXTRA_FILE_ID = "file_id";
    public static final String EXTRA_RECORD_ID = "record_id";

    private long fileId;
    private long recordId;

    private TextInputLayout tilBarcode;
    private TextInputEditText etBarcode;
    private TextInputEditText etName;
    private TextInputEditText etDesc;
    private TextInputEditText etPhone;
    private AutoCompleteTextView etPackage;
    private TextView txtFormat;

    /** نوع بارکدی که اسکنر تشخیص داده (مثل EAN_13)؛ برای ورود دستی خالی است */
    private String format = "";
    /** آخرین مقدار اسکن‌شده؛ اگر کاربر متن را دستی عوض کند، نوع بارکد پاک می‌شود */
    private String scannedValue = "";

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result == null || result.getContents() == null) return; // انصراف
                scannedValue = result.getContents().trim();
                format = result.getFormatName() == null ? "" : result.getFormatName();
                etBarcode.setText(scannedValue);
                refreshFormatLabel();
                etName.requestFocus();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_form);

        fileId = getIntent().getLongExtra(EXTRA_FILE_ID, 0);
        recordId = getIntent().getLongExtra(EXTRA_RECORD_ID, 0);

        tilBarcode = findViewById(R.id.tilBarcode);
        etBarcode = findViewById(R.id.etBarcode);
        etName = findViewById(R.id.etName);
        etDesc = findViewById(R.id.etDesc);
        etPhone = findViewById(R.id.etPhone);
        etPackage = findViewById(R.id.etPackage);
        txtFormat = findViewById(R.id.txtFormat);
        MaterialButton btnScan = findViewById(R.id.btnScan);
        MaterialButton btnSave = findViewById(R.id.btnSave);
        MaterialButton btnSaveNext = findViewById(R.id.btnSaveNext);
        MaterialButton btnDelete = findViewById(R.id.btnDelete);

        setupPackageDropdown();

        etBarcode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                tilBarcode.setError(null);
                if (!s.toString().trim().equals(scannedValue)) format = "";
                refreshFormatLabel();
            }
        });

        btnScan.setOnClickListener(v -> launchScan());
        btnSave.setOnClickListener(v -> save(false));
        btnSaveNext.setOnClickListener(v -> save(true));
        btnDelete.setOnClickListener(v -> confirmDelete());

        if (recordId != 0) {
            setTitle(R.string.form_title_edit);
            loadRecord();
            btnSaveNext.setVisibility(View.GONE);
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            setTitle(R.string.form_title_new);
            etPackage.setText(getString(R.string.package_parcel), false);
            refreshFormatLabel();
            // رکورد جدید: بلافاصله اسکنر را باز کن
            if (savedInstanceState == null) etBarcode.post(this::launchScan);
        }
    }

    // ------------------------------------------------------------ اسکن

    private void launchScan() {
        ScanOptions o = new ScanOptions();
        o.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES); // همهٔ انواع بارکد و QR
        o.setPrompt(getString(R.string.scan_prompt));
        o.setBeepEnabled(true);
        o.setOrientationLocked(false);
        o.setBarcodeImageEnabled(false);
        scanLauncher.launch(o);
    }

    private void refreshFormatLabel() {
        String b = text(etBarcode).trim();
        String f;
        if (b.isEmpty()) f = "—";
        else if (format.isEmpty()) f = getString(R.string.format_manual);
        else f = format;
        txtFormat.setText(getString(R.string.format_label, f));
    }

    // ------------------------------------------------------------ نوع بسته (فقط «بسته» و «پاکت»)

    private void setupPackageDropdown() {
        String[] types = getResources().getStringArray(R.array.package_types);
        etPackage.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, types));
    }

    // ------------------------------------------------------------ بارگذاری/ذخیره

    private void loadRecord() {
        Db.Record r = Db.get(this).getRecord(recordId);
        if (r == null) {
            finish();
            return;
        }
        scannedValue = r.barcode;
        format = r.format == null ? "" : r.format;
        etBarcode.setText(r.barcode);
        etName.setText(r.fullName());
        etPackage.setText(r.packageType, false);
        etDesc.setText(r.description);
        etPhone.setText(r.phone);
        refreshFormatLabel();
    }

    private void save(boolean next) {
        String barcode = text(etBarcode).trim();
        if (barcode.isEmpty()) {
            tilBarcode.setError(getString(R.string.barcode_required));
            return;
        }
        if (Db.get(this).barcodeExists(fileId, barcode, recordId)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.duplicate_title)
                    .setMessage(R.string.duplicate_msg)
                    .setPositiveButton(R.string.save, (d, w) -> doSave(next))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            doSave(next);
        }
    }

    private void doSave(boolean next) {
        Db.Record r = new Db.Record();
        r.id = recordId;
        r.fileId = fileId;
        r.barcode = text(etBarcode).trim();
        r.format = format;
        r.firstName = text(etName).trim(); // نام کامل در یک فیلد
        r.lastName = "";
        r.packageType = text(etPackage).trim();
        r.description = text(etDesc).trim();
        r.phone = Util.normalizeDigits(text(etPhone)).trim();

        Db db = Db.get(this);
        if (recordId == 0) db.insertRecord(r);
        else db.updateRecord(r);
        Toast.makeText(this, R.string.record_saved, Toast.LENGTH_SHORT).show();

        if (next && recordId == 0) {
            clearForm();
            etBarcode.post(this::launchScan);
        } else {
            finish();
        }
    }

    private void clearForm() {
        scannedValue = "";
        format = "";
        etBarcode.setText("");
        etName.setText("");
        etPackage.setText(getString(R.string.package_parcel), false);
        etDesc.setText("");
        etPhone.setText("");
        tilBarcode.setError(null);
        refreshFormatLabel();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_record_title)
                .setMessage(R.string.delete_record_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Db.get(this).deleteRecord(recordId);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}

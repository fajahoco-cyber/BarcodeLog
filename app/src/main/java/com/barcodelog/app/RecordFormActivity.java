package com.barcodelog.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;

/**
 * فرم ثبت/ویرایش یک رکورد: اسکن بارکد + نام و نام خانوادگی، نوع بسته، تلفن، توضیحات.
 * تایپ صوتی فارسی اختیاری است (دکمهٔ ورود صوتی مرحله‌ای یا میکروفون کنار هر فیلد).
 */
public class RecordFormActivity extends BaseActivity {

    public static final String EXTRA_FILE_ID = "file_id";
    public static final String EXTRA_RECORD_ID = "record_id";

    private static final String PREFS = "barcodelog_prefs";
    private static final String PREF_AUTO_VOICE = "auto_voice";

    // مرحله‌های ورود صوتی
    private static final int STEP_NONE = -1;
    private static final int STEP_NAME = 0;
    private static final int STEP_PACKAGE = 1;
    private static final int STEP_PHONE = 2;
    private static final int STEP_DESC = 3;

    private long fileId;
    private long recordId;

    private TextInputLayout tilBarcode;
    private TextInputLayout tilPhone;
    private TextInputEditText etBarcode;
    private TextInputEditText etName;
    private TextInputEditText etDesc;
    private TextInputEditText etPhone;
    private AutoCompleteTextView etPackage;
    private MaterialCheckBox cbAutoVoice;
    private TextView txtFormat;

    /** نوع بارکدی که اسکنر تشخیص داده (مثل EAN_13)؛ برای ورود دستی خالی است */
    private String format = "";
    /** آخرین مقدار اسکن‌شده؛ اگر کاربر متن را دستی عوض کند، نوع بارکد پاک می‌شود */
    private String scannedValue = "";

    /** مرحلهٔ فعلی ورود صوتی و اینکه آیا زنجیره‌ای (مرحله‌ای) است یا فقط یک فیلد */
    private int voiceStep = STEP_NONE;
    private boolean voiceChain = false;

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result == null || result.getContents() == null) return; // انصراف
                scannedValue = result.getContents().trim();
                format = result.getFormatName() == null ? "" : result.getFormatName();
                etBarcode.setText(scannedValue);
                refreshFormatLabel();
                if (cbAutoVoice.isChecked()) {
                    etName.post(this::startVoiceChain); // ورود صوتی خودکار بعد از اسکن
                } else {
                    etName.requestFocus();
                }
            });

    private final ActivityResultLauncher<Intent> voiceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                int step = voiceStep;
                Intent data = res.getData();
                ArrayList<String> list = data == null ? null
                        : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (res.getResultCode() != RESULT_OK || list == null || list.isEmpty()) {
                    stopVoice(); // انصراف یا چیزی شنیده نشد
                    return;
                }
                applyVoice(step, list.get(0).trim());
                if (voiceChain && step < STEP_DESC) {
                    voiceStep = step + 1;
                    etName.post(this::askVoice);
                } else {
                    stopVoice();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_form);

        fileId = getIntent().getLongExtra(EXTRA_FILE_ID, 0);
        recordId = getIntent().getLongExtra(EXTRA_RECORD_ID, 0);

        tilBarcode = findViewById(R.id.tilBarcode);
        tilPhone = findViewById(R.id.tilPhone);
        TextInputLayout tilName = findViewById(R.id.tilName);
        TextInputLayout tilDesc = findViewById(R.id.tilDesc);
        etBarcode = findViewById(R.id.etBarcode);
        etName = findViewById(R.id.etName);
        etDesc = findViewById(R.id.etDesc);
        etPhone = findViewById(R.id.etPhone);
        etPackage = findViewById(R.id.etPackage);
        cbAutoVoice = findViewById(R.id.cbAutoVoice);
        txtFormat = findViewById(R.id.txtFormat);
        MaterialButton btnScan = findViewById(R.id.btnScan);
        MaterialButton btnVoice = findViewById(R.id.btnVoice);
        MaterialButton btnSave = findViewById(R.id.btnSave);
        MaterialButton btnSaveNext = findViewById(R.id.btnSaveNext);
        MaterialButton btnDelete = findViewById(R.id.btnDelete);

        setupPackageDropdown();
        setupPhoneField();

        // گزینهٔ اختیاری «ورود صوتی خودکار بعد از اسکن» (به‌خاطر سپرده می‌شود)
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        cbAutoVoice.setChecked(prefs.getBoolean(PREF_AUTO_VOICE, false));
        cbAutoVoice.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(PREF_AUTO_VOICE, checked).apply());

        // میکروفون کنار هر فیلد متنی
        tilName.setEndIconOnClickListener(v -> startVoiceSingle(STEP_NAME));
        tilPhone.setEndIconOnClickListener(v -> startVoiceSingle(STEP_PHONE));
        tilDesc.setEndIconOnClickListener(v -> startVoiceSingle(STEP_DESC));
        btnVoice.setOnClickListener(v -> startVoiceChain());

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

    // ------------------------------------------------------------ شمارهٔ موبایل (فقط 09 + ۱۱ رقم)

    private void setupPhoneField() {
        // فقط رقم (فارسی/عربی به انگلیسی تبدیل می‌شود) و حداکثر ۱۱ کاراکتر
        InputFilter digitsOnly = (source, start, end, dest, dstart, dend) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = Util.normalizeDigits(String.valueOf(source.charAt(i))).charAt(0);
                if (c >= '0' && c <= '9') sb.append(c);
            }
            return sb.toString();
        };
        etPhone.setFilters(new InputFilter[]{digitsOnly, new InputFilter.LengthFilter(11)});

        etPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                tilPhone.setError(phoneError(s.toString(), false));
            }
        });
    }

    /**
     * @param finalCheck اگر true باشد کمبود رقم هم خطا حساب می‌شود (هنگام ثبت)
     * @return متن خطا یا null اگر مشکلی نیست
     */
    private static String phoneError(String s, boolean finalCheck) {
        if (s.isEmpty()) return null; // شماره اختیاری است
        if (s.charAt(0) != '0' || (s.length() >= 2 && s.charAt(1) != '9')) {
            return "شمارهٔ موبایل باید با 09 شروع شود";
        }
        if (s.length() == 11 && Util.normalizeMobile(s) == null) {
            return "این شماره معتبر نیست";
        }
        if (finalCheck && s.length() < 11) {
            return "شماره باید ۱۱ رقم باشد (الان " + s.length() + " رقم)";
        }
        return null;
    }

    // ------------------------------------------------------------ ورود صوتی (فارسی)

    /** شروع زنجیره: نام ← نوع بسته ← تلفن ← توضیحات */
    private void startVoiceChain() {
        voiceChain = true;
        voiceStep = STEP_NAME;
        askVoice();
    }

    /** ورود صوتی فقط برای یک فیلد (دکمهٔ میکروفون کنار فیلد) */
    private void startVoiceSingle(int step) {
        voiceChain = false;
        voiceStep = step;
        askVoice();
    }

    private void stopVoice() {
        voiceStep = STEP_NONE;
        voiceChain = false;
    }

    private void askVoice() {
        String prompt;
        switch (voiceStep) {
            case STEP_NAME:
                prompt = "نام و نام خانوادگی را بگویید";
                break;
            case STEP_PACKAGE:
                prompt = "نوع بسته را بگویید: «بسته» یا «پاکت»";
                break;
            case STEP_PHONE:
                prompt = "شماره موبایل را بگویید (۱۱ رقم)";
                break;
            case STEP_DESC:
                prompt = "توضیحات را بگویید";
                break;
            default:
                return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            voiceLauncher.launch(i);
        } catch (Exception e) {
            stopVoice();
            Toast.makeText(this,
                    "تایپ صوتی روی این گوشی در دسترس نیست (برنامهٔ Google و اینترنت لازم است).",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void applyVoice(int step, String spoken) {
        switch (step) {
            case STEP_NAME:
                etName.setText(spoken);
                etName.setSelection(text(etName).length());
                break;
            case STEP_PACKAGE: {
                String p = Util.matchPackage(spoken);
                if (p != null) {
                    etPackage.setText(p, false);
                } else {
                    Toast.makeText(this, "نوع بسته تشخیص داده نشد؛ دستی انتخاب کنید.",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case STEP_PHONE: {
                String digits = Util.spokenToDigits(spoken);
                etPhone.setText(digits); // فیلتر ورودی حداکثر ۱۱ رقم نگه می‌دارد
                if (Util.normalizeMobile(text(etPhone)) == null) {
                    Toast.makeText(this, "شمارهٔ کامل تشخیص داده نشد؛ لطفاً بررسی و اصلاح کنید.",
                            Toast.LENGTH_LONG).show();
                }
                break;
            }
            case STEP_DESC:
                etDesc.setText(spoken);
                etDesc.setSelection(text(etDesc).length());
                break;
            default:
                break;
        }
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
        // شمارهٔ تلفن اختیاری است؛ اگر وارد شده باید دقیقاً ۱۱ رقم و با 09 شروع شود
        String err = phoneError(text(etPhone).trim(), true);
        if (err != null) {
            tilPhone.setError(err);
            etPhone.requestFocus();
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
        String mobile = Util.normalizeMobile(text(etPhone));
        r.phone = mobile != null ? mobile : "";

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
        tilPhone.setError(null);
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

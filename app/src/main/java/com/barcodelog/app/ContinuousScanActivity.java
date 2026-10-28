package com.barcodelog.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

/**
 * اسکن سریع و پیوسته: هر بارکدی که اسکن شود خودکار در همان فایل ذخیره می‌شود و
 * اسکنر برای بعدی آماده می‌ماند (بدون هیچ دکمه یا فرمی). با «پایان» لیست و خروجی اکسل باز می‌شود.
 */
public class ContinuousScanActivity extends BaseActivity {

    public static final String EXTRA_FILE_ID = "file_id";

    /** اگر همان بارکد در این مدت دوباره دیده شود، نادیده گرفته می‌شود */
    private static final long SAME_CODE_WINDOW_MS = 2500;

    private long fileId;
    private int count;

    private DecoratedBarcodeView scanner;
    private TextView txtCount;
    private TextView txtLast;
    private ToneGenerator tone;

    private String lastText = "";
    private long lastTime = 0;
    private boolean torchOn = false;
    private boolean permissionGranted = false;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    permissionGranted = true;
                    scanner.resume();
                } else {
                    Toast.makeText(this, R.string.quick_camera_denied, Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result == null || result.getText() == null) return;
            handleScan(result.getText().trim(),
                    result.getBarcodeFormat() == null ? "" : result.getBarcodeFormat().toString());
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_continuous_scan);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        fileId = getIntent().getLongExtra(EXTRA_FILE_ID, 0);
        setTitle(Db.get(this).getFileName(fileId));

        scanner = findViewById(R.id.scanner);
        txtCount = findViewById(R.id.txtCount);
        txtLast = findViewById(R.id.txtLast);
        MaterialButton btnTorch = findViewById(R.id.btnTorch);
        MaterialButton btnFinish = findViewById(R.id.btnFinish);

        scanner.setStatusText(getString(R.string.quick_hint));
        scanner.decodeContinuous(callback); // همهٔ انواع بارکد، بدون توقف

        count = Db.get(this).listRecords(fileId).size();
        updateCount();

        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (RuntimeException e) {
            tone = null; // روی بعضی گوشی‌ها ساخته نمی‌شود؛ بدون صدا ادامه می‌دهیم
        }

        btnTorch.setOnClickListener(v -> {
            torchOn = !torchOn;
            if (torchOn) scanner.setTorchOn();
            else scanner.setTorchOff();
        });
        btnFinish.setOnClickListener(v -> finishAndExport());

        permissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionGranted) scanner.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanner.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tone != null) tone.release();
    }

    // ------------------------------------------------------------ پردازش هر اسکن

    private void handleScan(String text, String format) {
        if (text.isEmpty()) return;
        long now = System.currentTimeMillis();

        // همان بارکد که هنوز مقابل دوربین است: دوباره ثبت نشود
        boolean sameAsLast = text.equals(lastText) && now - lastTime < SAME_CODE_WINDOW_MS;
        lastText = text;
        lastTime = now;
        if (sameAsLast) return;

        Db db = Db.get(this);
        if (db.barcodeExists(fileId, text, 0)) {
            // قبلاً در همین فایل ثبت شده
            txtLast.setTextColor(Color.parseColor("#DC2626"));
            txtLast.setText(getString(R.string.quick_duplicate, text));
            beep(ToneGenerator.TONE_PROP_NACK, 250);
            return;
        }

        Db.Record r = new Db.Record();
        r.fileId = fileId;
        r.barcode = text;
        r.format = format;
        db.insertRecord(r);
        count++;
        updateCount();

        txtLast.setTextColor(Color.parseColor("#16A34A"));
        txtLast.setText(getString(R.string.quick_saved, text));
        beep(ToneGenerator.TONE_PROP_BEEP, 150);
    }

    private void beep(int toneType, int ms) {
        if (tone == null) return;
        try {
            tone.startTone(toneType, ms);
        } catch (RuntimeException ignored) {
            // صدا حیاتی نیست
        }
    }

    private void updateCount() {
        txtCount.setText(getString(R.string.records_count, count));
    }

    /** باز کردن لیست همین فایل و نمایش خودکار منوی اشتراک اکسل */
    private void finishAndExport() {
        scanner.pause();
        Intent i = new Intent(this, RecordsActivity.class);
        i.putExtra(RecordsActivity.EXTRA_FILE_ID, fileId);
        i.putExtra(RecordsActivity.EXTRA_AUTO_SHARE, true);
        startActivity(i);
        finish();
    }
}

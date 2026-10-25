package com.barcodelog.app;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/** پایهٔ همهٔ صفحه‌ها: اجبار چیدمان راست‌به‌چپ و چند تابع کمکی. */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // حتی اگر زبان گوشی انگلیسی باشد، برنامه راست‌به‌چپ نمایش داده شود
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    protected int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** یک EditText با حاشیه برای استفاده داخل دیالوگ */
    protected FrameLayout dialogInput(EditText input) {
        FrameLayout box = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(20), dp(8), dp(20), 0);
        input.setLayoutParams(lp);
        box.addView(input);
        return box;
    }

    protected static String text(TextView v) {
        CharSequence c = v.getText();
        return c == null ? "" : c.toString();
    }
}

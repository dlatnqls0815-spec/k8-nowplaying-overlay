package io.github.k8nowplaying;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Settings: on/off, navigation-only, bar position, size, text and background opacity. */
public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout column;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = Prefs.of(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(32, 24, 32, 24);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);

        TextView info = new TextView(this);
        info.setTextSize(18);
        info.setText("순정 블루투스·USB 음악의 곡 정보를 내비 지도 위에 띄웁니다. "
                + "차량 설정은 바꾸지 않으며, 이 앱을 지우면 원래대로 돌아갑니다.");
        column.addView(info);

        toggle("표시 켜기", Prefs.ENABLED, Prefs.enabled(prefs));
        toggle("내비 화면일 때만 표시", Prefs.NAVI_ONLY, Prefs.naviOnly(prefs));
        toggle("곡이 바뀔 때 순정 곡 정보 팝업 닫기", Prefs.HIDE_STOCK_POPUP, Prefs.hideStockPopup(prefs));
        toggle("전화 오거나 통화 중일 때 표시", Prefs.SHOW_CALLS, Prefs.showCalls(prefs));
        slider("가로 위치", Prefs.X, Prefs.x(prefs), 0, 1900, 10);
        slider("세로 위치", Prefs.Y, Prefs.y(prefs), 0, 700, 5);
        slider("너비", Prefs.WIDTH, Prefs.width(prefs), 200, 1920, 10);
        slider("높이", Prefs.HEIGHT, Prefs.height(prefs), 30, 160, 2);
        slider("글자 크기", Prefs.TEXT, Prefs.text(prefs), 14, 56, 1);
        slider("배경 진하기 %", Prefs.ALPHA, Prefs.alpha(prefs), 0, 100, 5);

        Button preview = new Button(this);
        preview.setText("15초 미리보기 (위치 맞추기)");
        preview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, OverlayService.class)
                        .putExtra(OverlayService.EXTRA_PREVIEW, true));
            }
        });
        column.addView(preview);
        if (Prefs.enabled(prefs)) startService(new Intent(this, OverlayService.class));
    }

    private void toggle(String label, final String key, boolean value) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextSize(18);
        box.setChecked(value);
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean(key, checked).apply();
                if (Prefs.ENABLED.equals(key)) {
                    Intent service = new Intent(MainActivity.this, OverlayService.class);
                    if (checked) startService(service); else stopService(service);
                }
            }
        });
        column.addView(box);
    }

    private void slider(final String label, final String key, int value, final int min, int max,
            final int step) {
        final TextView title = new TextView(this);
        title.setTextSize(16);
        title.setPadding(0, 20, 0, 0);
        title.setText(label + ": " + value);
        SeekBar bar = new SeekBar(this);
        bar.setMax((max - min) / step);
        bar.setProgress((value - min) / step);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = min + progress * step;
                title.setText(label + ": " + v);
                if (fromUser) prefs.edit().putInt(key, v).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        column.addView(title);
        column.addView(bar);
    }
}

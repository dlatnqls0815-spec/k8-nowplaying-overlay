package io.github.k8nowplaying;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * Draws a now-playing card over the stock navigation map. Reads the stock Bluetooth/USB audio
 * widget broadcasts and shows the card only while the navigation app is in front and media
 * updates keep arriving. Its buttons send the same keys as the steering-wheel switches.
 */
public final class OverlayService extends Service
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String TAG = "K8NowPlaying";
    static final String EXTRA_PREVIEW = "preview";
    static final String BT_ACTION = "com.hkmc.audio.btaudio.widgetupdate";
    static final String USB_ACTION = "com.hkmc.audio.usb.widgetupdate";
    static final String REQUEST_ACTION = "com.hkmc.request.widgetupdate";
    static final String NAVI_PACKAGE = "com.mnsoft.navi";
    static final long STALE_MS = 4000, POLL_MS = 500, PREVIEW_MS = 15000;
    /**
     * The stock BT audio app asks the split-screen panel to close its track OSD with this
     * broadcast (MusicService.sendCancelSplitScreenOSD). The OSD appears on a track change when
     * Settings.System media_notification is 1, and always after a steering-wheel or card key.
     */
    /** Call broadcasts; they only trigger an early read of the hands-free call state. */
    static final String[] CALL_ACTIONS = {"com.hkmc.phone.widgetupdate",
            "com.broadcom.bt.hfdevice.profile.action.CALL_STATE_CHANGED"};
    static final String SPLIT_CONTROL_ACTION = "ACTION_SPLITSCREEN_CONTROL";
    static final String EXTRA_CANCEL_OSD = "EXTRA_SPLITSCREEN_CONTROL_CANCEL_OSD";
    static final long[] CANCEL_OSD_DELAYS_MS = {150, 600, 1200};

    private final Handler handler = new Handler();
    private WindowManager windows;
    private SharedPreferences prefs;
    private FrameLayout bar;
    private LinearLayout row;
    private TextView icon;
    private MarqueeText title, artist;
    private FrameLayout artBox;
    private ImageView artView;
    private final AlbumArt albumArt = new AlbumArt();
    private CallProbe callProbe;
    private CallProbe.State call = CallProbe.State.NONE;
    private long activeSinceMs;
    private volatile boolean privacy;
    private volatile String contactName = "";
    private String lookedUpNumber = "";
    private TextView answerButton, endButton;
    private ProgressLine progress;
    private ControlButton prevButton, playButton, nextButton;
    private boolean attached, callMode;
    private String currentTitle = "", currentArtist = "";
    private long positionMs, durationMs;
    private boolean isPlaying;
    private long lastMediaMs, previewUntilMs, noticeUntilMs;
    private int extrasLogged;

    private final BroadcastReceiver media = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras == null) return;
            if (extrasLogged < 3) {
                extrasLogged++;
                StringBuilder keys = new StringBuilder();
                for (String key : extras.keySet()) {
                    keys.append(key).append('=').append(extras.get(key)).append(' ');
                }
                Log.i(TAG, intent.getAction() + " extras: " + keys);
            }
            String t = clean(intent.getStringExtra("filename"));
            String a = clean(intent.getStringExtra("artist_name"));
            if (t.isEmpty() && a.isEmpty()) return;
            if (!t.equals(currentTitle)) {
                albumArt.trackChanged();
                showArt();
                if (!currentTitle.isEmpty() && Prefs.hideStockPopup(prefs)) cancelStockOsd();
            }
            currentTitle = t;
            currentArtist = a;
            positionMs = number(extras, "audio_current_time", 0);
            durationMs = number(extras, "audio_total_time", 0);
            isPlaying = number(extras, "playstate", 1) == 1;
            lastMediaMs = SystemClock.elapsedRealtime();
            refresh();
        }
    };

    private final BroadcastReceiver calls = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "call " + intent.getAction() + " state="
                    + intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1));
            callProbe.readSoon();
        }
    };

    private final CallProbe.Listener callListener = new CallProbe.Listener() {
        @Override
        public void onCallState(CallProbe.State s) {
            long now = SystemClock.elapsedRealtime();
            if (s.active > 0 && call.active == 0) activeSinceMs = now;
            if (!s.any()) activeSinceMs = 0;
            if (s.any() && !call.any()) readPrivacy();
            call = s;
            refresh();
        }
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (albumArt.scan(Prefs.height(prefs))) showArt();
            refresh();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.of(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        buildBar();
        IntentFilter filter = new IntentFilter(BT_ACTION);
        filter.addAction(USB_ACTION);
        registerReceiver(media, filter);
        IntentFilter callFilter = new IntentFilter();
        for (String action : CALL_ACTIONS) callFilter.addAction(action);
        registerReceiver(calls, callFilter);
        callProbe = new CallProbe(this, callListener);
        callProbe.start();
        sendBroadcast(new Intent(REQUEST_ACTION));
        handler.post(poll);
        Log.i(TAG, "service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(EXTRA_PREVIEW, false)) {
            previewUntilMs = SystemClock.elapsedRealtime() + PREVIEW_MS;
            refresh();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(poll);
        unregisterReceiver(media);
        unregisterReceiver(calls);
        callProbe.stop();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        detach();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (!Prefs.enabled(p)) {
            stopSelf();
            return;
        }
        style();
        if (attached) windows.updateViewLayout(bar, params());
    }

    private void buildBar() {
        bar = new FrameLayout(this);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        icon = new TextView(this);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(0xFFB8C7D6);
        icon.setText("♪");
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        title = new MarqueeText(this);
        title.setColor(Color.WHITE);
        title.setBold(true);
        artist = new MarqueeText(this);
        artist.setColor(0xFF9FC1E6);
        texts.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        texts.addView(artist, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        artBox = new FrameLayout(this);
        artView = new ImageView(this);
        artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artBox.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        artBox.addView(artView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        showArt();
        row.addView(artBox);
        row.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        prevButton = control(ControlButton.PREVIOUS, MediaKeys.PREVIOUS);
        playButton = control(ControlButton.PAUSE, MediaKeys.PLAY_PAUSE);
        nextButton = control(ControlButton.NEXT, MediaKeys.NEXT);
        answerButton = callButton("받기", 0xFF2E9E5B, true);
        endButton = callButton("끊기", 0xFFD9443A, false);
        progress = new ProgressLine(this);
        bar.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        bar.addView(progress, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 4, Gravity.BOTTOM));
        style();
    }

    private ControlButton control(int shape, final int keyCode) {
        ControlButton b = new ControlButton(this, shape);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (SystemClock.elapsedRealtime() < previewUntilMs && !fresh()) return;
                MediaKeys.send(keyCode, new MediaKeys.Result() {
                    @Override
                    public void done(final boolean ok) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!ok) {
                                    noticeUntilMs = SystemClock.elapsedRealtime() + 3000;
                                } else if (keyCode == MediaKeys.PLAY_PAUSE) {
                                    isPlaying = !isPlaying;
                                }
                                refresh();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        sendBroadcast(new Intent(REQUEST_ACTION));
                                    }
                                }, 700);
                            }
                        });
                    }
                });
            }
        });
        row.addView(b);
        return b;
    }

    /** Answer or end/reject button of the call card (Bluetooth hands-free, no stock UI needed). */
    private TextView callButton(String label, int color, final boolean answer) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(8);
        b.setBackgroundDrawable(bg);
        b.setVisibility(View.GONE);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (answer) {
                    callProbe.answer();
                } else {
                    callProbe.hangup();
                }
                callProbe.readSoon();
            }
        });
        row.addView(b);
        return b;
    }

    /** Closes the stock track popup a few times, since it can open a moment after the metadata. */
    private void cancelStockOsd() {
        for (long delay : CANCEL_OSD_DELAYS_MS) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(SPLIT_CONTROL_ACTION);
                    i.putExtra(EXTRA_CANCEL_OSD, true);
                    sendBroadcast(i);
                }
            }, delay);
        }
        Log.i(TAG, "stock OSD cancel sent");
    }

    private boolean fresh() {
        return SystemClock.elapsedRealtime() - lastMediaMs < STALE_MS && !currentTitle.isEmpty();
    }

    @SuppressWarnings("deprecation")
    private void style() {
        int text = Prefs.text(prefs);
        int h = Prefs.height(prefs);
        title.setTextSizePx(text);
        artist.setTextSizePx(Math.round(text * 0.8f));
        int art = h - 16;
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(art, art);
        il.leftMargin = 8;
        il.rightMargin = 14;
        artBox.setLayoutParams(il);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_PX, art * 0.55f);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setColor(0xFF2B323B);
        artBg.setCornerRadius(6);
        icon.setBackgroundDrawable(artBg);
        int button = h - 12;
        for (ControlButton b : new ControlButton[] {prevButton, playButton, nextButton}) {
            b.setLayoutParams(new LinearLayout.LayoutParams(button, button));
        }
        for (TextView b : new TextView[] {answerButton, endButton}) {
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Math.round(button * 1.6f), button);
            bl.leftMargin = 8;
            b.setLayoutParams(bl);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, text);
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(Math.round(Prefs.alpha(prefs) * 2.55f), 22, 26, 32));
        bg.setCornerRadius(8);
        bar.setBackgroundDrawable(bg);
        row.setPadding(0, 0, 6, 4);
    }

    private void showArt() {
        if (callMode) return;
        Bitmap b = albumArt.bitmap();
        artView.setImageBitmap(b);
        artView.setVisibility(b != null ? View.VISIBLE : View.GONE);
    }

    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Prefs.width(prefs), Prefs.height(prefs),
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = Prefs.x(prefs);
        lp.y = Prefs.y(prefs);
        lp.setTitle("K8NowPlaying");
        return lp;
    }

    private void refresh() {
        long now = SystemClock.elapsedRealtime();
        boolean preview = now < previewUntilMs;
        boolean playing = fresh();
        boolean inCall = Prefs.showCalls(prefs) && call.any();
        boolean show = preview || ((playing || inCall) && (!Prefs.naviOnly(prefs) || naviInFront()));
        setCallMode(inCall);
        if (inCall) {
            title.setText(callStatus(call, activeSinceMs, now));
            String stockName = callProbe.callerName();
            lookUpName(call.number);
            artist.setText(callDetail(call, privacy, stockName.isEmpty() ? contactName : stockName));
            boolean ringing = call.setup == 4 || call.setup == 5;
            answerButton.setVisibility(ringing ? View.VISIBLE : View.GONE);
            endButton.setText(ringing ? "거절" : "끊기");
        } else if (preview && !playing) {
            title.setText("미리보기 곡 제목");
            artist.setText("가수 이름");
            progress.set(40, 100);
        } else {
            title.setText(currentTitle);
            artist.setText(now < noticeUntilMs ? "버튼 도우미(k8np.keys)가 없습니다" : currentArtist);
            progress.set(positionMs, durationMs);
        }
        playButton.setShape(isPlaying ? ControlButton.PAUSE : ControlButton.PLAY);
        try {
            if (show && !attached) {
                windows.addView(bar, params());
                attached = true;
            } else if (!show) {
                detach();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "overlay window failed", e);
        }
    }

    /** During a call the card shows the call instead of the track: no cover, buttons or progress. */
    private void setCallMode(boolean on) {
        if (on == callMode) return;
        callMode = on;
        icon.setText(on ? "☎" : "♪");
        int buttons = on ? View.GONE : View.VISIBLE;
        prevButton.setVisibility(buttons);
        playButton.setVisibility(buttons);
        nextButton.setVisibility(buttons);
        answerButton.setVisibility(View.GONE);
        endButton.setVisibility(on ? View.VISIBLE : View.GONE);
        progress.setVisibility(buttons);
        if (on) {
            artView.setVisibility(View.GONE);
        } else {
            showArt();
        }
    }

    /** Status line of the call card, e.g. "전화 수신 중" or "통화 중 1:05". */
    static String callStatus(CallProbe.State s, long activeSinceMs, long nowMs) {
        if (s.setup == 4) return "전화 수신 중";
        if (s.setup == 5) return "대기 전화 수신 중";
        if (s.active > 0) {
            long sec = activeSinceMs > 0 ? Math.max(0, (nowMs - activeSinceMs) / 1000) : 0;
            return "통화 중 " + clock(sec);
        }
        if (s.setup == 2 || s.setup == 3) return "전화 거는 중";
        if (s.held > 0) return "통화 대기 중";
        return "";
    }

    /** Second line of the call card: privacy mode first, then the saved name, then the number. */
    static String callDetail(CallProbe.State s, boolean privacy, String name) {
        if (privacy) return "프라이버시 모드";
        if (name != null && !name.trim().isEmpty()) return name.trim();
        return s.number.isEmpty() ? "블루투스 전화" : s.number;
    }

    /** Looks the number up in the Bluetooth phone book once per number, off the main thread. */
    private void lookUpName(final String number) {
        if (number.equals(lookedUpNumber)) return;
        lookedUpNumber = number;
        contactName = "";
        if (number.isEmpty() || privacy) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String name = contactName(number);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (number.equals(lookedUpNumber)) {
                            contactName = name;
                            refresh();
                        }
                    }
                });
            }
        }, "k8np-contact").start();
    }

    private String contactName(String number) {
        Cursor c = null;
        try {
            Uri uri = Uri.withAppendedPath(Uri.parse("content://com.android.contacts_bt/phone_lookup"),
                    Uri.encode(number));
            c = getContentResolver().query(uri, new String[] {"display_name"}, null, null, null);
            // The Bluetooth phone book ignores the projection and returns every column (_id first).
            int col = c == null ? -1 : c.getColumnIndex("display_name");
            String name = col >= 0 && c.moveToFirst() ? c.getString(col) : null;
            Log.i(TAG, "contact lookup found=" + (name != null && !name.isEmpty()));
            return name == null ? "" : name;
        } catch (RuntimeException e) {
            Log.w(TAG, "contact lookup failed: " + e);
            return "";
        } finally {
            if (c != null) c.close();
        }
    }

    /** A call started: ask the contacts provider once, off the main thread. */
    private void readPrivacy() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean on = PrivacyMode.read(OverlayService.this);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        privacy = on;
                        refresh();
                    }
                });
            }
        }, "k8np-privacy").start();
    }

    static String clock(long sec) {
        long h = sec / 3600, m = sec / 60 % 60, x = sec % 60;
        String mm = h > 0 && m < 10 ? "0" + m : String.valueOf(m);
        return (h > 0 ? h + ":" : "") + mm + ":" + (x < 10 ? "0" : "") + x;
    }

    private void detach() {
        if (!attached) return;
        windows.removeView(bar);
        attached = false;
    }

    @SuppressWarnings("deprecation")
    private boolean naviInFront() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        return !tasks.isEmpty() && tasks.get(0).topActivity != null
                && NAVI_PACKAGE.equals(tasks.get(0).topActivity.getPackageName());
    }

    /** Widget extras arrive as Integer or Long depending on the field; read either without warnings. */
    static long number(Bundle extras, String key, long fallback) {
        Object v = extras.get(key);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    static String clean(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isISOControl(c)) out.append(c);
        }
        return out.toString().trim();
    }
}

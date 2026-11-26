package dev.gen5w.taxi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.veloster.vehicle.TaxiMeter;
import com.veloster.vehicle.TaxiRenderer;
import com.veloster.vehicle.TaxiZones;
import hmgapi.client.HmgApi;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity implements LocationListener {
    private static final long POLL_MS = 650, GPS_MAX_AGE_MS = 5000;
    private static final String[] AREAS = {"자동(GPS)", "서울", "부산", "대구", "인천", "광주", "대전", "울산",
        "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"};
    private final Handler ui = new Handler();
    private final TaxiMeter meter = new TaxiMeter();
    private final SpeedIntervals intervals = new SpeedIntervals();
    private final ArrayDeque<String> log = new ArrayDeque<String>();
    private Handler worker;
    private HandlerThread thread;
    private volatile int session;
    private boolean active, collecting, diagnostic, receipt;
    private static final String PANEL_SHOW = "com.mobis.intent.action.SPLIT_SCREEN_SHOW";
    private static final String PANEL_HIDE = "com.mobis.intent.action.SPLIT_SCREEN_HIDE";
    private boolean compact;
    private final BroadcastReceiver panelReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String side = intent.getStringExtra("type");
            if (side == null || "right".equalsIgnoreCase(side)) {
                setCompact(PANEL_SHOW.equals(intent.getAction()));
            }
        }
    };
    private static final int FARE_AREA = 100;
    private static final android.graphics.RectF KR_AREA = new android.graphics.RectF(790, 416, 922, 456);
    private static final android.graphics.RectF JP_AREA = new android.graphics.RectF(688, 416, 778, 456);
    private SharedPreferences prefs;
    private TextView status;
    private MeterView face;
    private LocationManager gps;
    private Location fix;
    private String gpsStatus = "GPS 수신 대기";
    private String apiStatus = "HMG-API 응답 대기";
    private long sampleAt = -1, lastLog = -1, lastPlace = -1, requestMs;
    private float currentSpeed = Float.NaN;
    private String odometer = "–";
    private double diagnosticMeters;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        diagnostic = getPackageName().endsWith(".diag");
        prefs = getSharedPreferences("meter", MODE_PRIVATE);
        meter.setArea(prefs.getString("area", "서울"));
        meter.setRegion(prefs.getInt("layout", TaxiMeter.KOREA));
        meter.reset();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setPadding(12, 4, 12, 4);
        if (diagnostic) {
            ScrollView scroll = new ScrollView(this);
            scroll.addView(status);
            layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout actions = new LinearLayout(this);
            actions.addView(button("로그 복사", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Gen5W 진단", logText()));
                    Toast.makeText(MainActivity.this, "최근 기록을 복사했습니다", Toast.LENGTH_SHORT).show();
                }
            }));
            actions.addView(button("로그 내보내기", new View.OnClickListener() {
                @Override public void onClick(View v) {
                    exportLog();
                }
            }));
            layout.addView(actions);
        } else {
            face = new MeterView();
            layout.addView(face, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        setContentView(layout);
        thread = new HandlerThread("gen5w-taxi-telemetry");
        thread.start();
        worker = new Handler(thread.getLooper());
        if (!diagnostic) {
            worker.post(new Runnable() {
                @Override public void run() { TaxiZones.at(37.56, 126.98); }
            });
            IntentFilter filter = new IntentFilter(PANEL_SHOW);
            filter.addAction(PANEL_HIDE);
            registerReceiver(panelReceiver, filter);
        }
    }

    private void setCompact(boolean enabled) {
        if (compact == enabled) return;
        compact = enabled;
        face.clearTouch();
    }

    private Button button(String title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(listener);
        return button;
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        if (!collecting) startCollection();
        ui.post(refresh);
    }

    private void startCollection() {
        collecting = true;
        final int token = ++session;
        final HmgApi api = HmgApi.connect().timeout(4500);
        final String packageMod = diagnostic ? "gen5w-taxi-meter-diag" : "gen5w-taxi-meter";
        apiStatus = "HMG-API 응답 대기";
        startGps();
        worker.post(new Runnable() {
            private String version;
            private long nextOdometerAt;
            private String lastOdometer = "–";
            private String odometerError = "";
            @Override public void run() {
                if (token != session) return;
                final long start = SystemClock.elapsedRealtime();
                String state;
                boolean requestFailed = false;
                String speedWarning = "";
                String odo = lastOdometer;
                float speed = Float.NaN;
                try {
                    if (version == null) {
                        HmgApi.Result result = api.call("version");
                        if (result.code != 0) throw new IOException("버전 응답 코드 " + result.code);
                        if (!packageMod.equals(api.mod())) throw new IOException("Mod Manager에서 설치 후 활성화하세요");
                        version = result.text.trim();
                    }
                    if (diagnostic && start >= nextOdometerAt && version != null && nextOdometerAt != 0) {
                        nextOdometerAt = start + 30000;
                        try {
                            String reading = readDiagnosticValue(api.readVehicleLine("odometer"), "odometer");
                            lastOdometer = reading == null ? "–" : reading;
                            odometerError = "";
                        } catch (IOException e) {
                            lastOdometer = "–";
                            odometerError = " · 주행계 요청 실패: " + e.getMessage();
                        }
                        if (token == session) worker.postAtTime(this, Integer.valueOf(token), SystemClock.uptimeMillis() + POLL_MS);
                        return;
                    }
                    if (nextOdometerAt == 0) nextOdometerAt = start + 1000;
                    HmgApi.Result result = api.readVehicleLine("speed");
                    speed = SpeedIntervals.parse(readDiagnosticValue(result, "speed"));
                    if (diagnostic && result.code == HmgApi.LINE_RECEIVED) speedWarning = " · 값 수신 기준";
                    state = version + (Float.isNaN(speed) ? " · 차량 속도 미수신" : " · 연결됨") + speedWarning + odometerError;
                } catch (IOException | RuntimeException e) {
                    requestFailed = true;
                    String lastVersion = version;
                    version = null;
                    state = (lastVersion == null ? "HMG-API" : lastVersion) + " · 차량 데이터 요청 실패: " + e.getClass().getSimpleName() + " · " + e.getMessage();
                }
                final long now = SystemClock.elapsedRealtime();
                final String stateResult = state, odoResult = odo;
                final float speedResult = speed;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (token != session) return;
                        sample(speedResult, odoResult, stateResult, now, now - start);
                    }
                });
                if (token == session) worker.postAtTime(this, Integer.valueOf(token), SystemClock.uptimeMillis()
                    + (requestFailed ? 3200 : Math.max(50, POLL_MS - (now - start))));
            }
        });
    }

    @Override protected void onPause() {
        active = false;
        ui.removeCallbacks(refresh);
        if (diagnostic) stopCollection();
        super.onPause();
    }

    private void stopCollection() {
        if (!collecting) return;
        collecting = false;
        ++session;
        worker.removeCallbacksAndMessages(null);
        intervals.reset();
        sampleAt = -1;
        currentSpeed = Float.NaN;
        lastPlace = -1;
        fix = null;
        if (gps != null) {
            try { gps.removeUpdates(this); } catch (SecurityException ignored) { }
        }
    }

    @Override protected void onDestroy() {
        stopCollection();
        if (!diagnostic) unregisterReceiver(panelReceiver);
        thread.quitSafely();
        super.onDestroy();
    }

    private void startGps() {
        gps = (LocationManager) getSystemService(LOCATION_SERVICE);
        gpsStatus = "GPS 수신 대기";
        if (gps == null) {
            gpsStatus = "GPS 서비스 없음";
            return;
        }
        try {
            if (gps.getProvider(LocationManager.GPS_PROVIDER) == null) {
                gpsStatus = "GPS 제공자 없음";
                return;
            }
            if (!gps.isProviderEnabled(LocationManager.GPS_PROVIDER)) gpsStatus = "GPS 꺼짐";
            gps.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        } catch (IllegalArgumentException | SecurityException e) {
            gpsStatus = "GPS 접근 실패 · " + e.getMessage();
        }
    }

    @Override public void onLocationChanged(Location location) { fix = location; }
    @Override public void onProviderEnabled(String provider) { gpsStatus = "GPS 수신 대기"; }
    @Override public void onProviderDisabled(String provider) { fix = null; gpsStatus = "GPS 꺼짐"; }
    @Override public void onStatusChanged(String provider, int state, Bundle extras) {
        if (state != android.location.LocationProvider.AVAILABLE) fix = null;
    }

    private Location freshFix(long now) {
        if (fix == null) return null;
        long age = now - fix.getElapsedRealtimeNanos() / 1000000L;
        return age >= 0 && age <= GPS_MAX_AGE_MS ? fix : null;
    }

    private void updateArea(long now) {
        Location location = freshFix(now);
        int zone = -1;
        if (prefs.getBoolean("auto", true) && location != null && location.hasAccuracy()
                && location.getAccuracy() <= 100 && TaxiZones.ready()) {
            zone = TaxiZones.at(location.getLatitude(), location.getLongitude());
        }
        if (meter.place(zone, zone >= 0 ? TaxiZones.tariff(zone) : null)) {
            prefs.edit().putString("area", meter.area()).apply();
        }
    }

    private void chooseArea() {
        if (meter.state() == TaxiMeter.HIRED) {
            Toast.makeText(this, "운행 종료 후 요금 지역을 변경할 수 있습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("요금 지역")
            .setItems(AREAS, (dialog, which) -> {
                if (meter.state() == TaxiMeter.HIRED) return;
                boolean auto = which == 0;
                if (!auto) {
                    meter.place(-1, null);
                    meter.setArea(AREAS[which]);
                }
                prefs.edit().putBoolean("auto", auto).putString("area", meter.area()).apply();
                updateArea(SystemClock.elapsedRealtime());
            }).show();
    }

    private static String readDiagnosticValue(HmgApi.Result result, String name) throws IOException {
        String reply = result.text;
        String prefix = name + " ";
        if ((result.code == 0 || result.code == HmgApi.LINE_RECEIVED) && reply.startsWith(prefix)
                && reply.indexOf('\n') == reply.length() - 1) {
            String value = reply.substring(prefix.length(), reply.length() - 1).trim();
            if ("-".equals(value) || value.matches("[0-9]+(\\.[0-9]+)?")) return value;
        }
        throw new IOException("hmgapid code " + result.code + " · " + reply.trim());
    }

    private void sample(float speed, String odo, String state, long now, long latency) {
        if (latency > SpeedIntervals.MAX_GAP_MS || SystemClock.elapsedRealtime() - now > SpeedIntervals.MAX_GAP_MS) {
            if (!diagnostic) speed = Float.NaN;
            state += " · 긴 지연, 적분 제외";
        }
        long cadence = sampleAt < 0 ? 0 : now - sampleAt;
        long interval = intervals.accept(latency > SpeedIntervals.MAX_GAP_MS
            || SystemClock.elapsedRealtime() - now > SpeedIntervals.MAX_GAP_MS ? Float.NaN : speed, now);
        sampleAt = now;
        currentSpeed = speed;
        odometer = odo;
        apiStatus = state;
        requestMs = latency;
        if (diagnostic) {
            if (interval > 0) diagnosticMeters += speed * interval / 3600.0;
            if (lastLog < 0 || now - lastLog >= 1000) {
                lastLog = now;
                if (log.size() == 300) log.removeFirst();
                log.addLast(String.format(Locale.US, "%d,%s,%s,%d,%d,%.2f,%s,%s",
                    now, speedText(speed), odo, cadence, latency, diagnosticMeters,
                    freshFix(now) == null ? "no" : "yes", state.replace('\n', ' ').replace(',', ';')));
            }
            lastCadence = cadence;
        } else {
            long currentTime = SystemClock.elapsedRealtime();
            if (lastPlace < 0 || currentTime - lastPlace >= 1000) {
                lastPlace = currentTime;
                updateArea(currentTime);
            }
            int hour = hour();
            if (meter.autoStart(speed, hour)) {
                receipt = false;
            } else meter.update(speed, interval, hour);
        }
    }


    private long lastCadence;
    private static String speedText(float speed) {
        return Float.isNaN(speed) ? "–" : String.format(Locale.US, "%.2f", speed);
    }

    private float speedAt(long now) {
        return sampleAt >= 0 && now - sampleAt <= SpeedIntervals.MAX_GAP_MS ? currentSpeed : Float.NaN;
    }

    private static int hour() { return Calendar.getInstance().get(Calendar.HOUR_OF_DAY); }

    private void exportLog() {
        final String contents = logText();
        worker.post(new Runnable() {
            @Override public void run() {
                String message;
                try {
                    File directory = getExternalFilesDir(null);
                    if (directory == null) throw new IOException("내부 공유 저장소 사용 불가");
                    File file = new File(directory, "diagnostic.csv");
                    try (FileOutputStream output = new FileOutputStream(file)) {
                        output.write(contents.getBytes(StandardCharsets.UTF_8));
                    }
                    message = file.getAbsolutePath();
                } catch (IOException | SecurityException e) {
                    message = "로그 저장 실패 · " + e.getMessage();
                }
                final String result = message;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (active) new AlertDialog.Builder(MainActivity.this)
                            .setTitle("진단 로그").setMessage(result).setPositiveButton("닫기", null).show();
                    }
                });
            }
        });
    }

    private String logText() {
        StringBuilder text = new StringBuilder(
            "elapsed_ms,speed_kmh,odometer_km,interval_ms,request_ms,integrated_m,gps_fix,status\n");
        for (String row : log) text.append(row).append('\n');
        return text.toString();
    }

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!active) return;
            long now = SystemClock.elapsedRealtime();
            if (diagnostic) {
                Location location = freshFix(now);
                String locationText = location == null ? (fix == null ? gpsStatus : "GPS 최근 위치 만료")
                    : String.format(Locale.US, "GPS 수신 · %.6f, %.6f · 정확도 %s",
                        location.getLatitude(), location.getLongitude(),
                        location.hasAccuracy() ? location.getAccuracy() + " m" : "미제공");
                status.setText(String.format(Locale.US,
                    "%s\n차량 속도 %s km/h\n누적 주행계 %s km\n적분 거리 %.2f m\n수신 간격 %d ms · 값 수신 %d ms\n%s\n로그 %d/300건",
                    apiStatus, speedText(currentSpeed), odometer, diagnosticMeters, lastCadence, requestMs, locationText, log.size()));
            } else {
                face.invalidate();
            }
            ui.postDelayed(this, diagnostic ? 250 : 50);
        }
    };

    private final class MeterView extends View {
        private final TaxiRenderer renderer = new TaxiRenderer();
        private final android.graphics.Paint controlPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float scale = 1, left, top, downX, downY;
        private int downAction = -1;
        private boolean dismissReceipt;

        MeterView() {
            super(MainActivity.this);
            int id = getResources().getIdentifier("taxi_horse", "drawable", getPackageName());
            Bitmap horse = id == 0 ? null : BitmapFactory.decodeResource(getResources(), id);
            if (horse != null) {
                renderer.setHorse(horse, 8);
                horse.recycle();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(0xff0b0d0e);
            // A stock overlay can cover the app without changing its measured width.
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            float usableWidth = compact ? Math.min(getWidth(), screenWidth * 0.65f) : getWidth();
            int layoutWidth = TaxiRenderer.width(meter.region());
            scale = Math.min(usableWidth / layoutWidth, getHeight() / 428f);
            if (scale <= 0) return;
            left = (usableWidth - layoutWidth * scale) / 2;
            top = (getHeight() - 428 * scale) / 2;
            long now = SystemClock.elapsedRealtime();
            canvas.save();
            canvas.translate(left, top - 52 * scale);
            canvas.scale(scale, scale);
            renderer.draw(canvas, meter, speedAt(now), hour(), now, receipt);
            if (!receipt) {
                boolean japan = meter.region() == TaxiMeter.JAPAN;
                drawControl(canvas, japan ? JP_AREA : KR_AREA, "요금 지역", FARE_AREA);
            }
            canvas.restore();
        }

        private void drawControl(Canvas canvas, android.graphics.RectF bounds, String label, int action) {
            controlPaint.setColor(downAction == action ? 0xff355560 : 0xff263640);
            canvas.drawRoundRect(bounds, 8, 8, controlPaint);
            controlPaint.setColor(0xffecf1f4);
            controlPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            controlPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            controlPaint.setTextSize(18);
            canvas.drawText(label, bounds.centerX(),
                bounds.centerY() - (controlPaint.ascent() + controlPaint.descent()) / 2, controlPaint);
        }

        private int hitAction(float x, float y, int region) {
            boolean japan = region == TaxiMeter.JAPAN;
            if ((japan ? JP_AREA : KR_AREA).contains(x, y)) return FARE_AREA;
            return TaxiRenderer.hit(x, y, region);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (scale <= 0) return false;
            float x = (event.getX() - left) / scale;
            float y = (event.getY() - top) / scale + 52;
            int region = meter.region();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x;
                    downY = y;
                    dismissReceipt = receipt && y >= 52 && y <= 480 && x >= 0 && x <= TaxiRenderer.width(region);
                    downAction = receipt ? -1 : hitAction(x, y, region);
                    renderer.setPressed(downAction);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(x - downX) >= 16 || Math.abs(y - downY) >= 16) clearTouch();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (Math.abs(x - downX) < 16 && Math.abs(y - downY) < 16) {
                        if (dismissReceipt && receipt) {
                            receipt = false;
                            performClick();
                        } else if (downAction >= 0 && downAction == hitAction(x, y, region)
                            && TaxiRenderer.enabled(downAction, meter)) {
                            applyAction(downAction, region);
                            performClick();
                        }
                    }
                    clearTouch();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    clearTouch();
                    return true;
                default:
                    return true;
            }
        }

        private void clearTouch() {
            downAction = -1;
            dismissReceipt = false;
            renderer.setPressed(-1);
            invalidate();
        }

        @Override public boolean performClick() {
            super.performClick();
            playSoundEffect(SoundEffectConstants.CLICK);
            return true;
        }

        private void applyAction(int action, int region) {
            switch (action) {
                case FARE_AREA:
                    chooseArea();
                    break;
                case TaxiRenderer.VACANT_BUTTON:
                    meter.reset();
                    receipt = false;
                    break;
                case TaxiRenderer.HIRE_BUTTON:
                    if (Float.isNaN(speedAt(SystemClock.elapsedRealtime()))) {
                        Toast.makeText(MainActivity.this, "유효한 차량 속도를 기다리는 중입니다", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    meter.start(hour());
                    intervals.reset();
                    receipt = false;
                    break;
                case TaxiRenderer.SURCHARGE_BUTTON:
                    meter.toggleOutOfCity();
                    break;
                case TaxiRenderer.PAY_BUTTON:
                    meter.stop();
                    receipt = TaxiRenderer.payShowsReceipt(region);
                    break;
                case TaxiRenderer.RECEIPT_BUTTON:
                    receipt = true;
                    break;
                case TaxiRenderer.REGION_BUTTON:
                    meter.setRegion(region == TaxiMeter.KOREA ? TaxiMeter.JAPAN : TaxiMeter.KOREA);
                    prefs.edit().putInt("layout", meter.region()).apply();
                    intervals.reset();
                    receipt = false;
                    break;
                default:
                    break;
            }
        }
    }
}

package com.v2ray.ang.remna;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Remna VPN ana ekranı: üstte Otomatik seçim, ortada güç düğmesi, altta sunucu listesi. */
public class RemnaActivity extends Activity {
    static final int OFF = 0, CONNECTING = 1, ON = 2;
    static final int REQ_VPN = 41, REQ_NOTIF = 42;

    static final int BG1 = 0xFF070B16, BG2 = 0xFF0F1630, PANEL = 0xFF111830, CARD = 0x0FFFFFFF, LINE = 0x14FFFFFF;
    static final int ACC = 0xFF22D3EE, ACC2 = 0xFF8B5CF6, GREEN = 0xFF10B981, AMBER = 0xFFF59E0B, RED = 0xFFEF4444;
    static final int TX = 0xFFF1F5F9, TX2 = 0xFF94A3B8, TX3 = 0xFF64748B;

    final Handler ui = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    int state = OFF;
    volatile boolean busy = false, cancel = false;
    final Map<String, Integer> pings = new HashMap<>();
    String status = "";

    Ui.PowerButton power;
    TextView stateTv, serverTv, timerTv, autoSub, countTv, watchBtn;
    Ui.Switch autoSw;
    LinearLayout list, serverCard;
    Dialog serversDlg;
    volatile String coreInit = "";

    /* ---------------- yaşam döngüsü ---------------- */
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("remna", MODE_PRIVATE);
        Window w = getWindow();
        w.setStatusBarColor(BG1);
        w.setNavigationBarColor(PANEL);
        installCrashHandler();
        L.init(prefs.getString("lang", ""));
        try { androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES); } catch (Throwable ignored) {}
        setContentView(build());
        Ads.init(this);
        showPreviousCrash();
        new Thread(() -> coreInit = Ng.initCore(this)).start();
        ensureSubscription(false);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        poll.run();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(poll);
        ui.removeCallbacks(tick);
    }

    final Runnable poll = new Runnable() {
        @Override
        public void run() {
            ui.removeCallbacks(this);
            if (!busy) {
                new Thread(() -> {
                    boolean up = isUp();
                    ui.post(() -> { if (!busy && (up ? ON : OFF) != state) setState(up ? ON : OFF, null); });
                }).start();
            }
            ui.postDelayed(this, 2000);
        }
    };

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ui.removeCallbacks(this);
            long left = RemnaExpiry.remaining(RemnaActivity.this);
            if (left > 0) {
                long t = left / 1000;
                timerTv.setText(String.format(java.util.Locale.US, L.t("Kalan süre  %02d:%02d:%02d"), t / 3600, (t / 60) % 60, t % 60));
                timerTv.setTextColor(left < 5 * 60000 ? AMBER : TX2);
            } else {
                timerTv.setText(L.t("Süre yok — bağlanmak için video izle"));
                timerTv.setTextColor(TX3);
                if (state == ON && !busy) { toast(L.t("Süre doldu")); toggle(); }
            }
            boolean full = left > RemnaConfig.MAX_BANK_MS - RemnaConfig.REWARD_MS;
            watchBtn.setAlpha(full ? 0.45f : 1f);
            ui.postDelayed(this, 1000);
        }
    };

    void refreshAll() {
        renderAuto();
        renderList();
    }

    /* ---------------- yardımcılar ---------------- */
    int dp(float v) { return (int) Ui.dp(this, v); }

    GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    GradientDrawable roundStroke(int color, float r, int stroke) {
        GradientDrawable g = round(color, r);
        g.setStroke(dp(1), stroke);
        return g;
    }

    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    View iconBtn(int type, float size, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(roundStroke(CARD, size / 2f, LINE));
        Ui.Icon ic = new Ui.Icon(this, type, TX);
        f.addView(ic, new FrameLayout.LayoutParams(dp(size * 0.48f), dp(size * 0.48f), Gravity.CENTER));
        f.setOnClickListener(l);
        f.setClickable(true);
        f.setForeground(getDrawable(android.R.drawable.list_selector_background));
        return f;
    }

    LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static final int MP = ViewGroup.LayoutParams.MATCH_PARENT, WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    /* ---------------- arayüz ---------------- */
    View build() {
        FrameLayout frame = new FrameLayout(this);
        frame.setFitsSystemWindows(true);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG1, BG2}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, new FrameLayout.LayoutParams(MP, MP));

        // ---- başlık
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(20), dp(14), dp(20), dp(6));
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
        top.addView(logo, lp(dp(34), dp(34)));
        TextView title = text("Remna VPN", 19, TX, true);
        title.setPadding(dp(10), 0, 0, 0);
        title.setOnLongClickListener(v -> { diag(); return true; });
        top.addView(title, new LinearLayout.LayoutParams(0, WC, 1));
        View gear = iconBtn(Ui.Icon.GEAR, 40, v -> openSettings());
        top.addView(gear, lp(dp(40), dp(40)));
        root.addView(top);

        // ---- otomatik
        LinearLayout auto = new LinearLayout(this);
        auto.setGravity(Gravity.CENTER_VERTICAL);
        auto.setPadding(dp(14), dp(14), dp(16), dp(14));
        auto.setBackground(roundStroke(CARD, 18, LINE));
        FrameLayout bolt = new FrameLayout(this);
        GradientDrawable bb = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x3322D3EE, 0x338B5CF6});
        bb.setCornerRadius(dp(12));
        bolt.setBackground(bb);
        bolt.addView(new Ui.Icon(this, Ui.Icon.BOLT, ACC), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        auto.addView(bolt, lp(dp(42), dp(42)));
        LinearLayout ac = new LinearLayout(this);
        ac.setOrientation(LinearLayout.VERTICAL);
        ac.setPadding(dp(14), 0, dp(10), 0);
        ac.addView(text(L.t("Otomatik seçim"), 15.5f, TX, true));
        autoSub = text("", 12.5f, TX2, false);
        autoSub.setPadding(0, dp(4), 0, 0);
        autoSub.setSingleLine(true);
        autoSub.setEllipsize(TextUtils.TruncateAt.END);
        ac.addView(autoSub);
        auto.addView(ac, new LinearLayout.LayoutParams(0, WC, 1));
        autoSw = new Ui.Switch(this);
        auto.addView(autoSw, lp(dp(46), dp(26)));
        auto.setOnClickListener(v -> {
            boolean nv = !auto();
            prefs.edit().putBoolean("auto", nv).apply();
            autoSw.set(nv, true);
            refreshAll();
            if (state == ON && nv) reconnect();
        });
        LinearLayout.LayoutParams al = lp(MP, WC);
        al.leftMargin = dp(20); al.rightMargin = dp(20); al.topMargin = dp(12);
        root.addView(auto, al);

        // ---- güç düğmesi
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        power = new Ui.PowerButton(this);
        power.setOnClickListener(v -> toggle());
        center.addView(power, lp(dp(250), dp(250)));
        stateTv = text("", 22, TX, true);
        stateTv.setGravity(Gravity.CENTER);
        center.addView(stateTv);
        serverTv = text("", 13.5f, TX2, false);
        serverTv.setGravity(Gravity.CENTER);
        serverTv.setPadding(dp(24), dp(8), dp(24), 0);
        serverTv.setMaxLines(1);
        serverTv.setEllipsize(TextUtils.TruncateAt.END);
        center.addView(serverTv);
        timerTv = text("00:00:00", 13, TX2, false);
        timerTv.setTypeface(Typeface.MONOSPACE);
        timerTv.setPadding(dp(12), dp(5), dp(12), dp(5));
        timerTv.setBackground(round(0x14FFFFFF, 12));
        LinearLayout.LayoutParams tl = lp(WC, WC);
        tl.topMargin = dp(10);
        center.addView(timerTv, tl);
        watchBtn = text(L.t("Video izle  •  +1 saat"), 14, Color.WHITE, true);
        watchBtn.setShadowLayer(4, 0, 1, 0x55000000);
        watchBtn.setIncludeFontPadding(true);
        watchBtn.setGravity(Gravity.CENTER);
        watchBtn.setPadding(dp(16), dp(9), dp(16), dp(9));
        GradientDrawable wb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF0891B2, 0xFF6366F1});
        wb.setCornerRadius(dp(20));
        watchBtn.setBackground(wb);
        watchBtn.setOnClickListener(v -> watchAd(null));
        LinearLayout.LayoutParams wl = lp(WC, WC);
        wl.topMargin = dp(10);
        center.addView(watchBtn, wl);
        root.addView(center, new LinearLayout.LayoutParams(MP, 0, 1f));

        // ---- seçili sunucu kartı (dokununca liste açılır)
        serverCard = new LinearLayout(this);
        serverCard.setGravity(Gravity.CENTER_VERTICAL);
        serverCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        serverCard.setBackground(roundStroke(PANEL, 18, LINE));
        serverCard.setOnClickListener(v -> openServers());
        LinearLayout.LayoutParams scl = lp(MP, WC);
        scl.leftMargin = dp(20); scl.rightMargin = dp(20); scl.bottomMargin = dp(10);
        root.addView(serverCard, scl);

        View ad = Ads.banner(this);
        if (ad != null) {
            FrameLayout af = new FrameLayout(this);
            af.addView(ad, new FrameLayout.LayoutParams(WC, WC, Gravity.CENTER));
            af.setPadding(0, 0, 0, dp(8));
            root.addView(af, lp(MP, WC));
        }

        autoSw.set(auto(), false);
        setState(OFF, null);
        return frame;
    }

    boolean auto() { return prefs.getBoolean("auto", true); }

    void renderAuto() {
        autoSw.set(auto(), false);
        if (auto()) {
            String last = prefs.getString("last_good", null);
            autoSub.setText(state == ON && last != null ? L.t("Seçilen: ") + Ng.info(last)[0] : L.t("En hızlı çalışan sunucu otomatik seçilir"));
        } else autoSub.setText(L.t("Kapalı — listeden sunucu seç"));
    }

    static int hue(String s) {
        float[] hsv = {Math.abs(s.hashCode() % 360), 0.55f, 0.85f};
        return Color.HSVToColor(hsv);
    }

    static String initials(String n) {
        String t = n.replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
        if (t.isEmpty()) return "•";
        String[] p = t.split("\\s+");
        String r = p[0].substring(0, 1) + (p.length > 1 ? p[1].substring(0, 1) : (p[0].length() > 1 ? p[0].substring(1, 2) : ""));
        return r.toUpperCase();
    }

    void renderCard() {
        if (serverCard == null) return;
        serverCard.removeAllViews();
        boolean a = auto();
        String sel = Ng.selected();
        String name, meta;
        if (a) {
            String last = prefs.getString("last_good", null);
            name = L.t("Otomatik");
            meta = state == ON && last != null ? Ng.info(last)[0] : L.t("En hızlı sunucu seçilir");
        } else if (sel != null) {
            String[] in = Ng.info(sel);
            name = in[0].isEmpty() ? in[1] : in[0];
            meta = state == ON ? L.t("Bağlı") : L.t("Değiştirmek için dokun");
        } else { name = L.t("Sunucu seç"); meta = ""; }
        View av;
        if (a) {
            FrameLayout f = new FrameLayout(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x3322D3EE, 0x338B5CF6});
            g.setShape(GradientDrawable.OVAL);
            f.setBackground(g);
            f.addView(new Ui.Icon(this, Ui.Icon.BOLT, ACC), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
            av = f;
        } else {
            TextView t = text(initials(name), 13, Color.WHITE, true);
            t.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{hue(name), hue(name + "x")});
            g.setShape(GradientDrawable.OVAL);
            t.setBackground(g);
            av = t;
        }
        serverCard.addView(av, lp(dp(40), dp(40)));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), 0, dp(8), 0);
        TextView n = text(name, 15.5f, TX, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(n);
        TextView m = text(meta, 12.5f, TX3, false);
        m.setPadding(0, dp(4), 0, 0);
        m.setSingleLine(true);
        m.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(m);
        serverCard.addView(c, new LinearLayout.LayoutParams(0, WC, 1));
        TextView cnt = text(Ng.serverList().size() + L.t(" sunucu"), 12, TX2, false);
        cnt.setPadding(0, 0, dp(6), 0);
        serverCard.addView(cnt);
        serverCard.addView(new Ui.Icon(this, Ui.Icon.CHEVRON, TX2), lp(dp(20), dp(20)));
    }

    /** Sunucu listesi: alttan açılan sayfa. */
    void openServers() {
        Dialog d = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(PANEL);
        float r = dp(26);
        pb.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(pb);
        panel.setPadding(dp(16), dp(10), dp(16), 0);
        View grip = new View(this);
        grip.setBackground(round(0x33FFFFFF, 3));
        LinearLayout.LayoutParams gl = lp(dp(40), dp(5));
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        gl.bottomMargin = dp(12);
        panel.addView(grip, gl);
        LinearLayout ph = new LinearLayout(this);
        ph.setGravity(Gravity.CENTER_VERTICAL);
        ph.setPadding(dp(4), 0, 0, dp(6));
        ph.addView(text(L.t("Sunucular"), 17, TX, true));
        countTv = text("0", 12, TX2, true);
        countTv.setPadding(dp(8), dp(3), dp(8), dp(3));
        countTv.setBackground(round(0x1AFFFFFF, 10));
        LinearLayout.LayoutParams cl = lp(WC, WC);
        cl.leftMargin = dp(8);
        ph.addView(countTv, cl);
        ph.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        ph.addView(iconBtn(Ui.Icon.SIGNAL, 36, v -> pingAll()), lp(dp(36), dp(36)));
        LinearLayout.LayoutParams rl = lp(dp(36), dp(36));
        rl.leftMargin = dp(8);
        ph.addView(iconBtn(Ui.Icon.REFRESH, 36, v -> updateSubs()), rl);
        LinearLayout.LayoutParams xl = lp(dp(36), dp(36));
        xl.leftMargin = dp(8);
        ph.addView(iconBtn(Ui.Icon.CLOSE, 36, v -> d.dismiss()), xl);
        panel.addView(ph);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(24));
        sv.addView(list);
        panel.addView(sv, new LinearLayout.LayoutParams(MP, 0, 1));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setOnClickListener(v -> d.dismiss());
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.72f);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(MP, h, Gravity.BOTTOM);
        panel.setClickable(true);
        wrap.addView(panel, pl);
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x99000000));
            w.setLayout(MP, MP);
            w.setNavigationBarColor(PANEL);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        d.setOnDismissListener(x -> { list = null; serversDlg = null; renderCard(); });
        serversDlg = d;
        renderList();
        d.show();
    }

    void renderList() {
        renderCard();
        if (list == null) return;
        list.removeAllViews();
        List<String> ids = Ng.serverList();
        countTv.setText(String.valueOf(ids.size()));
        if (ids.isEmpty()) {
            LinearLayout e = new LinearLayout(this);
            e.setOrientation(LinearLayout.VERTICAL);
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            e.setPadding(dp(16), dp(30), dp(16), dp(30));
            Ui.Icon ic = new Ui.Icon(this, Ui.Icon.LINK, TX3);
            e.addView(ic, lp(dp(36), dp(36)));
            TextView t1 = text(L.t("Henüz sunucu yok"), 15, TX, true);
            t1.setPadding(0, dp(12), 0, dp(6));
            e.addView(t1);
            TextView t2 = text(L.t("Sunucuları yüklemek için yenile düğmesine dokun"), 13, TX2, false);
            t2.setGravity(Gravity.CENTER);
            e.addView(t2);
            list.addView(e, lp(MP, WC));
            return;
        }
        String sel = Ng.selected();
        boolean a = auto();
        String active = state == ON ? sel : null;
        for (String id : ids) {
            String[] in = Ng.info(id);
            String name = in[0].isEmpty() ? in[1] : in[0];
            StringBuilder meta = new StringBuilder(in[3].isEmpty() ? "" : in[3].toUpperCase());
            if (!in[4].isEmpty()) meta.append(" · ").append(in[4]);
            if (!in[5].isEmpty()) meta.append(" · ").append(in[5]);

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(11), dp(14), dp(11));
            boolean isSel = !a && id.equals(sel);
            boolean isAct = id.equals(active);
            row.setBackground(isSel || isAct ? roundStroke(0x1422D3EE, 16, isAct ? GREEN : ACC) : round(CARD, 16));

            TextView av = text(initials(name), 13, Color.WHITE, true);
            av.setGravity(Gravity.CENTER);
            GradientDrawable ab = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{hue(name), hue(name + "x")});
            ab.setShape(GradientDrawable.OVAL);
            av.setBackground(ab);
            row.addView(av, lp(dp(38), dp(38)));

            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(dp(12), 0, dp(8), 0);
            TextView n = text(name, 15, TX, true);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(n);
            if (isAct || isSel) {
                TextView s = text(isAct ? L.t("Bağlı") : L.t("Seçili"), 12, isAct ? GREEN : ACC, false);
                s.setPadding(0, dp(4), 0, 0);
                c.addView(s);
            }
            row.addView(c, new LinearLayout.LayoutParams(0, WC, 1));

            Integer ms = pings.get(id);
            if (ms != null) {
                int col = ms < 0 ? RED : ms < 300 ? GREEN : ms < 800 ? AMBER : RED;
                TextView pt = text(ms < 0 ? L.t("Yok") : ms + " ms", 12, col, true);
                pt.setPadding(dp(9), dp(4), dp(9), dp(4));
                pt.setBackground(round((col & 0x00FFFFFF) | 0x22000000, 10));
                row.addView(pt);
            }
            final String gid = id;
            row.setOnClickListener(v -> {
                prefs.edit().putBoolean("auto", false).apply();
                Ng.select(gid);
                if (serversDlg != null) serversDlg.dismiss();
                refreshAll();
                if (state == ON) reconnect();
            });
            LinearLayout.LayoutParams l = lp(MP, WC);
            l.topMargin = dp(8);
            list.addView(row, l);
        }
    }

    static String str(Object o) { return o == null ? "" : o.toString(); }

    void setState(int s, String msg) {
        int prev = state;
        state = s;
        power.setState(s);
        if (s == ON && prev != ON && prefs.getLong("on_since", 0) == 0) prefs.edit().putLong("on_since", System.currentTimeMillis()).apply();
        if (s == OFF) prefs.edit().putLong("on_since", 0).apply();
        stateTv.setText(s == ON ? L.t("Korunuyor") : s == CONNECTING ? L.t("Bağlanıyor…") : L.t("Bağlı değil"));
        stateTv.setTextColor(s == ON ? GREEN : s == CONNECTING ? AMBER : TX);
        if (msg != null) status = msg;
        else if (s == ON) status = currentName();
        else if (s == OFF) status = auto() ? L.t("Bağlanmak için dokun") : currentName();
        serverTv.setText(status);
        if (prev != s) { renderAuto(); renderList(); }
    }

    String currentName() {
        String sel = Ng.selected();
        if (sel == null) return "";
        String n = Ng.info(sel)[0];
        Integer ms = pings.get(sel);
        return n + (ms != null && ms > 0 ? "  ·  " + ms + " ms" : "");
    }

    /* ---------------- ayarlar sayfası ---------------- */
    void openSettings() {
        Dialog d = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(BG1);
        box.setFitsSystemWindows(true);

        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(20), dp(14), dp(20), dp(10));
        h.addView(text(L.t("Ayarlar"), 20, TX, true), new LinearLayout.LayoutParams(0, WC, 1));
        h.addView(iconBtn(Ui.Icon.CLOSE, 40, v -> d.dismiss()), lp(dp(40), dp(40)));
        box.addView(h);

        ScrollView sv = new ScrollView(this);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), 0, dp(20), dp(30));
        sv.addView(c);
        box.addView(sv, new LinearLayout.LayoutParams(MP, 0, 1));

        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            c.removeAllViews();
            c.addView(section(L.t("SUNUCULAR")));
            c.addView(action(Ui.Icon.REFRESH, L.t("Sunucuları güncelle"), v -> { ensureSubscription(true); }), cardLp());
            LinearLayout tm = card();
            LinearLayout tcol = new LinearLayout(this);
            tcol.setOrientation(LinearLayout.VERTICAL);
            tcol.addView(text(L.t("Kalan süre"), 14, TX, true));
            long lm = RemnaExpiry.remaining(this) / 60000;
            TextView tv = text(lm > 0 ? (lm / 60 > 0 ? (lm / 60) + L.t(" sa ") : "") + (lm % 60) + L.t(" dk") : L.t("Süre yok"), 12.5f, TX2, false);
            tv.setPadding(0, dp(4), 0, 0);
            tcol.addView(tv);
            tm.addView(tcol, new LinearLayout.LayoutParams(0, WC, 1));
            c.addView(tm, cardLp());

            c.addView(section(L.t("BAĞLANTI")));
            c.addView(action(Ui.Icon.SHIELD, L.t("Uygulama bazlı VPN"), v -> openClass("com.v2ray.ang.ui.PerAppProxyActivity")), cardLp());
            c.addView(action(Ui.Icon.LINK, L.t("Yönlendirme kuralları"), v -> openClass("com.v2ray.ang.ui.RoutingSettingActivity")), cardLp());
            c.addView(action(Ui.Icon.GEAR, L.t("Gelişmiş ayarlar"), v -> openClass("com.v2ray.ang.ui.SettingsActivity")), cardLp());

            c.addView(section(L.t("DİL")));
            LinearLayout lr = card();
            lr.addView(new Ui.Icon(this, Ui.Icon.LINK, ACC), lp(dp(22), dp(22)));
            TextView lt = text(L.t("Dil"), 15, TX, false);
            lt.setPadding(dp(14), 0, 0, 0);
            lr.addView(lt, new LinearLayout.LayoutParams(0, WC, 1));
            lr.addView(text(L.name(prefs.getString("lang", "")), 14, TX2, false));
            lr.addView(new Ui.Icon(this, Ui.Icon.CHEVRON, TX3), lp(dp(18), dp(18)));
            lr.setOnClickListener(v -> {
                String[] names = new String[L.CODES.length];
                for (int i = 0; i < names.length; i++) names[i] = L.name(L.CODES[i]);
                new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(L.t("Dil"))
                        .setItems(names, (dd, which) -> {
                            prefs.edit().putString("lang", L.CODES[which]).apply();
                            d.dismiss();
                            recreate();
                        }).show();
            });
            c.addView(lr, cardLp());

            c.addView(section(L.t("SORUN GİDERME")));
            c.addView(action(Ui.Icon.SIGNAL, L.t("Günlük (log)"), v -> openLog()), cardLp());

            c.addView(section(L.t("CİHAZ")));
            LinearLayout hw = card();
            LinearLayout hc = new LinearLayout(this);
            hc.setOrientation(LinearLayout.VERTICAL);
            hc.addView(text(L.t("Cihaz kimliği (HWID)"), 14, TX, true));
            TextView hv = text(RemnaHwid.id(), 12.5f, TX2, false);
            hv.setTypeface(Typeface.MONOSPACE);
            hv.setPadding(0, dp(4), 0, 0);
            hc.addView(hv);
            hw.addView(hc, new LinearLayout.LayoutParams(0, WC, 1));
            hw.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("hwid", RemnaHwid.id()));
                toast(L.t("Kopyalandı"));
            });
            c.addView(hw, cardLp());
            String ver = "";
            try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
            TextView foot = text("Remna VPN " + ver + L.t(" · Xray çekirdeği"), 12, TX3, false);
            foot.setGravity(Gravity.CENTER);
            foot.setPadding(0, dp(24), 0, 0);
            c.addView(foot, lp(MP, WC));
        };
        fill[0].run();
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setStatusBarColor(BG1);
            w.setNavigationBarColor(BG1);
            w.setWindowAnimations(android.R.style.Animation_Dialog);
        }
        d.setOnDismissListener(x -> refreshAll());
        d.show();
    }

    TextView section(String s) {
        TextView t = text(s, 12, TX3, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(22), 0, dp(4));
        return t;
    }

    TextView note(String s) {
        TextView t = text(s, 13, TX2, false);
        t.setPadding(dp(4), dp(8), 0, dp(4));
        return t;
    }

    LinearLayout card() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(14), dp(12), dp(14));
        r.setBackground(roundStroke(CARD, 16, LINE));
        return r;
    }

    LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams l = lp(MP, WC);
        l.topMargin = dp(8);
        return l;
    }

    View action(int icon, String label, View.OnClickListener l) {
        LinearLayout r = card();
        r.addView(new Ui.Icon(this, icon, ACC), lp(dp(22), dp(22)));
        TextView t = text(label, 15, TX, false);
        t.setPadding(dp(14), 0, 0, 0);
        r.addView(t, new LinearLayout.LayoutParams(0, WC, 1));
        r.addView(new Ui.Icon(this, Ui.Icon.CHEVRON, TX3), lp(dp(18), dp(18)));
        r.setOnClickListener(l);
        return r;
    }

    void toast(String s) { ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show()); }

    void status(String s) { ui.post(() -> { serverTv.setText(s); status = s; }); }

    /* ---------------- bağlan / kes ---------------- */
    void toggle() {
        if (state == ON || state == CONNECTING) {
            cancel = true;
            busy = true;
            setState(CONNECTING, L.t("Kesiliyor…"));
            new Thread(() -> {
                stopService();
                busy = false;
                ui.post(() -> setState(OFF, null));
            }).start();
            return;
        }
        if (Ng.serverList().isEmpty()) { toast(L.t("Sunucular yükleniyor…")); ensureSubscription(true); return; }
        if (RemnaExpiry.remaining(this) < 30000) { watchAd(this::toggle); return; }
        Intent i = VpnService.prepare(this);
        if (i != null) startActivityForResult(i, REQ_VPN);
        else connect();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_VPN) {
            if (res == RESULT_OK) connect();
            else toast(L.t("VPN izni verilmedi"));
        }
    }

    void reconnect() {
        Intent i = VpnService.prepare(this);
        if (i != null) startActivityForResult(i, REQ_VPN);
        else connect();
    }

    void connect() {
        if (busy) return;
        busy = true;
        cancel = false;
        setState(CONNECTING, auto() ? L.t("En hızlı sunucu aranıyor…") : currentName());
        new Thread(() -> {
            boolean ok = auto() ? connectAuto() : connectManual();
            busy = false;
            boolean up = ok || isUp();
            ui.post(() -> { setState(up ? ON : OFF, null); refreshAll(); });
        }).start();
    }

    boolean connectManual() {
        String sel = Ng.selected();
        if (sel == null) { List<String> l = Ng.serverList(); if (l.isEmpty()) return false; sel = l.get(0); Ng.select(sel); }
        if (!restart()) { toast(L.t("Bağlanılamadı: ") + Ng.lastErr); return false; }
        int ms = verify();
        pings.put(sel, ms);
        if (ms < 0) toast(L.t("Bağlandı ama internet çalışmıyor — başka sunucu dene"));
        return true;
    }

    boolean connectAuto() {
        List<String> ranked = rank();
        if (ranked.isEmpty()) { toast(L.t("Sunucu yok")); return false; }
        String last = prefs.getString("last_good", null);
        if (last != null && ranked.remove(last)) ranked.add(0, last);
        int tries = 0;
        for (String id : ranked) {
            if (cancel || tries++ >= 8) break;
            Integer tcp = pings.get(id);
            if (tcp != null && tcp < 0 && tries > 1) continue;
            Ng.select(id);
            status(L.t("Deneniyor: ") + Ng.info(id)[0]);
            if (!restart()) continue;
            int ms = verify();
            if (ms > 0) {
                pings.put(id, ms);
                prefs.edit().putString("last_good", id).apply();
                return true;
            }
            pings.put(id, -1);
        }
        if (cancel) return false;
        // hiçbiri doğrulanamadı: en iyi tcp sonucuyla bağlı kal
        Ng.select(ranked.get(0));
        restart();
        toast(isUp() ? L.t("Bağlandı, internet testi başarısız") : L.t("Bağlanılamadı: ") + Ng.lastErr);
        return isUp();
    }

    /** Servisi (yeniden) başlatır ve bağlantının kalkmasını bekler. */
    boolean restart() {
        if (isUp()) { stopService(); }
        CountDownLatch l = new CountDownLatch(1);
        final boolean[] ok = {false};
        ui.post(() -> { ok[0] = Ng.start(this); l.countDown(); });
        try { l.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        if (ok[0] && waitUp(true, 10000)) return true;
        // yedek: VPN servisini doğrudan başlat
        if (!isUp()) {
            ui.post(() -> Ng.startServiceDirect(this));
            if (waitUp(true, 10000)) return true;
        }
        return false;
    }

    void stopService() {
        Ng.stopAll(this);
        waitUp(false, 5000);
    }

    boolean waitUp(boolean up, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (isUp() == up) return true;
            if (cancel && up) return false;
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    Network vpnNetwork() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return n;
            }
        } catch (Exception ignored) {}
        return null;
    }

    boolean isUp() { return portOpen() || vpnNetwork() != null; }

    boolean waitPort(boolean open, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (portOpen() == open) return true;
            if (cancel && open) return false;
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    boolean portOpen() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", Ng.socksPort()), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Tünel üzerinden gerçek gecikme (ms), başarısızsa -1. */
    int verify() {
        String[] urls = {"https://www.gstatic.com/generate_204", "http://cp.cloudflare.com/generate_204"};
        boolean socks = portOpen();
        Network vpn = socks ? null : vpnNetwork();
        for (String u : urls) {
            if (cancel) return -1;
            HttpURLConnection c = null;
            try {
                long t = System.currentTimeMillis();
                URL url = new URL(u);
                if (socks) c = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", Ng.socksPort())));
                else if (vpn != null) c = (HttpURLConnection) vpn.openConnection(url);
                else c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                c.setUseCaches(false);
                int code = c.getResponseCode();
                if (code == 204 || code == 200) return (int) Math.max(1, System.currentTimeMillis() - t);
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return -1;
    }

    /* ---------------- ping ---------------- */
    int tcping(String id) {
        String[] in = Ng.info(id);
        String host = in[1];
        int port;
        try { port = Integer.parseInt(in[2].trim()); } catch (Exception e) { return -2; }
        if (host.isEmpty()) return -2;
        long t = System.currentTimeMillis();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 2500);
            return (int) Math.max(1, System.currentTimeMillis() - t);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Tüm sunucuları paralel TCP ping ile sıralar (ulaşılamayanlar sona). */
    List<String> rank() {
        List<String> ids = Ng.serverList();
        ExecutorService ex = Executors.newFixedThreadPool(16);
        final Map<String, Integer> r = Collections.synchronizedMap(new HashMap<>());
        for (String id : ids) ex.execute(() -> r.put(id, tcping(id)));
        ex.shutdown();
        try { ex.awaitTermination(8, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        for (Map.Entry<String, Integer> e : r.entrySet()) if (e.getValue() != -2) pings.put(e.getKey(), e.getValue());
        List<String> out = new ArrayList<>(ids);
        Collections.sort(out, (a, b) -> Integer.compare(score(r.get(a)), score(r.get(b))));
        ui.post(this::renderList);
        return out;
    }

    static int score(Integer v) { return v == null || v == -2 ? 50000 : v < 0 ? 100000 : v; }

    void pingAll() {
        toast(L.t("Ping test…"));
        new Thread(this::rank).start();
    }

    /* ---------------- abonelik ---------------- */
    void addSubDialog() { addSubDialog(null); }

    void addSubDialog(Runnable after) {
        EditText in = new EditText(this);
        in.setHint("https://panel.example.com/api/sub/…");
        in.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        in.setSingleLine(true);
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                if (t != null && t.toString().trim().startsWith("http")) in.setText(t.toString().trim());
            }
        } catch (Exception ignored) {}
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        f.addView(in);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Abonelik ekle")
                .setMessage("Remnawave abonelik linkini yapıştır (sunucu linkleri de olur).")
                .setView(f)
                .setPositiveButton("Ekle", (d, w) -> addSub(in.getText().toString().trim(), after))
                .setNegativeButton("İptal", null)
                .show();
    }

    void addSub(String url, Runnable after) {
        if (url.isEmpty()) return;
        toast("Ekleniyor…");
        new Thread(() -> {
            boolean ok = Ng.importText(url);
            if (ok && url.startsWith("http")) Ng.updateAll();
            ui.post(() -> {
                refreshAll();
                if (after != null) after.run();
                if (!ok) fallbackAddSub(url);
                else toast(Ng.serverList().isEmpty() ? "Sunucu gelmedi — linki kontrol et" : L.t("Sunucular güncellendi"));
            });
        }).start();
    }

    void fallbackAddSub(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("v2rayng://install-sub?url=" + Uri.encode(url) + "&name=Remna"));
            i.setPackage(getPackageName());
            startActivity(i);
        } catch (Exception e) {
            openClass("com.v2ray.ang.ui.SubSettingActivity");
        }
    }

    void updateSubs() {
        toast(L.t("Abonelik güncelleniyor…"));
        new Thread(() -> {
            boolean ok = Ng.updateAll();
            ui.post(() -> { refreshAll(); toast(ok ? L.t("Güncellendi") : L.t("Güncellenemedi")); });
        }).start();
    }

    /* ---------------- ödüllü video ---------------- */
    void watchAd(Runnable after) {
        if (RemnaExpiry.remaining(this) > RemnaConfig.MAX_BANK_MS - RemnaConfig.REWARD_MS) { toast(L.t("Yeterli süren var")); return; }
        AlertDialog wait = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setMessage(L.t("Video yükleniyor…")).setCancelable(false).create();
        if (!Ads.ready()) wait.show();
        Ads.showRewarded(this, 10000, (ok, err) -> {
            try { wait.dismiss(); } catch (Exception ignored) {}
            if (ok) {
                RemnaExpiry.add(this, RemnaConfig.REWARD_MS);
                toast(L.t("+1 saat eklendi"));
                if (after != null) after.run();
            } else if (err != null && err.contains("yarıda")) {
                toast(L.t("Süre için videoyu sonuna kadar izle"));
            } else {
                // reklam yüklenemedi (ağ engeli vb.): kısa deneme süresi, 3 saatte bir
                long last = prefs.getLong("trial_at", 0);
                if (System.currentTimeMillis() - last > RemnaConfig.TRIAL_COOLDOWN_MS) {
                    prefs.edit().putLong("trial_at", System.currentTimeMillis()).apply();
                    RemnaExpiry.add(this, RemnaConfig.TRIAL_MS);
                    toast(L.t("Reklam yüklenemedi — ") + (RemnaConfig.TRIAL_MS / 60000) + L.t(" dk deneme verildi. Bağlanınca video izleyip süre ekleyebilirsin."));
                    if (after != null) after.run();
                } else toast(L.t("Reklam yüklenemedi: ") + err);
            }
        });
    }

    /* ---------------- gömülü abonelik ---------------- */
    void ensureSubscription(boolean force) {
        new Thread(() -> {
            boolean has = false;
            for (Object[] sub : Ng.subscriptions()) {
                String url = str(Ng.get(sub[1], "url"));
                if (url.equals(RemnaConfig.SUB_URL)) has = true;
                else Ng.removeSubscription((String) sub[0]); // başka abonelikleri kaldır
            }
            if (!has) Ng.importText(RemnaConfig.SUB_URL);
            long last = prefs.getLong("sub_updated", 0);
            if (force || !has || Ng.serverList().isEmpty() || System.currentTimeMillis() - last > 6 * 3600000L) {
                if (Ng.updateAll()) prefs.edit().putLong("sub_updated", System.currentTimeMillis()).apply();
            }
            ui.post(() -> { refreshAll(); if (force) toast(L.t("Sunucular güncellendi")); });
        }).start();
    }

    /* ---------------- çökme kaydı ---------------- */
    void installCrashHandler() {
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String st = sw.toString();
                getSharedPreferences("remna", MODE_PRIVATE).edit().putString("crash", st.length() > 3000 ? st.substring(0, 3000) : st).commit();
            } catch (Throwable ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    void showPreviousCrash() {
        String c = prefs.getString("crash", null);
        if (c == null) return;
        prefs.edit().remove("crash").apply();
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(L.t("Önceki çökme"))
                .setMessage(c)
                .setPositiveButton(L.t("Kopyala"), (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("crash", c));
                })
                .setNegativeButton(L.t("Kapat"), null).show();
    }

    String diagText() {
        StringBuilder b = new StringBuilder();
        b.append("Sunucu: ").append(Ng.serverList().size()).append(" · Seçili: ").append(Ng.info(String.valueOf(Ng.selected()))[0]).append("\n");
        b.append("SOCKS ").append(Ng.socksPort()).append(portOpen() ? " açık" : " kapalı").append(" · VPN ağı: ").append(vpnNetwork() != null ? "var" : "yok").append("\n");
        String mc = Ng.managerClass(this);
        b.append("Yönetici: ").append(mc).append("\nVPN servisi: ").append(Ng.vpnServiceClass(this)).append("\n");
        b.append("Son hata: ").append(Ng.lastErr).append("\n");
        b.append("Çekirdek hazırlığı: ").append(coreInit).append("\n");
        return b.toString();
    }

    String readLog(boolean all) {
        StringBuilder out = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-t", "2000"});
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            java.util.ArrayDeque<String> keep = new java.util.ArrayDeque<>();
            java.util.regex.Pattern want = java.util.regex.Pattern.compile("(?i)(v2ray|xray|libv2ray|hev|tun2socks|vpnservice|com\\.v2ray|remna|AndroidRuntime|FATAL|Exception|failed)");
            String line;
            while ((line = r.readLine()) != null) {
                if (!all && !want.matcher(line).find()) continue;
                if (line.contains("chromium")) continue;
                keep.add(line);
                if (keep.size() > 300) keep.poll();
            }
            for (String l : keep) out.append(l).append('\n');
        } catch (Exception e) {
            out.append("logcat okunamadı: ").append(e.getMessage());
        }
        return out.length() == 0 ? "(kayıt yok)" : out.toString();
    }

    /** Ayarlar → Günlük: tanılama + filtrelenmiş logcat, kopyalanabilir. */
    void openLog() {
        Dialog d = new Dialog(this, android.R.style.Theme_Material_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(BG1);
        box.setFitsSystemWindows(true);
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(20), dp(14), dp(20), dp(10));
        h.addView(text(L.t("Günlük"), 20, TX, true), new LinearLayout.LayoutParams(0, WC, 1));
        h.addView(iconBtn(Ui.Icon.CLOSE, 40, v -> d.dismiss()), lp(dp(40), dp(40)));
        box.addView(h);
        TextView body = text(L.t("Yükleniyor…"), 11, TX2, false);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(dp(16), dp(8), dp(16), dp(24));
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        box.addView(sv, new LinearLayout.LayoutParams(MP, 0, 1));
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(16), dp(8), dp(16), dp(12));
        final String[] cur = {""};
        final boolean[] all = {false};
        Runnable load = () -> new Thread(() -> {
            String t = "== TANILAMA ==\n" + diagText() + "\n== LOG" + (all[0] ? " (tümü)" : " (filtreli)") + " ==\n" + readLog(all[0]);
            cur[0] = t;
            ui.post(() -> { body.setText(t); sv.post(() -> sv.fullScroll(View.FOCUS_DOWN)); });
        }).start();
        TextView copy = text(L.t("Kopyala"), 14, Color.WHITE, true);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(dp(12), dp(12), dp(12), dp(12));
        copy.setBackground(round(0xFF0891B2, 14));
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("remna-log", cur[0]));
            toast(L.t("Kopyalandı — sohbete yapıştır"));
        });
        bar.addView(copy, new LinearLayout.LayoutParams(0, WC, 1));
        TextView ref = text(L.t("Yenile"), 14, TX, true);
        ref.setGravity(Gravity.CENTER);
        ref.setPadding(dp(12), dp(12), dp(12), dp(12));
        ref.setBackground(roundStroke(CARD, 14, LINE));
        ref.setOnClickListener(v -> { body.setText(L.t("Yükleniyor…")); load.run(); });
        LinearLayout.LayoutParams rl2 = new LinearLayout.LayoutParams(0, WC, 1);
        rl2.leftMargin = dp(10);
        bar.addView(ref, rl2);
        TextView allB = text(L.t("Tümü"), 14, TX, true);
        allB.setGravity(Gravity.CENTER);
        allB.setPadding(dp(12), dp(12), dp(12), dp(12));
        allB.setBackground(roundStroke(CARD, 14, LINE));
        allB.setOnClickListener(v -> { all[0] = !all[0]; allB.setText(all[0] ? L.t("Filtreli") : L.t("Tümü")); body.setText(L.t("Yükleniyor…")); load.run(); });
        LinearLayout.LayoutParams al2 = new LinearLayout.LayoutParams(0, WC, 1);
        al2.leftMargin = dp(10);
        bar.addView(allB, al2);
        box.addView(bar);
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) { w.setStatusBarColor(BG1); w.setNavigationBarColor(BG1); }
        d.show();
        load.run();
    }

    /** Uygulamanın kendi logcat kayıtları (VPN süreci dahil, aynı UID) — izin gerektirmez. */
    void copyLog(String head) {
        toast("Log toplanıyor…");
        new Thread(() -> {
            StringBuilder out = new StringBuilder(head).append("\n---- LOG ----\n");
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-t", "1500"});
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                java.util.ArrayDeque<String> keep = new java.util.ArrayDeque<>();
                String line;
                java.util.regex.Pattern want = java.util.regex.Pattern.compile("(?i)(v2ray|xray|libv2ray|hev|tun2socks|vpn|ang|remna|AndroidRuntime|FATAL|Exception|error| E/| W/)");
                while ((line = r.readLine()) != null) {
                    if (!want.matcher(line).find()) continue;
                    if (line.contains("chromium") || line.contains("Ads") && !line.contains("Exception")) continue;
                    keep.add(line);
                    if (keep.size() > 220) keep.poll();
                }
                for (String l : keep) out.append(l).append('\n');
            } catch (Exception e) {
                out.append("logcat okunamadı: ").append(e.getMessage());
            }
            String txt = out.length() > 60000 ? out.substring(out.length() - 60000) : out.toString();
            ui.post(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("remna-log", txt));
                toast("Log kopyalandı (" + txt.length() / 1024 + " KB) — sohbete yapıştır");
            });
        }).start();
    }

    /** Başlığa uzun basınca: v2rayNG köprüsünün durumunu gösterir. */
    void diag() {
        StringBuilder b = new StringBuilder();
        b.append("Sunucu sayısı: ").append(Ng.serverList().size()).append("\n");
        b.append("Abonelik: ").append(Ng.subscriptions().size()).append("\n");
        b.append("Seçili: ").append(Ng.selected()).append("\n");
        b.append("SOCKS port: ").append(Ng.socksPort()).append(portOpen() ? " (açık)" : " (kapalı)").append("\n");
        b.append("VPN ağı: ").append(vpnNetwork() != null ? "var" : "yok").append("\n");
        b.append("Son hata: ").append(Ng.lastErr).append("\n");
        b.append("Reklam: ").append(Ads.ready() ? "hazır" : "yok " + Ads.error()).append("\n");
        b.append("Kalan süre (sn): ").append(RemnaExpiry.remaining(this) / 1000).append("\n");
        String mc = Ng.managerClass(this);
        b.append("Yönetici: ").append(mc).append("\n");
        b.append("VPN servisi: ").append(Ng.vpnServiceClass(this)).append("\n");
        b.append("Servis metotları: ").append(mc == null ? "-" : Ng.methods(new String[]{mc})).append("\n");
        for (String[] cls : new String[][]{Ng.MMKV, Ng.SERVICE, Ng.CONFIG, Ng.SETTINGS}) {
            String found = "YOK";
            for (String c : cls) { try { Class.forName(c); found = c; break; } catch (Exception ignored) {} }
            b.append(found).append("\n");
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Tanılama").setMessage(b.toString())
                .setPositiveButton(L.t("Tamam"), null)
                .setNeutralButton("Logu kopyala", (d, w) -> copyLog(b.toString()))
                .show();
    }

    void openAdvanced() { openClass("com.v2ray.ang.ui.MainActivity"); }

    void openClass(String cls) {
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), cls);
            startActivity(i);
        } catch (Exception e) {
            toast(L.t("Açılamadı"));
        }
    }
}

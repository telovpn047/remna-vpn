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
    TextView stateTv, serverTv, timerTv, autoSub, countTv;
    Ui.Switch autoSw;
    LinearLayout list;

    /* ---------------- yaşam döngüsü ---------------- */
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("remna", MODE_PRIVATE);
        Window w = getWindow();
        w.setStatusBarColor(BG1);
        w.setNavigationBarColor(PANEL);
        setContentView(build());
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
            long since = prefs.getLong("on_since", 0);
            if (state == ON && since > 0) {
                long t = (System.currentTimeMillis() - since) / 1000;
                timerTv.setText(String.format(java.util.Locale.US, "%02d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60));
                timerTv.setVisibility(View.VISIBLE);
            } else timerTv.setVisibility(View.INVISIBLE);
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
        View add = iconBtn(Ui.Icon.PLUS, 40, v -> addSubDialog());
        top.addView(add, lp(dp(40), dp(40)));
        View gear = iconBtn(Ui.Icon.GEAR, 40, v -> openSettings());
        LinearLayout.LayoutParams gl = lp(dp(40), dp(40));
        gl.leftMargin = dp(10);
        top.addView(gear, gl);
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
        ac.addView(text("Otomatik seçim", 15.5f, TX, true));
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
        root.addView(center, new LinearLayout.LayoutParams(MP, 0, 1.15f));

        // ---- sunucu paneli
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(PANEL);
        float r = dp(26);
        pb.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(pb);
        panel.setPadding(dp(16), dp(16), dp(16), 0);
        LinearLayout ph = new LinearLayout(this);
        ph.setGravity(Gravity.CENTER_VERTICAL);
        ph.setPadding(dp(4), 0, 0, dp(6));
        ph.addView(text("Sunucular", 16, TX, true));
        countTv = text("0", 12, TX2, true);
        countTv.setPadding(dp(8), dp(3), dp(8), dp(3));
        countTv.setBackground(round(0x1AFFFFFF, 10));
        LinearLayout.LayoutParams cl = lp(WC, WC);
        cl.leftMargin = dp(8);
        ph.addView(countTv, cl);
        ph.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        ph.addView(iconBtn(Ui.Icon.SIGNAL, 36, v -> pingAll()), lp(dp(36), dp(36)));
        View rf = iconBtn(Ui.Icon.REFRESH, 36, v -> updateSubs());
        LinearLayout.LayoutParams rl = lp(dp(36), dp(36));
        rl.leftMargin = dp(8);
        ph.addView(rf, rl);
        panel.addView(ph);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(16));
        sv.addView(list);
        panel.addView(sv, new LinearLayout.LayoutParams(MP, 0, 1));
        root.addView(panel, new LinearLayout.LayoutParams(MP, 0, 1f));

        autoSw.set(auto(), false);
        setState(OFF, null);
        return frame;
    }

    boolean auto() { return prefs.getBoolean("auto", true); }

    void renderAuto() {
        autoSw.set(auto(), false);
        if (auto()) {
            String last = prefs.getString("last_good", null);
            autoSub.setText(state == ON && last != null ? "Seçilen: " + Ng.info(last)[0] : "En hızlı çalışan sunucu otomatik seçilir");
        } else autoSub.setText("Kapalı — listeden sunucu seç");
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

    void renderList() {
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
            TextView t1 = text("Henüz sunucu yok", 15, TX, true);
            t1.setPadding(0, dp(12), 0, dp(6));
            e.addView(t1);
            TextView t2 = text("Abonelik linkini eklemek için + düğmesine dokun", 13, TX2, false);
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
            TextView s = text(meta.toString(), 12, TX3, false);
            s.setPadding(0, dp(4), 0, 0);
            s.setSingleLine(true);
            c.addView(s);
            row.addView(c, new LinearLayout.LayoutParams(0, WC, 1));

            Integer ms = pings.get(id);
            if (ms != null) {
                int col = ms < 0 ? RED : ms < 300 ? GREEN : ms < 800 ? AMBER : RED;
                TextView pt = text(ms < 0 ? "Yok" : ms + " ms", 12, col, true);
                pt.setPadding(dp(9), dp(4), dp(9), dp(4));
                pt.setBackground(round((col & 0x00FFFFFF) | 0x22000000, 10));
                row.addView(pt);
            }
            final String gid = id;
            row.setOnClickListener(v -> {
                prefs.edit().putBoolean("auto", false).apply();
                Ng.select(gid);
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
        stateTv.setText(s == ON ? "Korunuyor" : s == CONNECTING ? "Bağlanıyor…" : "Bağlı değil");
        stateTv.setTextColor(s == ON ? GREEN : s == CONNECTING ? AMBER : TX);
        if (msg != null) status = msg;
        else if (s == ON) status = currentName();
        else if (s == OFF) status = auto() ? "Bağlanmak için dokun" : currentName();
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
        h.addView(text("Ayarlar", 20, TX, true), new LinearLayout.LayoutParams(0, WC, 1));
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
            c.addView(section("ABONELİKLER"));
            List<Object[]> subs = Ng.subscriptions();
            if (subs.isEmpty()) c.addView(note("Henüz abonelik yok."));
            for (Object[] sub : subs) {
                String id = (String) sub[0];
                String nm = str(Ng.get(sub[1], "remarks"));
                String url = str(Ng.get(sub[1], "url"));
                LinearLayout row = card();
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView t = text(nm.isEmpty() ? "Abonelik" : nm, 15, TX, true);
                col.addView(t);
                TextView u = text(url, 12, TX3, false);
                u.setSingleLine(true);
                u.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                u.setPadding(0, dp(4), 0, 0);
                col.addView(u);
                row.addView(col, new LinearLayout.LayoutParams(0, WC, 1));
                View del = iconBtn(Ui.Icon.TRASH, 36, v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage("\"" + (nm.isEmpty() ? url : nm) + "\" ve sunucuları silinsin mi?")
                        .setPositiveButton("Sil", (x, y) -> { if (!Ng.removeSubscription(id)) toast("Silinemedi"); fill[0].run(); refreshAll(); })
                        .setNegativeButton("Vazgeç", null).show());
                row.addView(del, lp(dp(36), dp(36)));
                c.addView(row, cardLp());
            }
            c.addView(action(Ui.Icon.PLUS, "Abonelik ekle", v -> addSubDialog(() -> fill[0].run())), cardLp());
            c.addView(action(Ui.Icon.REFRESH, "Tümünü güncelle", v -> updateSubs()), cardLp());

            c.addView(section("BAĞLANTI"));
            c.addView(action(Ui.Icon.SHIELD, "Uygulama bazlı VPN", v -> openClass("com.v2ray.ang.ui.PerAppProxyActivity")), cardLp());
            c.addView(action(Ui.Icon.LINK, "Yönlendirme kuralları", v -> openClass("com.v2ray.ang.ui.RoutingSettingActivity")), cardLp());
            c.addView(action(Ui.Icon.GEAR, "Gelişmiş ayarlar", v -> openClass("com.v2ray.ang.ui.SettingsActivity")), cardLp());

            c.addView(section("CİHAZ"));
            LinearLayout hw = card();
            LinearLayout hc = new LinearLayout(this);
            hc.setOrientation(LinearLayout.VERTICAL);
            hc.addView(text("Cihaz kimliği (HWID)", 14, TX, true));
            TextView hv = text(RemnaHwid.id(), 12.5f, TX2, false);
            hv.setTypeface(Typeface.MONOSPACE);
            hv.setPadding(0, dp(4), 0, 0);
            hc.addView(hv);
            hw.addView(hc, new LinearLayout.LayoutParams(0, WC, 1));
            hw.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("hwid", RemnaHwid.id()));
                toast("Kopyalandı");
            });
            c.addView(hw, cardLp());
            String ver = "";
            try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
            TextView foot = text("Remna VPN " + ver + " · Xray çekirdeği", 12, TX3, false);
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
            setState(CONNECTING, "Kesiliyor…");
            new Thread(() -> {
                stopService();
                busy = false;
                ui.post(() -> setState(OFF, null));
            }).start();
            return;
        }
        if (Ng.serverList().isEmpty()) { addSubDialog(); return; }
        Intent i = VpnService.prepare(this);
        if (i != null) startActivityForResult(i, REQ_VPN);
        else connect();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_VPN) {
            if (res == RESULT_OK) connect();
            else toast("VPN izni verilmedi");
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
        setState(CONNECTING, auto() ? "En hızlı sunucu aranıyor…" : currentName());
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
        if (!restart()) { toast("Bağlanılamadı: " + Ng.lastErr); return false; }
        int ms = verify();
        pings.put(sel, ms);
        if (ms < 0) toast("Bağlandı ama internet çalışmıyor — başka sunucu dene");
        return true;
    }

    boolean connectAuto() {
        List<String> ranked = rank();
        if (ranked.isEmpty()) { toast("Sunucu yok"); return false; }
        String last = prefs.getString("last_good", null);
        if (last != null && ranked.remove(last)) ranked.add(0, last);
        int tries = 0;
        for (String id : ranked) {
            if (cancel || tries++ >= 8) break;
            Integer tcp = pings.get(id);
            if (tcp != null && tcp < 0 && tries > 1) continue;
            Ng.select(id);
            status("Deneniyor: " + Ng.info(id)[0]);
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
        toast(isUp() ? "Bağlandı, internet testi başarısız" : "Bağlanılamadı: " + Ng.lastErr);
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
        // yedek: v2rayNG kısayol aktivitesi (aç/kapat)
        if (!isUp()) {
            ui.post(() -> Ng.toggleViaShortcut(this));
            if (waitUp(true, 10000)) { Ng.lastErr += " | kısayol ile açıldı"; return true; }
        }
        return false;
    }

    void stopService() {
        Ng.stop(this);
        if (!waitUp(false, 4000)) {
            ui.post(() -> Ng.toggleViaShortcut(this));
            waitUp(false, 4000);
        }
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
        toast("Ping test…");
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
                else toast(Ng.serverList().isEmpty() ? "Sunucu gelmedi — linki kontrol et" : "Sunucular güncellendi");
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
        toast("Abonelik güncelleniyor…");
        new Thread(() -> {
            boolean ok = Ng.updateAll();
            ui.post(() -> { refreshAll(); toast(ok ? "Güncellendi" : "Güncellenemedi"); });
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
        b.append("Servis metotları: ").append(Ng.methods(Ng.SERVICE)).append("\n");
        for (String[] cls : new String[][]{Ng.MMKV, Ng.SERVICE, Ng.CONFIG, Ng.SETTINGS}) {
            String found = "YOK";
            for (String c : cls) { try { Class.forName(c); found = c; break; } catch (Exception ignored) {} }
            b.append(found).append("\n");
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Tanılama").setMessage(b.toString()).setPositiveButton("Tamam", null).show();
    }

    void openAdvanced() { openClass("com.v2ray.ang.ui.MainActivity"); }

    void openClass(String cls) {
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), cls);
            startActivity(i);
        } catch (Exception e) {
            toast("Açılamadı");
        }
    }
}

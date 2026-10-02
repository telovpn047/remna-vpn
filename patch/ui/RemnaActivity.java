package com.v2ray.ang.remna;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
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

/** Remna VPN ana ekranı: üstte Otomatik, ortada büyük bağlan düğmesi, altta sunucu listesi. */
public class RemnaActivity extends Activity {
    static final int OFF = 0, CONNECTING = 1, ON = 2;
    static final int REQ_VPN = 41, REQ_NOTIF = 42;

    static final int BG1 = 0xFF0B1020, BG2 = 0xFF1A1240, CARD = 0x1AFFFFFF, CARD_SEL = 0x3322D3EE;
    static final int ACC1 = 0xFF22D3EE, ACC2 = 0xFF8B5CF6, GREEN = 0xFF22C55E, AMBER = 0xFFF59E0B, RED = 0xFFEF4444;
    static final int TX = 0xFFFFFFFF, TX2 = 0xB3FFFFFF, TX3 = 0x80FFFFFF;

    final Handler ui = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    int state = OFF;
    volatile boolean busy = false, cancel = false;
    final Map<String, Integer> pings = new HashMap<>();
    String status = "";

    FrameLayout btnWrap;
    View ring;
    TextView btn, stateTv, serverTv, autoCheck;
    LinearLayout autoCard, list;
    ObjectAnimator pulse;

    /* ---------------- yaşam döngüsü ---------------- */
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("remna", MODE_PRIVATE);
        Window w = getWindow();
        w.setStatusBarColor(BG1);
        w.setNavigationBarColor(BG2);
        setContentView(build());
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderList();
        renderAuto();
        poll.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(poll);
    }

    final Runnable poll = new Runnable() {
        @Override
        public void run() {
            ui.removeCallbacks(this);
            if (!busy) {
                new Thread(() -> {
                    boolean up = portOpen();
                    ui.post(() -> { if (!busy) setState(up ? ON : OFF, null); });
                }).start();
            }
            ui.postDelayed(this, 2000);
        }
    };

    /* ---------------- arayüz ---------------- */
    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    TextView iconBtn(String s, View.OnClickListener l) {
        TextView t = text(s, 20, TX, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(CARD, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.leftMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        return t;
    }

    View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{BG1, BG2});
        root.setBackground(bg);
        root.setPadding(dp(16), dp(10), dp(16), 0);

        // üst çubuk
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Remna VPN", 22, TX, true);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        title.setOnLongClickListener(v -> { diag(); return true; });
        top.addView(iconBtn("+", v -> addSubDialog()));
        top.addView(iconBtn("⟳", v -> updateSubs()));
        top.addView(iconBtn("⚙", v -> openAdvanced()));
        root.addView(top);

        // otomatik kartı
        autoCard = new LinearLayout(this);
        autoCard.setGravity(Gravity.CENTER_VERTICAL);
        autoCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView bolt = text("⚡", 22, ACC1, false);
        autoCard.addView(bolt);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        col.addView(text("Otomatik", 16, TX, true));
        col.addView(text("En hızlı çalışan sunucuyu kendisi seçer", 12.5f, TX2, false));
        autoCard.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        autoCheck = text("", 18, ACC1, true);
        autoCard.addView(autoCheck);
        autoCard.setOnClickListener(v -> {
            prefs.edit().putBoolean("auto", true).apply();
            renderAuto();
            renderList();
            if (state == ON) reconnect();
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(14);
        root.addView(autoCard, alp);

        // büyük düğme
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        center.setPadding(0, dp(22), 0, dp(14));
        btnWrap = new FrameLayout(this);
        ring = new View(this);
        btnWrap.addView(ring, new FrameLayout.LayoutParams(dp(196), dp(196), Gravity.CENTER));
        btn = text("⏻", 58, TX, false);
        btn.setGravity(Gravity.CENTER);
        btn.setElevation(dp(8));
        btnWrap.addView(btn, new FrameLayout.LayoutParams(dp(158), dp(158), Gravity.CENTER));
        btn.setOnClickListener(v -> toggle());
        center.addView(btnWrap, new LinearLayout.LayoutParams(dp(210), dp(210)));
        stateTv = text("", 20, TX, true);
        stateTv.setGravity(Gravity.CENTER);
        stateTv.setPadding(0, dp(10), 0, 0);
        center.addView(stateTv);
        serverTv = text("", 13.5f, TX2, false);
        serverTv.setGravity(Gravity.CENTER);
        serverTv.setMaxLines(2);
        center.addView(serverTv);
        root.addView(center);

        // sunucular başlığı
        LinearLayout lh = new LinearLayout(this);
        lh.setGravity(Gravity.CENTER_VERTICAL);
        TextView lt = text("SUNUCULAR", 12.5f, TX3, true);
        lt.setLetterSpacing(0.08f);
        lh.addView(lt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView pingAll = text("Ping test", 13, ACC1, true);
        pingAll.setPadding(dp(10), dp(6), dp(4), dp(6));
        pingAll.setOnClickListener(v -> pingAll());
        lh.addView(pingAll);
        root.addView(lh);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(20));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setState(OFF, null);
        return root;
    }

    boolean auto() { return prefs.getBoolean("auto", true); }

    void renderAuto() {
        boolean a = auto();
        GradientDrawable g = round(a ? CARD_SEL : CARD, 18);
        if (a) g.setStroke(dp(1.5f), ACC1);
        autoCard.setBackground(g);
        autoCheck.setText(a ? "✓" : "");
    }

    void renderList() {
        list.removeAllViews();
        List<String> ids = Ng.serverList();
        if (ids.isEmpty()) {
            TextView e = text("Henüz sunucu yok.\n＋ ile Remnawave abonelik linkini ekle.", 14, TX2, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(16), dp(28), dp(16), dp(28));
            list.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        String sel = Ng.selected();
        boolean a = auto();
        for (String id : ids) {
            Object p = Ng.profile(id);
            String name = str(Ng.get(p, "remarks"));
            if (TextUtils.isEmpty(name)) name = str(Ng.get(p, "server"));
            Object type = Ng.get(p, "configType");
            String sub = (type == null ? "" : type.toString()) + (TextUtils.isEmpty(str(Ng.get(p, "server"))) ? "" : " · " + str(Ng.get(p, "server")));

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(11), dp(14), dp(11));
            boolean isSel = !a && id.equals(sel);
            GradientDrawable g = round(isSel ? CARD_SEL : CARD, 14);
            if (isSel) g.setStroke(dp(1.5f), ACC1);
            row.setBackground(g);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            TextView n = text(name, 15, TX, true);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(n);
            TextView s = text(sub, 12, TX3, false);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            c.addView(s);
            row.addView(c, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Integer ms = pings.get(id);
            TextView pt = text(ms == null ? "" : ms < 0 ? "✕" : ms + " ms", 13, ms == null ? TX3 : ms < 0 ? RED : ms < 300 ? GREEN : ms < 800 ? AMBER : RED, true);
            pt.setPadding(dp(10), 0, 0, 0);
            row.addView(pt);
            final String gid = id;
            row.setOnClickListener(v -> {
                prefs.edit().putBoolean("auto", false).apply();
                Ng.select(gid);
                renderAuto();
                renderList();
                if (state == ON) reconnect();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            list.addView(row, lp);
        }
    }

    static String str(Object o) { return o == null ? "" : o.toString(); }

    void setState(int s, String msg) {
        state = s;
        int c1, c2;
        String label;
        if (s == ON) { c1 = GREEN; c2 = 0xFF0EA5E9; label = "Bağlandı"; }
        else if (s == CONNECTING) { c1 = AMBER; c2 = 0xFFF97316; label = "Bağlanıyor…"; }
        else { c1 = ACC1; c2 = ACC2; label = "Bağlı değil"; }
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setShape(GradientDrawable.OVAL);
        btn.setBackground(g);
        GradientDrawable r = new GradientDrawable();
        r.setShape(GradientDrawable.OVAL);
        r.setColor((c1 & 0x00FFFFFF) | 0x22000000);
        r.setStroke(dp(2), (c1 & 0x00FFFFFF) | 0x66000000);
        ring.setBackground(r);
        stateTv.setText(label);
        stateTv.setTextColor(s == OFF ? TX : c1);
        if (msg != null) status = msg;
        else if (s == ON) status = currentName();
        else if (s == OFF) status = auto() ? "Otomatik mod" : currentName();
        serverTv.setText(status);
        if (s == CONNECTING) {
            if (pulse == null) {
                pulse = ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, 1.12f);
                pulse.setDuration(700);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.setRepeatMode(ValueAnimator.REVERSE);
                pulse.addUpdateListener(a -> ring.setScaleY(ring.getScaleX()));
                pulse.start();
            }
        } else if (pulse != null) {
            pulse.cancel();
            pulse = null;
            ring.setScaleX(1f);
            ring.setScaleY(1f);
        }
    }

    String currentName() {
        String sel = Ng.selected();
        if (sel == null) return "";
        Object p = Ng.profile(sel);
        String n = str(Ng.get(p, "remarks"));
        Integer ms = pings.get(sel);
        return n + (ms != null && ms > 0 ? "  ·  " + ms + " ms" : "");
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
                Ng.stop(this);
                waitPort(false, 4000);
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
            ui.post(() -> { renderList(); setState(ok ? ON : (portOpen() ? ON : OFF), null); });
        }).start();
    }

    boolean connectManual() {
        String sel = Ng.selected();
        if (sel == null) { List<String> l = Ng.serverList(); if (l.isEmpty()) return false; sel = l.get(0); Ng.select(sel); }
        if (!restart()) { toast("Bağlanılamadı"); return false; }
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
            status("Deneniyor: " + str(Ng.get(Ng.profile(id), "remarks")));
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
        toast("Hiçbir sunucu doğrulanamadı");
        return portOpen();
    }

    /** Servisi (yeniden) başlatır ve yerel SOCKS portunun açılmasını bekler. */
    boolean restart() {
        if (portOpen()) { Ng.stop(this); waitPort(false, 4000); }
        CountDownLatch l = new CountDownLatch(1);
        final boolean[] ok = {false};
        ui.post(() -> { ok[0] = Ng.start(this); l.countDown(); });
        try { l.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        if (!ok[0]) return false;
        return waitPort(true, 10000);
    }

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

    /** Proxy üzerinden gerçek gecikme (ms), başarısızsa -1. */
    int verify() {
        String[] urls = {"https://www.gstatic.com/generate_204", "http://cp.cloudflare.com/generate_204"};
        for (int attempt = 0; attempt < 1; attempt++) {
            for (String u : urls) {
                if (cancel) return -1;
                HttpURLConnection c = null;
                try {
                    Proxy p = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", Ng.socksPort()));
                    long t = System.currentTimeMillis();
                    c = (HttpURLConnection) new URL(u).openConnection(p);
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setUseCaches(false);
                    int code = c.getResponseCode();
                    if (code == 204 || code == 200) return (int) Math.max(1, System.currentTimeMillis() - t);
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }
        return -1;
    }

    /* ---------------- ping ---------------- */
    int tcping(String id) {
        Object p = Ng.profile(id);
        String host = str(Ng.get(p, "server"));
        int port;
        try { port = Integer.parseInt(str(Ng.get(p, "serverPort")).trim()); } catch (Exception e) { return -2; }
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
    void addSubDialog() {
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
        new AlertDialog.Builder(this)
                .setTitle("Abonelik ekle")
                .setMessage("Remnawave abonelik linkini yapıştır (sunucu linkleri de olur).")
                .setView(f)
                .setPositiveButton("Ekle", (d, w) -> addSub(in.getText().toString().trim()))
                .setNegativeButton("İptal", null)
                .show();
    }

    void addSub(String url) {
        if (url.isEmpty()) return;
        toast("Ekleniyor…");
        new Thread(() -> {
            boolean ok = Ng.importText(url);
            if (ok && url.startsWith("http")) Ng.updateAll();
            ui.post(() -> {
                renderList();
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
            ui.post(() -> { renderList(); toast(ok ? "Güncellendi" : "Güncellenemedi"); });
        }).start();
    }

    /** Başlığa uzun basınca: v2rayNG köprüsünün durumunu gösterir. */
    void diag() {
        StringBuilder b = new StringBuilder();
        b.append("Sunucu sayısı: ").append(Ng.serverList().size()).append("\n");
        b.append("Seçili: ").append(Ng.selected()).append("\n");
        b.append("SOCKS port: ").append(Ng.socksPort()).append(portOpen() ? " (açık)" : " (kapalı)").append("\n");
        for (String[] cls : new String[][]{Ng.MMKV, Ng.SERVICE, Ng.CONFIG, Ng.SETTINGS}) {
            String found = "YOK";
            for (String c : cls) { try { Class.forName(c); found = c; break; } catch (Exception ignored) {} }
            b.append(found).append("\n");
        }
        new AlertDialog.Builder(this).setTitle("Tanılama").setMessage(b.toString()).setPositiveButton("Tamam", null).show();
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

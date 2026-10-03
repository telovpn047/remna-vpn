package com.v2ray.ang.remna;

import android.content.Context;
import android.os.Build;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * v2rayNG iç API'lerine yansıma (reflection) ile erişir. Böylece v2rayNG sürüm değiştirdiğinde
 * derleme bozulmaz; birkaç olası sınıf/metot adı sırayla denenir.
 */
final class Ng {
    private Ng() {}

    static final String[] MMKV = {"com.v2ray.ang.handler.MmkvManager", "com.v2ray.ang.util.MmkvManager"};
    static final String[] SERVICE = {"com.v2ray.ang.handler.V2RayServiceManager", "com.v2ray.ang.service.V2RayServiceManager"};
    static final String[] CONFIG = {"com.v2ray.ang.handler.AngConfigManager", "com.v2ray.ang.util.AngConfigManager"};
    static final String[] SETTINGS = {"com.v2ray.ang.handler.SettingsManager", "com.v2ray.ang.util.SettingsManager"};

    static Object call(String[] classes, String[] methods, Object... args) throws Exception {
        Exception last = null;
        for (String cn : classes) {
            Class<?> c;
            try { c = Class.forName(cn); } catch (ClassNotFoundException e) { continue; }
            Object inst = null;
            try { Field f = c.getField("INSTANCE"); inst = f.get(null); } catch (Exception ignored) {}
            for (String mn : methods) {
                for (Method m : c.getMethods()) {
                    if (!m.getName().equals(mn) || m.getParameterTypes().length != args.length) continue;
                    if (!compatible(m.getParameterTypes(), args)) continue;
                    try {
                        return m.invoke(Modifier.isStatic(m.getModifiers()) ? null : inst, args);
                    } catch (Exception e) { last = e; }
                }
            }
        }
        throw last != null ? last : new NoSuchMethodException(methods[0]);
    }

    private static boolean compatible(Class<?>[] p, Object[] a) {
        for (int i = 0; i < p.length; i++) {
            if (a[i] == null) { if (p[i].isPrimitive()) return false; continue; }
            Class<?> t = p[i];
            if (t.isPrimitive()) {
                if (t == boolean.class && !(a[i] instanceof Boolean)) return false;
                if (t == int.class && !(a[i] instanceof Integer)) return false;
                if (t == long.class && !(a[i] instanceof Long)) return false;
                continue;
            }
            if (!t.isInstance(a[i])) return false;
        }
        return true;
    }

    static Object get(Object o, String name) {
        if (o == null) return null;
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String g : new String[]{"get" + cap, "is" + cap, name}) {
            try { return o.getClass().getMethod(g).invoke(o); } catch (Exception ignored) {}
        }
        try { Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch (Exception ignored) {}
        return null;
    }

    /* ---- sunucular ---- */
    static Class<?> cls(String[] names) {
        for (String n : names) { try { return Class.forName(n); } catch (Exception ignored) {} }
        return null;
    }

    static Object inst(Class<?> c) {
        try { return c.getField("INSTANCE").get(null); } catch (Exception e) { return null; }
    }

    static Object invoke(Class<?> c, Method m, Object... a) throws Exception {
        return m.invoke(Modifier.isStatic(m.getModifiers()) ? null : inst(c), a);
    }

    @SuppressWarnings("unchecked")
    static void addAll(java.util.LinkedHashSet<String> out, Object r) {
        if (r instanceof java.util.Collection) for (Object o : (java.util.Collection<Object>) r) if (o instanceof String) out.add((String) o);
    }

    /** Tüm sunucu GUID'leri: v2rayNG sürümüne göre farklı yollar denenir. */
    static List<String> serverList() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        Class<?> c = cls(MMKV);
        if (c == null) return new ArrayList<>();
        // 1) parametresiz decode*ServerList()
        for (Method m : c.getMethods()) {
            String n = m.getName().toLowerCase();
            if (m.getParameterTypes().length == 0 && n.startsWith("decode") && n.contains("server") && n.endsWith("list")) {
                try { addAll(out, invoke(c, m)); } catch (Exception ignored) {}
            }
        }
        // 2) abonelik başına decode*ServerList(subId)
        for (String sub : subscriptionIds()) {
            for (Method m : c.getMethods()) {
                String n = m.getName().toLowerCase();
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && p[0] == String.class && n.startsWith("decode") && n.contains("server") && n.endsWith("list")) {
                    try { addAll(out, invoke(c, m, sub)); } catch (Exception ignored) {}
                }
            }
        }
        // 3) MMKV deposundaki profil anahtarları
        if (out.isEmpty()) {
            for (String id : storeIds(c, "PROFILE", "SERVER_CONFIG")) {
                Object kv = mmkv(id);
                try {
                    String[] keys = (String[]) kv.getClass().getMethod("allKeys").invoke(kv);
                    if (keys != null) for (String k : keys) out.add(k);
                } catch (Exception ignored) {}
            }
        }
        List<String> r = new ArrayList<>();
        for (String g : out) if (profile(g) != null || !json(g).isEmpty()) r.add(g);
        return r;
    }

    static List<Object[]> subscriptions() {
        List<Object[]> out = new ArrayList<>();
        Class<?> c = cls(MMKV);
        if (c == null) return out;
        for (String mn : new String[]{"decodeSubscriptions", "decodeSubsList", "decodeAllSubscriptions"}) {
            try {
                Object r = c.getMethod(mn);
                Object list = invoke(c, (Method) r);
                if (!(list instanceof java.util.Collection)) continue;
                for (Object e : (java.util.Collection<?>) list) {
                    String id = null; Object item = e;
                    if (e != null && e.getClass().getName().equals("kotlin.Pair")) {
                        id = String.valueOf(get(e, "first"));
                        item = get(e, "second");
                    } else {
                        for (String f : new String[]{"guid", "subId", "id"}) { Object v = get(e, f); if (v != null) { id = v.toString(); break; } }
                        Object sub = get(e, "subscription");
                        if (sub != null) item = sub;
                    }
                    if (id != null) out.add(new Object[]{id, item});
                }
                if (!out.isEmpty()) return out;
            } catch (Exception ignored) {}
        }
        return out;
    }

    static List<String> subscriptionIds() {
        List<String> r = new ArrayList<>();
        for (Object[] s : subscriptions()) r.add((String) s[0]);
        return r;
    }

    static boolean removeSubscription(String id) {
        boolean ok = false;
        try { call(MMKV, new String[]{"removeServerViaSubid", "removeServersViaSubid"}, id); } catch (Exception ignored) {}
        try { call(MMKV, new String[]{"removeSubscription", "removeSubscriptionItem"}, id); ok = true; } catch (Exception ignored) {}
        return ok;
    }

    static List<String> storeIds(Class<?> c, String... must) {
        List<String> ids = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (f.getType() != String.class || !Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                String v = (String) f.get(null);
                if (v == null) continue;
                String u = v.toUpperCase() + "|" + f.getName().toUpperCase();
                for (String m : must) if (u.contains(m) && !u.contains("RAW") && !u.contains("AFF") && !ids.contains(v)) ids.add(v);
            } catch (Exception ignored) {}
        }
        return ids;
    }

    static Object mmkv(String id) {
        try {
            Class<?> k = Class.forName("com.tencent.mmkv.MMKV");
            return k.getMethod("mmkvWithID", String.class, int.class).invoke(null, id, 2 /* MULTI_PROCESS_MODE */);
        } catch (Exception e) { return null; }
    }

    /** Profil JSON'u (yedek yol). */
    static String json(String guid) {
        Class<?> c = cls(MMKV);
        if (c == null) return "";
        for (String id : storeIds(c, "PROFILE", "SERVER_CONFIG")) {
            Object kv = mmkv(id);
            try {
                Object v = kv.getClass().getMethod("decodeString", String.class).invoke(kv, guid);
                if (v != null && !v.toString().isEmpty()) return v.toString();
            } catch (Exception ignored) {}
        }
        return "";
    }

    static Object profile(String guid) {
        try { return call(MMKV, new String[]{"decodeServerConfig", "decodeProfileConfig", "decodeProfileItem"}, guid); } catch (Exception e) { return null; }
    }

    /** Ekranda gösterilecek bilgiler: {ad, sunucu, port, tür, ağ, güvenlik}. */
    static String[] info(String guid) {
        Object p = profile(guid);
        String[] r = new String[6];
        if (p != null) {
            r[0] = s(get(p, "remarks")); r[1] = s(get(p, "server")); r[2] = s(get(p, "serverPort"));
            r[3] = s(get(p, "configType")); r[4] = s(get(p, "network")); r[5] = s(get(p, "security"));
        } else {
            try {
                org.json.JSONObject j = new org.json.JSONObject(json(guid));
                r[0] = j.optString("remarks"); r[1] = j.optString("server"); r[2] = j.optString("serverPort");
                r[3] = j.optString("configType"); r[4] = j.optString("network"); r[5] = j.optString("security");
            } catch (Exception e) { for (int i = 0; i < 6; i++) r[i] = ""; }
        }
        for (int i = 0; i < 6; i++) if (r[i] == null || r[i].equals("null")) r[i] = "";
        return r;
    }

    static String s(Object o) { return o == null ? "" : o.toString(); }

    static String selected() {
        try { Object r = call(MMKV, new String[]{"getSelectServer", "decodeSelectServer"}); return r == null ? null : r.toString(); } catch (Exception e) { return null; }
    }

    static void select(String guid) {
        try { call(MMKV, new String[]{"setSelectServer", "encodeSelectServer"}, guid); } catch (Exception ignored) {}
    }

    /* ---- servis ---- */
    static String lastErr = "";
    private static String vpnCls = null, mgrCls = null;

    /** Uygulamadaki VPN servisini manifestten bulur (BIND_VPN_SERVICE izinli servis). */
    static String vpnServiceClass(Context c) {
        if (vpnCls != null) return vpnCls;
        try {
            android.content.pm.PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), android.content.pm.PackageManager.GET_SERVICES);
            if (pi.services != null) for (android.content.pm.ServiceInfo si : pi.services) {
                if ("android.permission.BIND_VPN_SERVICE".equals(si.permission)) { vpnCls = si.name; break; }
            }
        } catch (Throwable ignored) {}
        return vpnCls;
    }

    /** Servis yöneticisi sınıfını APK içindeki sınıf adlarından bulur (v2rayNG sürümüne göre adı değişebiliyor). */
    @SuppressWarnings("deprecation")
    static String managerClass(Context c) {
        if (mgrCls != null) return mgrCls.isEmpty() ? null : mgrCls;
        Class<?> k = cls(SERVICE);
        if (k != null) { mgrCls = k.getName(); return mgrCls; }
        mgrCls = "";
        try {
            dalvik.system.DexFile df = new dalvik.system.DexFile(c.getPackageCodePath());
            java.util.Enumeration<String> e = df.entries();
            String best = null;
            while (e.hasMoreElements()) {
                String n = e.nextElement();
                if (!n.startsWith("com.v2ray.ang") || n.contains("$")) continue;
                String simple = n.substring(n.lastIndexOf('.') + 1);
                if (!simple.endsWith("ServiceManager") && !simple.endsWith("CoreManager")) continue;
                try {
                    for (Method m : Class.forName(n).getDeclaredMethods()) {
                        String mn = m.getName();
                        if (mn.startsWith("startVService") || mn.equals("startV2Ray") || mn.equals("startCore")) { best = n; break; }
                    }
                } catch (Throwable ignored) {}
                if (best != null) break;
            }
            if (best != null) mgrCls = best;
        } catch (Throwable t) {
            lastErr += " | dex: " + t.getMessage();
        }
        return mgrCls.isEmpty() ? null : mgrCls;
    }

    /** Servisi başlat: v2rayNG sürümüne göre start* metotlarını dener. */
    static boolean start(Context c) {
        String mn0 = managerClass(c);
        Class<?> k = null;
        try { if (mn0 != null) k = Class.forName(mn0); } catch (Throwable ignored) {}
        if (k == null) { lastErr = "servis yöneticisi bulunamadı"; return false; }
        Object in = inst(k);
        StringBuilder err = new StringBuilder();
        for (String name : new String[]{"startVServiceFromToggle", "startVService", "startV2Ray", "startCore"}) {
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 0 || !p[0].isAssignableFrom(c.getClass())) continue;
                Object[] a = new Object[p.length];
                a[0] = c;
                for (int i = 1; i < p.length; i++) a[i] = def(p[i]);
                try {
                    m.setAccessible(true);
                    Object r = m.invoke(Modifier.isStatic(m.getModifiers()) ? null : in, a);
                    if (r instanceof Boolean && !((Boolean) r)) { err.append(name).append("=false; "); continue; }
                    lastErr = "OK: " + name + "(" + p.length + ")";
                    return true;
                } catch (Throwable t) {
                    Throwable x = t.getCause() != null ? t.getCause() : t;
                    err.append(name).append(": ").append(x.getClass().getSimpleName()).append(" ").append(x.getMessage()).append("; ");
                }
            }
        }
        lastErr = err.length() == 0 ? "start metodu bulunamadı" : err.toString();
        return false;
    }

    static Object def(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        return null;
    }

    static final String[] VPN_SERVICES = {"com.v2ray.ang.service.V2RayVpnService", "com.v2ray.ang.service.V2RayProxyOnlyService"};

    /** Yedek yol: v2rayNG'nin VPN servisini doğrudan başlat (seçili sunucuyla çalışır). */
    static boolean startServiceDirect(Context c) {
        try {
            String sc = vpnServiceClass(c);
            if (sc == null) sc = VPN_SERVICES[0];
            android.content.Intent i = new android.content.Intent();
            i.setClassName(c.getPackageName(), sc);
            android.content.ComponentName cn = Build.VERSION.SDK_INT >= 26 ? c.startForegroundService(i) : c.startService(i);
            if (cn == null) { lastErr += " | servis yok: " + sc; return false; }
            lastErr += " | servis başlatıldı: " + sc.substring(sc.lastIndexOf('.') + 1);
            return true;
        } catch (Throwable t) {
            lastErr += " | servis: " + t.getClass().getSimpleName() + " " + t.getMessage();
            return false;
        }
    }

    static void stopServiceDirect(Context c) {
        String sc = vpnServiceClass(c);
        java.util.List<String> all = new java.util.ArrayList<>(java.util.Arrays.asList(VPN_SERVICES));
        if (sc != null) all.add(0, sc);
        for (String s : all) {
            try {
                android.content.Intent i = new android.content.Intent();
                i.setClassName(c.getPackageName(), s);
                c.stopService(i);
            } catch (Throwable ignored) {}
        }
    }

    static void stop(Context c) {
        String m = managerClass(c);
        String[] cl = m != null ? new String[]{m} : SERVICE;
        try { call(cl, new String[]{"stopVService", "stopV2Ray", "stopCore"}, c); } catch (Exception ignored) {}
    }

    static void stopAll(Context c) {
        stop(c);
        stopServiceDirect(c);
    }

    static String methods(String[] classes) {
        Class<?> k = cls(classes);
        if (k == null) return "-";
        java.util.TreeSet<String> n = new java.util.TreeSet<>();
        for (Method m : k.getDeclaredMethods()) if (!m.getName().contains("$")) n.add(m.getName() + "/" + m.getParameterTypes().length);
        return n.toString();
    }

    /**
     * v2rayNG ana ekranı açılışta geoip/geosite dosyalarını kopyalar ve yönlendirme kurallarını hazırlar.
     * Biz o ekranı atladığımız için aynı hazırlığı burada yapıyoruz (yoksa Xray çekirdeği başlamaz).
     */
    static String initCore(Context c) {
        StringBuilder r = new StringBuilder();
        for (String m : new String[]{"initRoutingRulesets", "initRoutingRuleset"}) {
            try { call(SETTINGS, new String[]{m}, c); r.append(m).append(" ok; "); break; } catch (Exception ignored) {}
        }
        try { call(SETTINGS, new String[]{"initAssets"}, c, c.getAssets()); r.append("initAssets ok; "); } catch (Exception ignored) {}
        java.util.List<java.io.File> dirs = new java.util.ArrayList<>();
        try { java.io.File d = c.getExternalFilesDir("assets"); if (d != null) dirs.add(d); } catch (Exception ignored) {}
        try { dirs.add(c.getDir("assets", 0)); } catch (Exception ignored) {}
        int copied = 0;
        try {
            String[] names = c.getAssets().list("");
            if (names != null) for (String n : names) {
                if (!n.contains("geo") || !n.endsWith(".dat")) continue;
                for (java.io.File d : dirs) {
                    java.io.File f = new java.io.File(d, n);
                    if (f.exists() && f.length() > 1024) continue;
                    d.mkdirs();
                    try (java.io.InputStream in = c.getAssets().open(n); java.io.OutputStream out = new java.io.FileOutputStream(f)) {
                        byte[] buf = new byte[64 * 1024];
                        int k;
                        while ((k = in.read(buf)) > 0) out.write(buf, 0, k);
                        copied++;
                    } catch (Exception e) { r.append(n).append(" kopyalanamadı; "); }
                }
            }
        } catch (Exception e) { r.append("assets: ").append(e.getMessage()); }
        r.append("geo kopyalanan: ").append(copied).append(" → ");
        for (java.io.File d : dirs) r.append(d.getAbsolutePath()).append(' ');
        return r.toString();
    }

    /**
     * v2rayNG'nin varsayılan "reklam engelle" yönlendirme kuralı AdMob'u da engelliyor (VPN açıkken
     * ödüllü video yüklenmiyor). Bu kuralı kapatır.
     */
    @SuppressWarnings("unchecked")
    static String unblockAds() {
        Object list = null;
        try { list = call(SETTINGS, new String[]{"getRoutingRulesets"}); } catch (Exception ignored) {}
        if (!(list instanceof List)) try { list = call(MMKV, new String[]{"decodeRoutingRulesets"}); } catch (Exception ignored) {}
        if (!(list instanceof List)) return "kurallar okunamadı";
        int changed = 0;
        for (Object it : (List<Object>) list) {
            String dom = s(get(it, "domain")).toLowerCase(), tag = s(get(it, "outboundTag")).toLowerCase();
            String rem = s(get(it, "remarks")).toLowerCase();
            boolean ads = dom.contains("ads") || rem.contains("ad");
            if (!ads || !tag.contains("block")) continue;
            Object en = get(it, "enabled");
            if (Boolean.FALSE.equals(en)) continue;
            try { it.getClass().getMethod("setEnabled", boolean.class).invoke(it, false); changed++; } catch (Exception ignored) {}
        }
        if (changed > 0) {
            try { call(MMKV, new String[]{"encodeRoutingRulesets"}, list); } catch (Exception e) { return "kaydedilemedi: " + e.getMessage(); }
        }
        return "reklam kuralı kapatıldı: " + changed;
    }

    static int socksPort() {
        try {
            Object r = call(SETTINGS, new String[]{"getSocksPort"});
            if (r instanceof Integer && (Integer) r > 0) return (Integer) r;
        } catch (Exception ignored) {}
        return 10808;
    }

    /* ---- abonelik ---- */
    static boolean importText(String text) {
        try { call(CONFIG, new String[]{"importBatchConfig"}, text, "", true); return true; } catch (Exception ignored) {}
        try { call(CONFIG, new String[]{"importBatchConfig"}, text, ""); return true; } catch (Exception ignored) {}
        return false;
    }

    static boolean updateAll() {
        try { call(CONFIG, new String[]{"updateConfigViaSubAll"}); return true; } catch (Exception ignored) {}
        return false;
    }
}

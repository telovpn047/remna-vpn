package com.v2ray.ang.remna;

import android.content.Context;

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
    static boolean start(Context c) {
        try { call(SERVICE, new String[]{"startVServiceFromToggle", "startVService", "startV2Ray"}, c); return true; } catch (Exception e) { return false; }
    }

    static void stop(Context c) {
        try { call(SERVICE, new String[]{"stopVService", "stopV2Ray"}, c); } catch (Exception ignored) {}
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

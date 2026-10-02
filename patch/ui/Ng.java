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
    @SuppressWarnings("unchecked")
    static List<String> serverList() {
        try {
            Object r = call(MMKV, new String[]{"decodeServerList"});
            if (r instanceof List) return new ArrayList<>((List<String>) r);
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }

    static Object profile(String guid) {
        try { return call(MMKV, new String[]{"decodeServerConfig", "decodeProfileConfig"}, guid); } catch (Exception e) { return null; }
    }

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

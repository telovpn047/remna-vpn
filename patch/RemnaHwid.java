package com.v2ray.ang.remna;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Remnawave HWID (cihaz sınırı) başlıkları: x-hwid, x-device-os, x-ver-os, x-device-model, x-device-locale. */
public final class RemnaHwid {
    private static String cached;

    private RemnaHwid() {}

    private static Context app() {
        try {
            return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static synchronized String id() {
        if (cached != null) return cached;
        String aid = null;
        try {
            Context c = app();
            if (c != null) aid = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Throwable ignored) {
        }
        if (aid == null || aid.isEmpty()) aid = Build.MANUFACTURER + Build.MODEL + Build.FINGERPRINT;
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((aid + ":remna-vpn").getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", d[i] & 0xff));
            cached = sb.toString();
        } catch (Throwable t) {
            cached = Integer.toHexString(aid.hashCode());
        }
        return cached;
    }

    private static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : s.trim().toCharArray()) b.append(ch >= 0x20 && ch < 0x7f ? ch : '_');
        return b.toString();
    }

    public static Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("x-hwid", id());
        h.put("x-device-os", "Android");
        h.put("x-ver-os", clean(Build.VERSION.RELEASE));
        h.put("x-device-model", clean((Build.MANUFACTURER + " " + Build.MODEL)));
        h.put("x-device-locale", clean(Locale.getDefault().getLanguage()));
        return h;
    }

    private static boolean skipHost(String host) {
        return host == null || host.endsWith("github.com") || host.endsWith("githubusercontent.com");
    }

    /** HttpURLConnection için. */
    public static void inject(URLConnection c) {
        try {
            if (c == null || skipHost(c.getURL().getHost())) return;
            for (Map.Entry<String, String> e : headers().entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
        } catch (Throwable ignored) {
        }
    }
//OKHTTP_PLACEHOLDER
}

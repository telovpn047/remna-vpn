package com.v2ray.ang.remna;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Ödüllü süre bitince VPN'i kapatır (uygulama kapalı olsa bile). */
public class RemnaExpiry extends BroadcastReceiver {
    static long until(Context c) { return prefs(c).getLong("until", 0); }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("remna", Context.MODE_PRIVATE); }

    static long remaining(Context c) { return Math.max(0, until(c) - System.currentTimeMillis()); }

    /** Süre ekler (en fazla MAX_BANK kadar birikebilir) ve alarmı kurar. */
    static void add(Context c, long ms) {
        long now = System.currentTimeMillis();
        long u = Math.max(now, until(c)) + ms;
        u = Math.min(u, now + RemnaConfig.MAX_BANK_MS);
        prefs(c).edit().putLong("until", u).apply();
        schedule(c, u);
    }

    static void schedule(Context c, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 77, new Intent(c, RemnaExpiry.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); } catch (Throwable t) { am.set(AlarmManager.RTC_WAKEUP, at, pi); }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        long left = remaining(c);
        if (left > 15000) { schedule(c, until(c)); return; } // süre uzatılmış
        Ng.stopAll(c);
    }
}

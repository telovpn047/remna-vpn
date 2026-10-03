package com.v2ray.ang.remna;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/** AdMob: ödüllü video (VPN süresi) ve banner. */
final class Ads {
    interface Done { void result(boolean rewarded, String error); }

    private static boolean inited = false;
    private static RewardedAd rewarded;
    private static boolean loading = false;
    private static String lastError = "";
    private static final Handler ui = new Handler(Looper.getMainLooper());

    static void init(Context c) {
        if (inited) return;
        inited = true;
        try { MobileAds.initialize(c.getApplicationContext(), s -> preload(c)); } catch (Throwable t) { lastError = t.getMessage(); }
    }

    static boolean ready() { return rewarded != null; }

    static String error() { return lastError; }

    static void preload(Context c) {
        if (rewarded != null || loading) return;
        loading = true;
        ui.post(() -> {
            try {
                RewardedAd.load(c.getApplicationContext(), RemnaConfig.ADMOB_REWARDED, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                    @Override public void onAdLoaded(RewardedAd ad) { rewarded = ad; loading = false; lastError = ""; }
                    @Override public void onAdFailedToLoad(LoadAdError e) { rewarded = null; loading = false; lastError = e.getMessage(); }
                });
            } catch (Throwable t) { loading = false; lastError = t.getMessage(); }
        });
    }

    /** Reklam hazırsa gösterir; değilse en fazla timeoutMs bekleyip yükler. */
    static void showRewarded(Activity a, long timeoutMs, Done done) {
        if (rewarded == null) {
            preload(a);
            long end = System.currentTimeMillis() + timeoutMs;
            ui.postDelayed(new Runnable() {
                @Override public void run() {
                    if (rewarded != null) showRewarded(a, 0, done);
                    else if (!loading || System.currentTimeMillis() > end) done.result(false, lastError.isEmpty() ? "Reklam yüklenemedi" : lastError);
                    else ui.postDelayed(this, 300);
                }
            }, 300);
            return;
        }
        RewardedAd ad = rewarded;
        rewarded = null;
        final boolean[] earned = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { done.result(earned[0], earned[0] ? null : "Video yarıda kapatıldı"); preload(a); }
            @Override public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError e) { done.result(false, e.getMessage()); preload(a); }
        });
        try {
            ad.show(a, item -> earned[0] = true);
        } catch (Throwable t) {
            done.result(false, t.getMessage());
        }
    }

    static AdView banner(Activity a) {
        try {
            AdView v = new AdView(a);
            v.setAdSize(AdSize.BANNER);
            v.setAdUnitId(RemnaConfig.ADMOB_BANNER);
            v.setAdListener(new AdListener() {});
            v.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            v.loadAd(new AdRequest.Builder().build());
            return v;
        } catch (Throwable t) {
            return null;
        }
    }
}

#!/usr/bin/env python3
"""v2rayNG kaynağını Remna VPN olarak yeniden markalar ve Remnawave HWID başlıklarını ekler.
Kullanım: python3 patch/patch.py <v2rayNG-kök-dizini>"""
import os, re, sys, glob, shutil

APP_NAME = os.environ.get("APP_NAME", "Remna VPN")
APP_ID = os.environ.get("APP_ID", "com.remna.vpn")
HERE = os.path.dirname(os.path.abspath(__file__))
root = os.path.abspath(sys.argv[1])
app = os.path.join(root, "V2rayNG", "app")
main = os.path.join(app, "src", "main")
assert os.path.isdir(main), f"bulunamadı: {main}"

def read(p): return open(p, encoding="utf-8").read()
def write(p, s): open(p, "w", encoding="utf-8").write(s)
def esc_xml(s): return s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")

# 1) Uygulama adı
n = 0
for p in glob.glob(os.path.join(app, "src", "*", "res", "values*", "*.xml")):
    s = read(p)
    s2, k = re.subn(r'(<string\s+name="app_name"[^>]*>)(.*?)(</string>)', lambda m: m.group(1) + esc_xml(APP_NAME) + m.group(3), s, flags=re.S)
    if k: write(p, s2); n += k
for p in glob.glob(os.path.join(app, "build.gradle*")):
    s = read(p)
    s2, k = re.subn(r'(resValue\(\s*"string"\s*,\s*"app_name"\s*,\s*)"[^"]*"', r'\1"' + APP_NAME + '"', s)
    if k: write(p, s2); n += k
print(f"[ad] app_name değiştirildi: {n} yer")
if n == 0: sys.exit("✗ app_name bulunamadı")

# 1b) Arayüz metinlerindeki "v2rayNG" adı
nn = 0
for p_ in glob.glob(os.path.join(app, "src", "*", "res", "values*", "strings*.xml")):
    t = read(p_)
    t2, k = re.subn(r'(>[^<]*?)v2rayNG', lambda m: m.group(1) + APP_NAME, t)
    while k:
        t2, k2 = re.subn(r'(>[^<]*?)v2rayNG', lambda m: m.group(1) + APP_NAME, t2)
        k = k2
    if t2 != t: write(p_, t2); nn += 1
print(f"[ad] metinlerde v2rayNG değiştirildi: {nn} dosya")

# 1c) Renkler: Remna paleti (vurgu turkuaz, arka plan koyu lacivert)
ACC_C, BG_C = "#22D3EE", "#0B1020"
nc = 0
for p_ in glob.glob(os.path.join(app, "src", "main", "res", "values*", "*.xml")):
    t = read(p_)
    if "<color" not in t: continue
    def recolor(m):
        name = m.group(1).lower()
        if any(x in name for x in ("primary", "accent", "secondary")) and "text" not in name and "on_" not in name and "onprimary" not in name:
            return f'<color name="{m.group(1)}">{ACC_C}</color>'
        if any(x in name for x in ("background", "surface", "window")) and "night" in p_ :
            return f'<color name="{m.group(1)}">{BG_C}</color>'
        return m.group(0)
    t2 = re.sub(r'<color name="([^"]+)">#[0-9A-Fa-f]{6,8}</color>', recolor, t)
    if t2 != t: write(p_, t2); nc += 1
print(f"[renk] {nc} dosyada renkler güncellendi")

# 2) applicationId
gp = os.path.join(app, "build.gradle.kts")
if not os.path.exists(gp): gp = os.path.join(app, "build.gradle")
s = read(gp)
s2, k = re.subn(r'(applicationId\s*=?\s*)"com\.v2ray\.ang"', r'\1"' + APP_ID + '"', s)
write(gp, s2)
print(f"[id] applicationId -> {APP_ID}: {k}")

# 2b) User-Agent: "v2rayNG/x.y" -> "RemnaVPN/x.y"
ua = 0
for r_ in (os.path.join(main, "java"), os.path.join(main, "kotlin")):
    if not os.path.isdir(r_): continue
    for dp_, _, fs_ in os.walk(r_):
        for f_ in fs_:
            if not f_.endswith((".kt", ".java")): continue
            q = os.path.join(dp_, f_); t = read(q)
            t2, k = re.subn(r'"v2rayNG/', '"RemnaVPN/', t)
            if k: write(q, t2); ua += k; print(f"[ua] {os.path.relpath(q, root)}")
print(f"[ua] User-Agent değiştirildi: {ua}")

# 2c) Uygulamanın kendi trafiği de VPN'den geçsin (reklamlar ISP DNS engeline takılmasın).
#     v2rayNG kendi paketini VPN dışında bırakıyor; Xray soketleri zaten protect() ile korunuyor.
ex = 0
for r_ in (os.path.join(main, "java"), os.path.join(main, "kotlin")):
    if not os.path.isdir(r_): continue
    for dp_, _, fs_ in os.walk(r_):
        for f_ in fs_:
            if not f_.endswith(".kt"): continue
            q = os.path.join(dp_, f_); t = read(q)
            if "addDisallowedApplication" not in t: continue
            lines = t.split("\n"); out_ = []
            for ln in lines:
                if "addDisallowedApplication(" in ln and any(k in ln for k in ("APPLICATION_ID", "packageName", "ANG_PACKAGE", "applicationContext.packageName")):
                    out_.append(re.sub(r'\S.*', '// remna: kendi paketi VPN içinde', ln, count=1)); ex += 1
                else:
                    out_.append(ln)
            if ex: write(q, "\n".join(out_)); print(f"[vpn] {os.path.relpath(q, root)}")
print(f"[vpn] kendi paketini dışlayan satır kaldırıldı: {ex}")

# 3) İkonlar: eski ic_launcher* kaynaklarını sil, yenilerini koy
removed = 0
for p in glob.glob(os.path.join(main, "res", "mipmap-*", "ic_launcher*")):
    os.remove(p); removed += 1
for d in glob.glob(os.path.join(HERE, "icons", "*")):
    dst = os.path.join(main, "res", os.path.basename(d)); os.makedirs(dst, exist_ok=True)
    for f in os.listdir(d): shutil.copy(os.path.join(d, f), os.path.join(dst, f))
# manifest/kaynaklar ic_launcher_round istemiyorsa sorun değil; eksik referans kalmasın
print(f"[ikon] silinen {removed}, yeni ikonlar kopyalandı")

# 4) HWID başlıkları
src_roots = [p for p in (os.path.join(main, "java"), os.path.join(main, "kotlin")) if os.path.isdir(p)]
ua_conn = re.compile(r'^(\s*)(?:([A-Za-z_][\w]*)\??\.)?setRequestProperty\(\s*"User-[Aa]gent"')
ua_ok = re.compile(r'\.(header|addHeader)\(\s*"User-[Aa]gent"\s*,\s*((?:[^()]|\([^()]*\))*)\)')
hits_conn = hits_ok = 0
for r in src_roots:
    for dp, _, fs in os.walk(r):
        for f in fs:
            if not f.endswith((".kt", ".java")): continue
            p = os.path.join(dp, f); s = read(p)
            if "User-" not in s: continue
            out = []; changed = False
            for line in s.split("\n"):
                m = ua_conn.match(line)
                if m and line.rstrip().endswith(")") and "RemnaHwid" not in line:
                    recv = m.group(2) or "this"
                    semi = ";" if f.endswith(".java") else ""
                    out.append(line)
                    out.append(f"{m.group(1)}com.v2ray.ang.remna.RemnaHwid.inject({recv}){semi}")
                    hits_conn += 1; changed = True; continue
                if f.endswith(".kt") and ua_ok.search(line) and "RemnaHwid" not in line:
                    line = ua_ok.sub(lambda mm: mm.group(0) + ".also { com.v2ray.ang.remna.RemnaHwid.injectOk(it) }", line)
                    hits_ok += 1; changed = True
                out.append(line)
            if changed:
                write(p, "\n".join(out)); print(f"[hwid] {os.path.relpath(p, root)}")

java = read(os.path.join(HERE, "RemnaHwid.java"))
if hits_ok:
    java = java.replace("//OKHTTP_PLACEHOLDER", '''
    /** OkHttp için. */
    public static void injectOk(okhttp3.Request.Builder b) {
        try { for (Map.Entry<String, String> e : headers().entrySet()) b.header(e.getKey(), e.getValue()); } catch (Throwable ignored) {}
    }
''')
dst = os.path.join(main, "java", "com", "v2ray", "ang", "remna"); os.makedirs(dst, exist_ok=True)
write(os.path.join(dst, "RemnaHwid.java"), java)
print(f"[hwid] HttpURLConnection: {hits_conn}, OkHttp: {hits_ok}")

# 5a) Yapılandırma (gömülü abonelik, AdMob kimlikleri)
def E(k, d=''):
    return os.environ.get(k) or d
cfg = f"""package com.v2ray.ang.remna;

/** patch.py tarafından üretilir. */
final class RemnaConfig {{
    static final String SUB_URL = "{E('REMNA_SUB_URL', '')}";
    static final String ADMOB_APP = "{E('ADMOB_APP_ID', 'ca-app-pub-3940256099942544~3347511713')}";
    static final String ADMOB_REWARDED = "{E('ADMOB_REWARDED_ID', 'ca-app-pub-3940256099942544/5224354917')}";
    static final String ADMOB_BANNER = "{E('ADMOB_BANNER_ID', 'ca-app-pub-3940256099942544/6300978111')}";
    static final long REWARD_MS = {int(E('REWARD_MINUTES', '60'))} * 60000L;
    static final long MAX_BANK_MS = {int(E('MAX_BANK_HOURS', '3'))} * 3600000L;
    static final long TRIAL_MS = {int(E('TRIAL_MINUTES', '5'))} * 60000L;
    static final long TRIAL_COOLDOWN_MS = 3 * 3600000L;
    private RemnaConfig() {{}}
}}
"""
os.makedirs(dst, exist_ok=True)
write(os.path.join(dst, "RemnaConfig.java"), cfg)
if not E('REMNA_SUB_URL'): sys.exit("✗ REMNA_SUB_URL boş")
print("[cfg] RemnaConfig yazıldı")

# 5b) AdMob bağımlılığı
gk = os.path.join(app, "build.gradle.kts")
g = read(gk)
if "play-services-ads" not in g:
    g, k = re.subn(r'(\ndependencies\s*\{)', r'\1\n    implementation("com.google.android.gms:play-services-ads:23.6.0")\n    implementation("com.google.guava:guava:33.3.1-android")', g, count=1)
    if k == 0: sys.exit("✗ dependencies bloğu bulunamadı")
    write(gk, g)
print("[ads] play-services-ads eklendi")

# 5) Yeni ana ekran (RemnaActivity) + launcher
for f in glob.glob(os.path.join(HERE, "ui", "*.java")):
    shutil.copy(f, os.path.join(dst, os.path.basename(f)))
mp = os.path.join(main, "AndroidManifest.xml")
m = read(mp)
m, k = re.subn(r'\s*<category\s+android:name="android\.intent\.category\.LAUNCHER"\s*/>', "", m)
act = f"""
        <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="{E('ADMOB_APP_ID', 'ca-app-pub-3940256099942544~3347511713')}" />
        <receiver android:name="com.v2ray.ang.remna.RemnaExpiry" android:exported="false" />
        <activity
            android:name="com.v2ray.ang.remna.RemnaActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:theme="@android:style/Theme.Material.NoActionBar"
            android:configChanges="orientation|screenSize|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>"""
m = m.replace("</application>", act, 1)
write(mp, m)
pg = os.path.join(app, "proguard-rules.pro")
with open(pg, "a", encoding="utf-8") as fh:
    fh.write("\n# Remna VPN: yansıma ile erişilen sınıflar\n-keep class com.v2ray.ang.remna.** { *; }\n-keep class com.v2ray.ang.handler.** { *; }\n-keep class com.v2ray.ang.service.** { *; }\n-keep class com.v2ray.ang.util.** { *; }\n-keep class com.v2ray.ang.dto.** { *; }\n")
print(f"[ui] RemnaActivity eklendi, eski launcher kaldırıldı: {k}")
if k == 0: print("! uyarı: eski LAUNCHER bulunamadı")
if hits_conn + hits_ok == 0:
    os.system(f'grep -rn "User-" {" ".join(src_roots)} | head -20')
    sys.exit("✗ User-Agent ayarlanan yer bulunamadı, HWID eklenemedi")
print("✓ yama tamam")

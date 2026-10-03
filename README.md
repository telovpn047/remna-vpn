# Remna VPN

Remnawave paneli için Android VPN istemcisi. [v2rayNG](https://github.com/2dust/v2rayNG) (GPL-3.0) tabanlıdır; her derlemede v2rayNG'nin son sürümü indirilir ve şu değişiklikler uygulanır (`patch/patch.py`):

- Uygulama adı **Remna VPN**, paket adı `com.remna.vpn`, yeni ikon (v2rayNG ile yan yana kurulabilir).
- Yeni ana ekran: ortada büyük Bağlan/Kes düğmesi, üstte **Otomatik** (en hızlı çalışan sunucuyu TCP ping + gerçek bağlantı testiyle seçer, çalışmazsa sıradakine geçer), altta sunucu listesi. v2rayNG'nin tüm ayarları ⚙ (Gelişmiş) içinde.
- Abonelik güncellenirken Remnawave **HWID** başlıkları gönderilir: `x-hwid`, `x-device-os`, `x-ver-os`, `x-device-model`, `x-device-locale` (panelde cihaz sınırı çalışır).

Derleme GitHub Actions'ta otomatik yapılır; APK'lar **Releases** sayfasındadır.
`RemnaVPN-arm64.apk` çoğu telefon için yeterli ve daha küçüktür; `RemnaVPN.apk` tüm işlemcilerde çalışır.

Lisans: GPL-3.0 (v2rayNG ile aynı). Kaynak: https://github.com/2dust/v2rayNG

## Ödüllü süre ve gömülü abonelik
- Abonelik linki APK'ya gömülüdür (GitHub secret `REMNA_SUB_URL`); kullanıcı link eklemez/silemez.
- Bağlanmak için ödüllü video izlenir: video başına +60 dk (en fazla 3 saat birikir). Reklam yüklenemezse 3 saatte bir 15 dk deneme verilir.
- AdMob kimlikleri secret olarak verilir (`ADMOB_APP_ID`, `ADMOB_REWARDED_ID`, `ADMOB_BANNER_ID`); verilmezse Google'ın test reklamları kullanılır.

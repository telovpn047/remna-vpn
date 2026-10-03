package com.v2ray.ang.remna;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Basit çeviri: Türkçe metin anahtardır; tk / tr / ru / en desteklenir. */
final class L {
    static String lang = "tr";
    private static final Map<String, String[]> M = new HashMap<>();

    private static void put(String tr, String en, String ru, String tk) { M.put(tr, new String[]{en, ru, tk}); }

    static {
        put("Bağlanınca kısa bir video izlenir", "A short video plays after connecting", "После подключения покажется короткое видео", "Birikenden soň gysga wideo görkezilýär");
        put(" dk sonra tekrar denenecek", " min, then it will try again", " мин, затем повторим попытку", " minutdan soň gaýtadan synanyşylar");
        put("Video tamamlanmadı — VPN kapatıldı", "Video not finished — VPN disconnected", "Видео не досмотрено — VPN отключён", "Wideo ahyryna çenli görülmedi — VPN öçürildi");
        put("Reklam yüklenemedi", "Ad failed to load", "Реклама не загрузилась", "Mahabat ýüklenmedi");
        put("tekrar dene", "try again", "попробуйте ещё раз", "gaýtadan synanyşyň");
        put("Bağlandın — süre eklemek için video izle", "Connected — watch a video to add time", "Подключено — посмотрите видео, чтобы добавить время", "Birikdiňiz — wagt goşmak üçin wideo görüň");
        put("Kalan süre  %02d:%02d:%02d", "Time left  %02d:%02d:%02d", "Осталось  %02d:%02d:%02d", "Galan wagt  %02d:%02d:%02d");
        put("Süre yok — bağlanmak için video izle", "No time left — watch a video to connect", "Нет времени — посмотрите видео для подключения", "Wagt ýok — birikmek üçin wideo görüň");
        put("Süre doldu", "Time is up", "Время истекло", "Wagt gutardy");
        put("Otomatik seçim", "Auto select", "Автовыбор", "Awtomatik saýlaw");
        put("Video izle  •  +1 saat", "Watch video  •  +1 hour", "Смотреть видео  •  +1 час", "Wideo gör  •  +1 sagat");
        put("Seçilen: ", "Selected: ", "Выбран: ", "Saýlanan: ");
        put("En hızlı çalışan sunucu otomatik seçilir", "The fastest working server is chosen automatically", "Самый быстрый сервер выбирается автоматически", "Iň çalt işleýän serwer awtomatik saýlanýar");
        put("Kapalı — listeden sunucu seç", "Off — choose a server from the list", "Выкл. — выберите сервер из списка", "Öçük — sanawdan serwer saýlaň");
        put("Otomatik", "Auto", "Авто", "Awtomatik");
        put("En hızlı sunucu seçilir", "The fastest server is chosen", "Выбирается самый быстрый сервер", "Iň çalt serwer saýlanýar");
        put("Bağlı", "Connected", "Подключено", "Birikdi");
        put("Değiştirmek için dokun", "Tap to change", "Нажмите, чтобы изменить", "Üýtgetmek üçin basyň");
        put("Sunucu seç", "Choose a server", "Выберите сервер", "Serwer saýlaň");
        put(" sunucu", " servers", " серверов", " serwer");
        put("Sunucular", "Servers", "Серверы", "Serwerler");
        put("Henüz sunucu yok", "No servers yet", "Серверов пока нет", "Heniz serwer ýok");
        put("Sunucuları yüklemek için yenile düğmesine dokun", "Tap refresh to load servers", "Нажмите «обновить», чтобы загрузить серверы", "Serwerleri ýüklemek üçin täzele düwmesine basyň");
        put("Seçili", "Selected", "Выбран", "Saýlanan");
        put("Yok", "Fail", "Нет", "Ýok");
        put("Korunuyor", "Protected", "Защищено", "Goralýar");
        put("Bağlanıyor…", "Connecting…", "Подключение…", "Birikýär…");
        put("Bağlı değil", "Not connected", "Не подключено", "Birikmedik");
        put("Bağlanmak için dokun", "Tap to connect", "Нажмите, чтобы подключиться", "Birikmek üçin basyň");
        put("Ayarlar", "Settings", "Настройки", "Sazlamalar");
        put("SUNUCULAR", "SERVERS", "СЕРВЕРЫ", "SERWERLER");
        put("Sunucuları güncelle", "Update servers", "Обновить серверы", "Serwerleri täzele");
        put("Kalan süre", "Time left", "Осталось времени", "Galan wagt");
        put(" sa ", " h ", " ч ", " sag ");
        put(" dk", " min", " мин", " min");
        put("Süre yok", "No time", "Нет времени", "Wagt ýok");
        put("BAĞLANTI", "CONNECTION", "ПОДКЛЮЧЕНИЕ", "BIRIKME");
        put("Uygulama bazlı VPN", "Per-app VPN", "VPN для приложений", "Programmalar üçin VPN");
        put("Yönlendirme kuralları", "Routing rules", "Правила маршрутизации", "Ugrukdyryş düzgünleri");
        put("Gelişmiş ayarlar", "Advanced settings", "Расширенные настройки", "Giňişleýin sazlamalar");
        put("SORUN GİDERME", "TROUBLESHOOTING", "ДИАГНОСТИКА", "DIAGNOSTIKA");
        put("Günlük (log)", "Log", "Журнал", "Žurnal");
        put("CİHAZ", "DEVICE", "УСТРОЙСТВО", "ENJAM");
        put("Cihaz kimliği (HWID)", "Device ID (HWID)", "ID устройства (HWID)", "Enjamyň ID-si (HWID)");
        put("Kopyalandı", "Copied", "Скопировано", "Göçürildi");
        put(" · Xray çekirdeği", " · Xray core", " · ядро Xray", " · Xray ýadrosy");
        put("Kesiliyor…", "Disconnecting…", "Отключение…", "Kesilýär…");
        put("Sunucular yükleniyor…", "Loading servers…", "Загрузка серверов…", "Serwerler ýüklenýär…");
        put("VPN izni verilmedi", "VPN permission denied", "Разрешение VPN не выдано", "VPN rugsady berilmedi");
        put("En hızlı sunucu aranıyor…", "Finding the fastest server…", "Поиск самого быстрого сервера…", "Iň çalt serwer gözlenýär…");
        put("Bağlanılamadı: ", "Could not connect: ", "Не удалось подключиться: ", "Birikip bolmady: ");
        put("Bağlandı ama internet çalışmıyor — başka sunucu dene", "Connected, but no internet — try another server", "Подключено, но интернета нет — попробуйте другой сервер", "Birikdi, ýöne internet ýok — başga serwer synap görüň");
        put("Sunucu yok", "No servers", "Нет серверов", "Serwer ýok");
        put("Deneniyor: ", "Trying: ", "Пробуем: ", "Synanyşylýar: ");
        put("Bağlandı, internet testi başarısız", "Connected, internet check failed", "Подключено, проверка интернета не прошла", "Birikdi, internet barlagy şowsuz");
        put("Ping test…", "Ping test…", "Проверка пинга…", "Ping barlagy…");
        put("Abonelik güncelleniyor…", "Updating servers…", "Обновление серверов…", "Serwerler täzelenýär…");
        put("Güncellendi", "Updated", "Обновлено", "Täzelendi");
        put("Güncellenemedi", "Update failed", "Не удалось обновить", "Täzeläp bolmady");
        put("Sunucular güncellendi", "Servers updated", "Серверы обновлены", "Serwerler täzelendi");
        put("Yeterli süren var", "You already have enough time", "У вас достаточно времени", "Size ýeterlik wagt bar");
        put("Video yükleniyor…", "Loading video…", "Загрузка видео…", "Wideo ýüklenýär…");
        put("+1 saat eklendi", "+1 hour added", "Добавлен +1 час", "+1 sagat goşuldy");
        put("Süre için videoyu sonuna kadar izle", "Watch the video to the end to get time", "Досмотрите видео до конца, чтобы получить время", "Wagt almak üçin wideony ahyryna çenli görüň");
        put("Reklam yüklenemedi — ", "Ad failed to load — ", "Реклама не загрузилась — ", "Mahabat ýüklenmedi — ");
        put(" dk deneme verildi. Bağlanınca video izleyip süre ekleyebilirsin.", " min trial granted. Once connected, watch a video to add time.", " мин пробного времени. После подключения посмотрите видео, чтобы добавить время.", " min synag wagty berildi. Birikenden soň wideo görüp wagt goşup bilersiňiz.");
        put("Reklam yüklenemedi: ", "Ad failed to load: ", "Реклама не загрузилась: ", "Mahabat ýüklenmedi: ");
        put("Önceki çökme", "Previous crash", "Предыдущий сбой", "Öňki näsazlyk");
        put("Kopyala", "Copy", "Копировать", "Göçür");
        put("Kapat", "Close", "Закрыть", "Ýap");
        put("Günlük", "Log", "Журнал", "Žurnal");
        put("Yükleniyor…", "Loading…", "Загрузка…", "Ýüklenýär…");
        put("Kopyalandı — sohbete yapıştır", "Copied", "Скопировано", "Göçürildi");
        put("Yenile", "Refresh", "Обновить", "Täzele");
        put("Tümü", "All", "Все", "Hemmesi");
        put("Filtreli", "Filtered", "Фильтр", "Süzgüçli");
        put("Tamam", "OK", "ОК", "Bolýar");
        put("Açılamadı", "Could not open", "Не удалось открыть", "Açyp bolmady");
        put("DİL", "LANGUAGE", "ЯЗЫК", "DIL");
        put("Dil", "Language", "Язык", "Dil");
        put("Otomatik (sistem)", "Automatic (system)", "Автоматически (системный)", "Awtomatik (ulgam)");
    }

    static final String[] CODES = {"", "tk", "tr", "ru", "en"};
    static final String[] NAMES = {"Otomatik (sistem)", "Türkmençe", "Türkçe", "Русский", "English"};

    static void init(String pref) {
        if (pref != null && !pref.isEmpty()) { lang = pref; return; }
        String l = Locale.getDefault().getLanguage();
        if (l.equals("tk")) lang = "tk";
        else if (l.equals("tr") || l.equals("az")) lang = "tr";
        else if (l.equals("ru") || l.equals("uk") || l.equals("be") || l.equals("kk") || l.equals("ky") || l.equals("uz")) lang = "ru";
        else lang = "en";
    }

    static String t(String tr) {
        if (lang.equals("tr")) return tr;
        String[] v = M.get(tr);
        if (v == null) return tr;
        return lang.equals("en") ? v[0] : lang.equals("ru") ? v[1] : v[2];
    }

    static String name(String code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return i == 0 ? t(NAMES[0]) : NAMES[i];
        return code;
    }
}

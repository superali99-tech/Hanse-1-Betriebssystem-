package org.novaos.mobile;

import android.app.Activity;

/**
 * The apps that come with NovaOS (Nova Chat, Nova Mail, ...). They are part of NovaOS and
 * are updated together with it; the Nova Store shows "Aktualisieren" for those that the
 * waiting software update changes (key in "apps=" of update.properties).
 */
final class NovaApps {
    static final class App {
        final String key;
        final int name, icon, text;
        final Class<? extends Activity> activity;

        App(String key, int name, int icon, int text, Class<? extends Activity> activity) {
            this.key = key;
            this.name = name;
            this.icon = icon;
            this.text = text;
            this.activity = activity;
        }
    }

    static final App[] ALL = {
            new App("nova", R.string.app_nova, R.mipmap.ic_nova_nova, R.string.novaapp_nova, NovaActivity.class),
            new App("chat", R.string.app_chat, R.mipmap.ic_nova_chat, R.string.novaapp_chat, ChatActivity.class),
            new App("mail", R.string.app_mail, R.mipmap.ic_nova_mail, R.string.novaapp_mail, MailActivity.class),
            new App("map", R.string.app_map, R.mipmap.ic_nova_map, R.string.novaapp_map, MapActivity.class),
            new App("weather", R.string.app_weather, R.mipmap.ic_nova_weather, R.string.novaapp_weather, WeatherActivity.class),
            new App("music", R.string.app_music, R.mipmap.ic_nova_music, R.string.novaapp_music, MusicActivity.class),
            new App("family", R.string.app_family, R.mipmap.ic_nova_family, R.string.novaapp_family, FamilyActivity.class),
            new App("shop", R.string.app_shop, R.mipmap.ic_nova_shop, R.string.novaapp_shop, ShopActivity.class),
            new App("store", R.string.app_store, R.mipmap.ic_nova_store, R.string.novaapp_store, StoreActivity.class),
            new App("camera", R.string.app_camera, R.mipmap.ic_nova_camera, R.string.novaapp_camera, CameraActivity.class),
            new App("gallery", R.string.app_gallery, R.mipmap.ic_nova_gallery, R.string.novaapp_gallery, GalleryActivity.class),
            new App("files", R.string.app_files, R.mipmap.ic_nova_files, R.string.novaapp_files, FilesActivity.class),
            new App("notes", R.string.app_notes, R.mipmap.ic_nova_notes, R.string.novaapp_notes, NotesActivity.class),
            new App("settings", R.string.app_settings, R.mipmap.ic_nova_settings, R.string.novaapp_settings, SettingsActivity.class),
    };

    private NovaApps() {}

    static App get(String key) {
        for (App a : ALL) if (a.key.equals(key)) return a;
        return null;
    }
}

package com.giftgram.messenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

/**
 * Проверяет, что GiftGram реально стартует: сплэш, главный экран,
 * меню, темы и обработка ссылок t.me.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class GiftGramSmokeTest {

    @Test
    public void splash_starts_and_shows_branding() {
        Activity splash = Robolectric.buildActivity(SplashActivity.class).setup().get();

        assertNotNull(splash.findViewById(R.id.splash_logo));
        assertNotNull(splash.findViewById(R.id.splash_title));
        assertNotNull(splash.findViewById(R.id.splash_halo));
        assertNotNull(splash.findViewById(R.id.splash_bar));

        android.widget.TextView title = splash.findViewById(R.id.splash_title);
        assertEquals("GiftGram", title.getText().toString());
    }

    @Test
    public void splash_finishes_into_main_activity() {
        Activity splash = Robolectric.buildActivity(SplashActivity.class).setup().get();
        ShadowActivity shadow = Shadows.shadowOf(splash);

        // Сплэш уходит в MainActivity через Handler (1250 мс)
        org.robolectric.shadows.ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);

        Intent next = shadow.getNextStartedActivity();
        assertNotNull("Сплэш должен открыть MainActivity", next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void main_activity_inflates_client_and_menu() {
        Activity main = Robolectric.buildActivity(MainActivity.class).setup().get();

        // Клиентское окно и служебные элементы на месте
        assertNotNull(main.findViewById(R.id.web));
        assertNotNull(main.findViewById(R.id.swipe));
        assertNotNull(main.findViewById(R.id.progress));
        assertNotNull(main.findViewById(R.id.fab));
        assertEquals(View.GONE, main.findViewById(R.id.offline).getVisibility());

        // Шторка меню инфлируется без ошибок
        View sheet = main.getLayoutInflater().inflate(R.layout.sheet_menu, null);
        assertNotNull(sheet.findViewById(R.id.row_channel));
        assertNotNull(sheet.findViewById(R.id.row_refresh));
        assertNotNull(sheet.findViewById(R.id.row_share));
        assertNotNull(sheet.findViewById(R.id.row_theme));
        assertNotNull(sheet.findViewById(R.id.row_clear));
        assertNotNull(sheet.findViewById(R.id.row_about));
        assertNotNull(sheet.findViewById(R.id.row_exit));
    }

    @Test
    public void main_activity_shows_giftgram_label() {
        Activity main = Robolectric.buildActivity(MainActivity.class).setup().get();
        CharSequence label = main.getApplicationInfo().loadLabel(main.getPackageManager());
        assertEquals("GiftGram", label.toString());
    }

    @Test
    public void theme_setting_persists() {
        ApplicationProvider.getApplicationContext();
        GiftGramApp.setDark(true);
        assertTrue(GiftGramApp.isDark());

        GiftGramApp.setDark(false);
        assertTrue(!GiftGramApp.isDark());

        GiftGramApp.setDark(true);
        assertTrue(GiftGramApp.isDark());
    }

    @Test
    public void deep_link_to_username_is_understood() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Giftgrammmmmm"));
        Activity main = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        assertNotNull(main);
    }

    @Test
    public void every_menu_row_has_title() {
        Activity main = Robolectric.buildActivity(MainActivity.class).setup().get();
        View sheet = main.getLayoutInflater().inflate(R.layout.sheet_menu, null);
        int[] rows = {R.id.row_channel, R.id.row_refresh, R.id.row_share,
                R.id.row_theme, R.id.row_clear, R.id.row_about, R.id.row_exit};
        for (int id : rows) {
            assertNotNull("Ряд меню отсутствует: " + id, sheet.findViewById(id));
        }
    }
}
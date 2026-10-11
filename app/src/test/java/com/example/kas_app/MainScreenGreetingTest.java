package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.os.LocaleListCompat;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.Locale;

/**
 * Guards the "Welcome back!" hero greeting on the main screen: it must come
 * from {@code R.string.welcome_back} (so the existing EN/ID switch drives it),
 * wear the Borel font, take its colour from the theme's {@code colorOnPrimary}
 * so it stays readable on the light header in light mode and on the dark
 * header in dark mode, and sit centred in the hero gap without touching the
 * Fund Data card.
 *
 * <p>The language and night-mode tests drive the same mechanisms the profile
 * sheet uses at runtime (saved locale preference + {@link AppCompatDelegate}),
 * so a regression in those paths shows up here rather than on a device.
 */
@RunWith(RobolectricTestRunner.class)
public class MainScreenGreetingTest {

    private static final String LOCALE_PREFS = "locale_prefs";
    private static final String KEY_APP_LANG = "app_lang";

    @After
    public void resetGlobalState() {
        // Language and night mode are process-wide AppCompat state; every test
        // must leave them the way it found them (English, light).
        Context context = RuntimeEnvironment.getApplication();
        // commit(), not apply(): Robolectric's paused main looper never runs
        // the async apply() before the next test reads the preference.
        context.getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_APP_LANG).commit();
        AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.create(Locale.ENGLISH));
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    @Test
    public void greetingShowsTheEnglishWelcomeBackText() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            TextView greeting = activity.findViewById(R.id.tvGreeting);
            assertNotNull("tvGreeting must inflate on the main screen", greeting);
            assertEquals("Welcome back!", greeting.getText().toString());
            assertEquals("text must come from the string resource, not a literal",
                    activity.getString(R.string.welcome_back),
                    greeting.getText().toString());
        }
    }

    /**
     * The Indonesian string is selected through the same {@code values-in}
     * resource system the rest of the app uses. The saved-locale preference is
     * written exactly like the profile sheet writes it, and the {@code "in"}
     * qualifier supplies the Indonesian resource configuration that, on a real
     * device, comes from the framework locale manager (AppCompat 1.7
     * delegates to {@code LocaleManager} on API 33+, which Robolectric does
     * not apply).
     */
    @Test
    @Config(qualifiers = "in")
    public void greetingShowsTheIndonesianTextWhenTheAppIsInIndonesian() {
        // The profile sheet persists the choice before flipping the locale;
        // applySavedLocale reads it back on the next launch. commit() so the
        // value is on disk before onCreate reads it under Robolectric.
        RuntimeEnvironment.getApplication()
                .getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_APP_LANG, "id").commit();

        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            TextView greeting = activity.findViewById(R.id.tvGreeting);
            assertNotNull("tvGreeting must inflate on the main screen", greeting);
            // assertTrue, not assertEquals: ComparisonFailure discards the
            // custom message, and the diagnostics below are the point.
            assertTrue("greeting must come from values-in, but was <"
                            + greeting.getText() + ">"
                            + " [pref=" + RuntimeEnvironment.getApplication()
                            .getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
                            .getString(KEY_APP_LANG, "<unset>")
                            + ", default=" + Locale.getDefault()
                            + ", configLocales="
                            + activity.getResources().getConfiguration().getLocales()
                            + ", appLocales=" + AppCompatDelegate.getApplicationLocales()
                            + ", activityStr=" + activity.getString(R.string.welcome_back)
                            + "]",
                    "Selamat datang kembali!"
                            .contentEquals(greeting.getText()));
        }
    }

    @Test
    public void greetingUsesTheBorelFont() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            TextView greeting = activity.findViewById(R.id.tvGreeting);
            Typeface borel = ResourcesCompat.getFont(activity, R.font.borel);
            Typeface worn = greeting.getTypeface();
            TextView reference = activity.findViewById(R.id.tvDataTitle);
            Typeface themeFace = reference.getTypeface();

            assertTrue("@font/borel must load from resources, but got <"
                            + borel + ">",
                    borel != null);
            assertTrue("greeting must wear exactly the Borel typeface, "
                            + "not fall back to the theme font [worn=<" + worn
                            + ">, borel=<" + borel
                            + ">, themeFont=<" + themeFace + ">]",
                    borel.equals(worn));
            assertTrue("greeting must differ from a plain themed TextView, "
                            + "otherwise it silently wears plus_jakarta_sans",
                    themeFace == null || !themeFace.equals(worn));
        }
    }

    @Test
    public void greetingColourFollowsTheLightTheme() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            TextView greeting = activity.findViewById(R.id.tvGreeting);
            assertEquals("?attr/colorOnPrimary in light mode",
                    resolveThemeColor(activity,
                            com.google.android.material.R.attr.colorOnPrimary),
                    greeting.getCurrentTextColor());
        }
    }

    @Test
    public void greetingColourFollowsTheDarkTheme() {
        // Same path as the profile sheet's Dark Mode switch.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            int nightMask = activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            assertEquals("activity must really be in night mode",
                    Configuration.UI_MODE_NIGHT_YES, nightMask);

            TextView greeting = activity.findViewById(R.id.tvGreeting);
            int darkColor = resolveThemeColor(activity,
                    com.google.android.material.R.attr.colorOnPrimary);
            assertEquals("?attr/colorOnPrimary in dark mode",
                    darkColor, greeting.getCurrentTextColor());
            assertEquals("dark-mode colourOnPrimary",
                    0xFF381E72, darkColor);
        }
    }

    @Test
    public void greetingStaysClearOfTheToolbarAndFundDataCard() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            TextView greeting = activity.findViewById(R.id.tvGreeting);
            View header = activity.findViewById(R.id.viewHeaderBackground);
            View cardFundData = activity.findViewById(R.id.cardFundData);
            Toolbar toolbar = activity.findViewById(R.id.toolbarMain);

            View parent = (View) greeting.getParent();

            assertTrue("greeting must sit below the toolbar region",
                    greeting.getTop() >= 0);
            assertTrue("greeting must not overlap the Fund Data card",
                    greeting.getBottom() <= cardFundData.getTop());
            assertTrue("greeting box must have a real height",
                    greeting.getHeight() > 0);

            // The toolbar and the greeting live in different parents, so the
            // comparison has to happen in screen coordinates.
            int[] toolbarLocation = new int[2];
            toolbar.getLocationOnScreen(toolbarLocation);
            int[] greetingLocation = new int[2];
            greeting.getLocationOnScreen(greetingLocation);
            assertTrue("greeting must not be drawn under the toolbar",
                    greetingLocation[1]
                            >= toolbarLocation[1] + toolbar.getHeight());

            int greetingCentreY = (greeting.getTop() + greeting.getBottom()) / 2;
            int heroCentreY = (header.getTop() + cardFundData.getTop()) / 2;
            assertEquals("greeting must be vertically centred in the hero gap",
                    heroCentreY, greetingCentreY, 2);

            int greetingCentreX = (greeting.getLeft() + greeting.getRight()) / 2;
            assertEquals("greeting must be horizontally centred",
                    parent.getWidth() / 2, greetingCentreX, 2);
        }
    }

    @Test
    public void greetingFitsOnASingleLine() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            TextView greeting = activity.findViewById(R.id.tvGreeting);
            assertTrue("greeting text must stay on one line, not wrap "
                            + "awkwardly",
                    greeting.getLineCount() <= 1);
            assertTrue("autosized text must fit inside the greeting box",
                    greeting.getLayout() == null
                            || greeting.getLayout().getEllipsisCount(0) == 0);
        }
    }

    private static int resolveThemeColor(Activity activity, int attr) {
        TypedValue value = new TypedValue();
        if (!activity.getTheme().resolveAttribute(attr, value, true)) {
            throw new AssertionError("could not resolve attr " + attr);
        }
        return value.data;
    }
}

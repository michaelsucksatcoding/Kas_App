package com.example.kas_app;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.content.res.Configuration;
import android.util.TypedValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * The landing screen background is painted from {@code ?attr/colorPrimary}, so its
 * colour is decided entirely by the effective night mode of this Activity.
 *
 * <p>The app stores its own dark-mode preference (default: light). That preference has to be
 * handed to AppCompatDelegate <em>before</em> an AppCompatActivity computes its
 * night-mode configuration override in {@code attachBaseContext}. Applying it inside
 * {@code onCreate} is too late for the launcher Activity, which then inherits the device's
 * system night mode and renders the Material 3 dark palette
 * ({@code colorPrimary} = {@code #D0BCFF}) instead of the intended light purple
 * ({@code colorPrimary} = {@code #6750A4}).
 */
@RunWith(RobolectricTestRunner.class)
public class WelcomeScreenNightModeTest {

    private static final int LIGHT_MODE_PRIMARY = 0xFF6750A4;

    private static int nightModeOf(Activity activity) {
        return activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
    }

    private static int themeColor(Activity activity, int attr) {
        TypedValue value = new TypedValue();
        if (!activity.getTheme().resolveAttribute(attr, value, true)) {
            throw new AssertionError("could not resolve attr " + attr);
        }
        return value.data;
    }

    /**
     * A device in night mode must not change the landing screen: the app's saved
     * preference wins, and the background stays the light purple.
     */
    @Test
    @Config(qualifiers = "night")
    public void landingScreenUsesSavedPreferenceOnNightDevice() {
        try (ActivityController<WelcomeActivity> controller =
                     Robolectric.buildActivity(WelcomeActivity.class)) {
            WelcomeActivity activity = controller.setup().get();

            assertEquals("landing screen must use the saved light mode, not the device "
                            + "night mode",
                    Configuration.UI_MODE_NIGHT_NO, nightModeOf(activity));
            assertEquals("landing background gradient must resolve to the light purple",
                    LIGHT_MODE_PRIMARY,
                    themeColor(activity, androidx.appcompat.R.attr.colorPrimary));
        }
    }
}
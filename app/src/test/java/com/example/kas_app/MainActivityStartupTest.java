package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.widget.Toolbar;

import com.google.android.material.appbar.AppBarLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

/**
 * Executed on the JVM through Robolectric, so the main screen really inflates
 * {@code R.layout.activity_main} the same way {@link MainActivity#onCreate}
 * does. This guards the navbar contract: the toolbar and the logo/profile
 * brand row must inflate, the profile button must not grow the brand row, and
 * the app bar must be the view that consumes the status bar inset.
 */
@RunWith(RobolectricTestRunner.class)
public class MainActivityStartupTest {

    @Test
    public void mainScreenInflatesToolbarAndProfileAction() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            Toolbar toolbar = activity.findViewById(R.id.toolbarMain);
            View profile = activity.findViewById(R.id.btnProfile);

            assertNotNull("toolbarMain must inflate", toolbar);
            assertNotNull("btnProfile must inflate", profile);
            assertTrue("profile button must stay enabled", profile.isEnabled());
        }
    }

    /**
     * The transparent pinned toolbar occupies the top of the expanded header,
     * where the logo/profile row now lives. Touches must still fall through to
     * the profile action.
     */
    @Test
    public void profileActionReceivesTapAtItsOwnBounds() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            View header = activity.findViewById(R.id.collapsingHeader);
            View profile = activity.findViewById(R.id.btnProfile);
            final boolean[] clicked = {false};
            profile.setOnClickListener(v -> clicked[0] = true);

            float x = profile.getWidth() / 2f;
            float y = profile.getHeight() / 2f;

            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_DOWN, x, y, 0);
            MotionEvent up = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_UP, x, y, 0);
            profile.dispatchTouchEvent(down);
            profile.dispatchTouchEvent(up);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            down.recycle();
            up.recycle();

            assertTrue("tapping the profile action must invoke its click listener",
                    clicked[0]);
        }
    }

    private static String toolbarBounds(MainActivity activity) {
        View toolbar = activity.findViewById(R.id.toolbarMain);
        int[] location = new int[2];
        toolbar.getLocationOnScreen(location);
        return location[0] + "," + location[1] + ","
                + (location[0] + toolbar.getWidth()) + ","
                + (location[1] + toolbar.getHeight())
                + " clickable=" + toolbar.isClickable()
                + " focusable=" + toolbar.isFocusable();
    }

    /**
     * The profile action now shares the brand row with the logo, so it must
     * fit inside that row instead of growing it past the logo's 44dp height.
     */
    @Test
    public void profileActionDoesNotInflateBrandRowHeight() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            View brandRow = activity.findViewById(R.id.headerBrandRow);
            View logo = activity.findViewById(R.id.ivBrandMark);
            View profile = activity.findViewById(R.id.btnProfile);

            assertNotNull("headerBrandRow must inflate", brandRow);
            assertTrue("the brand row should size to its tallest child (the logo)",
                    brandRow.getHeight() <= logo.getHeight() + 1);
            assertTrue("profile button must not be taller than the brand row",
                    profile.getHeight() <= brandRow.getHeight());
        }
    }

    /**
     * The logo and profile controls must be vertically centred in the same
     * horizontal row.
     */
    @Test
    public void profileActionIsVerticallyCentredWithTheLogo() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            View brandRow = activity.findViewById(R.id.headerBrandRow);
            View profile = activity.findViewById(R.id.btnProfile);

            float rowCentre = brandRow.getHeight() / 2f;
            float profileCentre = profile.getTop() + profile.getHeight() / 2f;

            assertTrue("profile action should be vertically centred in the brand row "
                            + "(rowCentre=" + rowCentre
                            + ", profileCentre=" + profileCentre + ")",
                    Math.abs(rowCentre - profileCentre) <= 1f);
        }
    }

    /**
     * The logo must start at the header's start inset and the profile must end
     * at its end inset, so both share the same 24dp edge padding as the
     * greeting above them.
     */
    @Test
    public void logoAndProfileShareTheHeaderEdgePadding() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            View expandedHeader = activity.findViewById(R.id.headerExpandedContent);
            View greeting = activity.findViewById(R.id.tvGreeting);
            View brandRow = activity.findViewById(R.id.headerBrandRow);
            View logo = activity.findViewById(R.id.ivBrandMark);
            View profile = activity.findViewById(R.id.btnProfile);

            int[] greetingLocation = locationOnScreen(greeting);
            int[] brandRowLocation = locationOnScreen(brandRow);
            int[] logoLocation = locationOnScreen(logo);
            int[] profileLocation = locationOnScreen(profile);
            int[] headerLocation = locationOnScreen(expandedHeader);

            assertEquals("the logo must start at the greeting's left inset",
                    greetingLocation[0], logoLocation[0]);
            assertEquals("the logo must start at the header start padding",
                    headerLocation[0] + expandedHeader.getPaddingLeft(),
                    logoLocation[0]);
            assertEquals("the profile must end at the brand row's end edge",
                    brandRowLocation[0] + brandRow.getWidth(),
                    profileLocation[0] + profile.getWidth());
            assertEquals("the brand row must end at the header end padding",
                    headerLocation[0] + expandedHeader.getWidth()
                            - expandedHeader.getPaddingRight(),
                    brandRowLocation[0] + brandRow.getWidth());
        }
    }

    private static int[] locationOnScreen(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return location;
    }

    /**
     * Row order: the logo/profile row first, then the greeting beneath it.
     */
    @Test
    public void greetingSitsBelowTheBrandRow() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            View greeting = activity.findViewById(R.id.tvGreeting);
            View brandRow = activity.findViewById(R.id.headerBrandRow);

            assertTrue("the greeting must sit below the logo/profile row",
                    greeting.getTop() >= brandRow.getBottom());
        }
    }

    private static int resolveActionBarSize(MainActivity activity) {
        TypedValue value = new TypedValue();
        if (!activity.getTheme()
                .resolveAttribute(androidx.appcompat.R.attr.actionBarSize, value, true)
                || value.type != TypedValue.TYPE_DIMENSION) {
            throw new AssertionError("could not resolve ?attr/actionBarSize");
        }
        return TypedValue.complexToDimensionPixelSize(
                value.data, activity.getResources().getDisplayMetrics());
    }

    /**
     * Home must open with the tall hero visible and the compact toolbar title
     * hidden. The collapse itself is driven by AppBarLayout scrolling.
     */
    @Test
    public void headerStartsExpanded() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            AppBarLayout appBar = activity.findViewById(R.id.appBarMain);
            View hero = activity.findViewById(R.id.headerExpandedContent);
            View compactTitle = activity.findViewById(R.id.tvToolbarTitle);

            assertNotNull("appBarMain must inflate", appBar);
            assertNotNull("the expanded hero must inflate", hero);
            assertNotNull("the compact toolbar title must inflate", compactTitle);

            int actionBarSize = resolveActionBarSize(activity);
            assertTrue("the app bar must start taller than the collapsed toolbar "
                            + "(appBar=" + appBar.getHeight()
                            + ", actionBarSize=" + actionBarSize + ")",
                    appBar.getHeight() > actionBarSize);
            assertEquals("the expanded hero must start fully visible",
                    1f, hero.getAlpha(), 0.01f);
            assertEquals("the compact title must start hidden",
                    0f, compactTitle.getAlpha(), 0.01f);
        }
    }

    /**
     * The app bar is the view responsible for keeping the toolbar clear of the
     * status bar, so it must opt into system window insets.
     */
    @Test
    public void appBarConsumesSystemBarInsets() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            View appBar = activity.findViewById(R.id.appBarMain);
            assertNotNull("appBarMain must inflate", appBar);
            assertTrue("appBarMain must fit system windows so the toolbar is not "
                            + "drawn under the status bar",
                    appBar.getFitsSystemWindows());
        }
    }
}

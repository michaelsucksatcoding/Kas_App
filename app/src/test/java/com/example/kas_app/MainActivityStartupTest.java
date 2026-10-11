package com.example.kas_app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.util.TypedValue;
import android.view.View;

import androidx.appcompat.widget.Toolbar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

/**
 * Executed on the JVM through Robolectric, so the main screen really inflates
 * {@code R.layout.activity_main} the same way {@link MainActivity#onCreate}
 * does. This guards the navbar contract: the toolbar and its profile action
 * must inflate, the profile button must not inflate the toolbar height, and
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
     * The profile action must fit inside a normal toolbar instead of growing it.
     */
    @Test
    public void profileActionDoesNotInflateToolbarHeight() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            Toolbar toolbar = activity.findViewById(R.id.toolbarMain);
            View profile = activity.findViewById(R.id.btnProfile);

            int actionBarSize = resolveActionBarSize(activity);

            assertTrue("toolbar height should equal ?attr/actionBarSize but was "
                            + toolbar.getHeight() + " (actionBarSize=" + actionBarSize
                            + ", profile=" + profile.getHeight() + ")",
                    Math.abs(toolbar.getHeight() - actionBarSize) <= 1);
            assertTrue("profile button must not be taller than the toolbar",
                    profile.getHeight() <= toolbar.getHeight());
        }
    }

    /**
     * The profile action must sit vertically centred in the toolbar, like a
     * normal toolbar action icon.
     */
    @Test
    public void profileActionIsVerticallyCentredInToolbar() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            Toolbar toolbar = activity.findViewById(R.id.toolbarMain);
            View profile = activity.findViewById(R.id.btnProfile);

            float toolbarCentre = toolbar.getHeight() / 2f;
            float profileCentre = profile.getTop() + profile.getHeight() / 2f;

            assertTrue("profile action should be vertically centred in the toolbar "
                            + "(toolbarCentre=" + toolbarCentre
                            + ", profileCentre=" + profileCentre + ")",
                    Math.abs(toolbarCentre - profileCentre) <= 1f);
        }
    }

    /**
     * The profile control must sit at the end of the toolbar, next to the content
     * inset, rather than drifting towards the title.
     */
    @Test
    public void profileActionSitsAtTheEndOfToolbar() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.setup().get();

            Toolbar toolbar = activity.findViewById(R.id.toolbarMain);
            View profile = activity.findViewById(R.id.btnProfile);

            float density = activity.getResources().getDisplayMetrics().density;
            int gapToRightEdge = Math.round(toolbar.getWidth() - profile.getRight());

            assertTrue("profile action should be on the right side of the toolbar "
                            + "(toolbarWidth=" + toolbar.getWidth()
                            + ", profileRight=" + profile.getRight() + ")",
                    gapToRightEdge >= 0 && gapToRightEdge <= 32 * density);
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
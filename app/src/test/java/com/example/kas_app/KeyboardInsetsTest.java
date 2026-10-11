package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;
import android.widget.LinearLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit tests for the shared {@link KeyboardInsets} helper that both the add and
 * edit payment forms rely on to stay above the software keyboard.
 */
@RunWith(RobolectricTestRunner.class)
public class KeyboardInsetsTest {

    private static final int VIEWPORT_WIDTH = 500;
    private static final int VIEWPORT_HEIGHT = 400;
    private static final int ROW_HEIGHT = 200;

    private static WindowInsetsCompat insets(int imeBottom, int navBarBottom, int statusTop) {
        return new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(),
                        Insets.of(0, statusTop, 0, navBarBottom))
                .setInsets(WindowInsetsCompat.Type.ime(),
                        Insets.of(0, 0, 0, imeBottom))
                .build();
    }

    private static NestedScrollView laidOutScroll(Context ctx) {
        NestedScrollView scroll = new NestedScrollView(ctx);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 4; i++) {
            View row = new View(ctx);
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ROW_HEIGHT));
        }
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int widthSpec = MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH, MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT, MeasureSpec.EXACTLY);
        scroll.measure(widthSpec, heightSpec);
        scroll.layout(0, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        return scroll;
    }

    @Test
    public void imeInsetBecomesBottomPaddingSoFieldsSitAboveIt() {
        Context ctx = ApplicationProvider.getApplicationContext();
        NestedScrollView content = new NestedScrollView(ctx);
        KeyboardInsets.applyTo(content);

        ViewCompat.dispatchApplyWindowInsets(content, insets(0, 100, 0));
        assertEquals("with no keyboard the form clears the navigation bar",
                100, content.getPaddingBottom());

        ViewCompat.dispatchApplyWindowInsets(content, insets(500, 100, 0));
        assertEquals("with the keyboard open the form is padded above it",
                500, content.getPaddingBottom());

        ViewCompat.dispatchApplyWindowInsets(content, insets(0, 0, 0));
        assertEquals("when the keyboard closes no gap is left behind",
                0, content.getPaddingBottom());
    }

    @Test
    public void padTopAlsoClearsTheStatusBarForTopAnchoredContent() {
        Context ctx = ApplicationProvider.getApplicationContext();
        NestedScrollView content = new NestedScrollView(ctx);
        KeyboardInsets.applyTo(content, true);

        ViewCompat.dispatchApplyWindowInsets(content, insets(0, 100, 60));
        assertEquals("dialog content clears the status bar", 60, content.getPaddingTop());
        assertEquals("dialog content clears the navigation bar", 100, content.getPaddingBottom());
    }

    @Test
    public void focusedFieldBelowTheViewportScrollsUpIntoView() {
        Context ctx = ApplicationProvider.getApplicationContext();
        NestedScrollView scroll = laidOutScroll(ctx);
        View lastRow = ((ViewGroup) scroll.getChildAt(0)).getChildAt(3);

        int delta = KeyboardInsets.computeScrollDelta(scroll, lastRow);
        assertEquals("a field hidden under the keyboard is scrolled up by its "
                + "overflow amount", VIEWPORT_HEIGHT, delta);
    }

    @Test
    public void fieldAlreadyVisibleDoesNotScroll() {
        Context ctx = ApplicationProvider.getApplicationContext();
        NestedScrollView scroll = laidOutScroll(ctx);
        View lastRow = ((ViewGroup) scroll.getChildAt(0)).getChildAt(3);

        scroll.scrollTo(0, VIEWPORT_HEIGHT);
        assertEquals("an already-visible field is left in place",
                0, KeyboardInsets.computeScrollDelta(scroll, lastRow));
    }

    @Test
    public void fieldAboveTheViewportScrollsDownIntoView() {
        Context ctx = ApplicationProvider.getApplicationContext();
        NestedScrollView scroll = laidOutScroll(ctx);
        View firstRow = ((ViewGroup) scroll.getChildAt(0)).getChildAt(0);

        scroll.scrollTo(0, VIEWPORT_HEIGHT);
        assertEquals("a field hidden above the viewport is scrolled down",
                -VIEWPORT_HEIGHT, KeyboardInsets.computeScrollDelta(scroll, firstRow));
    }

    @Test
    public void bottomPaddingShrinksTheVisibleArea() {
        Context ctx = ApplicationProvider.getApplicationContext();
        NestedScrollView scroll = laidOutScroll(ctx);
        scroll.setPadding(0, 0, 0, 150);
        View lastRow = ((ViewGroup) scroll.getChildAt(0)).getChildAt(3);

        assertEquals("keyboard padding counts as hidden area",
                ROW_HEIGHT * 4 - (VIEWPORT_HEIGHT - 150),
                KeyboardInsets.computeScrollDelta(scroll, lastRow));
    }

    @Test
    public void fieldAndFollowingActionRevealTogether() {
        Context ctx = ApplicationProvider.getApplicationContext();
        NestedScrollView scroll = laidOutScroll(ctx);
        ViewGroup content = (ViewGroup) scroll.getChildAt(0);
        View field = content.getChildAt(2);
        View action = content.getChildAt(3);

        assertEquals("the field and the button right below it are revealed as "
                + "one region so the button never hides behind the keyboard",
                ROW_HEIGHT * 4 - VIEWPORT_HEIGHT,
                KeyboardInsets.computeScrollDelta(scroll, field, action));
    }

    @Test
    public void notYetLaidOutViewsAreSafe() {
        Context ctx = ApplicationProvider.getApplicationContext();
        NestedScrollView scroll = new NestedScrollView(ctx);
        View row = new View(ctx);
        scroll.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ROW_HEIGHT));

        assertNotNull("the scroll view must exist", scroll);
        assertEquals("an un-laid-out form must not request a scroll",
                0, KeyboardInsets.computeScrollDelta(scroll, row));
    }
}

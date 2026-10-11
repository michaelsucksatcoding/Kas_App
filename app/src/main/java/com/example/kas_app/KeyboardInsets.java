package com.example.kas_app;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.textfield.TextInputLayout;

/**
 * Keeps a scrollable form clear of the software keyboard.
 *
 * <p>On Android 15+ (targetSdk 35) edge-to-edge is enforced and the framework no
 * longer resizes the window for the IME, so {@code SOFT_INPUT_ADJUST_RESIZE} and
 * {@code enableEdgeToEdge=false} do nothing - the keyboard draws over the
 * content. A window has to make room for the IME itself.
 *
 * <p>The two popups need different room-making strategies because they are
 * different views:
 *
 * <ul>
 *   <li><b>Bottom sheet.</b> Material already makes room: when the style opts
 *       into {@code paddingBottomSystemWindowInsets}, {@code BottomSheetBehavior}
 *       adds the keyboard height as the sheet's bottom padding. Padding the
 *       sheet again would double the lift, so this class only adds the missing
 *       scroll (see {@link #keepFieldAndActionVisible(View)}).</li>
 *   <li><b>Dialog.</b> A plain dialog has no such machinery, so its content view
 *       is padded directly by the keyboard inset (see {@link #applyTo(View, boolean)}).</li>
 * </ul>
 */
final class KeyboardInsets {

    private KeyboardInsets() {
    }

    /**
     * Reveals the focused field (and the action button just below it) whenever
     * the bottom sheet is laid out.
     *
     * <p>The sheet already makes room for the keyboard: the Material bottom
     * sheet style sets {@code paddingBottomSystemWindowInsets}, so
     * {@code BottomSheetBehavior} lifts the sheet by the keyboard height. Padding
     * the sheet again would double that lift. What the sheet does not do is
     * scroll, and the form stays taller than the space left above the keyboard,
     * so the last control ("Save Payment") can still be cut off. Re-running the
     * reveal on every layout keeps the field and its button visible as the sheet
     * settles, and scrolls no further than it has to.
     */
    static void keepFieldAndActionVisible(final View sheetView) {
        sheetView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            View focused = v.findFocus();
            if (focused != null) {
                scrollFieldIntoView(focused);
            }
        });
    }

    /**
     * Pads {@code contentView} above the navigation bar and the keyboard, and
     * scrolls the focused field into view when the keyboard opens. The top
     * padding is left untouched.
     */
    static void applyTo(View contentView) {
        applyTo(contentView, false);
    }

    /**
     * @param padTop when true the top system-bar inset is also applied, for
     *               content that starts at the top of the screen (e.g. a dialog
     *               whose window is laid out edge-to-edge).
     */
    static void applyTo(View contentView, boolean padTop) {
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int top = padTop ? systemBars.top : view.getPaddingTop();
            view.setPadding(
                    systemBars.left,
                    top,
                    systemBars.right,
                    Math.max(systemBars.bottom, ime.bottom));

            // The keyboard just opened or resized: keep the field being edited
            // visible instead of making the user scroll to find it.
            if (ime.bottom > 0) {
                scrollFieldIntoView(view.findFocus());
            }
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** Scrolls {@code field} into view whenever it gains focus. */
    static void keepVisibleWhenFocused(View field) {
        if (field == null) {
            return;
        }
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollFieldIntoView(v);
            }
        });
    }

    /** Scrolls {@code field} into view, if a scrollable ancestor exists. */
    static void scrollFieldIntoView(View field) {
        if (field == null) {
            return;
        }
        field.post(() -> {
            NestedScrollView scrollView = findScrollView(field);
            if (scrollView == null) {
                return;
            }
            // Reveal the focused field together with the action that follows it
            // (e.g. "Save Payment"), so the two never straddle the keyboard.
            View top = targetFor(field);
            int delta = computeScrollDelta(scrollView, top, nextVisibleSibling(top));
            if (delta != 0) {
                scrollView.smoothScrollBy(0, delta);
            }
        });
    }

    private static NestedScrollView findScrollView(View field) {
        ViewParent parent = field.getParent();
        while (parent != null) {
            if (parent instanceof NestedScrollView) {
                return (NestedScrollView) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    /**
     * Scrolls to a field's wrapping {@link TextInputLayout}, not to the field
     * itself, so the hint and any error message stay visible too. Material wraps
     * the edit text in an internal frame, so the layout can be several ancestors
     * up.
     */
    private static View targetFor(View field) {
        ViewParent parent = field.getParent();
        while (parent instanceof View) {
            if (parent instanceof TextInputLayout) {
                return (View) parent;
            }
            parent = parent.getParent();
        }
        return field;
    }

    private static View nextVisibleSibling(View view) {
        ViewParent parent = view.getParent();
        if (!(parent instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) parent;
        int index = group.indexOfChild(view);
        for (int i = index + 1; i >= 0 && i < group.getChildCount(); i++) {
            View sibling = group.getChildAt(i);
            if (sibling.getVisibility() == View.VISIBLE) {
                return sibling;
            }
        }
        return null;
    }

    /**
     * Delta in pixels to add to the scroll position so that {@code field} sits
     * fully inside the scroll view's padded viewport.
     */
    static int computeScrollDelta(NestedScrollView scrollView, View field) {
        return computeScrollDelta(scrollView, field, null);
    }

    /**
     * Delta in pixels to add to the scroll position so that the region from the
     * top of {@code top} to the bottom of {@code bottom} (or of {@code top} when
     * {@code bottom} is null) sits fully inside the scroll view's padded
     * viewport. Zero means the region is already visible, so repeated calls
     * never start a scroll loop.
     */
    static int computeScrollDelta(NestedScrollView scrollView, View top, View bottom) {
        if (scrollView == null || top == null) {
            return 0;
        }
        View content = scrollView.getChildAt(0);
        if (content == null || scrollView.getHeight() == 0) {
            return 0;
        }
        int topOffset = offsetWithinContent(top, content);
        if (topOffset < 0) {
            return 0;
        }
        View bottomView = bottom != null ? bottom : top;
        int bottomOffset = offsetWithinContent(bottomView, content);
        if (bottomOffset < 0) {
            bottomOffset = topOffset + top.getHeight();
        }
        int regionTop = topOffset;
        int regionBottom = bottomOffset + bottomView.getHeight();
        if (regionBottom <= regionTop) {
            regionBottom = regionTop + top.getHeight();
        }

        int visibleTop = scrollView.getScrollY();
        int visibleBottom = scrollView.getScrollY()
                + scrollView.getHeight()
                - scrollView.getPaddingTop()
                - scrollView.getPaddingBottom();
        if (regionBottom > visibleBottom) {
            return regionBottom - visibleBottom;
        }
        if (regionTop < visibleTop) {
            return regionTop - visibleTop;
        }
        return 0;
    }

    /**
     * Top edge of {@code view} relative to {@code content}, or -1 when
     * {@code view} is not a descendant of {@code content}.
     */
    private static int offsetWithinContent(View view, View content) {
        int offset = 0;
        View current = view;
        while (current != null && current != content) {
            offset += current.getTop();
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return current == content ? offset : -1;
    }
}

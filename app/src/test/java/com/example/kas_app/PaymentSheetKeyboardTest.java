package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;

import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

/**
 * The "Add New Payment" form used to squash and push its bottom controls
 * off-screen when the numeric keyboard opened. The sheet is a
 * {@link BottomSheetDialog}, and on Android 15+ (which this app targets)
 * edge-to-edge is enforced, so the window is never resized for the IME and
 * {@code adjustResize}/{@code enableEdgeToEdge=false} do nothing.
 *
 * <p>Material's bottom sheet already lifts itself above the keyboard (its style
 * sets {@code paddingBottomSystemWindowInsets}), so the form must <b>not</b> add
 * its own keyboard padding - that would double the lift. What is left for the
 * app is to scroll the focused field and the Save button into view as the sheet
 * settles; see {@link KeyboardInsets#keepFieldAndActionVisible(View)}.
 *
 * <p>These tests pin that setup: the form is scrollable, the sheet stays
 * edge-to-edge (without which Material would not lift it at all), the reveal
 * covers the amount field and the Save button together, and the amount field
 * still asks for a numeric keyboard.
 */
@RunWith(RobolectricTestRunner.class)
public class PaymentSheetKeyboardTest {

    private static StudentPaymentBottomSheet showSheet(FundDataActivity activity) {
        StudentPaymentBottomSheet sheet = StudentPaymentBottomSheet.newInstance(1, "Budi");
        sheet.show(activity.getSupportFragmentManager(), "StudentPaymentBottomSheet");
        activity.getSupportFragmentManager().executePendingTransactions();
        ShadowLooper.idleMainLooper();
        return sheet;
    }

    private static NestedScrollView scrollViewOf(StudentPaymentBottomSheet sheet) {
        View content = sheet.getView();
        assertNotNull("payment sheet content must inflate", content);
        assertTrue("the form root must be scrollable so a reduced visible height "
                        + "scrolls instead of clipping fields (was "
                        + content.getClass().getName() + ")",
                content instanceof NestedScrollView);
        return (NestedScrollView) content;
    }

    @Test
    public void paymentFormIsScrollableSoItCanAdaptToTheKeyboard() {
        try (ActivityController<FundDataActivity> controller =
                     Robolectric.buildActivity(FundDataActivity.class)) {
            FundDataActivity activity = controller.setup().get();
            StudentPaymentBottomSheet sheet = showSheet(activity);

            ViewGroup form = (ViewGroup) scrollViewOf(sheet).getChildAt(0);
            assertNotNull("the form fields must live inside the scroll view", form);
            assertNotNull("student name must stay reachable",
                    form.findViewById(R.id.tvStudentNameTitle));
            assertNotNull("payment history must stay reachable",
                    form.findViewById(R.id.rvPaymentHistory));
            assertNotNull("month input must stay reachable",
                    form.findViewById(R.id.spinnerMonth));
            assertNotNull("year input must stay reachable",
                    form.findViewById(R.id.spinnerYear));
            assertNotNull("amount input must stay reachable",
                    form.findViewById(R.id.etAmount));
            assertNotNull("save button must stay reachable",
                    form.findViewById(R.id.btnSavePayment));
        }
    }

    @Test
    public void paymentSheetStaysEdgeToEdgeSoInsetsAreDelivered() {
        try (ActivityController<FundDataActivity> controller =
                     Robolectric.buildActivity(FundDataActivity.class)) {
            FundDataActivity activity = controller.setup().get();
            StudentPaymentBottomSheet sheet = showSheet(activity);

            Dialog dialog = sheet.getDialog();
            assertNotNull("the sheet must be shown as a dialog", dialog);
            assertTrue("the sheet must remain a BottomSheetDialog",
                    dialog instanceof BottomSheetDialog);
            assertTrue("the sheet must stay edge-to-edge, otherwise the framework "
                            + "stops delivering the IME inset that makes room for "
                            + "the keyboard",
                    ((BottomSheetDialog) dialog).getEdgeToEdgeEnabled());
        }
    }

    @Test
    public void amountFieldAndSaveButtonAreRevealedTogether() {
        try (ActivityController<FundDataActivity> controller =
                     Robolectric.buildActivity(FundDataActivity.class)) {
            FundDataActivity activity = controller.setup().get();
            StudentPaymentBottomSheet sheet = showSheet(activity);
            NestedScrollView content = scrollViewOf(sheet);

            View amount = content.findViewById(R.id.etAmount);
            View save = content.findViewById(R.id.btnSavePayment);
            assertNotNull("amount field must inflate", amount);
            assertNotNull("save button must inflate", save);

            View target = amount;
            while (target != null && !(target instanceof TextInputLayout)) {
                target = target.getParent() instanceof View
                        ? (View) target.getParent() : null;
            }
            assertNotNull("the amount field must sit inside a TextInputLayout so its "
                    + "hint stays visible when it is revealed", target);

            ViewParent form = target.getParent();
            assertTrue("the Save button must share the amount field's form so the two "
                            + "can be revealed as one region",
                    form == save.getParent());
            assertTrue("Save must follow the amount field in that form",
                    ((ViewGroup) form).indexOfChild(save)
                            > ((ViewGroup) form).indexOfChild(target));
            assertEquals("Save must be visible so the reveal can include it",
                    View.VISIBLE, save.getVisibility());
        }
    }

    @Test
    public void amountFieldRequestsANumericKeyboard() {
        try (ActivityController<FundDataActivity> controller =
                     Robolectric.buildActivity(FundDataActivity.class)) {
            FundDataActivity activity = controller.setup().get();
            StudentPaymentBottomSheet sheet = showSheet(activity);

            EditText amount = scrollViewOf(sheet).findViewById(R.id.etAmount);
            assertNotNull("amount field must inflate", amount);
            int inputClass = amount.getInputType() & InputType.TYPE_MASK_CLASS;
            assertEquals("the amount field must open a numeric keyboard",
                    InputType.TYPE_CLASS_NUMBER, inputClass);
        }
    }
}

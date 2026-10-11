package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDatePickerDialog;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.Calendar;
import java.util.List;

/**
 * The ledger dialog's Date field used to be inert: it showed nothing on Add, no
 * picker could be opened, and the stored date was never written back. These
 * tests drive the real {@link FundReportActivity} dialogs, the real layout and
 * real SQLite through Robolectric.
 */
@RunWith(RobolectricTestRunner.class)
public class LedgerDatePickerTest {

    private static final String DB_NAME = "kas_app.db";

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_NAME);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(DB_NAME);
    }

    // --- helpers ---

    private static AlertDialog openAddDialog(FundReportActivity activity) {
        activity.findViewById(R.id.fabAddLedger).performClick();
        ShadowLooper.idleMainLooper();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("the add dialog must open", dialog);
        return dialog;
    }

    private static EditText dateField(AlertDialog dialog) {
        EditText date = dialog.findViewById(R.id.etLedgerDate);
        assertNotNull("the date field must be in the dialog", date);
        return date;
    }

    private static DatePickerDialog openPicker(EditText dateField) {
        dateField.performClick();
        ShadowLooper.idleMainLooper();
        DatePickerDialog picker = (DatePickerDialog) ShadowDialog.getLatestDialog();
        assertNotNull("tapping the date field must open a DatePickerDialog", picker);
        return picker;
    }

    private static void pickDate(DatePickerDialog picker, int year, int monthZeroBased, int day) {
        ShadowDatePickerDialog shadow = Shadow.extract(picker);
        shadow.getOnDateSetListenerCallback()
                .onDateSet(picker.getDatePicker(), year, monthZeroBased, day);
        ShadowLooper.idleMainLooper();
    }

    private List<LedgerEntry> allEntries() {
        DatabaseHelper helper = new DatabaseHelper(context);
        try {
            return helper.getAllLedgerEntries();
        } finally {
            helper.close();
        }
    }

    private int seedEntry(String date) {
        DatabaseHelper helper = new DatabaseHelper(context);
        long id = helper.addLedgerEntry(date, "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 1000);
        helper.close();
        assertTrue("seed insert must succeed", id > 0);
        return (int) id;
    }

    // --- add dialog ---

    @Test
    public void aNewEntryDefaultsToToday() {
        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class).setup()) {
            EditText date = dateField(openAddDialog(controller.get()));

            assertEquals("a new ledger entry must start on today's date",
                    IsoDate.today(), date.getText().toString());
        }
    }

    @Test
    public void theDateFieldIsTappableAndNeverRaisesTheKeyboard() {
        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class).setup()) {
            EditText date = dateField(openAddDialog(controller.get()));

            assertTrue("the date field must be clickable", date.isClickable());
            assertTrue("the date field must be focusable for keyboard/accessibility",
                    date.isFocusable());
            assertFalse("the date field must not take focus from touch, or the first tap "
                            + "would only steal focus instead of opening the picker",
                    date.isFocusableInTouchMode());
            assertEquals("the date is chosen from a picker, never typed",
                    InputType.TYPE_NULL, date.getInputType());
            assertFalse("the date field must not be a numeric/keyboard field",
                    (date.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER);
        }
    }

    @Test
    public void tappingTheFieldOpensThePickerOnTheShownDay() {
        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class).setup()) {
            EditText date = dateField(openAddDialog(controller.get()));
            Calendar shown = IsoDate.toCalendar(date.getText().toString());

            ShadowDatePickerDialog picker = Shadow.extract(openPicker(date));

            assertEquals(shown.get(Calendar.YEAR), picker.getYear());
            assertEquals(shown.get(Calendar.MONTH), picker.getMonthOfYear());
            assertEquals(shown.get(Calendar.DAY_OF_MONTH), picker.getDayOfMonth());
        }
    }

    @Test
    public void pickingADayUpdatesTheFieldAsIsoDate() {
        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class).setup()) {
            EditText date = dateField(openAddDialog(controller.get()));

            pickDate(openPicker(date), 2024, 2, 9);

            assertEquals("2024-03-09", date.getText().toString());
        }
    }

    @Test
    public void cancellingThePickerLeavesTheShownDayUnchanged() {
        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class).setup()) {
            EditText date = dateField(openAddDialog(controller.get()));
            String before = date.getText().toString();

            openPicker(date).cancel();
            ShadowLooper.idleMainLooper();

            assertEquals("a cancelled picker must not change the field", before,
                    date.getText().toString());
        }
    }

    @Test
    public void savingANewEntryPersistsThePickedDay() {
        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class).setup()) {
            AlertDialog dialog = openAddDialog(controller.get());
            EditText date = dateField(dialog);

            pickDate(openPicker(date), 2025, 11, 31);

            ((EditText) dialog.findViewById(R.id.etDescription)).setText("Donation");
            ((EditText) dialog.findViewById(R.id.etLedgerAmount)).setText("50000");
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            ShadowLooper.idleMainLooper();

            List<LedgerEntry> entries = allEntries();
            assertEquals("exactly one row must be stored", 1, entries.size());
            assertEquals("the picked day must be saved exactly as picked", "2025-12-31",
                    entries.get(0).getDate());
        }
    }

    // --- edit ---

    @Test
    public void editingWithoutTouchingTheDateKeepsTheStoredDay() {
        int id = seedEntry("2020-05-06");

        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class).setup()) {
            FundReportActivity activity = controller.get();
            activity.showEditLedgerDialog(
                    new LedgerEntry(id, "2020-05-06", "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 1000));

            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            EditText date = dateField(dialog);
            assertEquals("the edit dialog must start on the stored day",
                    "2020-05-06", date.getText().toString());

            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            ShadowLooper.idleMainLooper();

            assertEquals("an untouched edit must not rewrite the day",
                    "2020-05-06", allEntries().get(0).getDate());
        }
    }

    @Test
    public void editingAndPickingADaySavesTheNewDay() {
        int id = seedEntry("2020-05-06");

        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class).setup()) {
            FundReportActivity activity = controller.get();
            activity.showEditLedgerDialog(
                    new LedgerEntry(id, "2020-05-06", "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 1000));

            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            EditText date = dateField(dialog);

            pickDate(openPicker(date), 2021, 0, 1);
            assertEquals("2021-01-01", date.getText().toString());

            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            ShadowLooper.idleMainLooper();

            assertEquals("the newly picked day must be saved", "2021-01-01",
                    allEntries().get(0).getDate());
        }
    }

    @Test
    public void cancellingTheEditPickerDoesNotChangeTheStoredDay() {
        int id = seedEntry("2020-05-06");

        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class).setup()) {
            FundReportActivity activity = controller.get();
            activity.showEditLedgerDialog(
                    new LedgerEntry(id, "2020-05-06", "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 1000));

            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            EditText date = dateField(dialog);

            openPicker(date).cancel();
            ShadowLooper.idleMainLooper();
            assertEquals("cancelling must not change the field", "2020-05-06",
                    date.getText().toString());

            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            ShadowLooper.idleMainLooper();

            assertEquals("a cancelled picker must not rewrite the stored day",
                    "2020-05-06", allEntries().get(0).getDate());
        }
    }

    @Test
    public void theStoredDateSurvivesAReopen() {
        seedEntry("2019-12-31");

        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class).setup()) {
            List<LedgerEntry> entries = allEntries();
            assertEquals("2019-12-31", entries.get(0).getDate());
            assertTrue("the stored date must match yyyy-MM-dd",
                    entries.get(0).getDate().matches("\\d{4}-\\d{2}-\\d{2}"));
        }
    }
}

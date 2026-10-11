package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Executed on the JVM through Robolectric against real SQLite and the real
 * layouts.
 *
 * <p>Verifies the authoritative accounting rule end to end:
 *
 * <pre>
 *   DEBIT  = PEMASUKAN   = +balance
 *   KREDIT = PENGELUARAN = -balance
 *   balance = SUM(debit) - SUM(credit)
 * </pre>
 *
 * <p>and that the Fund Report <b>displays</b> each type the same way it
 * counts it: a debit row is shown as Pemasukan (+) and a credit row as
 * Pengeluaran (-), never reversed.
 */
@RunWith(RobolectricTestRunner.class)
public class DebitCreditSemanticsTest {

    private static final String DB_NAME = "kas_app.db";
    private static final int GREEN = Color.parseColor("#2E7D32");

    private Context context;
    private DatabaseHelper helper;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_NAME);
        helper = new DatabaseHelper(context);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context.deleteDatabase(DB_NAME);
    }

    // --- helpers ---

    private long debit(String description, int amount) {
        return helper.addLedgerEntry("2026-01-01", description,
                DatabaseHelper.LEDGER_TYPE_DEBIT, amount);
    }

    private long credit(String description, int amount) {
        return helper.addLedgerEntry("2026-01-02", description,
                DatabaseHelper.LEDGER_TYPE_CREDIT, amount);
    }

    /** @return the single ledger row mirroring this payment, or {@code null}. */
    private LedgerEntry mirrorOf(long paymentId) {
        Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT id, date, description, type, amount FROM " + DatabaseHelper.TABLE_LEDGER
                        + " WHERE " + DatabaseHelper.COLUMN_LEDGER_PAYMENT_ID + "=?",
                new String[]{String.valueOf(paymentId)});
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            assertEquals("a payment may only ever have one mirrored row", 1, cursor.getCount());
            return new LedgerEntry(cursor.getInt(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getInt(4));
        } finally {
            cursor.close();
        }
    }

    private int mirrorCount() {
        Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_LEDGER
                        + " WHERE " + DatabaseHelper.COLUMN_LEDGER_PAYMENT_ID + " IS NOT NULL",
                null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    // --- Test 1: debit increases balance ---

    @Test
    public void test1_aDebitIncreasesTheBalance() {
        assertTrue(debit("Opening", 100000) > 0);
        assertEquals(100000L, helper.getBalance());

        assertTrue(debit("Dues", 20000) > 0);
        assertEquals("Debit Rp20.000 must turn Rp100.000 into Rp120.000",
                120000L, helper.getBalance());
    }

    // --- Test 2: credit decreases balance ---

    @Test
    public void test2_aCreditDecreasesTheBalance() {
        assertTrue(debit("Opening", 100000) > 0);
        assertEquals(100000L, helper.getBalance());

        assertTrue(credit("Snacks", 20000) > 0);
        assertEquals("Credit Rp20.000 must turn Rp100.000 into Rp80.000",
                80000L, helper.getBalance());
    }

    // --- Test 3: both transaction types ---

    @Test
    public void test3_bothTransactionTypesNetCorrectly() {
        assertTrue(debit("Opening", 100000) > 0);
        assertTrue(debit("Donation", 50000) > 0);
        assertTrue(credit("Snacks", 20000) > 0);

        assertEquals("100.000 + 50.000 - 20.000", 130000L, helper.getBalance());
    }

    // --- Test 4: Fund Data creates a debit ---

    @Test
    public void test4_fundDataCreatesExactlyOneDebitThatRaisesTheBalance() {
        helper.addStudent("Student A");
        assertTrue(debit("Opening", 100000) > 0);

        long paymentId = helper.addFundDataPayment(1, "January", 2026, 20000);
        assertTrue(paymentId > 0);

        LedgerEntry mirror = mirrorOf(paymentId);
        assertNotNull("one ledger row must mirror the payment", mirror);
        assertEquals("the mirrored type must be debit", DatabaseHelper.LEDGER_TYPE_DEBIT,
                mirror.getType());
        assertEquals("the mirrored amount must be the Fund Data amount", 20000,
                mirror.getAmount());
        assertEquals("the balance must grow by exactly 20000", 120000L, helper.getBalance());
    }

    @Test
    public void fundDataFromTwoStudentsAddsUpLikeTheSpecification() {
        helper.addStudent("Student A");
        helper.addStudent("Student B");
        assertTrue(debit("Opening", 100000) > 0);

        assertTrue(helper.addFundDataPayment(1, "January", 2026, 20000) > 0);
        assertTrue(helper.addFundDataPayment(2, "January", 2026, 30000) > 0);

        assertEquals("20.000 + 30.000 = 50.000 of debit added", 2, mirrorCount());
        assertEquals("100.000 + 50.000", 150000L, helper.getBalance());
    }

    // --- Test 5: Fund Report mapping ---

    /**
     * The dialog must offer Debit as Pemasukan and Kredit as Pengeluaran, in
     * both locales, and Debit must be the preselected type: money entering the
     * fund is the normal entry, exactly as before the mapping was corrected.
     */
    @Test
    public void test5_theTypeLabelsMapDebitToPemasukanAndCreditToPengeluaran() {
        assertEquals("Debit (Income)", context.getString(R.string.label_debit));
        assertEquals("Credit (Expense)", context.getString(R.string.label_credit));

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(new Locale("in"));
        Context indonesian = context.createConfigurationContext(config);
        assertEquals("Debit (Pemasukan)", indonesian.getString(R.string.label_debit));
        assertEquals("Kredit (Pengeluaran)", indonesian.getString(R.string.label_credit));

        // The dialog is inflated through the report screen, which owns it, so
        // the Material3 theme is the one the user really sees.
        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class)) {
            FundReportActivity activity = controller.setup().get();
            View dialog = LayoutInflater.from(activity).inflate(R.layout.dialog_add_ledger, null);
            RadioGroup group = dialog.findViewById(R.id.rgType);
            assertEquals("Debit (Pemasukan) must be the default selection",
                    R.id.rbDebit, group.getCheckedRadioButtonId());
            RadioButton debitButton = dialog.findViewById(R.id.rbDebit);
            RadioButton creditButton = dialog.findViewById(R.id.rbCredit);
            assertEquals(context.getString(R.string.label_debit), debitButton.getText().toString());
            assertEquals(context.getString(R.string.label_credit), creditButton.getText().toString());
        }
    }

    /**
     * The rows themselves: a debit is rendered as a positive green amount
     * (Pemasukan) and a credit as a negative red amount (Pengeluaran), through
     * the real {@link LedgerAdapter} and the real layout.
     */
    @Test
    public void test5_fundReportRowsShowDebitAsPositiveAndCreditAsNegative() {
        List<LedgerEntry> rows = new ArrayList<>();
        rows.add(new LedgerEntry(1, "2026-01-01", "Dues", DatabaseHelper.LEDGER_TYPE_DEBIT, 100000));
        rows.add(new LedgerEntry(2, "2026-01-02", "Snacks", DatabaseHelper.LEDGER_TYPE_CREDIT, 30000));

        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class)) {
            FundReportActivity activity = controller.setup().get();
            LedgerAdapter adapter = new LedgerAdapter(rows);
            android.view.ViewGroup parent = new android.widget.FrameLayout(activity);

            LedgerAdapter.LedgerViewHolder debitHolder = adapter.onCreateViewHolder(parent, 0);
            adapter.onBindViewHolder(debitHolder, 0);
            TextView debitAmount = debitHolder.itemView.findViewById(R.id.tvLedgerAmount);
            assertEquals("+ " + CurrencyFormatter.format(100000), debitAmount.getText().toString());
            assertEquals("a Pemasukan amount must be green", GREEN,
                    debitAmount.getTextColors().getDefaultColor());

            LedgerAdapter.LedgerViewHolder creditHolder = adapter.onCreateViewHolder(parent, 0);
            adapter.onBindViewHolder(creditHolder, 1);
            TextView creditAmount = creditHolder.itemView.findViewById(R.id.tvLedgerAmount);
            assertEquals("- " + CurrencyFormatter.format(30000), creditAmount.getText().toString());
            assertEquals("a Pengeluaran amount must be red", Color.RED,
                    creditAmount.getTextColors().getDefaultColor());
        }
    }

    // --- Test 6: no duplicate ledger records ---

    @Test
    public void test6_oneFundDataSubmissionProducesExactlyOneLedgerRecord() {
        helper.addStudent("Student A");
        assertTrue(debit("Opening", 50000) > 0);

        long paymentId = helper.addFundDataPayment(1, "March", 2026, 15000);
        assertTrue(paymentId > 0);
        assertEquals(1, mirrorCount());

        // Reads a screen performs: history, balance, totals, reopening.
        for (int i = 0; i < 3; i++) {
            helper.getPaymentsByStudent(1);
            helper.getAllLedgerEntries();
            helper.getBalance();
            helper.getLedgerTotals();
        }

        assertEquals("reading the screens must never duplicate the entry", 1, mirrorCount());
        assertEquals("one opening row plus one mirror", 2,
                helper.getAllLedgerEntries().size());
        assertEquals(65000L, helper.getBalance());
    }

    // --- Test 7: edit ---

    @Test
    public void test7_editingFundDataUpdatesItsLedgerRecordInsteadOfAddingOne() {
        helper.addStudent("Student A");
        assertTrue(debit("Opening", 100000) > 0);
        long paymentId = helper.addFundDataPayment(1, "January", 2026, 20000);
        assertEquals(120000L, helper.getBalance());
        int ledgerRowsBefore = helper.getAllLedgerEntries().size();

        assertEquals(1, helper.updateFundDataPayment((int) paymentId, 1,
                "January", 2026, 30000));

        LedgerEntry mirror = mirrorOf(paymentId);
        assertNotNull(mirror);
        assertEquals("the mirrored row must become debit Rp30.000",
                DatabaseHelper.LEDGER_TYPE_DEBIT, mirror.getType());
        assertEquals(30000, mirror.getAmount());
        assertEquals("no second Rp30.000 debit may appear",
                ledgerRowsBefore, helper.getAllLedgerEntries().size());
        assertEquals("100.000 + 30.000", 130000L, helper.getBalance());
    }

    // --- Test 8: delete ---

    @Test
    public void test8_deletingFundDataRemovesItsLedgerRecordAndTheBalanceReturns() {
        helper.addStudent("Student A");
        assertTrue(debit("Opening", 100000) > 0);
        long paymentId = helper.addFundDataPayment(1, "January", 2026, 20000);
        assertEquals(120000L, helper.getBalance());

        assertEquals(1, helper.deleteFundDataPayment((int) paymentId));

        assertEquals("no orphan transaction may keep affecting the balance",
                100000L, helper.getBalance());
        assertEquals("the mirrored row must be gone", 0, mirrorCount());
        assertEquals("only the opening row remains", 1, helper.getAllLedgerEntries().size());
        assertEquals(0, helper.getPaymentsByStudent(1).size());
    }

    // --- Fund Report summary (spec example) ---

    /**
     * The worked example from the specification:
     *
     * <pre>
     *   Debit   Rp100.000   -&gt; Total Pemasukan   Rp100.000
     *   Credit  Rp30.000    -&gt; Total Pengeluaran Rp30.000
     *   Saldo               Rp70.000
     * </pre>
     */
    @Test
    public void summaryTotalsMatchTheBalanceFormula() {
        assertTrue(debit("Donation", 100000) > 0);
        assertTrue(credit("Snacks", 30000) > 0);

        DatabaseHelper.LedgerTotals totals = helper.getLedgerTotals();
        assertEquals("Total Pemasukan", 100000L, totals.income);
        assertEquals("Total Pengeluaran", 30000L, totals.expense);
        assertEquals("Saldo = income - expense", 70000L, helper.getBalance());
        assertEquals("the summary must agree with the balance",
                totals.income - totals.expense, helper.getBalance());
    }

    /**
     * Case C of the device test plan: money in through Fund Data, money out
     * through the ledger, net effect visible in one balance.
     */
    @Test
    public void fundDataPlusExpenditureNetToTheDifference() {
        helper.addStudent("Student A");
        assertTrue(helper.addFundDataPayment(1, "January", 2026, 20000) > 0);
        assertEquals(20000L, helper.getBalance());

        assertTrue(credit("Snacks", 10000) > 0);
        assertEquals("20.000 in - 10.000 out = +10.000", 10000L, helper.getBalance());

        DatabaseHelper.LedgerTotals totals = helper.getLedgerTotals();
        assertEquals(20000L, totals.income);
        assertEquals(10000L, totals.expense);
    }

    // --- the Fund Report screen itself ---

    /**
     * Launches the real screen with a debit and a credit stored and checks
     * what the user reads: summary totals and the balance, all formatted by
     * the shared formatter.
     */
    @Test
    public void fundReportScreenShowsTheTotalsAndTheBalance() {
        assertTrue(debit("Donation", 100000) > 0);
        assertTrue(credit("Snacks", 30000) > 0);
        helper.close();

        try (ActivityController<FundReportActivity> controller =
                     Robolectric.buildActivity(FundReportActivity.class)) {
            FundReportActivity activity = controller.setup().get();

            TextView income = activity.findViewById(R.id.tvTotalIncome);
            TextView expense = activity.findViewById(R.id.tvTotalExpense);
            TextView balance = activity.findViewById(R.id.tvBalanceAmount);

            assertTrue("Total Pemasukan must show Rp100.000, was: " + income.getText(),
                    income.getText().toString().contains(CurrencyFormatter.format(100000)));
            assertTrue("Total Pengeluaran must show Rp30.000, was: " + expense.getText(),
                    expense.getText().toString().contains(CurrencyFormatter.format(30000)));
            assertEquals(CurrencyFormatter.format(70000), balance.getText().toString());
        }
    }
}

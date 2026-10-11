package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

/**
 * Executed on the JVM through Robolectric against real SQLite.
 *
 * <p>Covers the Fund Data rule: money recorded for a student through
 * Fund Data writes the obligation row <b>and</b> exactly one mirrored
 * {@code debit} row in {@code table_ledger}, in one transaction, and that
 * mirror increases the balance. Editing updates the mirror, deleting removes
 * it, a failed write leaves neither side behind, and the pre-existing manual
 * cash-book rules are untouched.
 */
@RunWith(RobolectricTestRunner.class)
public class FundDataLedgerTest {

    private static final String DB_NAME = "kas_app.db";

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

    private void openingBalance(int amount) {
        assertTrue("the manual opening debit must be accepted",
                helper.addLedgerEntry("2026-01-01", "Opening balance",
                        DatabaseHelper.LEDGER_TYPE_DEBIT, amount) > 0);
    }

    private int countRows(String table) {
        Cursor cursor = helper.getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
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

    // --- Test 1: one Fund Data input, one debit, higher balance ---

    @Test
    public void aFundDataInputCreatesOneDebitAndRaisesTheBalance() {
        helper.addStudent("Michael Trisatrio Mukti");
        openingBalance(100000);
        assertEquals(100000L, helper.getBalance());

        long paymentId = helper.addFundDataPayment(1, "January", 2026, 20000);
        assertTrue(paymentId > 0);

        assertEquals("the Fund Data amount must enter the balance",
                120000L, helper.getBalance());

        LedgerEntry mirror = mirrorOf(paymentId);
        assertNotNull("the payment must have a mirrored ledger entry", mirror);
        assertEquals(DatabaseHelper.LEDGER_TYPE_DEBIT, mirror.getType());
        assertEquals(20000, mirror.getAmount());
        assertTrue("the description must identify the Fund Data origin: "
                        + mirror.getDescription(),
                mirror.getDescription().startsWith("Fund Data"));
        assertTrue("the description must name the student: " + mirror.getDescription(),
                mirror.getDescription().contains("Michael Trisatrio Mukti"));
        assertTrue("the description must carry the covered period: "
                        + mirror.getDescription(),
                mirror.getDescription().contains("January 2026"));
        assertTrue("the date must be yyyy-MM-dd, was: " + mirror.getDate(),
                mirror.getDate().matches("\\d{4}-\\d{2}-\\d{2}"));

        // The payment row itself is stored as before.
        List<Payment> payments = helper.getPaymentsByStudent(1);
        assertEquals(1, payments.size());
        assertEquals(20000, payments.get(0).getAmount());
    }

    // --- Test 2: several students ---

    @Test
    public void severalFundDataInputsAccumulateInTheBalance() {
        helper.addStudent("Student A");
        helper.addStudent("Student B");
        helper.addStudent("Student C");
        openingBalance(100000);

        assertTrue(helper.addFundDataPayment(1, "January", 2026, 20000) > 0);
        assertTrue(helper.addFundDataPayment(2, "January", 2026, 30000) > 0);
        assertTrue(helper.addFundDataPayment(3, "January", 2026, 10000) > 0);

        assertEquals("100000 + 20000 + 30000 + 10000", 160000L, helper.getBalance());
        assertEquals("one mirror per input", 3, mirrorCount());
        assertEquals("one opening debit plus three mirrors",
                4, countRows(DatabaseHelper.TABLE_LEDGER));
    }

    // --- Test 3: exactly one ledger entry per submission ---

    @Test
    public void oneSubmissionProducesExactlyOneLedgerEntryAndReadsNeverAddMore() {
        helper.addStudent("Aulia Nursalsabila");
        openingBalance(50000);

        long paymentId = helper.addFundDataPayment(1, "March", 2026, 15000);
        assertTrue(paymentId > 0);
        assertEquals(1, mirrorCount());
        assertEquals(2, countRows(DatabaseHelper.TABLE_LEDGER));

        // Reads a screen performs: list refresh, balance reload, reopening.
        for (int i = 0; i < 3; i++) {
            helper.getPaymentsByStudent(1);
            helper.getAllLedgerEntries();
            helper.getBalance();
        }

        assertEquals("reading the screens must never duplicate the ledger entry",
                1, mirrorCount());
        assertEquals(2, countRows(DatabaseHelper.TABLE_LEDGER));
        assertEquals(65000L, helper.getBalance());
    }

    // --- Test 4: a failed operation never stores one side only ---

    @Test
    public void anInvalidAmountIsRefusedBeforeAnythingIsWritten() {
        helper.addStudent("Student A");
        openingBalance(100000);

        assertEquals(-1L, helper.addFundDataPayment(1, "January", 2026, 0));
        assertEquals(-1L, helper.addFundDataPayment(1, "January", 2026, -5000));

        assertEquals("no payment row for a rejected amount",
                0, countRows(DatabaseHelper.TABLE_PAYMENTS));
        assertEquals("no ledger row for a rejected amount",
                1, countRows(DatabaseHelper.TABLE_LEDGER));
        assertEquals(100000L, helper.getBalance());
    }

    @Test
    public void aFailedLedgerWriteRollsThePaymentBack() {
        helper.addStudent("Student A");
        int paymentsBefore = countRows(DatabaseHelper.TABLE_PAYMENTS);

        // Force the ledger side of the operation to fail: without the table
        // the mirror insert cannot succeed. SQLiteDatabase.insert reports that
        // as -1 rather than as an exception.
        helper.getWritableDatabase().execSQL("DROP TABLE " + DatabaseHelper.TABLE_LEDGER);

        long result = helper.addFundDataPayment(1, "January", 2026, 20000);

        assertEquals("the ledger failure must be reported to the caller", -1L, result);
        assertEquals("the payment must be rolled back with its ledger row",
                paymentsBefore, countRows(DatabaseHelper.TABLE_PAYMENTS));
    }

    // --- Test 5: editing ---

    @Test
    public void editingAFundDataRecordUpdatesTheMirrorInsteadOfAddingASecondRow() {
        helper.addStudent("Student A");
        openingBalance(100000);
        long paymentId = helper.addFundDataPayment(1, "January", 2026, 20000);
        assertEquals(120000L, helper.getBalance());

        assertEquals(1, helper.updateFundDataPayment((int) paymentId, 1,
                "February", 2026, 30000));

        assertEquals("100000 + 30000", 130000L, helper.getBalance());
        assertEquals("the edit must not add a second ledger row",
                1, mirrorCount());
        assertEquals(2, countRows(DatabaseHelper.TABLE_LEDGER));

        LedgerEntry mirror = mirrorOf(paymentId);
        assertNotNull(mirror);
        assertEquals("the ledger must follow the edited amount",
                30000, mirror.getAmount());
        assertTrue("the description must follow the edited period",
                mirror.getDescription().contains("February 2026"));

        Payment payment = helper.getPaymentsByStudent(1).get(0);
        assertEquals("February", payment.getMonth());
        assertEquals(30000, payment.getAmount());

        // Saving the same values again is still a success and still one row.
        assertEquals(1, helper.updateFundDataPayment((int) paymentId, 1,
                "February", 2026, 30000));
        assertEquals(1, mirrorCount());
        assertEquals(130000L, helper.getBalance());
    }

    @Test
    public void editingAnUnknownPaymentChangesNothing() {
        helper.addStudent("Student A");
        openingBalance(100000);

        assertEquals(0, helper.updateFundDataPayment(9999, 1, "January", 2026, 20000));
        assertEquals(0, countRows(DatabaseHelper.TABLE_PAYMENTS));
        assertEquals(0, mirrorCount());
        assertEquals(100000L, helper.getBalance());
    }

    @Test
    public void anInvalidEditAmountIsRefusedAndTheMirrorKeepsTheOldValue() {
        helper.addStudent("Student A");
        openingBalance(100000);
        long paymentId = helper.addFundDataPayment(1, "January", 2026, 20000);

        assertEquals(0, helper.updateFundDataPayment((int) paymentId, 1, "January", 2026, 0));

        assertEquals(20000, mirrorOf(paymentId).getAmount());
        assertEquals(120000L, helper.getBalance());
        assertEquals(20000, helper.getPaymentsByStudent(1).get(0).getAmount());
    }

    // --- Test 6: deleting ---

    @Test
    public void deletingAFundDataRecordRemovesItsMirrorAndTheBalanceReturns() {
        helper.addStudent("Student A");
        openingBalance(100000);
        long paymentId = helper.addFundDataPayment(1, "January", 2026, 20000);
        assertEquals(120000L, helper.getBalance());

        assertEquals(1, helper.deleteFundDataPayment((int) paymentId));

        assertEquals("the deleted amount must leave the balance",
                100000L, helper.getBalance());
        assertEquals("no orphan mirror may survive the payment",
                0, mirrorCount());
        assertEquals("only the opening debit remains",
                1, countRows(DatabaseHelper.TABLE_LEDGER));
        assertEquals(0, countRows(DatabaseHelper.TABLE_PAYMENTS));

        assertEquals("deleting twice reports nothing removed",
                0, helper.deleteFundDataPayment((int) paymentId));
        assertEquals(100000L, helper.getBalance());
    }

    @Test
    public void deletingOnePaymentLeavesTheOtherMirrorsIntact() {
        helper.addStudent("Student A");
        openingBalance(100000);
        long first = helper.addFundDataPayment(1, "January", 2026, 20000);
        long second = helper.addFundDataPayment(1, "February", 2026, 30000);
        assertEquals(150000L, helper.getBalance());

        assertEquals(1, helper.deleteFundDataPayment((int) first));

        assertEquals(130000L, helper.getBalance());
        assertEquals(1, mirrorCount());
        assertNotNull(mirrorOf(second));
        assertEquals(30000, mirrorOf(second).getAmount());
    }

    // --- Test 7: the existing cash-book rules are untouched ---

    @Test
    public void manualLedgerRulesStillApplyAlongsideFundData() {
        helper.addStudent("Student A");
        openingBalance(100000);
        assertTrue(helper.addFundDataPayment(1, "January", 2026, 20000) > 0);
        assertEquals(120000L, helper.getBalance());

        // A manual credit (money out) is still capped by the balance, fund
        // money included.
        assertTrue(helper.addLedgerEntry("2026-02-01", "Snacks",
                DatabaseHelper.LEDGER_TYPE_CREDIT, 120000) > 0);
        assertEquals(0L, helper.getBalance());

        assertEquals("a manual credit still cannot overdraw the fund",
                DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE,
                helper.addLedgerEntry("2026-02-02", "More snacks",
                        DatabaseHelper.LEDGER_TYPE_CREDIT, 1));
        assertEquals(0L, helper.getBalance());

        // Fund Data money still arrives even when the balance is zero.
        assertTrue(helper.addFundDataPayment(1, "March", 2026, 5000) > 0);
        assertEquals(5000L, helper.getBalance());
        assertEquals(2, mirrorCount());
    }

    @Test
    public void aFundDataMirrorCanBeCorrectedInCashBookWithoutTheOverdraftRule() {
        helper.addStudent("Student A");
        openingBalance(100000);
        long paymentId = helper.addFundDataPayment(1, "January", 2026, 20000);
        LedgerEntry mirror = mirrorOf(paymentId);
        assertNotNull(mirror);

        // The mirror is money in, so correcting its amount is never an overdraft.
        assertEquals(1L, helper.updateLedgerEntry(mirror.getId(), mirror.getDate(),
                mirror.getDescription(), DatabaseHelper.LEDGER_TYPE_DEBIT, 500000));
        assertEquals("100000 debit + 500000 Fund Data", 600000L, helper.getBalance());
        assertEquals("the link must survive a cash-book correction",
                1, mirrorCount());

        // A manual expense is still checked against that same balance.
        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE,
                helper.addLedgerEntry("2026-02-01", "Too much",
                        DatabaseHelper.LEDGER_TYPE_CREDIT, 600001));
        assertEquals(600000L, helper.getBalance());
    }

    // --- payment history recorded before the Fund Data rule ---

    @Test
    public void aPaymentStoredWithoutAMirrorNeverMovesTheBalance() {
        helper.addStudent("Student A");
        openingBalance(100000);

        // The raw obligation row: the shape payment history had before the
        // Fund Data rule, with no ledger counterpart.
        assertTrue(helper.addPayment(1, "January", 2026, 20000) > 0);
        assertEquals("unlinked history must stay out of the balance",
                100000L, helper.getBalance());
        assertEquals(0, mirrorCount());

        int paymentId = helper.getPaymentsByStudent(1).get(0).getId();
        assertEquals(1, helper.updateFundDataPayment(paymentId, 1, "January", 2026, 30000));
        assertEquals("editing unlinked history must not inject money",
                100000L, helper.getBalance());
        assertEquals(0, mirrorCount());

        assertEquals(1, helper.deleteFundDataPayment(paymentId));
        assertEquals(100000L, helper.getBalance());
        assertEquals(0, countRows(DatabaseHelper.TABLE_PAYMENTS));
    }
}

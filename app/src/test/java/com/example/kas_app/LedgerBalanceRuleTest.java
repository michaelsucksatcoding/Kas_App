package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Executed on the JVM through Robolectric, so the approved financial rules are
 * verified against real SQLite rather than a mock.
 *
 * <p>Rule under test: a <b>credit</b> (Pengeluaran, money leaving the fund) is
 * stored only when {@code amount <= current balance}; a <b>debit</b> (Pemasukan,
 * money entering the fund) is always stored and is never compared against the
 * balance, so the fund can grow from zero.
 */
@RunWith(RobolectricTestRunner.class)
public class LedgerBalanceRuleTest {

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

    private long credit(String description, int amount) {
        return helper.addLedgerEntry("2026-01-01", description,
                DatabaseHelper.LEDGER_TYPE_CREDIT, amount);
    }

    private long debit(String description, int amount) {
        return helper.addLedgerEntry("2026-01-02", description,
                DatabaseHelper.LEDGER_TYPE_DEBIT, amount);
    }

    private int ledgerRowCount() {
        Cursor cursor = helper.getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_LEDGER, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    private void openingBalance(int amount) {
        // The opening balance is money the fund already holds: Pemasukan = debit.
        assertTrue("opening debit should be accepted", debit("Opening", amount) > 0);
    }

    // --- credit within balance succeeds ---

    @Test
    public void creditWithinBalanceSucceeds() {
        openingBalance(100000);

        assertTrue(credit("Books", 60000) > 0);

        assertEquals(40000L, helper.getBalance());
        assertEquals(2, ledgerRowCount());
    }

    // --- credit equal to balance succeeds ---

    @Test
    public void creditEqualToBalanceSucceeds() {
        openingBalance(100000);

        assertTrue("spending the whole balance is allowed", credit("Full spend", 100000) > 0);

        assertEquals(0L, helper.getBalance());
        assertEquals(2, ledgerRowCount());
    }

    // --- credit greater than balance fails ---

    @Test
    public void creditGreaterThanBalanceFails() {
        openingBalance(100000);

        long rowId = credit("Too expensive", 100001);

        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE, rowId);
        assertEquals(100000L, helper.getBalance());
    }

    // --- credit from zero balance fails ---

    @Test
    public void creditFromZeroBalanceFails() {
        long rowId = credit("Nothing to spend", 1);

        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE, rowId);
        assertEquals(0L, helper.getBalance());
    }

    // --- debit from zero balance succeeds ---

    @Test
    public void debitFromZeroBalanceSucceeds() {
        assertTrue(debit("First income", 100000) > 0);

        assertEquals(100000L, helper.getBalance());
        assertEquals(1, ledgerRowCount());
    }

    // --- debit greater than current balance succeeds ---

    @Test
    public void debitGreaterThanCurrentBalanceSucceeds() {
        openingBalance(100000);

        assertTrue("a debit is never compared against the balance",
                debit("Donation", 500000) > 0);

        assertEquals(600000L, helper.getBalance());
    }

    // --- a failed credit does not change the row count ---

    @Test
    public void failedCreditDoesNotChangeRowCount() {
        openingBalance(100000);
        int rowsBefore = ledgerRowCount();

        long rowId = credit("Overspend", 250000);
        int rowsAfter = ledgerRowCount();

        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE, rowId);
        assertEquals("a rejected credit must never create a ledger row",
                rowsBefore, rowsAfter);
        assertEquals(1, rowsAfter);
    }

    @Test
    public void repeatedRejectedCreditsNeverAccumulateRows() {
        openingBalance(100000);
        int rowsBefore = ledgerRowCount();

        for (int i = 0; i < 5; i++) {
            assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE,
                    credit("Retry " + i, 999999));
        }

        assertEquals(rowsBefore, ledgerRowCount());
        assertEquals(100000L, helper.getBalance());
    }

    // --- a student payment still does not affect the balance ---

    @Test
    public void studentPaymentDoesNotAffectTheBalance() {
        openingBalance(121000);

        long paymentId = helper.addPayment(1, "January", 2026, 10000);

        assertTrue(paymentId > 0);
        assertEquals("a payment is an obligation record, not a cash movement",
                121000L, helper.getBalance());
        assertEquals("a payment must not create a ledger row", 1, ledgerRowCount());
    }

    @Test
    public void paymentPlusMatchingManualDebitCountsTheDebitOnce() {
        openingBalance(121000);
        helper.addPayment(1, "January", 2026, 10000);
        helper.addPayment(1, "February", 2026, 10000);

        // The February collection is entered once, by hand, in the ledger.
        assertTrue(debit("February dues", 10000) > 0);

        assertEquals("the payment must not be added again on top of the debit",
                131000L, helper.getBalance());
    }

    @Test
    public void paymentsDoNotUnlockSpendingOnTheLedger() {
        openingBalance(50000);
        helper.addPayment(1, "January", 2026, 100000);

        assertEquals("a large payment must not raise the spendable balance",
                DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE, credit("Books", 60000));
        assertEquals(50000L, helper.getBalance());
    }

    // --- boundary behaviour around the approved rule ---

    @Test
    public void sequentialCreditsAreCheckedAgainstTheRunningBalance() {
        openingBalance(100000);

        assertTrue(credit("First", 60000) > 0);
        assertEquals(40000L, helper.getBalance());

        // 50000 now exceeds the remaining 40000 and must be refused.
        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE, credit("Second", 50000));
        assertEquals(40000L, helper.getBalance());

        assertTrue(credit("Third", 40000) > 0);
        assertEquals(0L, helper.getBalance());
    }

    @Test
    public void debitAfterAFullySpentBalanceRestoresSpendingRoom() {
        openingBalance(100000);
        assertTrue(credit("Spend it all", 100000) > 0);
        assertEquals(0L, helper.getBalance());

        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE, credit("Too early", 1));
        assertTrue(debit("New income", 50000) > 0);
        assertTrue(credit("Now affordable", 50000) > 0);
        assertEquals(0L, helper.getBalance());
    }

    @Test
    public void aRejectedCreditDoesNotBlockLaterValidCredits() {
        openingBalance(100000);

        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE, credit("Too much", 200000));
        assertTrue(credit("Affordable", 25000) > 0);

        assertEquals(75000L, helper.getBalance());
        assertEquals(2, ledgerRowCount());
    }

    @Test
    public void aNonPositiveAmountIsRefusedBeforeItCanCorruptTheBalance() {
        openingBalance(100000);

        assertEquals(DatabaseHelper.LEDGER_INSERT_FAILED, debit("Zero", 0));
        assertEquals(DatabaseHelper.LEDGER_INSERT_FAILED, debit("Negative", -5000));
        assertEquals(DatabaseHelper.LEDGER_INSERT_FAILED, credit("Zero", 0));

        assertEquals(100000L, helper.getBalance());
        assertEquals(1, ledgerRowCount());
    }

    @Test
    public void deletionIsNotSubjectToTheRuleSoATrustworthyHistoryCanBeCorrected() {
        openingBalance(100000);
        long id = credit("Mistake", 40000);
        assertEquals(60000L, helper.getBalance());

        // A wrong entry must remain correctable, otherwise the rule would trap
        // the user with a balance they can no longer fix.
        assertEquals(1, helper.deleteLedgerEntry((int) id));
        assertEquals(100000L, helper.getBalance());
    }
}

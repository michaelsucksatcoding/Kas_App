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

import java.util.List;

/**
 * Executed on the JVM through Robolectric against real SQLite.
 *
 * <p>Rule under test: editing a ledger entry must update the existing row and
 * must enforce the insufficient balance rule against the balance <b>without</b>
 * the record being edited. Validating a new credit (Pengeluaran) against a
 * balance that still contains the old transaction would wrongly reject
 * legitimate corrections, and validating it against a balance that ignores
 * other rows would allow an overdraft. A debit (Pemasukan) is never checked.
 */
@RunWith(RobolectricTestRunner.class)
public class LedgerUpdateRuleTest {

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

    private LedgerEntry entryById(long id) {
        List<LedgerEntry> entries = helper.getAllLedgerEntries();
        for (LedgerEntry entry : entries) {
            if (entry.getId() == (int) id) {
                return entry;
            }
        }
        return null;
    }

    private void assertRowUnchanged(long id, String description, String type, int amount) {
        LedgerEntry stored = entryById(id);
        assertTrue("the edited entry must still exist", stored != null);
        assertEquals("a rejected edit must not change the description",
                description, stored.getDescription());
        assertEquals("a rejected edit must not change the type", type, stored.getType());
        assertEquals("a rejected edit must not change the amount", amount, stored.getAmount());
    }

    // --- update debit ---

    @Test
    public void updateDebitRecalculatesTheBalanceAndKeepsTheRow() {
        long id = debit("Opening", 100000);
        assertEquals(100000L, helper.getBalance());

        assertEquals(1, helper.updateLedgerEntry((int) id, "2026-01-02",
                "Opening corrected", DatabaseHelper.LEDGER_TYPE_DEBIT, 150000));

        assertEquals("the balance must be recalculated", 150000L, helper.getBalance());
        assertEquals("an update must never duplicate the entry", 1, ledgerRowCount());

        LedgerEntry stored = entryById(id);
        assertTrue(stored != null);
        assertEquals("Opening corrected", stored.getDescription());
        assertEquals(DatabaseHelper.LEDGER_TYPE_DEBIT, stored.getType());
        assertEquals(150000, stored.getAmount());
    }

    // --- update credit ---

    @Test
    public void updateCreditRecalculatesTheBalanceAndKeepsTheRow() {
        debit("Donation", 100000);
        long id = credit("Expense", 30000);
        assertEquals(70000L, helper.getBalance());

        assertEquals(1, helper.updateLedgerEntry((int) id, "2026-01-01",
                "Expense corrected", DatabaseHelper.LEDGER_TYPE_CREDIT, 50000));

        assertEquals("100.000 debit - 50.000 credit", 50000L, helper.getBalance());
        assertEquals("an update must never duplicate the entry", 2, ledgerRowCount());

        LedgerEntry stored = entryById(id);
        assertTrue(stored != null);
        assertEquals("Expense corrected", stored.getDescription());
        assertEquals(DatabaseHelper.LEDGER_TYPE_CREDIT, stored.getType());
        assertEquals(50000, stored.getAmount());
    }

    // --- the worked example from the specification, mirrored to the rule ---
    //
    //   Current balance = Rp100.000
    //   Existing credit = Rp30.000   (the fund really holds Rp130.000)
    //   Change the credit to Rp120.000
    //
    // Rp120.000 exceeds the displayed Rp100.000 and would be wrongly rejected
    // against the unadjusted balance, but it fits the Rp130.000 the fund holds
    // once the old credit is taken back out, so the edit must be accepted.

    @Test
    public void aLargerCreditIsValidatedAgainstTheBalanceWithoutTheOldRecord() {
        debit("Donation", 130000);
        long id = credit("Books", 30000);
        assertEquals("the balance shown to the user", 100000L, helper.getBalance());

        assertEquals(1, helper.updateLedgerEntry((int) id, "2026-01-01",
                "Books", DatabaseHelper.LEDGER_TYPE_CREDIT, 120000));

        assertEquals("130.000 debit - 120.000 credit", 10000L, helper.getBalance());
        assertEquals("an update must never duplicate the entry", 2, ledgerRowCount());
        assertEquals(120000, entryById(id).getAmount());
    }

    @Test
    public void aCreditEditIsStillRejectedWhenItExceedsTheBalanceWithoutTheOldRecord() {
        debit("Donation", 130000);
        long id = credit("Books", 30000);
        assertEquals(100000L, helper.getBalance());

        // Without the old credit the fund holds 130.000, so 140.000 is an overdraft.
        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE,
                helper.updateLedgerEntry((int) id, "2026-01-01",
                        "Books", DatabaseHelper.LEDGER_TYPE_CREDIT, 140000));

        assertEquals("a rejected edit must not change the balance",
                100000L, helper.getBalance());
        assertEquals("a rejected edit must not create a row", 2, ledgerRowCount());
        assertRowUnchanged(id, "Books", DatabaseHelper.LEDGER_TYPE_CREDIT, 30000);
    }

    @Test
    public void aCreditEditIsValidatedAgainstTheBalanceAfterOtherEntriesToo() {
        long opening = debit("Opening", 100000);
        long id = credit("Books", 40000);
        assertEquals(60000L, helper.getBalance());

        // A later debit raises the balance; the edit must see it.
        assertTrue(debit("Late donation", 10000) > 0);
        assertEquals(70000L, helper.getBalance());

        // 70.000 + 40.000 (old credit removed) = 110.000 available for the new credit.
        assertEquals(1, helper.updateLedgerEntry((int) id, "2026-01-01",
                "Books", DatabaseHelper.LEDGER_TYPE_CREDIT, 110000));
        assertEquals("110.000 debit - 110.000 credit", 0L, helper.getBalance());

        // And the next edit must still respect the current balance.
        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE,
                helper.updateLedgerEntry((int) id, "2026-01-01",
                        "Books", DatabaseHelper.LEDGER_TYPE_CREDIT, 110001));
        assertEquals(0L, helper.getBalance());
        assertTrue(opening > 0);
    }

    // --- type changes ---

    @Test
    public void changingADebitToACreditIsCheckedAgainstTheBalanceWithoutThatDebit() {
        long id = debit("Opening", 100000);
        debit("Second income", 50000);
        assertEquals(150000L, helper.getBalance());

        // Without the old debit the fund holds 50.000, so a 60.000 credit overdrafts.
        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE,
                helper.updateLedgerEntry((int) id, "2026-01-02",
                        "Opening", DatabaseHelper.LEDGER_TYPE_CREDIT, 60000));
        assertEquals(150000L, helper.getBalance());
        assertRowUnchanged(id, "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 100000);

        // 50.000 fits, so the same flip is allowed once the amount is corrected.
        assertEquals(1, helper.updateLedgerEntry((int) id, "2026-01-02",
                "Opening", DatabaseHelper.LEDGER_TYPE_CREDIT, 50000));
        assertEquals("50.000 debit - 50.000 credit", 0L, helper.getBalance());
        assertEquals(DatabaseHelper.LEDGER_TYPE_CREDIT, entryById(id).getType());
    }

    @Test
    public void changingACreditToADebitIsAlwaysAllowed() {
        debit("Opening", 100000);
        long id = credit("Books", 40000);
        assertEquals(60000L, helper.getBalance());

        assertEquals(1, helper.updateLedgerEntry((int) id, "2026-01-01",
                "Refund", DatabaseHelper.LEDGER_TYPE_DEBIT, 40000));

        assertEquals("money coming in is never checked against the balance",
                140000L, helper.getBalance());
        assertEquals(2, ledgerRowCount());
        LedgerEntry stored = entryById(id);
        assertEquals("Refund", stored.getDescription());
        assertEquals(DatabaseHelper.LEDGER_TYPE_DEBIT, stored.getType());
    }

    // --- guard rails ---

    @Test
    public void aNonPositiveAmountIsRefusedWhenUpdating() {
        long id = debit("Opening", 100000);

        assertEquals(DatabaseHelper.LEDGER_INSERT_FAILED,
                helper.updateLedgerEntry((int) id, "2026-01-02",
                        "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 0));
        assertEquals(DatabaseHelper.LEDGER_INSERT_FAILED,
                helper.updateLedgerEntry((int) id, "2026-01-02",
                        "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, -5000));

        assertEquals(100000L, helper.getBalance());
        assertRowUnchanged(id, "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 100000);
    }

    @Test
    public void updatingAnUnknownEntryReportsFailureAndChangesNothing() {
        debit("Opening", 100000);

        assertEquals(DatabaseHelper.LEDGER_INSERT_FAILED,
                helper.updateLedgerEntry(9999, "2026-01-01",
                        "Ghost", DatabaseHelper.LEDGER_TYPE_DEBIT, 1));

        assertEquals(100000L, helper.getBalance());
        assertEquals(1, ledgerRowCount());
    }

    // --- the separation rule: ledger CRUD only ---

    @Test
    public void ledgerUpdatesNeverTouchThePaymentsTable() {
        helper.addStudent("Michael Trisatrio Mukti");
        helper.addPayment(1, "January", 2026, 10000);
        long id = debit("Opening", 100000);

        assertEquals(1, helper.updateLedgerEntry((int) id, "2026-01-02",
                "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 90000));

        List<Payment> payments = helper.getPaymentsByStudent(1);
        assertEquals("the payment record must be untouched", 1, payments.size());
        assertEquals(10000, payments.get(0).getAmount());
        assertEquals(90000L, helper.getBalance());
    }
}

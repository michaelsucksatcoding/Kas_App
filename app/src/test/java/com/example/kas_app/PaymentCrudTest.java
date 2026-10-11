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
 * <p>Rule under test: {@code table_payments} is an obligation tracker, so the
 * whole payment CRUD cycle (create, update, delete) must leave the fund balance
 * and the ledger untouched. Updates must rewrite the existing row rather than
 * insert a second one, and a delete must remove only the selected record.
 */
@RunWith(RobolectricTestRunner.class)
public class PaymentCrudTest {

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

    private int paymentRowCount() {
        Cursor cursor = helper.getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_PAYMENTS, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
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
        assertTrue("opening debit should be accepted",
                helper.addLedgerEntry("2026-01-01", "Opening",
                        DatabaseHelper.LEDGER_TYPE_DEBIT, amount) > 0);
    }

    private Payment onlyPaymentOf(int studentId) {
        List<Payment> payments = helper.getPaymentsByStudent(studentId);
        assertEquals("expected exactly one payment for student " + studentId, 1, payments.size());
        return payments.get(0);
    }

    // --- create ---

    @Test
    public void createPaymentStoresTheRowWithTheGivenValues() {
        long id = helper.addPayment(1, "January", 2026, 50000);

        assertTrue(id > 0);
        Payment stored = onlyPaymentOf(1);
        assertEquals((int) id, stored.getId());
        assertEquals(1, stored.getStudentId());
        assertEquals("January", stored.getMonth());
        assertEquals(2026, stored.getYear());
        assertEquals(50000, stored.getAmount());
        assertEquals(1, paymentRowCount());
    }

    // --- update ---

    @Test
    public void updatePaymentRewritesTheExistingRowInsteadOfInsertingASecondOne() {
        long firstId = helper.addPayment(1, "January", 2026, 10000);
        long secondId = helper.addPayment(1, "February", 2026, 20000);
        assertEquals(2, paymentRowCount());

        int updated = helper.updatePayment((int) firstId, 1, "March", 2027, 35000);

        assertEquals(1, updated);
        assertEquals("an update must never duplicate the payment", 2, paymentRowCount());

        List<Payment> payments = helper.getPaymentsByStudent(1);
        Payment edited = null;
        Payment untouched = null;
        for (Payment payment : payments) {
            if (payment.getId() == (int) firstId) {
                edited = payment;
            } else {
                untouched = payment;
            }
        }
        assertTrue("the edited payment must keep its primary key", edited != null);
        assertEquals("March", edited.getMonth());
        assertEquals(2027, edited.getYear());
        assertEquals(35000, edited.getAmount());

        assertTrue(untouched != null);
        assertEquals((int) secondId, untouched.getId());
        assertEquals("February", untouched.getMonth());
        assertEquals(20000, untouched.getAmount());
    }

    @Test
    public void updatePaymentOfAnUnknownIdReportsFailure() {
        assertEquals(0, helper.updatePayment(9999, 1, "January", 2026, 10000));
        assertEquals(0, paymentRowCount());
    }

    @Test
    public void updatePaymentRejectsAnInvalidAmountAndKeepsTheStoredRow() {
        long id = helper.addPayment(1, "January", 2026, 10000);

        assertEquals("zero amount", 0, helper.updatePayment((int) id, 1, "January", 2026, 0));
        assertEquals("negative amount", 0, helper.updatePayment((int) id, 1, "January", 2026, -5000));

        Payment stored = onlyPaymentOf(1);
        assertEquals("a rejected update must leave the record as it was", 10000, stored.getAmount());
    }

    // --- delete ---

    @Test
    public void deletePaymentRemovesOnlyTheSelectedRecord() {
        long firstId = helper.addPayment(1, "January", 2026, 10000);
        helper.addPayment(1, "February", 2026, 20000);

        assertEquals(1, helper.deletePayment((int) firstId));

        assertEquals(1, paymentRowCount());
        Payment remaining = onlyPaymentOf(1);
        assertEquals("the other payment must survive", "February", remaining.getMonth());
        assertEquals(20000, remaining.getAmount());

        assertEquals("deleting a missing row reports nothing removed",
                0, helper.deletePayment(9999));
        assertEquals(1, paymentRowCount());
    }

    // --- the separation rule: payment CRUD never touches the cash book ---

    @Test
    public void theWholePaymentCrudCycleLeavesTheBalanceAndTheLedgerUntouched() {
        openingBalance(100000);
        assertEquals(1, ledgerRowCount());

        // Create.
        long id = helper.addPayment(1, "January", 2026, 10000);
        assertEquals(100000L, helper.getBalance());
        assertEquals(1, ledgerRowCount());

        // Update.
        assertTrue(helper.updatePayment((int) id, 1, "January", 2026, 25000) > 0);
        assertEquals("an update is not a cash movement", 100000L, helper.getBalance());
        assertEquals(1, ledgerRowCount());

        // Delete.
        assertTrue(helper.deletePayment((int) id) > 0);
        assertEquals("a deletion is not a cash movement", 100000L, helper.getBalance());
        assertEquals(1, ledgerRowCount());
        assertEquals(0, paymentRowCount());
    }

    @Test
    public void aLargePaymentUpdateStillCannotUnlockSpendingOnTheLedger() {
        openingBalance(50000);
        long id = helper.addPayment(1, "January", 2026, 1000);
        assertTrue(helper.updatePayment((int) id, 1, "January", 2026, 500000) > 0);

        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE,
                helper.addLedgerEntry("2026-01-02", "Books",
                        DatabaseHelper.LEDGER_TYPE_CREDIT, 60000));
        assertEquals(50000L, helper.getBalance());
    }
}

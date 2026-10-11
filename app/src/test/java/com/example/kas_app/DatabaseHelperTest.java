package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Executed on the JVM through Robolectric, so the real SQLite code paths in
 * {@link DatabaseHelper} are covered: schema creation, the v1 -&gt; v2 -&gt; v3
 * migrations, the balance definition, and the payment / ledger relationship.
 */
@RunWith(RobolectricTestRunner.class)
public class DatabaseHelperTest {

    private static final String DB_NAME = "kas_app.db";
    /** Schema version shipped before Priority 0. */
    private static final int LEGACY_VERSION = 1;

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

    // --- fresh database ---

    @Test
    public void freshDatabaseCreatesAllTablesAndIsEmpty() {
        assertEquals(0L, helper.getBalance());
        assertTrue(helper.getAllStudents().isEmpty());
        assertTrue(helper.getAllLedgerEntries().isEmpty());
        assertTrue(helper.getPaymentsByStudent(1).isEmpty());
    }

    @Test
    public void createsTheTableSetTheAppExpects() {
        helper.getWritableDatabase();

        Set<String> tables = new HashSet<>();
        Cursor cursor = helper.getReadableDatabase()
                .rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        while (cursor.moveToNext()) {
            tables.add(cursor.getString(0));
        }
        cursor.close();

        assertTrue(tables.contains(DatabaseHelper.TABLE_STUDENTS));
        assertTrue(tables.contains(DatabaseHelper.TABLE_PAYMENTS));
        assertTrue(tables.contains(DatabaseHelper.TABLE_LEDGER));
    }

    // --- migration ---

    /**
     * Builds a database exactly as an older build of the app left it: the three
     * tables, no index, real rows in each of them, {@code user_version} set to
     * the requested legacy version. The ledger table deliberately has no
     * {@code payment_id} column, because that column only exists from v3.
     */
    private void createLegacyDatabaseWithData(int version) {
        context.deleteDatabase(DB_NAME);
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(DB_NAME).getAbsolutePath(), null);
        legacy.execSQL("CREATE TABLE " + DatabaseHelper.TABLE_STUDENTS
                + "(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
        legacy.execSQL("CREATE TABLE " + DatabaseHelper.TABLE_PAYMENTS
                + "(id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER, month TEXT,"
                + " year INTEGER, amount INTEGER)");
        legacy.execSQL("CREATE TABLE " + DatabaseHelper.TABLE_LEDGER
                + "(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT, description TEXT,"
                + " type TEXT, amount INTEGER)");

        insertStudent(legacy, 1, "Achtar Gulfam Furozh");
        insertStudent(legacy, 2, "Alif Radinka Putra");
        insertPayment(legacy, 1, 1, "January", 2026, 10000);
        insertPayment(legacy, 2, 1, "February", 2026, 10000);
        insertLedger(legacy, "2026-01-01", "Opening balance", DatabaseHelper.LEDGER_TYPE_DEBIT, 121000);

        legacy.setVersion(version);
        legacy.close();
    }

    private void createLegacyV1DatabaseWithData() {
        createLegacyDatabaseWithData(LEGACY_VERSION);
    }

    private void insertStudent(SQLiteDatabase db, int id, String name) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", name);
        db.insert(DatabaseHelper.TABLE_STUDENTS, null, values);
    }

    private void insertPayment(SQLiteDatabase db, int id, int studentId, String month, int year, int amount) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("student_id", studentId);
        values.put("month", month);
        values.put("year", year);
        values.put("amount", amount);
        db.insert(DatabaseHelper.TABLE_PAYMENTS, null, values);
    }

    private void insertLedger(SQLiteDatabase db, String date, String description, String type, int amount) {
        ContentValues values = new ContentValues();
        values.put("date", date);
        values.put("description", description);
        values.put("type", type);
        values.put("amount", amount);
        db.insert(DatabaseHelper.TABLE_LEDGER, null, values);
    }

    private boolean hasIndex(SQLiteDatabase db, String name) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=?", new String[]{name});
        try {
            return cursor.moveToFirst() && cursor.getInt(0) > 0;
        } finally {
            cursor.close();
        }
    }

    private boolean hasColumn(SQLiteDatabase db, String table, String column) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(nameIndex))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    /**
     * v3 added the Fund Data mirror link. The upgrade must only append the
     * nullable column: existing rows keep {@code NULL} (manually entered cash
     * book rows) and no stored balance may change.
     */
    @Test
    public void upgradingFromV2AddsTheFundDataLinkWithoutTouchingRows() {
        createLegacyDatabaseWithData(2);

        DatabaseHelper upgraded = new DatabaseHelper(context);
        try {
            assertTrue("v3 must append the payment_id link column",
                    hasColumn(upgraded.getReadableDatabase(),
                            DatabaseHelper.TABLE_LEDGER,
                            DatabaseHelper.COLUMN_LEDGER_PAYMENT_ID));

            assertEquals("v2 ledger rows must survive", 1, upgraded.getAllLedgerEntries().size());
            assertEquals("v2 payments must survive", 2, upgraded.getPaymentsByStudent(1).size());
            assertEquals("v2 balance must be unchanged by the upgrade",
                    121000L, upgraded.getBalance());
        } finally {
            upgraded.close();
        }
    }

    @Test
    public void upgradingFromV1PreservesEveryRecord() {
        createLegacyV1DatabaseWithData();

        DatabaseHelper upgraded = new DatabaseHelper(context);
        try {
            assertEquals("v1 students must survive the upgrade", 2, upgraded.getAllStudents().size());
            assertEquals("Achtar Gulfam Furozh", upgraded.getAllStudents().get(0).getName());

            List<Payment> payments = upgraded.getPaymentsByStudent(1);
            assertEquals("v1 payments must survive the upgrade", 2, payments.size());

            List<LedgerEntry> ledger = upgraded.getAllLedgerEntries();
            assertEquals("v1 ledger must survive the upgrade", 1, ledger.size());
            assertEquals(121000, ledger.get(0).getAmount());
            assertEquals(DatabaseHelper.LEDGER_TYPE_DEBIT, ledger.get(0).getType());

            assertEquals("v1 balance must be unchanged by the upgrade", 121000L, upgraded.getBalance());
        } finally {
            upgraded.close();
        }
    }

    @Test
    public void upgradeIsIdempotentAndAddsThePaymentIndex() {
        createLegacyV1DatabaseWithData();
        assertFalse("legacy v1 database has no index", hasIndex(SQLiteDatabase.openDatabase(
                context.getDatabasePath(DB_NAME).getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY),
                "idx_payments_student_id"));

        DatabaseHelper upgraded = new DatabaseHelper(context);
        try {
            upgraded.getWritableDatabase();
            assertTrue("upgrade must add the payment lookup index",
                    hasIndex(upgraded.getReadableDatabase(), "idx_payments_student_id"));

            // Re-running the same DDL must not throw and must not duplicate data.
            upgraded.getWritableDatabase().execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_payments_student_id ON "
                            + DatabaseHelper.TABLE_PAYMENTS + "(student_id)");
            assertEquals(2, upgraded.getAllStudents().size());
        } finally {
            upgraded.close();
        }
    }

    @Test
    public void upgradeRepairsADatabaseMissingItsTables() {
        context.deleteDatabase(DB_NAME);
        SQLiteDatabase broken = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(DB_NAME).getAbsolutePath(), null);
        broken.setVersion(LEGACY_VERSION);
        broken.close();

        DatabaseHelper upgraded = new DatabaseHelper(context);
        try {
            assertEquals(0L, upgraded.getBalance());
            assertTrue(upgraded.getAllStudents().isEmpty());
        } finally {
            upgraded.close();
        }
    }

    // --- balance ---

    @Test
    public void balanceIsDebitsMinusCredits() {
        helper.addLedgerEntry("2026-01-01", "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 121000);
        assertEquals(121000L, helper.getBalance());

        helper.addLedgerEntry("2026-02-01", "Books", DatabaseHelper.LEDGER_TYPE_CREDIT, 21000);
        assertEquals(100000L, helper.getBalance());

        helper.addLedgerEntry("2026-03-01", "Donation", DatabaseHelper.LEDGER_TYPE_DEBIT, 5000);
        assertEquals(105000L, helper.getBalance());
    }

    @Test
    public void creditCannotPushTheBalanceNegative() {
        // The insufficient balance rule rejects a Pengeluaran larger than the
        // balance: the credit is refused and the balance stays where it was.
        // The full rule is asserted in LedgerBalanceRuleTest.
        helper.addLedgerEntry("2026-01-01", "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 1000);

        long rowId = helper.addLedgerEntry("2026-01-02", "Expense",
                DatabaseHelper.LEDGER_TYPE_CREDIT, 2500);

        assertEquals(DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE, rowId);
        assertEquals(1000L, helper.getBalance());
    }

    @Test
    public void deletingALedgerEntryMovesTheBalance() {
        long debitId = helper.addLedgerEntry("2026-01-01", "Opening",
                DatabaseHelper.LEDGER_TYPE_DEBIT, 100000);
        helper.addLedgerEntry("2026-01-02", "Expense", DatabaseHelper.LEDGER_TYPE_CREDIT, 40000);
        assertEquals(60000L, helper.getBalance());

        assertEquals(1, helper.deleteLedgerEntry((int) debitId));
        assertEquals(-40000L, helper.getBalance());
        assertEquals("deleting a missing row reports nothing removed",
                0, helper.deleteLedgerEntry(9999));
    }

    // --- payment / ledger relationship ---

    @Test
    public void paymentsDoNotAffectTheBalanceAndDoNotCreateLedgerRows() {
        helper.addStudent("Michael Trisatrio Mukti");
        helper.addLedgerEntry("2026-01-01", "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 50000);
        assertEquals(50000L, helper.getBalance());

        helper.addPayment(1, "January", 2026, 10000);
        helper.addPayment(1, "February", 2026, 10000);

        assertEquals("a payment is a dues record, not a cash movement",
                50000L, helper.getBalance());
        assertEquals("a payment must not create a ledger row",
                1, helper.getAllLedgerEntries().size());

        helper.addLedgerEntry("2026-02-01", "Fees", DatabaseHelper.LEDGER_TYPE_CREDIT, 20000);
        assertEquals(30000L, helper.getBalance());
    }

    @Test
    public void paymentsAndTheirMatchingManualEntriesAreNeverCountedTwice() {
        helper.addStudent("Rayyan Al Farhan");
        helper.addLedgerEntry("2026-01-01", "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 121000);

        for (int i = 0; i < 12; i++) {
            helper.addPayment(1, DatabaseHelper.PAYMENT_MONTHS[i], 2026, 10000);
        }
        assertEquals("120000 of recorded payments must not be added on top of the ledger",
                121000L, helper.getBalance());

        // Recording the same month's money once in the cash book moves the
        // balance exactly one month, never two.
        helper.addLedgerEntry("2026-01-31", "January dues", DatabaseHelper.LEDGER_TYPE_DEBIT, 10000);
        assertEquals(131000L, helper.getBalance());
    }

    @Test
    public void balanceIsUnaffectedByUnknownLedgerTypes() {
        helper.addLedgerEntry("2026-01-01", "Opening", DatabaseHelper.LEDGER_TYPE_DEBIT, 10000);
        helper.addLedgerEntry("2026-01-02", "Odd", "transfer", 50000);
        assertEquals(10000L, helper.getBalance());
    }

    @Test
    public void balanceDoesNotOverflowOnLargeSums() {
        int max = Integer.MAX_VALUE;
        helper.addLedgerEntry("2026-01-01", "A", DatabaseHelper.LEDGER_TYPE_DEBIT, max);
        helper.addLedgerEntry("2026-01-02", "B", DatabaseHelper.LEDGER_TYPE_DEBIT, max);
        helper.addLedgerEntry("2026-01-03", "C", DatabaseHelper.LEDGER_TYPE_CREDIT, max);
        assertEquals((long) max, helper.getBalance());
    }

    // --- payment reads ---

    @Test
    public void paymentHistoryIsChronologicalNotAlphabetical() {
        helper.addStudent("Joicha Falent Big Diyo");
        for (int i = 0; i < DatabaseHelper.PAYMENT_MONTHS.length; i++) {
            helper.addPayment(1, DatabaseHelper.PAYMENT_MONTHS[i], 2026, 1000);
        }
        helper.addPayment(1, "January", 2025, 500);

        List<Payment> payments = helper.getPaymentsByStudent(1);

        assertEquals(13, payments.size());
        // Newest first: 2026 December down to 2026 January, then 2025 January.
        // Sorted as text this would start with "April", which is the bug.
        assertEquals("December", payments.get(0).getMonth());
        assertEquals(2026, payments.get(0).getYear());
        assertEquals("November", payments.get(1).getMonth());
        assertEquals("March", payments.get(9).getMonth());
        assertEquals("January", payments.get(11).getMonth());
        assertEquals(2026, payments.get(11).getYear());
        assertEquals("January", payments.get(12).getMonth());
        assertEquals(2025, payments.get(12).getYear());
    }

    @Test
    public void paymentHistoryIsChronologicalAcrossYearsWithinOneStudent() {
        helper.addStudent("Daffa Izzati Nazhifah");
        helper.addPayment(1, "December", 2025, 1000);
        helper.addPayment(1, "January", 2026, 1000);

        List<Payment> payments = helper.getPaymentsByStudent(1);

        assertEquals(2, payments.size());
        assertEquals("January", payments.get(0).getMonth());
        assertEquals(2026, payments.get(0).getYear());
        assertEquals("December", payments.get(1).getMonth());
        assertEquals(2025, payments.get(1).getYear());
    }

    @Test
    public void paymentHistoryIsScopedToOneStudent() {
        helper.addStudent("Aulia Nursalsabila");
        helper.addStudent("Aulia Salsabila Putri Dearie");
        helper.addPayment(1, "January", 2026, 10000);
        helper.addPayment(2, "January", 2026, 20000);

        assertEquals(1, helper.getPaymentsByStudent(1).size());
        assertEquals(10000, helper.getPaymentsByStudent(1).get(0).getAmount());
        assertEquals(1, helper.getPaymentsByStudent(2).size());
        assertEquals(20000, helper.getPaymentsByStudent(2).get(0).getAmount());
    }

    // --- write results ---

    @Test
    public void insertMethodsReportSuccess() {
        assertTrue(helper.addStudent("Bimo Aditya Sasmito") > 0);
        assertTrue(helper.addPayment(1, "March", 2026, 10000) > 0);
        assertTrue(helper.addLedgerEntry("2026-03-01", "Dues",
                DatabaseHelper.LEDGER_TYPE_DEBIT, 10000) > 0);
    }
}

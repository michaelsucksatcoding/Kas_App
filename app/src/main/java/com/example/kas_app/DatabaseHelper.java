package com.example.kas_app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The single data access point of the app.
 *
 * <h3>Accounting model</h3>
 * <ul>
 *   <li>{@code table_ledger} is the cash book. It is the <b>only</b> source of
 *       truth for the class fund balance. A {@code debit} row is
 *       <b>Pemasukan</b> (money entering the fund), a {@code credit} row is
 *       <b>Pengeluaran</b> (money leaving it).</li>
 *   <li><b>Fund Data rule:</b> every amount recorded for a student through
 *       Fund Data is mirrored, in the same transaction, as exactly one
 *       {@code table_ledger} row of type {@code debit} carrying that row's
 *       {@code payment_id}. Because {@code debit} means Pemasukan, that mirror
 *       increases the balance like every other debit: money a student pays
 *       into the class fund is money the fund holds. The balance is therefore
 *       {@code SUM(debit) - SUM(credit)} over all ledger rows, with no
 *       exception for any row.</li>
 *   <li>{@code table_payments} records which student has paid which month. It
 *       stays an obligation tracker; the balance never reads it directly, it
 *       reads the mirrored ledger rows instead. A raw {@link #addPayment}
 *       writes only the obligation row and is the building block used inside
 *       {@link #addFundDataPayment}; screens that record money use the Fund
 *       Data methods so a payment can never enter the app without its ledger
 *       counterpart.</li>
 * </ul>
 *
 * <h3>Insufficient balance rule</h3>
 * A manually entered <b>credit</b> (Pengeluaran) is stored only when
 * {@code amount <= current balance}; otherwise the insert is rejected inside a
 * transaction and the balance is left untouched. A debit (Pemasukan) is never
 * compared against the balance, so the fund can grow from zero, and neither is
 * a Fund Data debit: that row is money arriving, inserted by
 * {@link #addFundDataPayment}. The rule lives in {@link #addLedgerEntry} so
 * that no caller can bypass it.
 *
 * <h3>Schema history</h3>
 * <pre>
 *   1 -> initial schema: table_students, table_payments, table_ledger
 *   2 -> additive only: index on table_payments(student_id)
 *   3 -> additive only: table_ledger.payment_id (Fund Data mirror link)
 * </pre>
 * Every version step is additive. No migration drops, recreates or rewrites an
 * existing table, and no migration deletes existing rows.
 *
 * <h3>Connection handling</h3>
 * Methods here deliberately do not call {@code db.close()}. Each screen builds
 * its own helper, so closing the database from inside one screen could close the
 * connection another screen is reading and raise an SQLite exception. The
 * framework owns the connection lifetime; it is released when the process dies.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "kas_app.db";

    /**
     * v3 is the current schema. v1 -&gt; v3 differs from v2 only by an added
     * index and an added nullable column, so upgrading an existing database
     * copies no data and loses no data.
     */
    private static final int DATABASE_VERSION = 3;

    // Table names
    public static final String TABLE_STUDENTS = "table_students";
    public static final String TABLE_PAYMENTS = "table_payments";
    public static final String TABLE_LEDGER = "table_ledger";

    // Ledger entry types
    public static final String LEDGER_TYPE_CREDIT = "credit";
    public static final String LEDGER_TYPE_DEBIT = "debit";

    /**
     * Result codes for {@link #addLedgerEntry}. These preserve the convention
     * established in Priority 0, where a negative return meant the row was not
     * stored, and add one distinguishable code so the caller can tell a business
     * rule rejection apart from a storage failure.
     */
    /** The row could not be stored. Also returned for an invalid amount. */
    public static final long LEDGER_INSERT_FAILED = -1L;
    /** A credit was refused because it exceeded the current balance. */
    public static final long LEDGER_INSERT_INSUFFICIENT_BALANCE = -2L;

    /**
     * Result codes for {@link #deleteStudent(int)}. A student is only removed
     * when they have no payment history, so that historical financial records
     * are never destroyed.
     */
    public static final int STUDENT_DELETE_DELETED = 0;
    public static final int STUDENT_DELETE_HAS_HISTORY = 1;
    public static final int STUDENT_DELETE_NOT_FOUND = 2;

    /**
     * Month vocabulary persisted in {@link #COLUMN_PAYMENT_MONTH}, in
     * chronological order. This is the single source of truth for the values
     * the payment month spinner offers, for the chronological ordering of the
     * payment history, and for any migration that later normalises this column.
     * Changing these strings requires an explicit data migration.
     */
    public static final String[] PAYMENT_MONTHS = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    // Common column
    public static final String COLUMN_ID = "id";

    // Students table columns
    public static final String COLUMN_STUDENT_NAME = "name";

    // Payments table columns
    public static final String COLUMN_PAYMENT_STUDENT_ID = "student_id";
    public static final String COLUMN_PAYMENT_MONTH = "month";
    public static final String COLUMN_PAYMENT_YEAR = "year";
    public static final String COLUMN_PAYMENT_AMOUNT = "amount";

    // Ledger table columns
    public static final String COLUMN_LEDGER_DATE = "date";
    public static final String COLUMN_LEDGER_DESCRIPTION = "description";
    public static final String COLUMN_LEDGER_TYPE = "type"; // "credit" or "debit"
    public static final String COLUMN_LEDGER_AMOUNT = "amount";
    /**
     * The Fund Data payment this row mirrors, or {@code NULL} for a manually
     * entered cash-book row. It is the link that lets one Fund Data input
     * produce exactly one ledger entry, lets an edit update that entry instead
     * of adding a second one, and lets a delete remove it. The balance does
     * not need it: a Fund Data mirror is a {@code debit} like any other, and
     * {@code debit} means Pemasukan.
     */
    public static final String COLUMN_LEDGER_PAYMENT_ID = "payment_id";

    // Create table statements
    private static final String CREATE_TABLE_STUDENTS = "CREATE TABLE IF NOT EXISTS " + TABLE_STUDENTS + "("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_STUDENT_NAME + " TEXT" + ")";

    private static final String CREATE_TABLE_PAYMENTS = "CREATE TABLE IF NOT EXISTS " + TABLE_PAYMENTS + "("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_PAYMENT_STUDENT_ID + " INTEGER,"
            + COLUMN_PAYMENT_MONTH + " TEXT,"
            + COLUMN_PAYMENT_YEAR + " INTEGER,"
            + COLUMN_PAYMENT_AMOUNT + " INTEGER" + ")";

    private static final String CREATE_TABLE_LEDGER = "CREATE TABLE IF NOT EXISTS " + TABLE_LEDGER + "("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_LEDGER_DATE + " TEXT,"
            + COLUMN_LEDGER_DESCRIPTION + " TEXT,"
            + COLUMN_LEDGER_TYPE + " TEXT,"
            + COLUMN_LEDGER_AMOUNT + " INTEGER,"
            + COLUMN_LEDGER_PAYMENT_ID + " INTEGER" + ")";

    /**
     * {@code student_id} is the only column the app ever filters payments by, so
     * it is the only index the app needs. Additive and idempotent.
     */
    private static final String CREATE_INDEX_PAYMENTS_STUDENT = "CREATE INDEX IF NOT EXISTS "
            + "idx_payments_student_id ON " + TABLE_PAYMENTS + "(" + COLUMN_PAYMENT_STUDENT_ID + ")";

    /**
     * The one and only balance definition over {@code table_ledger}:
     * <pre>
     *   balance = SUM(debit) - SUM(credit)
     * </pre>
     * A {@code debit} row is Pemasukan and is counted positively, a
     * {@code credit} row is Pengeluaran and is counted negatively. The
     * {@code payment_id} link is deliberately <b>not</b> part of the formula:
     * a Fund Data mirror is a debit like every other debit, so the Fund Data
     * rule and the accounting rule agree instead of competing. An unrecognised
     * type contributes zero. {@code table_payments} is intentionally excluded:
     * the balance reads the mirrored rows, never the obligation table itself.
     */
    private static final String SELECT_BALANCE =
            "SELECT COALESCE(SUM(CASE"
                    + " WHEN " + COLUMN_LEDGER_TYPE + " = ? THEN " + COLUMN_LEDGER_AMOUNT
                    + " WHEN " + COLUMN_LEDGER_TYPE + " = ? THEN -" + COLUMN_LEDGER_AMOUNT
                    + " ELSE 0 END), 0) FROM " + TABLE_LEDGER;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTablesIfNotExist(db);
        createPaymentsStudentIndexIfNotExist(db);
    }

    /**
     * Stepwise, data preserving upgrade. Never drops a table and never deletes
     * or rewrites a row: students, payments and ledger entries are real
     * financial records.
     *
     * <p>{@code SQLiteOpenHelper} already runs this inside a transaction, so a
     * failing step rolls the whole upgrade back.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            migrate1To2(db);
        }
        if (oldVersion < 3) {
            migrate2To3(db);
        }
    }

    /**
     * v1 -&gt; v2. Purely additive: repairs a database whose tables are missing
     * or partially created and adds the payment lookup index. Existing rows are
     * left untouched.
     */
    private void migrate1To2(SQLiteDatabase db) {
        createTablesIfNotExist(db);
        createPaymentsStudentIndexIfNotExist(db);
    }

    /**
     * v2 -&gt; v3. Purely additive: appends the nullable
     * {@link #COLUMN_LEDGER_PAYMENT_ID} column so a ledger row can name the
     * Fund Data payment it mirrors. Existing rows keep {@code NULL}, which
     * means "manually entered cash-book row", so no stored balance changes.
     *
     * <p>The column is only added when it is missing, because a v1 database
     * that runs both migrations in one upgrade may already have received the
     * column from the table repair in {@link #migrate1To2}. No row is
     * rewritten and no table is dropped.
     */
    private void migrate2To3(SQLiteDatabase db) {
        addLedgerPaymentIdIfMissing(db);
    }

    private static void addLedgerPaymentIdIfMissing(SQLiteDatabase db) {
        boolean present = false;
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + TABLE_LEDGER + ")", null);
        try {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                if (COLUMN_LEDGER_PAYMENT_ID.equals(cursor.getString(nameIndex))) {
                    present = true;
                    break;
                }
            }
        } finally {
            cursor.close();
        }
        if (!present) {
            db.execSQL("ALTER TABLE " + TABLE_LEDGER
                    + " ADD COLUMN " + COLUMN_LEDGER_PAYMENT_ID + " INTEGER");
        }
    }

    private static void createTablesIfNotExist(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_STUDENTS);
        db.execSQL(CREATE_TABLE_PAYMENTS);
        db.execSQL(CREATE_TABLE_LEDGER);
    }

    private static void createPaymentsStudentIndexIfNotExist(SQLiteDatabase db) {
        db.execSQL(CREATE_INDEX_PAYMENTS_STUDENT);
    }

    public void importStudentsFromCSV(Context context) {
        SQLiteDatabase db = this.getWritableDatabase();
        // Check if table is already populated to avoid duplicates
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_STUDENTS, null);
        boolean alreadyImported;
        try {
            alreadyImported = cursor.moveToFirst() && cursor.getInt(0) > 0;
        } finally {
            cursor.close();
        }
        if (alreadyImported) {
            return;
        }

        // A seed import writes many rows, so it is done in one transaction: a
        // failure part way through leaves the table empty rather than half filled.
        db.beginTransaction();
        InputStream is = null;
        try {
            is = context.getAssets().open("students.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                if (columns.length >= 2) {
                    ContentValues values = new ContentValues();
                    values.put(COLUMN_ID, columns[0].trim());
                    values.put(COLUMN_STUDENT_NAME, columns[1].trim());
                    db.insert(TABLE_STUDENTS, null, values);
                }
            }
            db.setTransactionSuccessful();
        } catch (IOException e) {
            // Transaction is not marked successful, so it rolls back and the
            // import is retried on the next launch.
            Log.w("DatabaseHelper", "Failed to import students.csv", e);
        } finally {
            db.endTransaction();
            closeQuietly(is);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Nothing useful to do while unwinding.
        }
    }

    // --- Student Methods ---

    /**
     * @return the new row id, or {@code -1} when the student could not be stored.
     */
    public long addStudent(String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STUDENT_NAME, name);
        return db.insert(TABLE_STUDENTS, null, values);
    }

    /**
     * Renames a student in place.
     *
     * <p>The row identity is preserved: the update is keyed on the existing id,
     * so this can never create a second student and never re-points a student's
     * payment history.
     *
     * <p>Note that {@code SQLiteDatabase.update} reports 0 both when no such
     * student exists and when the new name is byte for byte the name already
     * stored. Saving an unchanged name is a success, not a failure, so this
     * method reports on whether the student exists and now carries the given
     * name.
     *
     * @return {@code 1} when the student exists and now has this name,
     *         {@code 0} when no student has that id
     */
    public int updateStudent(int studentId, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STUDENT_NAME, name);
        if (db.update(TABLE_STUDENTS, values, COLUMN_ID + "=?", new String[]{String.valueOf(studentId)}) > 0) {
            return 1;
        }
        return studentExists(studentId) ? 1 : 0;
    }

    private boolean studentExists(int studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_STUDENTS, new String[]{COLUMN_ID},
                COLUMN_ID + "=?", new String[]{String.valueOf(studentId)}, null, null, null, "1");
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    /**
     * @return how many payment records belong to this student, {@code 0} when
     *         the student is unknown or has never paid for anything
     */
    public int countPaymentsForStudent(int studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PAYMENTS
                + " WHERE " + COLUMN_PAYMENT_STUDENT_ID + "=?",
                new String[]{String.valueOf(studentId)});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    /**
     * Removes a student from the active roster.
     *
     * <p><b>Deletion policy: a student with payment history is never
     * deleted.</b> {@code table_payments} is a financial record of money that
     * was actually collected, so removing the student would orphan those rows
     * and destroy history that the balance was derived from. This method
     * therefore refuses to delete and leaves both the student and the payments
     * exactly as they are.
     *
     * <p>There is no {@code ON DELETE CASCADE} and no foreign key on
     * {@code table_payments.student_id}; the historical rows are preserved by
     * simply not deleting the student.
     *
     * <p>The rule is enforced here rather than only in the UI so no screen can
     * bypass it.
     *
     * @return {@link #STUDENT_DELETE_DELETED} when the row was removed,
     *         {@link #STUDENT_DELETE_HAS_HISTORY} when payment history blocks it,
     *         {@link #STUDENT_DELETE_NOT_FOUND} when no such student exists
     */
    public int deleteStudent(int studentId) {
        if (countPaymentsForStudent(studentId) > 0) {
            return STUDENT_DELETE_HAS_HISTORY;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        int removed = db.delete(TABLE_STUDENTS, COLUMN_ID + "=?",
                new String[]{String.valueOf(studentId)});
        return removed > 0 ? STUDENT_DELETE_DELETED : STUDENT_DELETE_NOT_FOUND;
    }

    public List<Student> getAllStudents() {
        List<Student> studentList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_STUDENTS + " ORDER BY " + COLUMN_STUDENT_NAME + " ASC";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Student student = new Student(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STUDENT_NAME))
                );
                studentList.add(student);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return studentList;
    }

    // --- Payment Methods ---

    /**
     * Records that a student paid for a month: <b>only</b> the obligation row.
     *
     * <p>This is the raw building block used inside the transactional Fund
     * Data methods. Screens that record money call
     * {@link #addFundDataPayment} instead, which wraps this call together with
     * the mirrored ledger entry, because the raw row alone must never be what
     * the app stores for a new Fund Data input.
     *
     * @return the new row id, or {@code -1} when the payment could not be stored.
     */
    public long addPayment(int studentId, String month, int year, int amount) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PAYMENT_STUDENT_ID, studentId);
        values.put(COLUMN_PAYMENT_MONTH, month);
        values.put(COLUMN_PAYMENT_YEAR, year);
        values.put(COLUMN_PAYMENT_AMOUNT, amount);
        return db.insert(TABLE_PAYMENTS, null, values);
    }

    public List<Payment> getPaymentsByStudent(int studentId) {
        List<Payment> paymentList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PAYMENTS, null,
                COLUMN_PAYMENT_STUDENT_ID + "=?", new String[]{String.valueOf(studentId)},
                null, null, COLUMN_PAYMENT_YEAR + " DESC, " + buildChronologicalMonthOrderBy() + " DESC");

        if (cursor.moveToFirst()) {
            do {
                Payment payment = new Payment(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PAYMENT_STUDENT_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PAYMENT_MONTH)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PAYMENT_YEAR)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PAYMENT_AMOUNT))
                );
                paymentList.add(payment);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return paymentList;
    }

    /**
     * Updates an existing payment record: <b>only</b> the obligation row.
     *
     * <p>Screens call {@link #updateFundDataPayment}, which wraps this call in
     * a transaction together with the mirrored ledger row.
     *
     * @return 1 on success, 0 on failure or invalid amount
     */
    public int updatePayment(int paymentId, int studentId, String month, int year, int amount) {
        if (!AmountValidator.validate(amount).isValid()) {
            return 0;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PAYMENT_STUDENT_ID, studentId);
        values.put(COLUMN_PAYMENT_MONTH, month);
        values.put(COLUMN_PAYMENT_YEAR, year);
        values.put(COLUMN_PAYMENT_AMOUNT, amount);
        return db.update(TABLE_PAYMENTS, values, COLUMN_ID + "=?", new String[]{String.valueOf(paymentId)});
    }

    /**
     * Removes a payment record: <b>only</b> the obligation row.
     *
     * <p>Screens call {@link #deleteFundDataPayment}, which wraps this call in
     * a transaction together with the mirrored ledger row.
     *
     * @return 1 on success, 0 when no record was found
     */
    public int deletePayment(int paymentId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_PAYMENTS, COLUMN_ID + "=?", new String[]{String.valueOf(paymentId)});
    }

    /**
     * Maps a stored month name to its chronological position so payment history
     * is ordered by real calendar order. Without this, {@code month} is a TEXT
     * column and would sort alphabetically ("September" before "May").
     *
     * <p>Values come from the {@link #PAYMENT_MONTHS} constant, never from user
     * input, so inlining them into the statement is safe.
     */
    private static String buildChronologicalMonthOrderBy() {
        StringBuilder builder = new StringBuilder("CASE ").append(COLUMN_PAYMENT_MONTH);
        for (int i = 0; i < PAYMENT_MONTHS.length; i++) {
            builder.append(" WHEN '").append(PAYMENT_MONTHS[i]).append("' THEN ").append(i + 1);
        }
        return builder.append(" ELSE ").append(PAYMENT_MONTHS.length + 1).append(" END").toString();
    }

    // --- Fund Data <-> ledger ---

    /**
     * How a Fund Data ledger row is labelled so the Fund Report can be read
     * back: {@code "Fund Data - <student> - <month> <year>"} when the student
     * is known, otherwise without the name. This is stored data, not UI text,
     * exactly like the English month names already stored in
     * {@link #COLUMN_PAYMENT_MONTH}.
     */
    private static final String FUND_DATA_DESCRIPTION_PREFIX = "Fund Data";

    /**
     * Records money received for a student through Fund Data: the obligation
     * row <b>and</b> exactly one mirrored ledger row, atomically.
     *
     * <p>The mirror is a {@link #LEDGER_TYPE_DEBIT} row carrying
     * {@link #COLUMN_LEDGER_PAYMENT_ID}. Because {@code debit} means
     * Pemasukan, the mirror increases the balance like any other debit (see
     * {@link #SELECT_BALANCE}). Because the money is coming <i>in</i>, the
     * insufficient balance rule is not applied here: a payment can never
     * exceed the balance.
     *
     * <p>Both rows are written inside one transaction, so the database can
     * never hold a payment without its ledger entry or a ledger entry without
     * its payment. Reads (loading history, refreshing a screen, rotating the
     * device) never call this method, so they can never duplicate an entry.
     *
     * <p>Amount validation reuses {@link AmountValidator}: an empty, zero or
     * negative amount is refused before anything is written.
     *
     * @return the new payment row id, or {@code -1} when the amount was
     *         invalid or the rows could not be stored
     */
    public long addFundDataPayment(int studentId, String month, int year, int amount) {
        if (!AmountValidator.validate(amount).isValid()) {
            return -1L;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            long paymentId = addPayment(studentId, month, year, amount);
            if (paymentId == -1L) {
                // Not marked successful, so the transaction rolls back.
                return -1L;
            }
            if (insertFundDataLedgerEntry(db, paymentId, studentId, month, year, amount) == -1L) {
                return -1L;
            }
            db.setTransactionSuccessful();
            return paymentId;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Updates a Fund Data record and the ledger row it mirrors, atomically.
     *
     * <p>When a mirror exists it is updated in place (amount and description
     * follow the record, the stored date does not change), so an edit can
     * never leave {@code Rp20.000} in the ledger next to a {@code Rp30.000}
     * payment and can never add a second row for the same payment.
     *
     * <p>A payment recorded before the Fund Data rule has no mirror row.
     * Editing or deleting such a record changes only the payment: historical
     * payment history is deliberately never pushed into the balance behind
     * the user's back.
     *
     * @return {@code 1} when the payment exists with these values,
     *         {@code 0} when the payment was not found or the amount was invalid
     */
    public int updateFundDataPayment(int paymentId, int studentId, String month, int year, int amount) {
        if (!AmountValidator.validate(amount).isValid()) {
            return 0;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            int updated = updatePayment(paymentId, studentId, month, year, amount);
            if (updated == 0 && !paymentExists(paymentId)) {
                return 0;
            }
            int ledgerId = findLedgerIdForPayment(db, paymentId);
            if (ledgerId != -1) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_LEDGER_DESCRIPTION,
                        buildFundDataDescription(db, studentId, month, year));
                values.put(COLUMN_LEDGER_AMOUNT, amount);
                if (db.update(TABLE_LEDGER, values, COLUMN_ID + "=?",
                        new String[]{String.valueOf(ledgerId)}) <= 0) {
                    return 0;
                }
            }
            db.setTransactionSuccessful();
            return 1;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Deletes a Fund Data record and the ledger row it mirrors, atomically, so
     * no orphan entry keeps moving a balance that no longer has a payment
     * behind it.
     *
     * @return the number of payment rows removed, {@code 0} when nothing matched
     */
    public int deleteFundDataPayment(int paymentId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            int ledgerId = findLedgerIdForPayment(db, paymentId);
            if (ledgerId != -1) {
                db.delete(TABLE_LEDGER, COLUMN_ID + "=?",
                        new String[]{String.valueOf(ledgerId)});
            }
            int deleted = deletePayment(paymentId);
            if (deleted <= 0) {
                return 0;
            }
            db.setTransactionSuccessful();
            return deleted;
        } finally {
            db.endTransaction();
        }
    }

    private long insertFundDataLedgerEntry(SQLiteDatabase db, long paymentId,
                                           int studentId, String month, int year, int amount) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_LEDGER_DATE, todayIsoDate());
        values.put(COLUMN_LEDGER_DESCRIPTION, buildFundDataDescription(db, studentId, month, year));
        values.put(COLUMN_LEDGER_TYPE, LEDGER_TYPE_DEBIT);
        values.put(COLUMN_LEDGER_AMOUNT, amount);
        values.put(COLUMN_LEDGER_PAYMENT_ID, paymentId);
        return db.insert(TABLE_LEDGER, null, values);
    }

    /**
     * The ledger date of a new Fund Data entry: the day the money was
     * recorded, in the {@code yyyy-MM-dd} format the ledger already uses. No
     * date is converted or shifted.
     */
    private static String todayIsoDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private static String buildFundDataDescription(SQLiteDatabase db, int studentId,
                                                   String month, int year) {
        String suffix = month + " " + year;
        String studentName = null;
        Cursor cursor = db.query(TABLE_STUDENTS, new String[]{COLUMN_STUDENT_NAME},
                COLUMN_ID + "=?", new String[]{String.valueOf(studentId)}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                studentName = cursor.getString(0);
            }
        } finally {
            cursor.close();
        }
        if (studentName == null) {
            return FUND_DATA_DESCRIPTION_PREFIX + " - " + suffix;
        }
        return FUND_DATA_DESCRIPTION_PREFIX + " - " + studentName + " - " + suffix;
    }

    private static int findLedgerIdForPayment(SQLiteDatabase db, int paymentId) {
        Cursor cursor = db.query(TABLE_LEDGER, new String[]{COLUMN_ID},
                COLUMN_LEDGER_PAYMENT_ID + "=?", new String[]{String.valueOf(paymentId)},
                null, null, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        } finally {
            cursor.close();
        }
    }

    private boolean paymentExists(int paymentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PAYMENTS, new String[]{COLUMN_ID},
                COLUMN_ID + "=?", new String[]{String.valueOf(paymentId)}, null, null, null, "1");
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    // --- Ledger Methods ---

    /**
     * Inserts a ledger entry, enforcing the approved financial rules.
     *
     * <p><b>Debit</b> (Pemasukan, money entering the fund) is always inserted;
     * the balance is never compared against a debit, so the fund can grow from
     * zero.
     *
     * <p><b>Credit</b> (Pengeluaran, money leaving the fund) is inserted only
     * when {@code amount <= current balance}. This is the insufficient balance
     * rule, and it is enforced here rather than only in the UI so that no
     * caller can create an invalid ledger state.
     *
     * <p>The balance read and the insert run inside a single transaction, so a
     * credit can never be accepted against a balance that has already been
     * spent by an earlier write. When the credit is rejected the transaction is
     * rolled back, which is what guarantees <b>a rejected credit never creates a
     * ledger row</b>. Debits need no transaction because a single insert is
     * atomic on its own.
     *
     * <p>Student obligations are a different path: {@link #addPayment} writes
     * only {@code table_payments}, and a Fund Data record writes its ledger
     * row through {@link #addFundDataPayment} as a debit (Pemasukan). A manual
     * entry here and a Fund Data entry therefore never count the same money
     * twice.
     *
     * @return the new row id on success, {@link #LEDGER_INSERT_INSUFFICIENT_BALANCE}
     *         when a credit exceeds the current balance, or
     *         {@link #LEDGER_INSERT_FAILED} when the row could not be stored
     */
    public long addLedgerEntry(String date, String description, String type, int amount) {
        if (!AmountValidator.validate(amount).isValid()) {
            // A zero or negative amount would corrupt the balance.
            return LEDGER_INSERT_FAILED;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_LEDGER_DATE, date);
        values.put(COLUMN_LEDGER_DESCRIPTION, description);
        values.put(COLUMN_LEDGER_TYPE, type);
        values.put(COLUMN_LEDGER_AMOUNT, amount);

        boolean isCredit = LEDGER_TYPE_CREDIT.equalsIgnoreCase(type);
        if (!isCredit) {
            // A debit (Pemasukan), and any unrecognised type, is inserted without
            // a balance check: money coming in can never overdraw the fund.
            return db.insert(TABLE_LEDGER, null, values);
        }

        db.beginTransaction();
        try {
            if (amount > computeBalance(db)) {
                // Not marked successful, so the transaction rolls back and no row
                // is written.
                return LEDGER_INSERT_INSUFFICIENT_BALANCE;
            }
            long rowId = db.insert(TABLE_LEDGER, null, values);
            if (rowId == -1L) {
                return LEDGER_INSERT_FAILED;
            }
            db.setTransactionSuccessful();
            return rowId;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Updates an existing ledger entry while enforcing the insufficient balance rule.
     *
     * <p>When updating a record, this record's own contribution is first
     * removed from the balance: a debit (money in) is subtracted again, a
     * credit (money out) is added back. The new credit is then accepted only
     * when {@code new credit <= balance without this record}, so an edit can
     * never push the balance below zero.
     *
     * <p>A Fund Data mirror row ({@link #COLUMN_LEDGER_PAYMENT_ID} set) is a
     * debit, i.e. Pemasukan, so it is never compared against the balance:
     * correcting such an amount can only change how much the fund holds, it
     * can never overdraw it. The mirror link itself is never rewritten here,
     * so the row stays attached to its payment.
     *
     * @return 1L on success, {@link #LEDGER_INSERT_INSUFFICIENT_BALANCE} when a
     *         credit exceeds the available balance, or {@link #LEDGER_INSERT_FAILED}
     *         on failure
     */
    public long updateLedgerEntry(int id, String date, String description, String type, int amount) {
        if (!AmountValidator.validate(amount).isValid()) {
            return LEDGER_INSERT_FAILED;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor cursor = db.query(TABLE_LEDGER,
                    new String[]{COLUMN_LEDGER_TYPE, COLUMN_LEDGER_AMOUNT},
                    COLUMN_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
            if (!cursor.moveToFirst()) {
                cursor.close();
                return LEDGER_INSERT_FAILED;
            }
            String oldType = cursor.getString(0);
            int oldAmount = cursor.getInt(1);
            cursor.close();

            long currentBalance = computeBalance(db);
            long effectiveBalance = currentBalance;
            if (LEDGER_TYPE_DEBIT.equalsIgnoreCase(oldType)) {
                effectiveBalance -= oldAmount;
            } else if (LEDGER_TYPE_CREDIT.equalsIgnoreCase(oldType)) {
                effectiveBalance += oldAmount;
            }

            boolean isNewCredit = LEDGER_TYPE_CREDIT.equalsIgnoreCase(type);
            if (isNewCredit && amount > effectiveBalance) {
                return LEDGER_INSERT_INSUFFICIENT_BALANCE;
            }

            ContentValues values = new ContentValues();
            values.put(COLUMN_LEDGER_DATE, date);
            values.put(COLUMN_LEDGER_DESCRIPTION, description);
            values.put(COLUMN_LEDGER_TYPE, type);
            values.put(COLUMN_LEDGER_AMOUNT, amount);

            int updated = db.update(TABLE_LEDGER, values, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
            if (updated > 0) {
                db.setTransactionSuccessful();
                return 1L;
            }
            return LEDGER_INSERT_FAILED;
        } finally {
            db.endTransaction();
        }
    }

    public List<LedgerEntry> getAllLedgerEntries() {
        List<LedgerEntry> ledgerList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_LEDGER + " ORDER BY " + COLUMN_LEDGER_DATE + " DESC";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                LedgerEntry entry = new LedgerEntry(
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LEDGER_DATE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LEDGER_DESCRIPTION)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LEDGER_TYPE)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LEDGER_AMOUNT))
                );
                ledgerList.add(entry);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return ledgerList;
    }

    /**
     * @return the number of ledger rows removed, {@code 0} when nothing matched.
     */
    public int deleteLedgerEntry(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_LEDGER, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
    }

    /**
     * The single deterministic balance of the class fund.
     *
     * <p>Computed over {@link #TABLE_LEDGER} only, with the definition in
     * {@link #SELECT_BALANCE}: debits (Pemasukan) minus credits (Pengeluaran).
     * Payments are excluded, so no amount is ever counted twice. Reports, the
     * insufficient balance rule and any future validation must use this method
     * (or {@link #computeBalance}, the one formula behind it) rather than
     * re-deriving the total.
     *
     * @return the balance in Rupiah; {@code 0} when the ledger is empty
     */
    public long getBalance() {
        return computeBalance(this.getReadableDatabase());
    }

    /**
     * The Pemasukan and Pengeluaran sums the Fund Report summary shows, read
     * in a single query so both numbers always describe the same database
     * state. Consistent with {@link #getBalance()} by construction:
     * {@code income - expense} is exactly the balance formula.
     */
    public LedgerTotals getLedgerTotals() {
        String query = "SELECT COALESCE(SUM(CASE WHEN " + COLUMN_LEDGER_TYPE + " = ? THEN "
                + COLUMN_LEDGER_AMOUNT + " ELSE 0 END), 0),"
                + " COALESCE(SUM(CASE WHEN " + COLUMN_LEDGER_TYPE + " = ? THEN "
                + COLUMN_LEDGER_AMOUNT + " ELSE 0 END), 0) FROM " + TABLE_LEDGER;
        Cursor cursor = this.getReadableDatabase().rawQuery(query,
                new String[]{LEDGER_TYPE_DEBIT, LEDGER_TYPE_CREDIT});
        try {
            if (cursor.moveToFirst()) {
                return new LedgerTotals(cursor.getLong(0), cursor.getLong(1));
            }
        } finally {
            cursor.close();
        }
        return new LedgerTotals(0L, 0L);
    }

    /** The two summary totals of the cash book, in Rupiah. */
    public static final class LedgerTotals {
        /** Sum of all debit rows: money entering the fund (Pemasukan). */
        public final long income;
        /** Sum of all credit rows: money leaving the fund (Pengeluaran). */
        public final long expense;

        public LedgerTotals(long income, long expense) {
            this.income = income;
            this.expense = expense;
        }
    }

    /**
     * The one balance formula. Taking the database as a parameter lets the
     * insufficient balance rule read the balance inside its own transaction
     * without opening a second connection.
     */
    private static long computeBalance(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery(SELECT_BALANCE,
                new String[]{LEDGER_TYPE_DEBIT, LEDGER_TYPE_CREDIT});
        long balance = 0L;
        try {
            if (cursor.moveToFirst()) {
                balance = cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        return balance;
    }
}

package com.example.kas_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
 * Executed on the JVM through Robolectric, so the real SQLite behaviour of the
 * Priority 1 student operations is covered: update, the deletion policy, and
 * the guarantee that historical payment records survive.
 */
@RunWith(RobolectricTestRunner.class)
public class StudentCrudTest {

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

    private Student studentNamed(String name) {
        long id = helper.addStudent(name);
        assertTrue("student should be created", id > 0);
        return findStudent((int) id);
    }

    private Student findStudent(int id) {
        for (Student student : helper.getAllStudents()) {
            if (student.getId() == id) {
                return student;
            }
        }
        return null;
    }

    /** Row count straight from SQLite, so a test cannot be fooled by a query. */
    private int countRows(String table) {
        Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    /** How many foreign keys the schema declares on this table. */
    private int foreignKeyCount(String table) {
        Cursor cursor = helper.getReadableDatabase()
                .rawQuery("PRAGMA foreign_key_list(" + table + ")", null);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    // --- create / read (existing behaviour must not regress) ---

    @Test
    public void createAndReadStudent() {
        long id = helper.addStudent("Bimo Aditya Sasmito");

        assertTrue(id > 0);
        List<Student> students = helper.getAllStudents();
        assertEquals(1, students.size());
        assertEquals("Bimo Aditya Sasmito", students.get(0).getName());
    }

    @Test
    public void readReportsNothingForAnUnknownStudent() {
        assertNull(findStudent(404));
        assertEquals(0, helper.countPaymentsForStudent(404));
    }

    // --- update ---

    @Test
    public void updateRenamesTheStudentInPlaceAndKeepsTheId() {
        Student student = studentNamed("Alif Radinka Putra");

        assertEquals(1, helper.updateStudent(student.getId(), "Alif Radinka F"));

        Student updated = findStudent(student.getId());
        assertNotNull(updated);
        assertEquals("Alif Radinka F", updated.getName());
        assertEquals("the student id must not change", student.getId(), updated.getId());
    }

    @Test
    public void updateDoesNotCreateADuplicateStudent() {
        Student a = studentNamed("Aulia Nursalsabila");
        studentNamed("Aulia Salsabila Putri Dearie");

        helper.updateStudent(a.getId(), "Aulia Salsabila Putri Dearie");

        assertEquals("renaming must not add a row", 2, helper.getAllStudents().size());
        assertEquals(2, countRows(DatabaseHelper.TABLE_STUDENTS));
    }

    @Test
    public void updateIsNotAFailureWhenTheNameIsUnchanged() {
        Student student = studentNamed("Daffa Izzati Nazhifah");

        // SQLite reports 0 changed rows when the value is identical; the app
        // must still treat that as a successful operation.
        assertEquals(1, helper.updateStudent(student.getId(), "Daffa Izzati Nazhifah"));
        assertEquals("Daffa Izzati Nazhifah", findStudent(student.getId()).getName());
    }

    @Test
    public void updateOfAnUnknownStudentReportsFailure() {
        assertEquals(0, helper.updateStudent(999, "Nobody"));
    }

    @Test
    public void updateKeepsTheStudentsPaymentHistoryAttached() {
        Student student = studentNamed("Joicha Falent Big Diyo");
        helper.addPayment(student.getId(), "January", 2026, 10000);

        helper.updateStudent(student.getId(), "Joicha F. Big Diyo");

        assertEquals("the renamed student keeps their history", 1,
                helper.getPaymentsByStudent(student.getId()).size());
    }

    // --- delete policy: history blocks deletion ---

    @Test
    public void deleteSucceedsForAStudentWithNoPaymentHistory() {
        Student student = studentNamed("Michael Trisatrio Mukti");

        assertEquals(DatabaseHelper.STUDENT_DELETE_DELETED,
                helper.deleteStudent(student.getId()));

        assertNull(findStudent(student.getId()));
        assertEquals(0, helper.getAllStudents().size());
    }

    @Test
    public void deleteIsRefusedWhenTheStudentHasPaymentHistory() {
        Student student = studentNamed("Aulia Nursalsabila");
        helper.addPayment(student.getId(), "January", 2026, 10000);
        helper.addPayment(student.getId(), "February", 2026, 10000);

        assertEquals(DatabaseHelper.STUDENT_DELETE_HAS_HISTORY,
                helper.deleteStudent(student.getId()));

        assertNotNull("the student record must be kept", findStudent(student.getId()));
        assertEquals("the payment history must be kept", 2,
                countRows(DatabaseHelper.TABLE_PAYMENTS));
    }

    @Test
    public void refusedDeleteLeavesBothTheStudentAndEveryPaymentIntact() {
        Student student = studentNamed("Aulia Salsabila Putri Dearie");
        helper.addPayment(student.getId(), "March", 2026, 10000);
        helper.addPayment(student.getId(), "April", 2026, 10000);
        helper.addPayment(student.getId(), "May", 2026, 10000);

        helper.deleteStudent(student.getId());

        assertEquals("Aulia Salsabila Putri Dearie", findStudent(student.getId()).getName());
        assertEquals(3, helper.countPaymentsForStudent(student.getId()));
        assertEquals(3, helper.getPaymentsByStudent(student.getId()).size());
        assertEquals(10000, helper.getPaymentsByStudent(student.getId()).get(0).getAmount());
    }

    @Test
    public void deleteOfAnUnknownStudentReportsNotFound() {
        assertEquals(DatabaseHelper.STUDENT_DELETE_NOT_FOUND, helper.deleteStudent(999));
    }

    @Test
    public void deletingOneStudentNeverTouchesAnotherStudentsHistory() {
        Student keep = studentNamed("Keep Me");
        Student blocked = studentNamed("Blocked From Deletion");
        helper.addPayment(keep.getId(), "January", 2026, 10000);
        helper.addPayment(blocked.getId(), "January", 2026, 20000);

        assertEquals("a student with history is kept", DatabaseHelper.STUDENT_DELETE_HAS_HISTORY,
                helper.deleteStudent(blocked.getId()));
        assertEquals(2, helper.getAllStudents().size());

        // Both payment rows survive; deleting a student is never a cascade.
        assertEquals(2, countRows(DatabaseHelper.TABLE_PAYMENTS));
        assertEquals(10000, helper.getPaymentsByStudent(keep.getId()).get(0).getAmount());
        assertEquals(20000, helper.getPaymentsByStudent(blocked.getId()).get(0).getAmount());
    }

    @Test
    public void deletingAHistoryFreeStudentLeavesOtherStudentsPaymentsIntact() {
        Student keep = studentNamed("Keep Me");
        Student remove = studentNamed("Remove Me");
        helper.addPayment(keep.getId(), "January", 2026, 10000);

        assertEquals(DatabaseHelper.STUDENT_DELETE_DELETED, helper.deleteStudent(remove.getId()));

        assertEquals(1, helper.getAllStudents().size());
        assertEquals("only the removed student is gone", 1, countRows(DatabaseHelper.TABLE_PAYMENTS));
        assertEquals(10000, helper.getPaymentsByStudent(keep.getId()).get(0).getAmount());
    }

    @Test
    public void noForeignKeyCascadeIsConfiguredOnPayments() {
        helper.addStudent("Cascade Check");
        helper.addPayment(1, "January", 2026, 10000);

        // The schema declares no foreign key at all, so there is no
        // ON DELETE CASCADE that could silently remove historical payments.
        assertEquals(0, foreignKeyCount(DatabaseHelper.TABLE_PAYMENTS));
    }

    // --- student operations must not disturb the balance ---

    @Test
    public void studentCrudDoesNotChangeTheBalance() {
        helper.addLedgerEntry("2026-01-01", "Opening",
                DatabaseHelper.LEDGER_TYPE_DEBIT, 121000);

        Student student = studentNamed("Rayyan Al Farhan");
        helper.updateStudent(student.getId(), "Rayyan A. Al Farhan");
        helper.deleteStudent(student.getId());

        assertEquals(121000L, helper.getBalance());
    }

    @Test
    public void deletingAStudentDoesNotRemoveTheirPaymentsFromTheBalance() {
        helper.addLedgerEntry("2026-01-01", "Opening",
                DatabaseHelper.LEDGER_TYPE_DEBIT, 121000);
        Student student = studentNamed("Salma Nur Aini");
        helper.addPayment(student.getId(), "January", 2026, 10000);

        helper.deleteStudent(student.getId());

        assertEquals("balance is ledger driven and unaffected by student removal",
                121000L, helper.getBalance());
    }
}

package com.example.kas_app;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class FundDataActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private StudentAdapter adapter;
    private List<Student> studentList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fund_data);

        dbHelper = new DatabaseHelper(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        RecyclerView rvStudents = findViewById(R.id.rvStudents);
        rvStudents.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new StudentAdapter(studentList, student -> {
            StudentPaymentBottomSheet bottomSheet = StudentPaymentBottomSheet.newInstance(student.getId(), student.getName());
            bottomSheet.show(getSupportFragmentManager(), "StudentPaymentBottomSheet");
        }, this::showStudentActions);
        rvStudents.setAdapter(adapter);

        FloatingActionButton fabAddStudent = findViewById(R.id.fabAddStudent);
        fabAddStudent.setOnClickListener(v -> showAddStudentDialog());

        loadStudents();
    }

    private void loadStudents() {
        studentList = dbHelper.getAllStudents();
        adapter.updateData(studentList);
    }

    /**
     * Entry point for student management. Tapping the row still opens the
     * payment history; this overflow menu is where the name and the record can
     * be managed.
     */
    private void showStudentActions(Student student) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(student.getName())
                .setItems(new CharSequence[]{getString(R.string.action_edit_student),
                        getString(R.string.action_delete_student)}, (dialog, which) -> {
                    if (which == 0) {
                        showEditStudentDialog(student);
                    } else {
                        confirmDeleteStudent(student);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Renames a student in place. Reuses the existing add-student dialog layout
     * and the same deferred positive-button pattern, so an invalid name leaves
     * the dialog open instead of being discarded.
     */
    private void showEditStudentDialog(Student student) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_edit_student);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_student, null);
        final EditText etStudentName = view.findViewById(R.id.etStudentName);
        etStudentName.setText(student.getName());
        etStudentName.setSelection(etStudentName.getText().length());

        builder.setView(view);
        builder.setPositiveButton(R.string.action_save, null);
        builder.setNegativeButton(R.string.cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> saveStudentName(dialog, etStudentName, student)));
        dialog.show();
    }

    private void saveStudentName(AlertDialog dialog, EditText etStudentName, Student student) {
        etStudentName.setError(null);
        String name = etStudentName.getText().toString().trim();
        if (name.isEmpty()) {
            etStudentName.setError(getString(R.string.error_student_name_empty));
            return;
        }
        if (name.equals(student.getName())) {
            // Nothing to change, so do not issue a pointless write.
            dialog.dismiss();
            return;
        }
        if (dbHelper.updateStudent(student.getId(), name) == 0) {
            Toast.makeText(this, R.string.error_update_student_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        loadStudents();
        dialog.dismiss();
        Toast.makeText(this, R.string.student_updated, Toast.LENGTH_SHORT).show();
    }

    /**
     * Deleting a student is never a one-tap action.
     *
     * <p>A student who has payment history is not offered a delete button at
     * all: the confirmation explains that their financial history is retained,
     * so the refusal is never a surprise and never a silent failure. The same
     * rule is enforced again inside {@link DatabaseHelper#deleteStudent(int)}.
     */
    private void confirmDeleteStudent(Student student) {
        int payments = dbHelper.countPaymentsForStudent(student.getId());
        if (payments > 0) {
            showDeleteBlocked(payments);
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_delete_student)
                .setMessage(getString(R.string.confirm_delete_student_no_history, student.getName()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_delete_student, (dialog, which) -> deleteStudent(student))
                .show();
    }

    /**
     * Explains that a student was kept. Never a silent failure.
     */
    private void showDeleteBlocked(int paymentCount) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_delete_blocked)
                .setMessage(getResources().getQuantityString(
                        R.plurals.error_student_has_history, paymentCount, paymentCount))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void deleteStudent(Student student) {
        int result = dbHelper.deleteStudent(student.getId());
        if (result == DatabaseHelper.STUDENT_DELETE_DELETED) {
            loadStudents();
            Toast.makeText(this, R.string.student_deleted, Toast.LENGTH_SHORT).show();
            return;
        }
        if (result == DatabaseHelper.STUDENT_DELETE_HAS_HISTORY) {
            showDeleteBlocked(dbHelper.countPaymentsForStudent(student.getId()));
            return;
        }
        Toast.makeText(this, R.string.error_delete_student_failed, Toast.LENGTH_SHORT).show();
    }

    private void showAddStudentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Student");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_student, null);
        final EditText etStudentName = view.findViewById(R.id.etStudentName);

        builder.setView(view);

        // Wired in onShow so the dialog stays open when the name is rejected.
        // With setPositiveButton(..., listener) Android dismisses the dialog
        // even when the listener rejects the input.
        builder.setPositiveButton("Add", null);
        builder.setNegativeButton("Cancel", null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        etStudentName.setError(null);
                        String name = etStudentName.getText().toString().trim();
                        if (name.isEmpty()) {
                            etStudentName.setError(getString(R.string.error_student_name_empty));
                            return;
                        }
                        if (dbHelper.addStudent(name) == -1L) {
                            Toast.makeText(FundDataActivity.this, R.string.error_save_failed,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        loadStudents();
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
    }
}
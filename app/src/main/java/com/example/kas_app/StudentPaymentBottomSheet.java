package com.example.kas_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

public class StudentPaymentBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_STUDENT_ID = "student_id";
    private static final String ARG_STUDENT_NAME = "student_name";

    private static final Integer[] YEARS = {2026, 2027, 2028};

    private int studentId;
    private String studentName;
    private DatabaseHelper dbHelper;
    private PaymentAdapter adapter;
    private RecyclerView rvPaymentHistory;
    private Spinner spinnerMonth, spinnerYear;
    private EditText etAmount;

    public static StudentPaymentBottomSheet newInstance(int studentId, String studentName) {
        StudentPaymentBottomSheet fragment = new StudentPaymentBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_STUDENT_ID, studentId);
        args.putString(ARG_STUDENT_NAME, studentName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep this dialog edge-to-edge (ThemeOverlay.Kas_App.PaymentSheet).
        // Material only wires up its bottom-sheet inset handling when
        // enableEdgeToEdge is true, which is what lets the sheet rise above the
        // keyboard; a plain dialog would just be covered. See KeyboardInsets.
        setStyle(DialogFragment.STYLE_NORMAL, R.style.ThemeOverlay_Kas_App_PaymentSheet);
        if (getArguments() != null) {
            studentId = getArguments().getInt(ARG_STUDENT_ID);
            studentName = getArguments().getString(ARG_STUDENT_NAME);
        }
        dbHelper = new DatabaseHelper(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_payment, container, false);

        TextView tvStudentNameTitle = view.findViewById(R.id.tvStudentNameTitle);
        tvStudentNameTitle.setText(studentName);

        rvPaymentHistory = view.findViewById(R.id.rvPaymentHistory);
        rvPaymentHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        
        spinnerMonth = view.findViewById(R.id.spinnerMonth);
        spinnerYear = view.findViewById(R.id.spinnerYear);
        etAmount = view.findViewById(R.id.etAmount);
        MaterialButton btnSavePayment = view.findViewById(R.id.btnSavePayment);

        setupSpinners();
        loadPaymentHistory();

        btnSavePayment.setOnClickListener(v -> savePayment());

        KeyboardInsets.keepVisibleWhenFocused(etAmount);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // The sheet itself is lifted above the keyboard by Material (see
        // KeyboardInsets); here we only re-reveal the focused field and the
        // button below it whenever the sheet lays out anew. See KeyboardInsets.
        View content = getView();
        if (content != null) {
            ViewParent sheet = content.getParent();
            if (sheet instanceof View) {
                KeyboardInsets.keepFieldAndActionVisible((View) sheet);
            }
        }
    }

    private void setupSpinners() {
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_spinner_item, DatabaseHelper.PAYMENT_MONTHS);
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMonth.setAdapter(monthAdapter);

        ArrayAdapter<Integer> yearAdapter = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_spinner_item, YEARS);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(yearAdapter);
    }

    private void loadPaymentHistory() {
        List<Payment> payments = dbHelper.getPaymentsByStudent(studentId);
        if (adapter == null || rvPaymentHistory.getAdapter() == null) {
            adapter = new PaymentAdapter(payments, this::showPaymentActions);
            rvPaymentHistory.setAdapter(adapter);
        } else {
            adapter.updateData(payments);
        }
    }

    private static void hideIfPresent(View root, int viewId) {
        View view = root.findViewById(viewId);
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    private void showPaymentActions(Payment payment) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(payment.getMonth() + " " + payment.getYear() + " - " + CurrencyFormatter.format(payment.getAmount()))
                .setItems(new CharSequence[]{getString(R.string.action_edit),
                        getString(R.string.action_delete)}, (dialog, which) -> {
                    if (which == 0) {
                        showEditPaymentDialog(payment);
                    } else {
                        confirmDeletePayment(payment);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showEditPaymentDialog(Payment payment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.title_edit_payment);

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_payment, null);
        // Hide the history list and the "add" chrome so the dialog only shows
        // the fields being edited; every hidden view has an id so it can be
        // turned off without touching the rest of the layout.
        hideIfPresent(view, R.id.tvStudentNameTitle);
        hideIfPresent(view, R.id.tvPaymentHistoryLabel);
        hideIfPresent(view, R.id.rvPaymentHistory);
        hideIfPresent(view, R.id.viewFormDivider);
        hideIfPresent(view, R.id.tvAddPaymentLabel);
        hideIfPresent(view, R.id.btnSavePayment);

        Spinner editMonth = view.findViewById(R.id.spinnerMonth);
        Spinner editYear = view.findViewById(R.id.spinnerYear);
        EditText editAmount = view.findViewById(R.id.etAmount);

        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, DatabaseHelper.PAYMENT_MONTHS);
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        editMonth.setAdapter(monthAdapter);

        for (int i = 0; i < DatabaseHelper.PAYMENT_MONTHS.length; i++) {
            if (DatabaseHelper.PAYMENT_MONTHS[i].equalsIgnoreCase(payment.getMonth())) {
                editMonth.setSelection(i);
                break;
            }
        }

        ArrayAdapter<Integer> yearAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, YEARS);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        editYear.setAdapter(yearAdapter);

        for (int i = 0; i < YEARS.length; i++) {
            if (YEARS[i] == payment.getYear()) {
                editYear.setSelection(i);
                break;
            }
        }

        editAmount.setText(String.valueOf(payment.getAmount()));

        // Same keyboard treatment as the add form: the dialog window is made
        // edge-to-edge and the IME inset becomes bottom padding, so the amount
        // field stays visible above the numeric keyboard.
        KeyboardInsets.applyTo(view, true);
        KeyboardInsets.keepVisibleWhenFocused(editAmount);

        builder.setView(view);
        builder.setPositiveButton(R.string.action_save, null);
        builder.setNegativeButton(R.string.cancel, null);

        AlertDialog dialog = builder.create();
        Window window = dialog.getWindow();
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
        }
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Object selMonth = editMonth.getSelectedItem();
            Object selYear = editYear.getSelectedItem();
            if (!(selMonth instanceof String) || !(selYear instanceof Integer)) {
                Toast.makeText(getContext(), R.string.error_select_month_and_year, Toast.LENGTH_SHORT).show();
                return;
            }
            editAmount.setError(null);
            AmountValidator.Result result = AmountValidator.validate(editAmount.getText().toString());
            if (!result.isValid()) {
                editAmount.setError(getString(amountErrorMessage(result.getError())));
                return;
            }

            // Updates the payment and its mirrored ledger entry in one
            // transaction, so the ledger keeps matching the edited amount
            // instead of accumulating a second row.
            int updated = dbHelper.updateFundDataPayment(payment.getId(), studentId,
                    (String) selMonth, (Integer) selYear, result.getAmount());
            if (updated > 0) {
                loadPaymentHistory();
                dialog.dismiss();
                Toast.makeText(getContext(), R.string.payment_updated, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), R.string.error_save_failed, Toast.LENGTH_SHORT).show();
            }
        }));
        dialog.show();
    }

    private void confirmDeletePayment(Payment payment) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_delete_payment)
                .setMessage(R.string.confirm_delete_payment)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    // Removes the payment and its mirrored ledger entry in one
                    // transaction, so no orphan entry keeps moving the balance.
                    if (dbHelper.deleteFundDataPayment(payment.getId()) > 0) {
                        loadPaymentHistory();
                        Toast.makeText(getContext(), R.string.payment_deleted, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), R.string.error_save_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void savePayment() {
        Object selectedMonth = spinnerMonth.getSelectedItem();
        Object selectedYear = spinnerYear.getSelectedItem();
        if (!(selectedMonth instanceof String) || !(selectedYear instanceof Integer)) {
            Toast.makeText(getContext(), R.string.error_select_month_and_year, Toast.LENGTH_SHORT).show();
            return;
        }
        String month = (String) selectedMonth;
        int year = (Integer) selectedYear;

        etAmount.setError(null);
        AmountValidator.Result result = AmountValidator.validate(etAmount.getText().toString());
        if (!result.isValid()) {
            etAmount.setError(getString(amountErrorMessage(result.getError())));
            return;
        }

        // Fund Data rule: the obligation row and its mirrored ledger debit are
        // written in one transaction, so a saved payment always enters the
        // fund balance exactly once and a failed write leaves neither row
        // behind. History refreshes and screen recreation never reach here, so
        // they can never duplicate a ledger entry.
        long rowId = dbHelper.addFundDataPayment(studentId, month, year, result.getAmount());
        if (rowId == -1L) {
            Toast.makeText(getContext(), R.string.error_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        // Refresh history
        loadPaymentHistory();

        // Clear input
        etAmount.setText("");
        Toast.makeText(getContext(), R.string.payment_saved, Toast.LENGTH_SHORT).show();
    }

    private static int amountErrorMessage(AmountValidator.Error error) {
        if (error == AmountValidator.Error.NOT_A_NUMBER) {
            return R.string.error_amount_not_a_number;
        }
        if (error == AmountValidator.Error.NOT_POSITIVE) {
            return R.string.error_amount_not_positive;
        }
        if (error == AmountValidator.Error.TOO_LARGE) {
            return R.string.error_amount_too_large;
        }
        return R.string.error_amount_empty;
    }
}
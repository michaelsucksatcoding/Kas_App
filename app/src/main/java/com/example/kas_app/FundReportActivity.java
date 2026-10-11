package com.example.kas_app;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class FundReportActivity extends AppCompatActivity {

    private static final String LOG_TAG = "FundReportActivity";

    private DatabaseHelper dbHelper;
    private LedgerAdapter adapter;
    private List<LedgerEntry> ledgerList = new ArrayList<>();
    private TextView tvBalanceAmount;
    private TextView tvTotalIncome;
    private TextView tvTotalExpense;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fund_report);

        dbHelper = new DatabaseHelper(this);

        Toolbar toolbar = findViewById(R.id.toolbarReport);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        tvBalanceAmount = findViewById(R.id.tvBalanceAmount);
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpense = findViewById(R.id.tvTotalExpense);

        RecyclerView rvLedger = findViewById(R.id.rvLedger);
        rvLedger.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LedgerAdapter(ledgerList, this::showLedgerActions);
        rvLedger.setAdapter(adapter);

        FloatingActionButton fabAddLedger = findViewById(R.id.fabAddLedger);
        fabAddLedger.setOnClickListener(v -> showAddLedgerDialog());

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position >= adapter.getLedgerList().size()) {
                    return;
                }
                LedgerEntry entry = adapter.getLedgerList().get(position);
                confirmDeleteLedgerEntry(entry, position);
            }
        }).attachToRecyclerView(rvLedger);
    }

    /**
     * Entry point for ledger management. Tapping the row or its overflow icon
     * offers the same two actions the payment history offers, so both financial
     * lists behave the same way.
     */
    private void showLedgerActions(LedgerEntry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(entry.getDescription() + " - " + CurrencyFormatter.format(entry.getAmount()))
                .setItems(new CharSequence[]{getString(R.string.action_edit),
                        getString(R.string.action_delete)}, (dialog, which) -> {
                    if (which == 0) {
                        showEditLedgerDialog(entry);
                    } else {
                        confirmDeleteLedgerEntry(entry, RecyclerView.NO_POSITION);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Deleting a ledger entry changes the cash book, so it is never a one-tap
     * action. The swiped row is restored whenever the request is refused or the
     * dialog is dismissed, otherwise the list would visually hide an entry that
     * still exists in the database.
     */
    private void confirmDeleteLedgerEntry(LedgerEntry entry, int swipedPosition) {
        final boolean[] deleted = {false};
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_delete_ledger)
                .setMessage(R.string.confirm_delete_ledger)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    if (dbHelper.deleteLedgerEntry(entry.getId()) > 0) {
                        deleted[0] = true;
                        Toast.makeText(this, R.string.ledger_deleted, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show();
                    }
                    // Deleted entries changed the cash book, so the balance and
                    // the list are both re-read rather than patched in place.
                    refresh();
                });
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            if (!deleted[0] && swipedPosition != RecyclerView.NO_POSITION
                    && swipedPosition < adapter.getItemCount()) {
                adapter.notifyItemChanged(swipedPosition);
            }
        });
        dialog.show();
    }

    /**
     * The report screen shows the authoritative balance, so it is re-read every
     * time the screen becomes visible. This is the existing Activity lifecycle
     * pattern: no ViewModel, LiveData or observer was introduced.
     *
     * <p>Re-reading here means the balance is correct after adding a credit,
     * adding a debit, deleting an entry, and returning to this screen from
     * another one.
     */
    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /**
     * Re-reads the balance, the ledger and the Pemasukan / Pengeluaran totals
     * from the database. All three come from the same database state, so the
     * displayed balance can never contradict the entries listed underneath it:
     * {@code total income - total expense} is the balance formula itself.
     */
    private void refresh() {
        loadBalance();
        loadLedgerEntries();
        loadTotals();
    }

    /**
     * Reads the balance through the single authoritative calculation
     * {@link DatabaseHelper#getBalance()}. The SQL is never duplicated here.
     */
    private void loadBalance() {
        try {
            tvBalanceAmount.setText(CurrencyFormatter.format(dbHelper.getBalance()));
        } catch (RuntimeException e) {
            // A failed query must not be shown as a fake zero balance, so the
            // previous value is left alone and the failure is reported.
            Log.e(LOG_TAG, "Failed to read balance", e);
            Toast.makeText(this, R.string.error_load_balance_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadLedgerEntries() {
        ledgerList = dbHelper.getAllLedgerEntries();
        adapter.updateData(ledgerList);
    }

    /**
     * Reads the summary totals through the single query in
     * {@link DatabaseHelper#getLedgerTotals()}: debit rows are Pemasukan,
     * credit rows are Pengeluaran. A failed query keeps the previous values
     * rather than showing a fake zero.
     */
    private void loadTotals() {
        try {
            DatabaseHelper.LedgerTotals totals = dbHelper.getLedgerTotals();
            tvTotalIncome.setText(getString(R.string.total_income_label,
                    CurrencyFormatter.format(totals.income)));
            tvTotalExpense.setText(getString(R.string.total_expense_label,
                    CurrencyFormatter.format(totals.expense)));
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "Failed to read ledger totals", e);
        }
    }

    private void showAddLedgerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_add_ledger);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_ledger, null);
        final EditText etDate = view.findViewById(R.id.etLedgerDate);
        final EditText etDescription = view.findViewById(R.id.etDescription);
        final EditText etAmount = view.findViewById(R.id.etLedgerAmount);
        final RadioGroup rgType = view.findViewById(R.id.rgType);

        // A new entry is dated today until the user picks another day.
        etDate.setText(IsoDate.today());
        wireDateField(view, etDate);

        builder.setView(view);

        // The positive button is wired in onShow so that the dialog is only
        // dismissed once validation and the insert have both succeeded. With
        // setPositiveButton(..., listener) Android dismisses the dialog even
        // when the listener rejects the input.
        builder.setPositiveButton(R.string.action_save, null);
        builder.setNegativeButton(R.string.cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        saveLedgerEntry(dialog, etDate, etDescription, etAmount, rgType);
                    }
                });
            }
        });
        dialog.show();
    }

    /**
     * Edits an existing ledger entry in place.
     *
     * <p>The dialog reuses {@code dialog_add_ledger.xml} and starts on the
     * stored date, so editing something else never rewrites the record's day by
     * accident. The date can still be changed through the picker, and whatever
     * the field shows when Save is pressed is what gets written. The row is
     * updated through its primary key, so an edit can never duplicate the entry.
     */
    void showEditLedgerDialog(final LedgerEntry entry) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_edit_ledger);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_ledger, null);
        final EditText etDate = view.findViewById(R.id.etLedgerDate);
        final EditText etDescription = view.findViewById(R.id.etDescription);
        final EditText etAmount = view.findViewById(R.id.etLedgerAmount);
        final RadioGroup rgType = view.findViewById(R.id.rgType);

        etDate.setText(entry.getDate());
        wireDateField(view, etDate);
        etDescription.setText(entry.getDescription());
        etDescription.setSelection(etDescription.getText().length());
        etAmount.setText(String.valueOf(entry.getAmount()));
        rgType.check(DatabaseHelper.LEDGER_TYPE_DEBIT.equalsIgnoreCase(entry.getType())
                ? R.id.rbDebit
                : R.id.rbCredit);

        builder.setView(view);
        builder.setPositiveButton(R.string.action_save, null);
        builder.setNegativeButton(R.string.cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> updateLedgerEntry(dialog, entry, etDate, etDescription, etAmount, rgType)));
        dialog.show();
    }

    /**
     * Opens a picker on the field's current day and writes the chosen day back
     * into the field. Nothing is written to the database here, so cancelling the
     * picker simply leaves the field - and the stored date - as it was.
     */
    private void showDatePicker(final EditText dateField) {
        Calendar current = IsoDate.toCalendar(dateField.getText().toString());
        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) ->
                        dateField.setText(IsoDate.format(year, month, dayOfMonth)),
                current.get(Calendar.YEAR),
                current.get(Calendar.MONTH),
                current.get(Calendar.DAY_OF_MONTH)).show();
    }

    /**
     * Makes a date field open {@link #showDatePicker} when it is tapped, and
     * stops it from ever raising the soft keyboard. The same action is attached
     * to the layout's end icon, so whenever the design shows one both the field
     * and the icon open one and the same picker.
     */
    private void wireDateField(View dialogView, final EditText dateField) {
        // A date is picked, never typed. Clearing the input type removes the
        // field from the IME's view entirely (so no numeric keyboard can ever
        // appear), and showSoftInputOnFocus is the belt-and-braces guarantee.
        dateField.setInputType(InputType.TYPE_NULL);
        dateField.setShowSoftInputOnFocus(false);
        View.OnClickListener openPicker = v -> showDatePicker(dateField);
        dateField.setOnClickListener(openPicker);
        TextInputLayout dateLayout = dialogView.findViewById(R.id.tilLedgerDate);
        if (dateLayout != null) {
            dateLayout.setEndIconOnClickListener(openPicker);
        }
    }

    /** The date the field shows, or today if it is somehow blank. */
    private static String dateFromField(EditText dateField) {
        String date = dateField.getText().toString().trim();
        return date.isEmpty() ? IsoDate.today() : date;
    }

    private void saveLedgerEntry(AlertDialog dialog, EditText etDate,
                                 EditText etDescription, EditText etAmount, RadioGroup rgType) {
        LedgerInput input = readLedgerInput(etDescription, etAmount, rgType);
        if (input == null) {
            return;
        }
        String date = dateFromField(etDate);

        // The ledger is the cash book, so this single insert is what changes
        // the balance. No payment is involved and none is expected here. The
        // insufficient balance rule itself lives in DatabaseHelper; the Activity
        // only reports which result came back.
        long rowId = dbHelper.addLedgerEntry(date, input.description, input.type, input.amount);
        if (reportLedgerResult(rowId, etAmount)) {
            // A new ledger row changes the cash book, so the balance is re-read too.
            refresh();
            dialog.dismiss();
        }
    }

    private void updateLedgerEntry(AlertDialog dialog, final LedgerEntry entry, EditText etDate,
                                   EditText etDescription, EditText etAmount, RadioGroup rgType) {
        LedgerInput input = readLedgerInput(etDescription, etAmount, rgType);
        if (input == null) {
            return;
        }

        // The date written back is whatever the field shows: the stored day
        // unless the user picked another one; see showEditLedgerDialog. The
        // insufficient balance rule for an edit is enforced in DatabaseHelper,
        // which validates the new credit against the balance without this record.
        long result = dbHelper.updateLedgerEntry(entry.getId(), dateFromField(etDate),
                input.description, input.type, input.amount);
        if (reportLedgerResult(result, etAmount)) {
            refresh();
            Toast.makeText(this, R.string.ledger_updated, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }
    }

    /**
     * Shared validation for the add and edit ledger dialogs.
     *
     * @return the validated input, or {@code null} when a field was rejected
     *         and the error is already shown on that field
     */
    private LedgerInput readLedgerInput(EditText etDescription, EditText etAmount, RadioGroup rgType) {
        etDescription.setError(null);
        etAmount.setError(null);

        String desc = etDescription.getText().toString().trim();
        if (desc.isEmpty()) {
            etDescription.setError(getString(R.string.error_description_empty));
            return null;
        }

        AmountValidator.Result result = AmountValidator.validate(etAmount.getText().toString());
        if (!result.isValid()) {
            etAmount.setError(getString(amountErrorMessage(result.getError())));
            return null;
        }

        int checkedId = rgType.getCheckedRadioButtonId();
        if (checkedId != R.id.rbCredit && checkedId != R.id.rbDebit) {
            Toast.makeText(this, R.string.error_select_ledger_type, Toast.LENGTH_SHORT).show();
            return null;
        }
        String type = checkedId == R.id.rbCredit
                ? DatabaseHelper.LEDGER_TYPE_CREDIT
                : DatabaseHelper.LEDGER_TYPE_DEBIT;
        return new LedgerInput(desc, type, result.getAmount());
    }

    /**
     * Maps a ledger write result onto the dialog. The insufficient balance rule
     * is the one user-correctable rejection, so it is reported on the amount
     * field and leaves the dialog open, exactly like a validation failure.
     *
     * @return {@code true} when the write succeeded
     */
    private boolean reportLedgerResult(long rowId, EditText etAmount) {
        if (rowId == DatabaseHelper.LEDGER_INSERT_INSUFFICIENT_BALANCE) {
            etAmount.setError(getString(R.string.error_insufficient_balance));
            return false;
        }
        if (rowId < 0L) {
            Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /** The three fields the ledger dialog collects, after validation. */
    private static final class LedgerInput {
        final String description;
        final String type;
        final int amount;

        LedgerInput(String description, String type, int amount) {
            this.description = description;
            this.type = type;
            this.amount = amount;
        }
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
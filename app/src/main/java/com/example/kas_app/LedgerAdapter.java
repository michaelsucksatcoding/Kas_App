package com.example.kas_app;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LedgerAdapter extends RecyclerView.Adapter<LedgerAdapter.LedgerViewHolder> {

    public interface OnLedgerActionListener {
        void onLedgerAction(LedgerEntry entry);
    }

    private List<LedgerEntry> ledgerList;
    private OnLedgerActionListener listener;

    public LedgerAdapter(List<LedgerEntry> ledgerList) {
        this(ledgerList, null);
    }

    public LedgerAdapter(List<LedgerEntry> ledgerList, OnLedgerActionListener listener) {
        this.ledgerList = ledgerList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public LedgerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ledger, parent, false);
        return new LedgerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LedgerViewHolder holder, int position) {
        LedgerEntry entry = ledgerList.get(position);
        holder.tvLedgerDate.setText(entry.getDate());
        holder.tvLedgerDescription.setText(entry.getDescription());

        // Single shared Rupiah formatter, so the balance shown in the header and
        // the amounts in this list can never be formatted differently.
        String amountText = CurrencyFormatter.format(entry.getAmount());

        if (DatabaseHelper.LEDGER_TYPE_DEBIT.equalsIgnoreCase(entry.getType())) {
            // Debit = Pemasukan = money in: positive sign, green.
            holder.tvLedgerAmount.setText("+ " + amountText);
            holder.tvLedgerAmount.setTextColor(Color.parseColor("#2E7D32")); // Dark Green
        } else {
            // Credit = Pengeluaran = money out: negative sign, red.
            holder.tvLedgerAmount.setText("- " + amountText);
            holder.tvLedgerAmount.setTextColor(Color.RED);
        }

        if (listener != null) {
            View.OnClickListener actionListener = v -> listener.onLedgerAction(entry);
            holder.ivLedgerActions.setOnClickListener(actionListener);
            holder.itemView.setOnClickListener(actionListener);
        } else {
            holder.ivLedgerActions.setOnClickListener(null);
            holder.itemView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return ledgerList.size();
    }

    public void updateData(List<LedgerEntry> newList) {
        this.ledgerList = newList;
        notifyDataSetChanged();
    }

    public List<LedgerEntry> getLedgerList() {
        return ledgerList;
    }

    static class LedgerViewHolder extends RecyclerView.ViewHolder {
        TextView tvLedgerDate, tvLedgerDescription, tvLedgerAmount;
        View ivLedgerActions;

        public LedgerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLedgerDate = itemView.findViewById(R.id.tvLedgerDate);
            tvLedgerDescription = itemView.findViewById(R.id.tvLedgerDescription);
            tvLedgerAmount = itemView.findViewById(R.id.tvLedgerAmount);
            ivLedgerActions = itemView.findViewById(R.id.ivLedgerActions);
        }
    }
}
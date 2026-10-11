package com.example.kas_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PaymentAdapter extends RecyclerView.Adapter<PaymentAdapter.PaymentViewHolder> {

    public interface OnPaymentActionListener {
        void onPaymentAction(Payment payment);
    }

    private List<Payment> paymentList;
    private OnPaymentActionListener listener;

    public PaymentAdapter(List<Payment> paymentList) {
        this.paymentList = paymentList;
    }

    public PaymentAdapter(List<Payment> paymentList, OnPaymentActionListener listener) {
        this.paymentList = paymentList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PaymentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment, parent, false);
        return new PaymentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PaymentViewHolder holder, int position) {
        Payment payment = paymentList.get(position);
        holder.tvPaymentDate.setText(payment.getMonth() + " " + payment.getYear());
        holder.tvPaymentAmount.setText(CurrencyFormatter.format(payment.getAmount()));

        if (listener != null) {
            View.OnClickListener actionListener = v -> listener.onPaymentAction(payment);
            holder.ivPaymentActions.setOnClickListener(actionListener);
            holder.itemView.setOnClickListener(actionListener);
        }
    }

    @Override
    public int getItemCount() {
        return paymentList.size();
    }

    public void updateData(List<Payment> newList) {
        this.paymentList = newList;
        notifyDataSetChanged();
    }

    static class PaymentViewHolder extends RecyclerView.ViewHolder {
        TextView tvPaymentDate, tvPaymentAmount;
        ImageView ivPaymentActions;

        public PaymentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPaymentDate = itemView.findViewById(R.id.tvPaymentDate);
            tvPaymentAmount = itemView.findViewById(R.id.tvPaymentAmount);
            ivPaymentActions = itemView.findViewById(R.id.ivPaymentActions);
        }
    }
}
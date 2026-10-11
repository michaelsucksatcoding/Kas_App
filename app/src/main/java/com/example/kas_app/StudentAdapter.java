package com.example.kas_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.StudentViewHolder> {

    private List<Student> studentList;
    private OnStudentClickListener listener;
    private OnStudentActionsListener actionsListener;

    public interface OnStudentClickListener {
        void onStudentClick(Student student);
    }

    /**
     * Opens the manage options for a student (edit name, delete). Kept separate
     * from {@link OnStudentClickListener} so tapping the row still opens the
     * payment history, which is the existing behaviour.
     */
    public interface OnStudentActionsListener {
        void onStudentActions(Student student);
    }

    public StudentAdapter(List<Student> studentList, OnStudentClickListener listener) {
        this(studentList, listener, null);
    }

    public StudentAdapter(List<Student> studentList, OnStudentClickListener listener,
                         OnStudentActionsListener actionsListener) {
        this.studentList = studentList;
        this.listener = listener;
        this.actionsListener = actionsListener;
    }

    @NonNull
    @Override
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student, parent, false);
        return new StudentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StudentViewHolder holder, int position) {
        Student student = studentList.get(position);
        holder.tvStudentName.setText(student.getName());
        holder.itemView.setOnClickListener(v -> listener.onStudentClick(student));
        holder.ivStudentActions.setOnClickListener(v -> {
            if (actionsListener != null) {
                actionsListener.onStudentActions(student);
            }
        });
    }

    @Override
    public int getItemCount() {
        return studentList.size();
    }

    public void updateData(List<Student> newList) {
        this.studentList = newList;
        notifyDataSetChanged();
    }

    static class StudentViewHolder extends RecyclerView.ViewHolder {
        TextView tvStudentName;
        View ivStudentActions;

        public StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStudentName = itemView.findViewById(R.id.tvStudentName);
            ivStudentActions = itemView.findViewById(R.id.ivStudentActions);
        }
    }
}
package com.example.kas_app;

public class Payment {
    private int id;
    private int studentId;
    private String month;
    private int year;
    private int amount;

    public Payment(int id, int studentId, String month, int year, int amount) {
        this.id = id;
        this.studentId = studentId;
        this.month = month;
        this.year = year;
        this.amount = amount;
    }

    public int getId() {
        return id;
    }

    public int getStudentId() {
        return studentId;
    }

    public String getMonth() {
        return month;
    }

    public int getYear() {
        return year;
    }

    public int getAmount() {
        return amount;
    }
}
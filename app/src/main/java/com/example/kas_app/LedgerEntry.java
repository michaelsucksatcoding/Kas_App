package com.example.kas_app;

public class LedgerEntry {
    private int id;
    private String date;
    private String description;
    private String type; // "credit" or "debit"
    private int amount;

    public LedgerEntry(int id, String date, String description, String type, int amount) {
        this.id = id;
        this.date = date;
        this.description = description;
        this.type = type;
        this.amount = amount;
    }

    public int getId() {
        return id;
    }

    public String getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }
}
package edu.school21;
import java.time.LocalDateTime;

public class Expense {
    private String name;
    private String abstraction;
    private double amount;
    private LocalDateTime date;
    private long owner_id;

    public Expense(String name, String abstraction, double amount, LocalDateTime date, long owner_id) {
        this.name = name;
        this.abstraction = abstraction;
        this.amount = amount;
        this.date = date;
        this.owner_id = owner_id;
    }
    public String getName() {
        return name;
    }
    public double getAmount() {
        return amount;
    }
    public String getAbstraction() { return abstraction; }
    public LocalDateTime getDate() { return date; }
    public Long getOwnerId() { return owner_id; }
}
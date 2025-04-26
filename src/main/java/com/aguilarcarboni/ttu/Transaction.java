package com.aguilarcarboni.ttu;
import java.io.Serializable;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    private String licensePlate;
    private int kilometers;
    private boolean discountApplied;
    private double charge;

    public Transaction(String licensePlate, int kilometers, boolean discountApplied, double charge) {
        this.licensePlate = licensePlate;
        this.kilometers = kilometers;
        this.discountApplied = discountApplied;
        this.charge = charge;
    }
    
    public int getKilometers() { return kilometers; }
    public String getLicensePlate() { return licensePlate; }
    public boolean isDiscountApplied() { return discountApplied; }
    public double getCharge() { return charge; }

    @Override
    public String toString() {
        return String.format("Transaction Details:\n   Vehicle: %s\n   Distance: %d km\n   Discount: %s\n   Total: $%.2f", 
            licensePlate, kilometers, (discountApplied ? "10%" : "None"), charge);
    }
}

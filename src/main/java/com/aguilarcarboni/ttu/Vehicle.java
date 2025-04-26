package com.aguilarcarboni.ttu;
import java.io.Serializable;

// "SEDAN", "SUV", or "VAN"
public class Vehicle implements Serializable {
    private static final long serialVersionUID = 1L;
    private String licensePlate;
    private String type;
    private int kilometers;

    public Vehicle(String licensePlate, String type, int kilometers) {
        this.licensePlate = licensePlate;
        this.type = type;
        this.kilometers = kilometers;
    }

    public int getKilometers() { return kilometers; }
    public void addKilometers(int km) { this.kilometers += km; }
    public String getLicensePlate() { return licensePlate; }
    public String getType() { return type; }

    @Override
    public String toString() { 
        return String.format("%s | Type: %s | Distance: %d km", licensePlate, type, kilometers); 
    }
}

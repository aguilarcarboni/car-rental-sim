package com.aguilarcarboni.ttu;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class RentalShop {
    // Core shop properties
    private String shopLocation;
    private int parkingCapacity;
    private List<String> associatedLots;
    
    // Vehicle management
    private Map<String, Vehicle> availableVehicles = new HashMap<>();
    private Map<String, RentedRecord> activeRentals = new HashMap<>();
    private List<Transaction> rentalHistory = new ArrayList<>();
    private double totalRevenue = 0.0;
    
    // State persistence
    private static final String OUTPUTS_DIRECTORY = "outputs";
    private static final String SHOPS_DIRECTORY = OUTPUTS_DIRECTORY + File.separator + "shops";
    private static final String REGISTRY_DIRECTORY = OUTPUTS_DIRECTORY + File.separator + "registry";
    private String binaryStateFile;   // e.g. "SanJose.ser"
    private String textStateFile;     // e.g. "SanJose.txt"
    private static final String RENTAL_REGISTRY = REGISTRY_DIRECTORY + File.separator + "rented_registry.txt";

    public static void main(String[] args) {
        ensureOutputsDirectoryExists();
        ensureRegistryDirectoryExists();
        ensureShopsDirectoryExists();
        Map<String, String> flags = parseArgs(args);
        String loc = flags.get("--location");
        if (loc == null) {
           System.err.println("Error: --location must be provided."); 
           System.exit(1);
        }
    
        if (new File(loc + ".txt").exists()) {
            RentalShop shop = new RentalShop(loc, 0, List.of());
            shop.runCommandLoop();
        } else {
            int spaces = Integer.parseInt(flags.getOrDefault("--spaces-available", "10"));
            List<String> lots = Arrays.asList(flags.getOrDefault("--lots","").split(","));
            RentalShop shop = new RentalShop(loc, spaces, lots);
            shop.runCommandLoop();
        }
    }

    public RentalShop(String location, int parkingSpaces, List<String> lots) {
        this.shopLocation = location;
        this.parkingCapacity = parkingSpaces;
        this.associatedLots = lots;
        
        this.binaryStateFile = SHOPS_DIRECTORY + File.separator + location + ".ser";
        this.textStateFile = SHOPS_DIRECTORY + File.separator + location + ".txt";
        
        if (new File(textStateFile).exists()) {
            System.out.println("Found existing text state " + textStateFile + ", loading binary snapshot.");
            loadState();
            syncWithGlobalRegistryOnStartup();
            writeHumanState();
        } else {
            initializeInventory();
        }
    }

    public String executeCommand(String command) {
        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        PrintStream outputStream = new PrintStream(outputBuffer);
        PrintStream originalOutput = System.out;
        System.setOut(outputStream);

        processCommand(command);
        saveState();

        System.out.flush();
        System.setOut(originalOutput);
        return outputBuffer.toString();
    }

    private void runCommandLoop() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Welcome to " + shopLocation + " Rental Shop!");
        System.out.println("Available commands: RENT, RETURN, LIST, TRANSACTIONS");
        System.out.println("Type EXIT to quit.");
        while(true) {
            System.out.print("> ");
            String input = scanner.nextLine();
            if(input == null || input.trim().equalsIgnoreCase("EXIT")) {
                break;
            }
            processCommand(input.trim());
            saveState();
        }
        scanner.close();
    }

    private void processCommand(String input) {
        String[] tokens = input.split("\\s+");
        if(tokens.length == 0) return;
        
        String command = tokens[0].toUpperCase();
        switch(command) {
            case "RENT":
                handleRentCommand(tokens);
                break;
            case "RETURN":
                handleReturnCommand(tokens);
                break;
            case "LIST":
                listShopState();
                break;
            case "TRANSACTIONS":
                listTransactions();
                break;
            default:
                System.out.println("Unknown command.");
        }
    }

    private void handleRentCommand(String[] tokens) {
        if(tokens.length < 2) {
            System.out.println("Usage: RENT <VEHICLE TYPE>");
            return;
        }
        rentVehicle(tokens[1].toUpperCase());
    }

    private void handleReturnCommand(String[] tokens) {
        if(tokens.length < 3) {
            System.out.println("Usage: RETURN <LICENSE PLATE> <KILOMETERS>");
            return;
        }
        try {
            int km = Integer.parseInt(tokens[2]);
            returnVehicle(tokens[1].toUpperCase(), km);
        } catch(NumberFormatException e) {
            System.out.println("Invalid kilometers value.");
        }
    }

    private void rentVehicle(String vehicleType) {
        Vehicle vehicle = findAvailableVehicle(vehicleType);
        boolean hasLotDiscount = false;

        if (vehicle != null) {
            availableVehicles.remove(vehicle.getLicensePlate());
            System.out.println("RENT: Vehicle " + vehicle.getLicensePlate() + " (" + vehicle.getType() + ") has been provided from shop inventory.");
        } else {
            vehicle = requestVehicleFromLots(vehicleType);
            if (vehicle != null) {
                hasLotDiscount = true;
                System.out.println("RENT: Vehicle " + vehicle.getLicensePlate() + " (" + vehicle.getType() + ") has been obtained from lot with 10% discount.");
            } else {
                System.out.println("RENT: No vehicles of type " + vehicleType + " are available in shop or lots.");
                return;
            }
        }

        activeRentals.put(vehicle.getLicensePlate(), new RentedRecord(vehicle, hasLotDiscount));
        addToGlobalRegistry(vehicle.getLicensePlate(), vehicle.getType(), hasLotDiscount);
    }

    private Vehicle findAvailableVehicle(String vehicleType) {
        for(Vehicle v : availableVehicles.values()) {
            if(v.getType().equalsIgnoreCase(vehicleType)) {
                return v;
            }
        }
        return null;
    }

    private void returnVehicle(String licensePlate, int kilometers) {
        RentedRecord record = activeRentals.remove(licensePlate);
        if (record == null) {
            record = fetchFromGlobalRegistry(licensePlate);
            if (record == null) {
                System.out.println("RETURN: Vehicle " + licensePlate + " is not rented by any shop.");
                return;
            }
        }

        processVehicleReturn(record, kilometers);
        handleParkingSpaceManagement();
    }

    private void processVehicleReturn(RentedRecord record, int kilometers) {
        Vehicle vehicle = record.vehicle;
        vehicle.addKilometers(kilometers);
        
        double charge = calculateCharge(kilometers, record.hasLotDiscount);
        totalRevenue += charge;
        rentalHistory.add(new Transaction(vehicle.getLicensePlate(), kilometers, record.hasLotDiscount, charge));
        
        System.out.println("RETURN: Vehicle " + vehicle.getLicensePlate() + " has been returned. Distance added: " + kilometers + " km. Charge: $" + String.format("%.2f", charge));
        availableVehicles.put(vehicle.getLicensePlate(), vehicle);
    }

    private double calculateCharge(int kilometers, boolean hasDiscount) {
        double charge = kilometers;
        if(hasDiscount) {
            charge -= (0.10 * charge);
        }
        return charge;
    }

    private void handleParkingSpaceManagement() {
        int totalVehicles = availableVehicles.size() + activeRentals.size();
        int emptySpots = parkingCapacity - totalVehicles;
        
        if(emptySpots < 2 && !availableVehicles.isEmpty()) {
            moveVehicleToLot();
        }
    }

    private void moveVehicleToLot() {
        Vehicle toMove = Collections.max(availableVehicles.values(), Comparator.comparingInt(Vehicle::getKilometers));
        availableVehicles.remove(toMove.getLicensePlate());
        String targetLot = associatedLots.get(0);
        returnVehicleToLot(toMove, targetLot);
        System.out.println("RETURN: Vehicle " + toMove.getLicensePlate() + " (" + toMove.getType() + ") has been moved to lot " + targetLot + " due to limited parking space.");
    }

    private void listShopState() {
        System.out.println("LIST: Requesting shop status report for" + shopLocation);
        System.out.println("----------------------------------------");
        System.out.println("Shop Status Report  ");
        System.out.println("Available Parking Spaces: " + (parkingCapacity - (availableVehicles.size() + activeRentals.size())));
        
        System.out.println("\nVehicles in Shop Inventory:");
        for(Vehicle v : availableVehicles.values()) {
            System.out.println("   " + v);
        }
        
        System.out.println("\nVehicles Currently Rented:");
        for(RentedRecord r : activeRentals.values()) {
            System.out.println("   " + r.vehicle);
        }
        
        System.out.println("\nTotal Revenue: $" + String.format("%.2f", totalRevenue));
        System.out.println("----------------------------------------");
    }

    private void listTransactions() {
        System.out.println("TRANSACTIONS: Requesting transaction history for" + shopLocation);
        System.out.println("----------------------------------------");
        System.out.println("Transaction History");
        double totalDiscount = 0;
        
        for(Transaction t : rentalHistory) {
            System.out.println(t);
            if(t.isDiscountApplied()) {
                double discount = (0.10 * t.getCharge()) / 0.90;
                totalDiscount += discount;
            }
        }
        
        System.out.println("\nTotal Earnings: $" + String.format("%.2f", totalRevenue));
        System.out.println("Total Discounts Given: $" + String.format("%.2f", totalDiscount));
        System.out.println("----------------------------------------");
    }

    private void initializeInventory() {
        if(availableVehicles.isEmpty()) {
            for(String type: new String[]{"SEDAN", "SUV", "VAN"}) {
                Vehicle vehicle = requestVehicleFromLots(type);
                if(vehicle != null) {
                    availableVehicles.put(vehicle.getLicensePlate(), vehicle);
                    System.out.println("Initialized shop with vehicle " + vehicle.getLicensePlate() + " (" + vehicle.getType() + ")");
                    break;
                }
            }
        }
    }

    private Vehicle requestVehicleFromLots(String vehicleType) {
        for(String lotName : associatedLots) {
            String lotFile = lotName + ".txt";
            List<Vehicle> lotVehicles = readLotFileWithLock(lotFile);
            Iterator<Vehicle> iter = lotVehicles.iterator();
            while(iter.hasNext()) {
                Vehicle v = iter.next();
                if(v.getType().equalsIgnoreCase(vehicleType)) {
                    iter.remove();
                    writeLotFileWithLock(lotFile, lotVehicles);
                    return v;
                }
            }
        }
        return null;
    }

    private void returnVehicleToLot(Vehicle vehicle, String lotName) {
        String lotFile = lotName + ".txt";
        List<Vehicle> lotVehicles = readLotFileWithLock(lotFile);
        lotVehicles.add(vehicle);
        writeLotFileWithLock(lotFile, lotVehicles);
    }

    private List<Vehicle> readLotFileWithLock(String fileName) {
        List<Vehicle> vehicles = new ArrayList<>();
        File file = new File(fileName);
        if(!file.exists()) {
            return vehicles;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock(0L, Long.MAX_VALUE, true)) {
             
            raf.seek(0);
            String line;
            while((line = raf.readLine()) != null) {
                processVehicleLine(line, vehicles);
            }
        } catch(IOException e) {
            System.err.println("Error reading lot file (" + fileName + "): " + e.getMessage());
        }
        return vehicles;
    }

    private void processVehicleLine(String line, List<Vehicle> vehicles) {
        String[] parts = line.split(",");
        if(parts.length >= 3) {
            String plate = parts[0].trim();
            String type = parts[1].trim();
            int km = Integer.parseInt(parts[2].trim());
            vehicles.add(new Vehicle(plate, type, km));
        }
    }

    private void writeLotFileWithLock(String fileName, List<Vehicle> vehicles) {
        File file = new File(fileName);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock()) {
             
            raf.setLength(0);
            for(Vehicle v : vehicles) {
                raf.writeBytes(v.getLicensePlate() + "," + v.getType() + "," + v.getKilometers() + System.lineSeparator());
            }
        } catch(IOException e) {
            System.err.println("Error writing lot file (" + fileName + "): " + e.getMessage());
        }
    }

    private void saveState() {
        saveBinaryState();
        writeHumanState();
    }

    private void saveBinaryState() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(binaryStateFile))) {
            oos.writeObject(availableVehicles);
            oos.writeObject(activeRentals);
            oos.writeObject(rentalHistory);
            oos.writeDouble(totalRevenue);
            oos.writeInt(parkingCapacity);
            oos.writeObject(associatedLots);
        } catch (IOException e) {
            System.err.println("Error saving binary state: " + e.getMessage());
        }
    }

    private void writeHumanState() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(textStateFile))) {
            writeShopHeader(pw);
            writeInventorySection(pw);
            writeRentedVehiclesSection(pw);
            writeTransactionsSection(pw);
        } catch (IOException e) {
            System.err.println("Error writing human state: " + e.getMessage());
        }
    }

    private void writeShopHeader(PrintWriter pw) {
        pw.println("LOCATION: " + shopLocation);
        pw.println("SPACES_AVAILABLE: " + parkingCapacity);
        int empty = parkingCapacity - (availableVehicles.size() + activeRentals.size());
        pw.println("EMPTY_SLOTS: " + empty);
        pw.println("CASH_EARNED: $" + String.format("%.2f", totalRevenue));
        pw.println();
    }

    private void writeInventorySection(PrintWriter pw) {
        pw.println("INVENTORY:");
        for (Vehicle v : availableVehicles.values()) {
            pw.printf("  %s,%s,%d%n", v.getLicensePlate(), v.getType(), v.getKilometers());
        }
        pw.println();
    }

    private void writeRentedVehiclesSection(PrintWriter pw) {
        pw.println("RENTED_OUT:");
        for (RentedRecord r : activeRentals.values()) {
            pw.printf("  %s,%s,%d,discount=%s%n",
                r.vehicle.getLicensePlate(),
                r.vehicle.getType(),
                r.vehicle.getKilometers(),
                r.hasLotDiscount ? "10%" : "0%");
        }
        pw.println();
    }

    private void writeTransactionsSection(PrintWriter pw) {
        pw.println("TRANSACTIONS:");
        for (Transaction t : rentalHistory) {
            pw.printf("  %s,%d,discount=%s,$%.2f%n",
                t.getLicensePlate(),
                t.getKilometers(),
                t.isDiscountApplied() ? "10%" : "0%",
                t.getCharge());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadState() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(binaryStateFile))) {
            availableVehicles = (Map<String, Vehicle>) ois.readObject();
            activeRentals = (Map<String, RentedRecord>) ois.readObject();
            rentalHistory = (List<Transaction>) ois.readObject();
            totalRevenue = ois.readDouble();
            parkingCapacity = ois.readInt();
            associatedLots = (List<String>) ois.readObject();
        } catch (Exception e) {
            System.err.println("Error loading binary state: " + e.getMessage());
        }
    }

    private void syncWithGlobalRegistryOnStartup() {
        Set<String> registeredPlates = new HashSet<>();
        File registryFile = new File(RENTAL_REGISTRY);
        
        if (registryFile.exists()) {
            try (RandomAccessFile raf = new RandomAccessFile(registryFile, "r");
                FileChannel channel = raf.getChannel();
                FileLock lock = channel.lock(0L, Long.MAX_VALUE, true)) {
                
                String line;
                while ((line = raf.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 1) registeredPlates.add(parts[0]);
                }
            } catch (IOException e) {
                System.err.println("Error reading global registry: " + e.getMessage());
            }
        }
        
        activeRentals.keySet().removeIf(plate -> !registeredPlates.contains(plate));
    }

    private void addToGlobalRegistry(String plate, String type, boolean discount) {
        File file = new File(RENTAL_REGISTRY);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
            FileChannel ch = raf.getChannel();
            FileLock lock = ch.lock()) {
            raf.seek(raf.length());
            raf.writeBytes(String.format("%s,%s,%b%n", plate, type, discount));
        } catch (IOException e) {
            System.err.println("Error writing to rented registry: " + e.getMessage());
        }
    }

    private RentedRecord fetchFromGlobalRegistry(String plate) {
        File file = new File(RENTAL_REGISTRY);
        if (!file.exists()) return null;
        
        List<String> lines = new ArrayList<>();
        RentedRecord found = null;
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
            FileChannel ch = raf.getChannel();
            FileLock lock = ch.lock()) {
            
            raf.seek(0);
            String line;
            while ((line = raf.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(plate) && found == null) {
                    boolean discount = Boolean.parseBoolean(parts[2]);
                    found = new RentedRecord(new Vehicle(plate, parts[1], 0), discount);
                } else {
                    lines.add(line);
                }
            }
            
            raf.setLength(0);
            for (String l : lines) raf.writeBytes(l + System.lineSeparator());
        } catch (IOException e) {
            System.err.println("Error reading rented registry: " + e.getMessage());
        }
        return found;
    }

    public static Map<String,String> parseArgs(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for(String arg: args) {
            if(arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if(eq > 0) {
                    String key = arg.substring(0, eq);
                    String value = arg.substring(eq + 1);
                    flags.put(key, value);
                } else {
                    flags.put(arg, "");
                }
            }
        }
        return flags;
    }

    private static void ensureOutputsDirectoryExists() {
        File outputsDir = new File(OUTPUTS_DIRECTORY);
        if (!outputsDir.exists()) {
            outputsDir.mkdir();
        }
    }

    private static void ensureShopsDirectoryExists() {
        File shopsDir = new File(SHOPS_DIRECTORY);
        if (!shopsDir.exists()) {
            shopsDir.mkdir();
        }
    }

    private static void ensureRegistryDirectoryExists() {
        File registryDir = new File(REGISTRY_DIRECTORY);
        if (!registryDir.exists()) {
            registryDir.mkdir();
        }
    }

    private static class RentedRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        Vehicle vehicle;
        boolean hasLotDiscount;
        
        public RentedRecord(Vehicle vehicle, boolean hasLotDiscount) {
            this.vehicle = vehicle;
            this.hasLotDiscount = hasLotDiscount;
        }
    }
}


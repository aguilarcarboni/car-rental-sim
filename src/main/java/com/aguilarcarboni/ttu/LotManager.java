package com.aguilarcarboni.ttu;
import java.io.*;
import java.util.*;

public class LotManager {
    
    private static final String OUTPUTS_DIRECTORY = "outputs";
    private static final String REGISTRY_DIRECTORY = OUTPUTS_DIRECTORY + File.separator + "registry";
    private static final String PLATE_REGISTRY_PATH = REGISTRY_DIRECTORY + File.separator + "plates_registry.txt";
    private static final String LOTS_DIRECTORY = OUTPUTS_DIRECTORY + File.separator + "lots";

    public static void main(String[] args) {
        ensureOutputsDirectoryExists();
        ensureLotsDirectoryExists();
        ensureRegistryDirectoryExists();
        Map<String, String> params = parseCommandLineArgs(args);
        String targetLot = validateLotName(params);
        String lotFilePath = getLotFilePath(targetLot);
        List<Vehicle> currentVehicles = loadLotVehicles(lotFilePath);
        
        processVehicleAdditions(params, currentVehicles);
        handleVehicleRemoval(params, targetLot, currentVehicles);
        
        persistLotVehicles(lotFilePath, currentVehicles);
        System.out.println("Lot " + targetLot + " has been updated successfully!");
        System.out.println("Current vehicle count: " + currentVehicles.size());
    }
    
    private static void ensureOutputsDirectoryExists() {
        File outputsDir = new File(OUTPUTS_DIRECTORY);
        if (!outputsDir.exists()) {
            outputsDir.mkdir();
        }
    }

    private static void ensureRegistryDirectoryExists() {
        File registryDir = new File(REGISTRY_DIRECTORY);
        if (!registryDir.exists()) {
            registryDir.mkdir();
        }
    }

    private static void ensureLotsDirectoryExists() {
        File lotsDir = new File(LOTS_DIRECTORY);
        if (!lotsDir.exists()) {
            lotsDir.mkdir();
        }
    }

    private static String getLotFilePath(String lotName) {
        return LOTS_DIRECTORY + File.separator + lotName + ".txt";
    }
    
    private static String validateLotName(Map<String, String> params) {
        String targetLot = params.get("--lot-name");
        if(targetLot == null || targetLot.isEmpty()){
            System.err.println("Error: --lot-name is required.");
            System.exit(1);
        }
        return targetLot;
    }
    
    private static void handleVehicleRemoval(Map<String, String> params, String targetLot, List<Vehicle> vehicles) {
        if(!params.containsKey("--remove-vehicle")) { return; }
        
        String targetPlate = params.get("--remove-vehicle");
        boolean wasRemoved = vehicles.removeIf(v -> v.getLicensePlate().equalsIgnoreCase(targetPlate));
        
        if(!wasRemoved){
            System.out.println("Vehicle with license " + targetPlate + " was not found in lot " + targetLot + " (possibly assigned to a shop or is in use).");
        } else {
            System.out.println("Vehicle with license " + targetPlate + " has been successfully removed from lot " + targetLot + ".");
        }
    }
    
    private static void processVehicleAdditions(Map<String, String> params, List<Vehicle> vehicles) {
        addVehiclesByType(params, vehicles, "--add-sedan", "SEDAN");
        addVehiclesByType(params, vehicles, "--add-suv", "SUV");
        addVehiclesByType(params, vehicles, "--add-van", "VAN");
    }
    
    private static void addVehiclesByType(Map<String, String> params, List<Vehicle> vehicles, String paramKey, String vehicleType) {
        if(!params.containsKey(paramKey)) {
            return;
        }
        
        int count = Integer.parseInt(params.get(paramKey));
        for(int i = 0; i < count; i++){
            vehicles.add(new Vehicle(generateNewPlate(), vehicleType, 0));
        }
    }
    
    private static String generateNewPlate() {
        Set<String> existingPlates = loadPlateRegistry();
        String newPlate = generateRandomPlate();
        
        while(existingPlates.contains(newPlate)) {
            newPlate = generateRandomPlate();
        }
        
        existingPlates.add(newPlate);
        savePlateRegistry(existingPlates);
        return newPlate;
    }
    
    private static String generateRandomPlate() {
        StringBuilder plateBuilder = new StringBuilder();
        Random rng = new Random();
        
        // Generate letters
        for(int i = 0; i < 3; i++){
            plateBuilder.append((char) ('A' + rng.nextInt(26)));
        }
        plateBuilder.append("-");
        
        // Generate numbers
        for(int i = 0; i < 3; i++){
            plateBuilder.append(rng.nextInt(10));
        }
        
        return plateBuilder.toString();
    }
    
    private static Map<String, String> parseCommandLineArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        
        for(String arg: args) {
            if(arg.startsWith("--")) {
                int separatorIndex = arg.indexOf('=');
                if(separatorIndex > 0) {
                    String key = arg.substring(0, separatorIndex);
                    String value = arg.substring(separatorIndex + 1);
                    params.put(key, value);
                } else {
                    params.put(arg, "");
                }
            }
        }
        return params;
    }
    
    private static List<Vehicle> loadLotVehicles(String filePath) {
        List<Vehicle> vehicles = new ArrayList<>();
        File lotFile = new File(filePath);
        
        if(!lotFile.exists()) {
            return vehicles;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(lotFile))) {
            String line;
            while((line = reader.readLine()) != null) {
                processVehicleLine(line, vehicles);
            }
        } catch(IOException e) {
            System.err.println("Error reading lot file: " + e.getMessage());
        }
        return vehicles;
    }
    
    private static void processVehicleLine(String line, List<Vehicle> vehicles) {
        String[] vehicleData = line.split(",");
        if(vehicleData.length >= 3) {
            String plate = vehicleData[0].trim();
            String type = vehicleData[1].trim();
            int km = Integer.parseInt(vehicleData[2].trim());
            vehicles.add(new Vehicle(plate, type, km));
        } else {
            System.err.println("Invalid line in lot file: " + line);
        }
    }
    
    private static void persistLotVehicles(String filePath, List<Vehicle> vehicles) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            for(Vehicle v : vehicles) {
                writer.println(v.getLicensePlate() + "," + v.getType() + "," + v.getKilometers());
            }
        } catch(IOException e) {
            System.err.println("Error writing lot file: " + e.getMessage());
        }
    }
    
    private static Set<String> loadPlateRegistry() {
        Set<String> plates = new HashSet<>();
        File registryFile = new File(PLATE_REGISTRY_PATH);
        
        if(!registryFile.exists()) {
            return plates;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(registryFile))) {
            String line;
            while((line = reader.readLine()) != null) {
                plates.add(line.trim());
            }
        } catch(IOException e) {
            System.err.println("Error reading registry file: " + e.getMessage());
        }
        return plates;
    }
    
    private static void savePlateRegistry(Set<String> plates) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(PLATE_REGISTRY_PATH))) {
            for(String plate : plates) {
                writer.println(plate);
            }
        } catch(IOException e) {
            System.err.println("Error writing registry file: " + e.getMessage());
        }
    }
}


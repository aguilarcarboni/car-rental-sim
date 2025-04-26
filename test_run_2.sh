echo "Cleaning and packaging"
mvn clean package

# Run comprehensive tests
echo "Running tests"

echo "Create Central lot"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.LotManager --lot-name=Central --add-sedan=6 --add-suv=4 --add-van=3
echo

echo "Create Beach lot"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.LotManager --lot-name=Beach --add-sedan=3 --add-suv=5 --add-van=2
echo

echo "Create Mountain lot"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.LotManager --lot-name=Mountain --add-sedan=4 --add-suv=3 --add-van=4
echo

echo "Remove vehicle XYZ-789 from Central lot"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.LotManager --lot-name=Central --remove-vehicle=XYZ-789
echo

echo "Add more vehicles to Beach lot"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.LotManager --lot-name=Beach --add-sedan=2 --add-suv=2 --add-van=1
echo

echo "First scenario in San Jose Central"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.RentalShop --location=SanJoseCentral --spaces-available=10 --lots="$(pwd)/outputs/lots/Central" < commands/shop1-commands.txt
echo

echo "Second scenario in San Jose Beach"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.RentalShop --location=SanJoseBeach --spaces-available=8 --lots="$(pwd)/outputs/lots/Beach" < commands/shop2-commands.txt
echo

echo "Third scenario in Alajuela Mountain"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.RentalShop --location=AlajuelaMountain --spaces-available=12 --lots="$(pwd)/outputs/lots/Mountain" < commands/shop3-commands.txt
echo

echo "Fourth scenario in Heredia (multiple lots)"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.RentalShop --location=Heredia --spaces-available=15 --lots="$(pwd)/outputs/lots/Central,$(pwd)/outputs/lots/Beach" < commands/shop4-commands.txt
echo 
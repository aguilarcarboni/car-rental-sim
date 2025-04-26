echo "Cleaning and packaging"
mvn clean package

# Run comprehensive tests
echo "Running tests"

echo "Create Downtown lot"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.LotManager --lot-name=Downtown --add-sedan=5 --add-suv=3 --add-van=2
echo

echo "Create Airport lot"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.LotManager --lot-name=Airport --add-sedan=3 --add-suv=2 --add-van=4
echo

echo "Create Mall lot"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.LotManager --lot-name=Mall --add-sedan=4 --add-suv=1 --add-van=3
echo

echo "Remove vehicle ABC-123 from Downtown lot"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.LotManager --lot-name=Downtown --remove-vehicle=ABC-123
echo

echo "Add more vehicles to Airport lot"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.LotManager --lot-name=Airport --add-sedan=2 --add-suv=1
echo

echo "First scenario in San Jose Downtown"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.RentalShop --location=SanJoseDowntown --spaces-available=8 --lots="$(pwd)/outputs/lots/Downtown" < commands/shop1-commands.txt
echo

echo "Second scenario in San Jose Airport"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.RentalShop --location=SanJoseAirport --spaces-available=6 --lots="$(pwd)/outputs/lots/Airport" < commands/shop2-commands.txt
echo

echo "Third scenario in Alajuela Mall"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.RentalShop --location=AlajuelaMall --spaces-available=10 --lots="$(pwd)/outputs/lots/Mall" < commands/shop3-commands.txt
echo

echo "Fourth scenario in Heredia (multiple lots)"
java -cp target/car-rental-1.0-SNAPSHOT.jar com.aguilarcarboni.ttu.RentalShop --location=Heredia --spaces-available=12 --lots="$(pwd)/outputs/lots/Downtown,$(pwd)/outputs/lots/Airport" < commands/shop4-commands.txt
echo
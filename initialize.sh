echo "Initializing..."
echo "Cloning lazysodium-java..."
rm -r lazysodium-java && git submodule init && git submodule update

echo "Building lazysodium-java..."
cd lazysodium-java
./gradlew build

echo "Copying lazysodium-java to lib/libs..."
cd ..
mkdir -p lib/libs
mv lazysodium-java/build/libs/* lib/libs/

echo "Running tests..."
gradle wrapper
./gradlew test
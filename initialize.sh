echo "Initializing..."
echo "Cloning lazysodium-java..."
rm -r lazysodium-java && git submodule init && git submodule update

echo "Building lazysodium-java..."
cd lazysodium-java
./gradlew build

echo "Copying lazysodium-java to bip32ed25519kotlin/libs..."
cd ..
mkdir -p bip32ed25519/libs/
mv lazysodium-java/build/libs/* bip32ed25519/libs/

echo "Running tests..."
gradle wrapper
./gradlew test
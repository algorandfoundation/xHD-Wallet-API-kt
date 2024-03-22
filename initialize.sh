echo "Initializing..."
echo "Cloning lazysodium-java..."
rm -r lazysodium-java && git submodule init && git submodule update

echo "Building lazysodium-java..."
cd lazysodium-java
./gradlew build

echo "Copying lazysodium-java to bip32ed25519kotlin/libs..."
cd ..
echo "Copying to Android..."
find lazysodium-java/build/libs/ -type f \( -name "lazysodium-java-*-javadoc.jar" -o -name "lazysodium-java-*.jar" \) ! -name "*-sources.jar" -exec cp {} android/bip32ed25519/libs/ \;
echo "Copying to Desktop..."
cp lazysodium-java/build/libs/* desktop/bip32ed25519/libs/

echo "Attempting to build Android..."
cd android
gradle wrapper
./gradlew build
./gradlew assemble
cd ..

echo "Attempting to build..."
cd desktop
gradle wrapper
./gradlew build
cd ..

mkdir -p dist/android && mkdir -p dist/desktop
cp android/bip32ed25519/build/outputs/aar/*-release.aar dist/android/
cp desktop/bip32ed25519/build/libs/*.jar desktop/bip32ed25519/libs/*.jar dist/desktop/

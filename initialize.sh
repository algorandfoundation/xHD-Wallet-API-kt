set -e

echo "Initializing..."
echo "Cloning lazysodium-java..."
rm -r lazysodium-java && git submodule init && git submodule update

echo "Building lazysodium-java..."
cd lazysodium-java
./gradlew build

echo "Copying lazysodium-java to sharedModule/libs..."
cd ..
find lazysodium-java/build/libs/ -type f \( -name "lazysodium-java-*-javadoc.jar" -o -name "lazysodium-java-*.jar" \) ! -name "*-sources.jar" -exec cp {} sharedModule/libs/ \;

echo "Attempting to build"
gradle wrapper
./gradlew build
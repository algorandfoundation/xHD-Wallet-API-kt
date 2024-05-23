#!/bin/bash
## This script is used to setup the project

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
gradle wrapper --gradle-version 8.6
./gradlew build

echo "Checking if Bip32Ed25519-Android*.aar and Bip32Ed25519-JVM.jar exist in build/ directory..."
for pattern in 'build/Bip32Ed25519-Android*.aar' 'build/Bip32Ed25519-JVM.jar'; do
    files=( $pattern )
    if [ -e "${files[0]}" ]; then
        echo "Build files matching expected $pattern exist."
    else
        echo "No files matching $pattern found."
        exit 0
    fi
done
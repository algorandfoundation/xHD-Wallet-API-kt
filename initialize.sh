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
./gradlew build

echo "Checking if XHDWalletAPI-Android*.aar and XHDWalletAPI-JVM.jar exist in build/ directory..."
for pattern in 'build/XHDWalletAPI-Android*.aar' 'build/XHDWalletAPI-JVM.jar'; do
    files=( $pattern )
    if [ -e "${files[0]}" ]; then
        echo "Build files matching expected $pattern exist."
    else
        echo "No files matching $pattern found."
        exit 1
    fi
done
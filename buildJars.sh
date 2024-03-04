cd lazysodium-java
./gradlew build
cd ..
mv lazysodium-java/build/libs/* lib/libs/
./gradlew test
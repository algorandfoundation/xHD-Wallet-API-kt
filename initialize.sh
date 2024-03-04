git submodule init && git submodule update
gradle wrapper
cd lazysodium-java
./gradlew build
cd ..
mkdir -p lib/libs
mv lazysodium-java/build/libs/* lib/libs/
./gradlew test
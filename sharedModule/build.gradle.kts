import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "1.9.22"
}

group = "com.algorandfoundation.bip32ed25519"
version = "0.1.0"


dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(kotlin("stdlib"))
    
    implementation("net.java.dev.jna:jna:5.12.1")
    implementation("com.goterl:resource-loader:2.0.2")

    // Bip39 implementation
    api("cash.z.ecc.android:kotlin-bip39:1.0.7") 

    // For data validation
    api("net.pwall.json:json-kotlin-schema:0.46")
    api("com.algorand:algosdk:2.4.0")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.9.8")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.16.1")

}


java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm") version "1.9.22"
    `java-library`
}

group = "foundation.algorand.xhdwalletapi"
version = project.property("version") as String


dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(kotlin("stdlib"))

    api("commons-codec:commons-codec:1.16.1")
    api("net.java.dev.jna:jna:5.12.1")
    api("com.goterl:resource-loader:2.0.2")

    // Bip39 implementation
    api("cash.z.ecc.android:kotlin-bip39:1.0.7") 

    // For data validation
    api("net.pwall.json:json-kotlin-schema:0.46")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core
    api("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    api("com.fasterxml.jackson.core:jackson-core:2.16.1")
    api("org.msgpack:jackson-dataformat-msgpack:0.9.8")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.16.1")

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
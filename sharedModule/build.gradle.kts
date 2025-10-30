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

    api("commons-codec:commons-codec:1.19.0")
    api("net.java.dev.jna:jna:5.18.1")
    api("com.goterl:resource-loader:2.1.0")

    // Bip39 implementation
    api("cash.z.ecc.android:kotlin-bip39:1.0.9")

    // For data validation
    api("net.pwall.json:json-kotlin-schema:0.57")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core
    api("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    api("com.fasterxml.jackson.core:jackson-core:2.20.0")
    api("org.msgpack:jackson-dataformat-msgpack:0.9.10")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.20.0")

}


java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
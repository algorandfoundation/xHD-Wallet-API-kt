/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Kotlin library project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.5/userguide/building_java_projects.html in the Gradle documentation.
 */

version = project.property("version") as String
group = "com.algorandfoundation.xhdwalletapi"

plugins {
    kotlin("plugin.serialization") version "1.9.22"
    kotlin("jvm")
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        java.setSrcDirs(setOf(file("../sharedModule/src/main/kotlin")))
    }
}

dependencies {
    api(project(":sharedModule"))
    
    testImplementation("com.algorand:algosdk:2.4.0")
    
    // Use the Kotlin JUnit 5 integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // Use the JUnit 5 integration.
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is exported to consumers, that is to say found on their compile classpath.
    // api("org.apache.commons:commons-math3:3.6.1")

    // This dependency is used internally, and not exposed to consumers on their own compile
    // classpath.
    implementation("com.google.guava:guava:31.0.1-jre")
}

// Apply a specific Java toolchain to ease working on different environments.
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

// Run ./gradlew test to execute tests not requiring an Algorand Sandbox network
tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform{
        excludeTags("sandbox")
    }
    testLogging.showStandardStreams = true
}

tasks.register<Test>("testWithKhovratovichSafetyDepth") {
    useJUnitPlatform{
        excludeTags("sandbox")
    }
    systemProperty("khovratovichSafetyTest", "true")
    testLogging.showStandardStreams = true
}

// Run ./gradlew testWithAlgorandSandbox to run tests that interact with an Algorand Sandbox network (e.g. Algokit Localnet)
tasks.register<Test>("testWithAlgorandSandbox") {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
}

tasks.jar {
    archiveFileName.set("XHDWalletAPI-JVM.jar")
}

task("copyJarToRoot", type = Copy::class) {
    dependsOn("assemble")

    from("$buildDir/libs")
    into("$rootDir/build")
    include("*.jar")
}

tasks.named("build") {
    finalizedBy("copyJarToRoot")
}
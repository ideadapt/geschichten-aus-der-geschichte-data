plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.ser)
    id("io.ktor.plugin") version "2.3.12"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("com.aallam.openai:openai-client:3.8.2")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.ktor:ktor-client-logging:2.3.12")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-java:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("net.ideadapt.gagmap.WorkerKt")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks {
    val fatJar = register<Jar>("fatJar") {
        // We need this for Gradle optimization to work
        dependsOn.addAll(
            listOf(
                "compileJava",
                "compileKotlin",
                "processResources"
            )
        )
        archiveClassifier.set("standalone") // Naming the jar
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes(mapOf("Main-Class" to application.mainClass)) }
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) } + sourcesMain.output
        from(contents)
    }
    build {
        dependsOn(fatJar) // Trigger fat jar creation during build
    }
}

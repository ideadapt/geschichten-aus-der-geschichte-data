plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.ser)
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
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

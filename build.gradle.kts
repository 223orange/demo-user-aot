import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.example"
version = "0.0.1-SNAPSHOT"
description = "demo-user-aot"

val javaVersion = JavaLanguageVersion.of(25)

java {
    toolchain {
        languageVersion = javaVersion
    }
    withSourcesJar()
    withJavadocJar()
}


repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    buildInfo()          // Embed build metadata into the jar (build time, version, etc.)
}


/* --------------------------
   Leyden AOT tasks (JDK 24/25)
   Implements 3-phase workflow:
   record -> assemble -> run
   -------------------------- */

// Where to place AOT artifacts
val aotDir = layout.buildDirectory.dir("aot")
val aotConfig = aotDir.map { it.file("aot-config.json") }
val aotCache = aotDir.map { it.file("aot-cache.jsa") }

val bootJarTask = tasks.named<BootJar>("bootJar")

// Use the Gradle toolchain's java launcher (so these Exec tasks run with the selected JDK, not system java)
val toolchainLauncher = javaToolchains.launcherFor {
    languageVersion.set(javaVersion)
}

// Phase 1: Training run — records classes & profiles into aot-config.json
tasks.register<Exec>("aotRecord") {
    group = "aot"
    description = "Training run: records classes & profiles"
    dependsOn("bootJar")
    doFirst {
        // ensure build/aot exists
        aotDir.get().asFile.mkdirs()

        // resolve the fat jar and run FROM its folder
        val bootJar = tasks
            .getByName<BootJar>("bootJar")
            .archiveFile.get().asFile

        workingDir = bootJar.parentFile
        executable = toolchainLauncher.get().executablePath.asFile.absolutePath
        args = listOf(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=${aotConfig.get().asFile.absolutePath}",
            "-jar", bootJar.name,                        // filename only (relative to workingDir)
            "--spring.main.web-application-type=none"    // auto-exit after init
        )
    }
}


// Phase 2: Assemble AOT cache (.jsa) from the recorded config
tasks.register<Exec>("aotAssemble") {
    group = "aot"
    description = "Create AOT cache from aot-config.json"
    dependsOn("aotRecord")
    doFirst {
        val bootJar = tasks
            .getByName<BootJar>("bootJar")
            .archiveFile.get().asFile

        workingDir = bootJar.parentFile  // must match record’s workingDir
        executable = toolchainLauncher.get().executablePath.asFile.absolutePath
        args = listOf(
            "-cp", bootJar.name,                         // 👈 set the same app classpath
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=${aotConfig.get().asFile.absolutePath}",
            "-XX:AOTCache=${aotCache.get().asFile.absolutePath}",
            "-Xlog:class+path=info"
        )
    }
}

// Phase 3: Run the app using the AOT cache
tasks.register<Exec>("aotRun") {
    group = "aot"
    description = "Run app using the AOT cache"
    dependsOn("aotAssemble")
    doFirst {
        val bootJar = tasks
            .getByName<BootJar>("bootJar")
            .archiveFile.get().asFile

        workingDir = bootJar.parentFile
        executable = toolchainLauncher.get().executablePath.asFile.absolutePath
        args = listOf(
            "-XX:AOTMode=on",
            "-XX:AOTCache=${aotCache.get().asFile.absolutePath}",
            "-jar", bootJar.name
        )
    }
}

/* --------------------------
   Optional: simple startup benchmark
   Writes GC & class-load logs into gc-benchmark.log
   -------------------------- */
tasks.register<JavaExec>("benchmarkStartup") {
    group = "application"
    description = "Simple startup benchmark"
    dependsOn("classes")
    mainClass.set("org.example.demouseraot.DemoUserAotApplication")
    classpath = sourceSets.main.get().runtimeClasspath
    // Use unified logging for GC, safepoints, and class loading into a file
    jvmArgs(listOf("-Xlog:gc*,safepoint,class+load=info:file=gc-benchmark.log:time,level,tags"))
    systemProperties(
        mapOf(
            "spring.main.log-startup-info" to "true"
        )
    )
    // Ensure this also runs with the selected toolchain JDK
    javaLauncher.set(toolchainLauncher)
}

// Optional: show a friendly message during packaging
tasks.named<BootJar>("bootJar") {
    mainClass.set("org.example.demouseraot.DemoUserAotApplication")

    doFirst {
        println("Building Spring Boot JAR for Java ${java.toolchain.languageVersion.get()}")
    }
}
tasks.named<BootRun>("bootRun") {
    mainClass.set("org.example.demouseraot.DemoUserAotApplication")
}



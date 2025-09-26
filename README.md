# Spring Boot Leyden AOT Demo (Java 25)

A demonstration project showing how to combine **Spring Boot 3.5.6** with **Project Leyden Ahead-of-Time (AOT)** support in **Java 25** using Gradle Kotlin DSL.

---

## 🚀 Features

- **Java 25 Toolchain**: Uses the latest JDK features with Gradle toolchains.
- **Leyden AOT (JDK-level)**: Demonstrates the record → assemble → run workflow (`aotRecord`, `aotAssemble`, `aotRun`).
- **Spring Boot 3.5.6**: Web, JPA, and Actuator starters.
- **REST API**: Endpoints for managing `User` entities.
- **Spring Data JPA + H2**: In-memory relational database with JPA repositories.
- **Runtime Hints**: Reflection, serialization, and resource hints registered for AOT.
- **Benchmarking**: Task to measure startup performance with GC/class load logs.

---

## 📋 Prerequisites

- **Java 25** (Leyden AOT enabled in HotSpot).
- **Gradle 8.8+** (wrapper included).
- **Git** for cloning the repository.

### Verify Java 25

```bash
java --version
# openjdk 25 ...
````

---

## 🏗️ Project Structure

```
demo-user-aot/
├── src/main/java/org/example/
│   ├── DemoUserAotApplication.java    # Main entrypoint with runtime hints
│   ├── controller
│       └── UserController.java            # REST controller
│   ├── entity
│       └── User.java                      # Entity
│   ├── repository
│       └── UserRepository.java            # JPA repository   
│   └── service
│       └── UserService.java               # Service layer    
├── src/main/resources/
│   └── application.properties
├── build.gradle.kts                   # Gradle Kotlin DSL build
└── README.md
```

---

## 🛠️ Building and Running

### Normal workflow

```bash
# Clean and build
./gradlew clean build

# Run app normally
./gradlew bootRun
```

### Leyden AOT workflow

The project defines three tasks to demonstrate **Ahead-of-Time startup optimization**:

```bash
# 1. Record phase (observes startup, produces aot-config.json)
./gradlew aotRecord

# 2. Assemble phase (builds AOT cache from config)
./gradlew aotAssemble

# 3. Run with AOT cache (.jsa)
./gradlew aotRun
```

On first run, `aotRun` will automatically run all three phases in sequence.

### Benchmark startup time

```bash
./gradlew benchmarkStartup
```

This produces logs in `gc-benchmark.log` with GC, safepoint, and class loading info.

---

## 🌐 API Endpoints

Base URL: `http://localhost:8080`

### Main

* `GET /` → Welcome message
* `GET /health` → Application health check
* `GET /memory` → JVM memory usage

### User management

* `GET /users` → List all users
* `GET /users/{id}` → Get user by ID
* `GET /users/search/{name}` → Search users by name

### Actuator

* `GET /actuator/health`
* `GET /actuator/info`
* `GET /actuator/metrics`
* `GET /actuator/startup`

### H2 Console

* URL: `http://localhost:8080/h2-console`
* JDBC URL: `jdbc:h2:mem:aotdemo`
* Username: `sa`
* Password: (empty)

---

## ⚡ AOT Benefits

Project Leyden AOT provides:

1. **Faster Startup**

    * Class loading and linking resolved ahead of time.
    * Reduced interpreter overhead.

2. **Better Warm-up**

    * Profiles collected during training reused in subsequent runs.

3. **Improved Deployment**

    * Especially useful for short-lived or serverless workloads.

---

## 🔧 Runtime Hints

The app registers custom hints for reflection, serialization, and resources:

```java
hints.reflection().registerType(User.class);
hints.serialization().registerType(User.class);
hints.resources().registerPattern("application*.properties");
hints.resources().registerPattern("application*.yml");
```

---

## 📊 Benchmarking Example

```bash
# Regular startup
time ./gradlew bootRun

# AOT optimized startup
time ./gradlew aotRun
```

Expect ~2–3x faster startup for Spring Boot apps depending on complexity.

---

## 🧪 Testing

```bash
./gradlew test
```

---

## 🐳 Docker Support

You can package as a container:

```bash
./gradlew bootBuildImage
docker run -p 8080:8080 demo-user-aot:0.0.1-SNAPSHOT
```

---

## 📚 Resources

* [Project Leyden Early Access](https://openjdk.org/projects/leyden/)
* [JEP 483: Ahead-of-Time Class Loading and Linking](https://openjdk.org/jeps/483)
* [Spring Boot AOT Processing](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html#aot)
* [Gradle Toolchains](https://docs.gradle.org/current/userguide/toolchains.html)

---

## 📝 License

MIT License – see [LICENSE](LICENSE).

---

**Happy coding with Spring Boot + Leyden AOT on Java 25!** 🎉

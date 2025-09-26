# When to use Leyden AOT
Use it when you care about **startup + warm-up**:
- Microservices that scale up/down frequently.
- Short-lived batch/CLI jobs.
- CI smoke tests where startup dominates.

If your service is long-lived and latency under load is all that matters, AOT brings less value (the JIT eventually catches up).

---

# How to produce & use the cache (record → create → run)
You already automated this in Gradle:
- `./gradlew aotRecord` → writes `build/aot/aot-config.json`
- `./gradlew aotAssemble` → writes `build/aot/aot-cache.jsa`
- `./gradlew aotRun` → runs with `-XX:AOTMode=on`

In production you’ll do the same steps, just **inside CI/CD or your image build**, then **run with the cache**.

---

# Option A: Docker (multi-stage) — build the cache into the image
This is the most common for AWS ECS/EKS/Beanstalk/App Runner.

```dockerfile
# ---------- Stage 1: build jar ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
# Build fat jar
RUN ./gradlew --no-daemon clean bootJar

# ---------- Stage 2: create AOT cache ----------
# Use the SAME JDK family as runtime (exact build if possible)
FROM eclipse-temurin:25-jdk AS aot
WORKDIR /app
# copy artifacts we need
COPY --from=build /app/build/libs/*.jar ./app.jar
# AOT output folder
RUN mkdir -p /app/aot

# 1) record (run from the jar folder; use filename only)
RUN java \
    -XX:AOTMode=record \
    -XX:AOTConfiguration=/app/aot/aot-config.json \
    -jar app.jar \
    --spring.main.web-application-type=none

# 2) create
RUN java \
    -cp app.jar \
    -XX:AOTMode=create \
    -XX:AOTConfiguration=/app/aot/aot-config.json \
    -XX:AOTCache=/app/aot/aot-cache.jsa

# ---------- Stage 3: runtime ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=aot /app/app.jar ./app.jar
COPY --from=aot /app/aot/aot-cache.jsa ./aot-cache.jsa

# Expose app port
EXPOSE 8080

# Run WITH the cache
ENTRYPOINT ["java", "-XX:AOTMode=on", "-XX:AOTCache=/app/aot-cache.jsa", "-jar", "/app/app.jar"]
```

**Notes**
- We run `record` with `-jar app.jar` and `create` with `-cp app.jar` so the VM sees the **same app classpath** (your earlier mismatch).
- Keep the **same JDK** for build and runtime (ideally the *exact* build) or you’ll have to recreate the cache.
- If your image produces multiple jars (classifier), adjust the `COPY`/names accordingly.

---

# Option B: CI creates cache, runtime just uses it
If you *don’t* build Docker in CI, you can still pre-create the cache and publish both the jar and `.jsa` as artifacts:

**GitHub Actions (sketch)**

```yaml
jobs:
  build-aot:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
      - name: Build JAR
        run: ./gradlew --no-daemon clean bootJar
      - name: Record
        run: |
          cd build/libs
          java -XX:AOTMode=record \
               -XX:AOTConfiguration=../aot/aot-config.json \
               -jar demo-user-aot-0.0.1-SNAPSHOT.jar \
               --spring.main.web-application-type=none
      - name: Create cache
        run: |
          cd build/libs
          java -cp demo-user-aot-0.0.1-SNAPSHOT.jar \
               -XX:AOTMode=create \
               -XX:AOTConfiguration=../aot/aot-config.json \
               -XX:AOTCache=../aot/aot-cache.jsa
      - uses: actions/upload-artifact@v4
        with:
          name: app-with-aot
          path: |
            build/libs/demo-user-aot-0.0.1-SNAPSHOT.jar
            build/aot/aot-cache.jsa
```

Then your deploy step ships both files and runs:
```bash
java -XX:AOTMode=on -XX:AOTCache=./aot-cache.jsa -jar demo-user-aot-0.0.1-SNAPSHOT.jar
```

---

# Option C: “Auto” mode (let the JVM train itself)
You can let the JVM **record + create** on first startup and reuse on subsequent launches:

```bash
java \
  -XX:AOTMode=auto \
  -XX:AOTCache=/data/aot-cache.jsa \
  -jar app.jar
```

**Caveats**
- Your container must have a **writable, persistent path** (`/data`) or you’ll lose the cache between restarts.
- First boot is slower (it’s recording/creating); later boots benefit.
- In Kubernetes/ECS, use a **persistent volume** or **host path** if you want to keep the cache across pod/task restarts.

---

# Where to run “record”
Choose one:
1) **CI recording** (deterministic): use a small integration test / app init path (what you’re doing now). Good if startup is fairly deterministic.
2) **Canary traffic**: spin up 1 canary and record with real startup workload; then “create” and roll out. Best representativeness, slightly more moving parts.
3) **Auto** mode: simplest ops, but needs a persistent volume if you want benefits across restarts.

---

# Gotchas (quick checklist)
- **Same JDK**: cache is tied to the JDK build, OS, arch. Recreate if you upgrade the JDK/image.
- **Same classpath**: keep the jar name stable between record/create (you’ve fixed this with workingDir + filename).
- **Recreate on change**: if you bump dependencies or app version, rebuild the cache.
- **Warnings** like “Verification failed …” during create are normal; the VM skips those classes.
- **AOT isn’t native-image**: it improves JVM startup while keeping full Java semantics (no closed world). Your app runs as a normal JVM process.

---

# How to *verify* it’s working in AWS
- Log the Spring Boot startup line (e.g., “Started … in 1.43 seconds”) and compare **without** vs **with** AOT.
- Add JVM logging for AOT on the first runs to confirm the cache is consumed:
  ```
  -Xlog:aot=info
  ```
- If something changes and the cache is invalid, the VM will ignore it and fall back to normal JIT (your app still runs).

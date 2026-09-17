plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

// Benchmarks are excluded from `test` so the Docker image build and CI stay fast; run them
// deliberately with `./gradlew :backend:benchmark`.
tasks.test {
    useJUnitPlatform { excludeTags("benchmark") }
}

tasks.register<Test>("benchmark") {
    description = "Runs the allocation matcher latency benchmark."
    group = "verification"
    useJUnitPlatform { includeTags("benchmark") }
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    // Percentiles are meaningless if the harness output is swallowed or a stale result reused.
    outputs.upToDateWhen { false }
    testLogging { showStandardStreams = true }
}

// ── Static analysis ───────────────────────────────────────────────────────────
// ktlint enforces the official Kotlin style (formatting, imports, naming).
// detekt catches code smells ktlint does not look at: complexity, long methods,
// swallowed exceptions, magic numbers.
ktlint {
    version = "1.5.0"
    filter {
        // Generated build output is not ours to format.
        exclude { it.file.path.contains("/build/") }
    }
}

// detekt 1.23.x ships compiled against Kotlin 2.0.21 and refuses to run on the project's
// 2.2.21 compiler. detekt analyses on its own isolated classpath, so pinning *that* classpath
// back to 2.0.21 is the documented fix and does not affect how the project itself compiles.
// Revisit when detekt 2.x leaves alpha — it targets Kotlin 2.2 natively.
configurations.named("detekt").configure {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.21")
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/backend/detekt.yml"))
    baseline = file("$rootDir/backend/detekt-baseline.xml").takeIf { it.exists() }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required = true
        xml.required = false
        txt.required = false
    }
}

plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.diffplug.spotless") version "6.25.0"
    id("org.flywaydb.flyway") version "10.21.0"
}

// Flyway Gradle plugin needs PostgreSQL driver on its classpath
buildscript {
    dependencies {
        classpath("org.postgresql:postgresql:42.7.4")
        classpath("org.flywaydb:flyway-database-postgresql:10.21.0")
    }
}

group = "com.ecom360"
version = "1.3.1+20260403"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

extra["testcontainersVersion"] = "1.20.4"

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Cache (Caffeine — fast in-process cache)
    implementation("com.github.ben-manes.caffeine:caffeine")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Utilities
    implementation("org.apache.commons:commons-lang3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Structured logging (JSON for prod)
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

// ── Spotless (formatting) ──
spotless {
    java {
        googleJavaFormat("1.22.0")
        trimTrailingWhitespace()
        removeUnusedImports()
    }
}

// ── JaCoCo coverage ──
jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)      // for CI upload
        html.required.set(true)     // for local dev
        csv.required.set(false)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/dto/**",
                    "**/config/**",
                    "**/Ecom360Application*"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.50".toBigDecimal()   // raise as coverage grows
            }
        }
    }
}

// ── Flyway (DB migrations) ──
flyway {
    url = "jdbc:postgresql://${project.findProperty("DB_HOST") ?: "localhost"}:${project.findProperty("DB_PORT") ?: "5432"}/${project.findProperty("DB_NAME") ?: "ecom360"}"
    user = (project.findProperty("DB_USERNAME") as String?) ?: "postgres"
    password = (project.findProperty("DB_PASSWORD") as String?) ?: "postgres"
    locations = arrayOf("classpath:db/migration")
    cleanDisabled = false
}

tasks.named("flywayMigrate") {
    mustRunAfter("flywayClean")
}
tasks.register("flywayReload") {
    group = "flyway"
    description = "Clean + migrate: reset DB and re-run all migrations"
    dependsOn("flywayClean", "flywayMigrate")
}

// ── Convenience task: full quality gate ──
tasks.register("qualityGate") {
    group = "verification"
    description = "Runs all quality checks: format, compile, test, coverage"
    dependsOn("spotlessCheck", "compileJava", "test", "jacocoTestReport", "jacocoTestCoverageVerification")
}

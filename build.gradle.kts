import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "no.nav.amt-deltaker"
version = "1.0-SNAPSHOT"

plugins {
    val kotlinVersion = "1.9.24"

    kotlin("jvm") version kotlinVersion
    id("io.ktor.plugin") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

val kotlinVersion = "1.9.23"
val ktorVersion = "2.3.10"
val logbackVersion = "1.5.6"
val prometeusVersion = "1.12.5"
val ktlintVersion = "1.2.1"
val jacksonVersion = "2.17.1"
val logstashEncoderVersion = "7.4"
val commonVersion = "3.2024.04.22_13.50-7815154a2573"
val poaoTilgangVersion = "2024.04.25_14.10-afd419c1e0ec"
val kafkaClientsVersion = "3.7.0"
val testcontainersVersion = "1.19.7"
val kotestVersion = "5.8.1"
val flywayVersion = "10.12.0"
val hikariVersion = "5.1.0"
val kotliqueryVersion = "1.9.0"
val postgresVersion = "42.7.3"
val caffeineVersion = "3.1.8"
val mockkVersion = "1.13.10"
val nimbusVersion = "9.37.3"

dependencies {
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-serialization-jackson-jvm")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-call-id-jvm")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometeusVersion")
    implementation("io.ktor:ktor-server-host-common-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("no.nav.common:log:$commonVersion")

    implementation("no.nav.poao-tilgang:client:$poaoTilgangVersion")

    implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json-jvm:$kotestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("no.nav.amt.deltaker.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "no.nav.amt.deltaker.ApplicationKt",
        )
    }
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set(ktlintVersion)
}

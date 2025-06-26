group = "no.nav.amt-deltaker"
version = "1.0-SNAPSHOT"

// nyeste versjon av ktlint er 1.6.0 (anbefalt), men bygget vil da knekke
val ktlintVersion = "1.2.1"

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

dependencies {
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-serialization-jackson-jvm")
    implementation(libs.jackson.datatype.jsr310)

    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-call-id-jvm")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm")
    implementation(libs.micrometer.registry.prometheus)
    implementation("io.ktor:ktor-server-host-common-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.caffeine)

    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.nav.common.log)

    implementation(libs.nav.amt.lib.kafka)
    implementation(libs.nav.amt.lib.utils)
    implementation(libs.nav.amt.lib.models)

    implementation(libs.nav.poao.tilgang.client)

    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.postgresql)
    implementation(libs.kotliquery)
    implementation(libs.unleash.client.java)

    testImplementation("io.ktor:ktor-server-test-host")
    implementation(libs.ktor.client.mock)
    implementation(libs.kotlin.test.junit)
    implementation(libs.kotest.assertions.core.jvm)
    implementation(libs.kotest.assertions.json.jvm)
    implementation(libs.mockk)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.nav.amt.lib.testing)
    implementation(libs.awaitility)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "no.nav.amt.deltaker.ApplicationKt"

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktlint {
    version = ktlintVersion
}

// plain-JAR benyttes sannsynligvis ikke, og body bør byttes ut med
// enabled = false
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "no.nav.amt.deltaker.ApplicationKt",
        )
    }
}

tasks.shadowJar {
    mergeServiceFiles()
}

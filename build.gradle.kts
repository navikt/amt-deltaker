group = "no.nav.amt-deltaker"
version = "1.0-SNAPSHOT"

plugins {
    val kotlinVersion = "2.2.21"

    kotlin("jvm") version kotlinVersion
    id("io.ktor.plugin") version "3.3.2"
    id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    application
    distribution
}

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

val ktorVersion = "3.3.2"
val logbackVersion = "1.5.21"
val prometeusVersion = "1.16.0"
val ktlintVersion = "1.6.0"
val jacksonVersion = "2.20.1"
val logstashEncoderVersion = "9.0"
val commonVersion = "3.2025.10.10_08.21-bb7c7830d93c"
val poaoTilgangVersion = "2025.11.03_14.33-9c5783f38a9a"
val kotestVersion = "6.0.4"
val flywayVersion = "11.17.0"
val hikariVersion = "7.0.2"
val kotliqueryVersion = "1.9.1"
val postgresVersion = "42.7.8"
val caffeineVersion = "3.2.3"
val mockkVersion = "1.14.6"
val nimbusVersion = "10.6"
val unleashVersion = "11.1.1"
val amtLibVersion = "1.2025.11.17_13.27-229d152fbd7f"

dependencies {
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
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
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-request-validation:$ktorVersion")

    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("no.nav.common:log:$commonVersion")

    implementation("no.nav.amt.lib:kafka:$amtLibVersion")
    implementation("no.nav.amt.lib:utils:$amtLibVersion")
    implementation("no.nav.amt.lib:ktor:$amtLibVersion")

    implementation("no.nav.poao-tilgang:client:$poaoTilgangVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("io.getunleash:unleash-client-java:$unleashVersion")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json-jvm:$kotestVersion")
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")
    testImplementation("no.nav.amt.lib:testing:$amtLibVersion")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xwarning-level=IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE:disabled",
        )
    }
}

application {
    mainClass.set("no.nav.amt.deltaker.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

ktlint {
    version = ktlintVersion
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-Xshare:off",
        "-XX:+EnableDynamicAgentLoading",
    )
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "no.nav.amt.deltaker.ApplicationKt",
        )
    }
}

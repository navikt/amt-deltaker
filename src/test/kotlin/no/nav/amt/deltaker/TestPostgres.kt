package no.nav.amt.deltaker

import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.database.DatabaseConfig
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.postgresql.PostgreSQLContainer

object TestPostgres {
    private var dbInitialized = false

    fun bootstrap() {
        if (!dbInitialized) {
            if (!container.isRunning) container.start()
            initDatabase()
            dbInitialized = true
        }
    }

    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:17-alpine")
            .withCommand("postgres", "-c", "wal_level=logical")
            .waitingFor(HostPortWaitStrategy())
            .apply { addEnv("TZ", "Europe/Oslo") }
    }

    private fun initDatabase() {
        val c = container
        Database.init(
            DatabaseConfig(
                dbUsername = c.username,
                dbPassword = c.password,
                dbDatabase = c.databaseName,
                dbHost = c.host,
                dbPort = c.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT).toString(),
            ),
        )
    }
}

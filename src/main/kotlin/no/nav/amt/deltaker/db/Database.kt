package no.nav.amt.deltaker.db

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Session
import kotliquery.sessionOf
import kotliquery.using
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import org.flywaydb.core.Flyway
import org.postgresql.util.PGobject
import javax.sql.DataSource

object Database {
    lateinit var dataSource: DataSource

    fun init(environment: Environment) {
        dataSource = HikariDataSource().apply {
            dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
            addDataSourceProperty("serverName", environment.dbHost)
            addDataSourceProperty("portNumber", environment.dbPort)
            addDataSourceProperty("databaseName", environment.dbDatabase)
            addDataSourceProperty("user", environment.dbUsername)
            addDataSourceProperty("password", environment.dbPassword)
            maximumPoolSize = 10
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        }

        runMigration()
    }

    fun <A> query(block: (Session) -> A): A {
        return using(sessionOf(dataSource)) { session ->
            block(session)
        }
    }

    fun close() {
        (dataSource as HikariDataSource).close()
    }

    private fun runMigration(initSql: String? = null): Int =
        Flyway.configure()
            .connectRetries(5)
            .dataSource(dataSource)
            .initSql(initSql)
            .validateMigrationNaming(true)
            .load()
            .migrate()
            .migrations
            .size
}

fun toPGObject(value: Any?) = PGobject().also {
    it.type = "json"
    it.value = value?.let { v -> objectMapper.writeValueAsString(v) }
}

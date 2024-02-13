package no.nav.amt.deltaker.utils

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.db.Database
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.PostgreSQLContainer.POSTGRESQL_PORT
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.utility.DockerImageName

object SingletonPostgresContainer {

    private val log = LoggerFactory.getLogger(javaClass)

    private val postgresDockerImageName = getPostgresImage()

    private var postgresContainer: PostgreSQLContainer<Nothing>? = null

    fun start() {
        if (postgresContainer == null) {
            log.info("Starting new postgres database...")

            val container = createContainer()
            postgresContainer = container

            container.start()

            configureEnv(container)

            Database.init(Environment())

            setupShutdownHook()
        }
    }

    private fun configureEnv(container: PostgreSQLContainer<Nothing>) {
        System.setProperty(Environment.DB_HOST_KEY, container.host)
        System.setProperty(Environment.DB_PORT_KEY, container.getMappedPort(POSTGRESQL_PORT).toString())
        System.setProperty(Environment.DB_DATABASE_KEY, container.databaseName)
        System.setProperty(Environment.DB_PASSWORD_KEY, container.password)
        System.setProperty(Environment.DB_USERNAME_KEY, container.username)
    }

    private fun createContainer(): PostgreSQLContainer<Nothing> {
        val container = PostgreSQLContainer<Nothing>(DockerImageName.parse(postgresDockerImageName).asCompatibleSubstituteFor("postgres"))
        container.addEnv("TZ", "Europe/Oslo")
        return container.waitingFor(HostPortWaitStrategy())
    }

    private fun setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info("Shutting down postgres database...")
                postgresContainer?.stop()
                Database.close()
            },
        )
    }

    private fun getPostgresImage(): String {
        val digest = when (System.getProperty("os.arch")) {
            "aarch64" -> "@sha256:58ddae4817fc2b7ed43ac43c91f3cf146290379b7b615210c33fa62a03645e70"
            else -> ""
        }
        return "postgres:14-alpine$digest"
    }
}

package no.nav.amt.deltaker.kafka.utils

import org.slf4j.LoggerFactory
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

object SingletonKafkaProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private var kafkaContainer: KafkaContainer? = null

    fun getHost(): String {
        if (kafkaContainer == null) {
            log.info("Starting new Kafka Instance...")
            kafkaContainer = KafkaContainer(DockerImageName.parse(getKafkaImage()))
            kafkaContainer!!.start()
            setupShutdownHook()
        }
        return kafkaContainer!!.bootstrapServers
    }

    private fun setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info("Shutting down Kafka server...")
                kafkaContainer?.stop()
            },
        )
    }

    private fun getKafkaImage(): String {
        val tag = when (System.getProperty("os.arch")) {
            "aarch64" -> "7.2.2-1-ubi8.arm64"
            else -> "7.2.2"
        }

        return "confluentinc/cp-kafka:$tag"
    }
}

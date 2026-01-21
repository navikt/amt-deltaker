package no.nav.amt.deltaker

import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.outbox.OutboxProcessor
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.utils.job.JobManager
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

object TestOutboxEnvironment {
    private val isReady: AtomicBoolean = AtomicBoolean(true)

    val kafkaProducer = Producer<String, String>(LocalKafkaConfig(SingletonKafkaProvider.getHost()))

    val outboxService = OutboxService().also { innerOutboxService ->
        OutboxProcessor(
            innerOutboxService,
            JobManager(
                isLeader = { true },
                applicationIsReady = {
                    isReady.get()
                },
            ),
            kafkaProducer,
        ).apply {
            start(initialDelay = Duration.ofMillis(100), period = Duration.ofMillis(100))

            Runtime.getRuntime().addShutdownHook(
                Thread {
                    isReady.set(false)
                    println("OutboxProcessor shutdown hook triggered")
                },
            )
        }
    }
}

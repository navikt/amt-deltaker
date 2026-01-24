package no.nav.amt.deltaker

import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import no.nav.amt.lib.kafka.Producer
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.outbox.OutboxProcessor
import no.nav.amt.lib.outbox.OutboxService
import no.nav.amt.lib.testing.SingletonKafkaProvider
import no.nav.amt.lib.utils.database.Database
import no.nav.amt.lib.utils.job.JobManager
import java.time.Duration

object TestOutboxEnvironment {
    private const val INIT_DELAY_IN_MS = 200L
    private const val PERIOD_IN_MS = 100L

    val kafkaProducer = Producer<String, String>(LocalKafkaConfig(SingletonKafkaProvider.getHost()))

    val outboxService: OutboxService by lazy {
        val jobManager = JobManager(
            isLeader = { true },
            applicationIsReady = { Database.isReady() },
        )

        OutboxService().also { innerOutboxService ->
            OutboxProcessor(
                innerOutboxService,
                jobManager,
                kafkaProducer,
            ).apply {
                start(
                    initialDelay = Duration.ofMillis(INIT_DELAY_IN_MS),
                    period = Duration.ofMillis(PERIOD_IN_MS),
                )

                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        runBlocking { jobManager.stopJobs() }
                        println("OutboxProcessor shutdown hook triggered")
                    },
                )
            }
        }
    }

    fun Database.isReady(): Boolean = try {
        val query = queryOf("SELECT 1").map { it.long(1) }.asSingle
        this.query { session -> session.run(query) }
        true
    } catch (_: Exception) {
        println("Database is not ready")
        false
    }
}

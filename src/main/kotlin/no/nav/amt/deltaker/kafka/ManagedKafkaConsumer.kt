package no.nav.amt.deltaker.kafka

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration

class ManagedKafkaConsumer<K, V>(
    val topic: String,
    val config: Map<String, *>,
    val consume: suspend (key: K, value: V) -> Unit,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var running = true

    fun run() = scope.launch {
        log.info("Started consumer for topic: $topic")
        KafkaConsumer<K, V>(config).use { consumer ->
            consumer.subscribe(listOf(topic))
            while (running) {
                consumer.poll(Duration.ofMillis(1000)).forEach { record ->
                    process(record)
                }
                consumer.commitSync()
            }
        }
    }

    private suspend fun process(record: ConsumerRecord<K, V>) {
        var retries = 0
        var success = false

        while (!success) {
            try {
                consume(record.key(), record.value())
                log.debug("Consumed record topic=${record.topic()} partition=${record.partition()} offset=${record.offset()}")
                success = true
            } catch (t: Throwable) {
                log.error("Failed to consume record topic=${record.topic()} partition=${record.partition()} offset=${record.offset()} error=$t")
                delay(exponentialBackoff(++retries))
            }
        }
    }

    fun stop() {
        running = false
        job.cancel()
    }

    private fun exponentialBackoff(retries: Int) = 1000L * (retries * retries)
}

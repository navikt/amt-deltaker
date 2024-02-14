package no.nav.amt.deltaker.kafka

import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.kafka.config.LocalKafkaConfig
import no.nav.amt.deltaker.kafka.utils.SingletonKafkaProvider
import no.nav.amt.deltaker.kafka.utils.produceStringString
import no.nav.amt.deltaker.kafka.utils.produceUUIDByteArray
import no.nav.amt.deltaker.utils.AsyncUtils
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import org.junit.Test
import java.util.UUID

class ManagedKafkaConsumerTest {
    val topic = "test.topic"

    val stringConsumerConfig = LocalKafkaConfig(SingletonKafkaProvider.getHost()).consumerConfig(
        keyDeserializer = StringDeserializer(),
        valueDeserializer = StringDeserializer(),
        groupId = "test-consumer",
    )

    @Test
    fun `ManagedKafkaConsumer - konsumerer record med String, String`() {
        val key = "key"
        val value = "value"
        val cache = mutableMapOf<String, String>()

        produceStringString(ProducerRecord(topic, key, value))

        val consumer = ManagedKafkaConsumer(topic, stringConsumerConfig) { k: String, v: String ->
            cache[k] = v
        }
        consumer.run()

        AsyncUtils.eventually {
            cache[key] shouldBe value
            consumer.stop()
        }
    }

    @Test
    fun `ManagedKafkaConsumer - konsumerer record med UUID, ByteArray`() {
        val key = UUID.randomUUID()
        val value = "value".toByteArray()
        val cache = mutableMapOf<UUID, ByteArray>()
        val uuidTopic = "uuid.topic"

        produceUUIDByteArray(ProducerRecord(uuidTopic, key, value))

        val config = LocalKafkaConfig(SingletonKafkaProvider.getHost())
            .consumerConfig(
                keyDeserializer = UUIDDeserializer(),
                valueDeserializer = ByteArrayDeserializer(),
                groupId = "test-consumer",
            )

        val consumer = ManagedKafkaConsumer(uuidTopic, config) { k: UUID, v: ByteArray ->
            cache[k] = v
        }
        consumer.run()

        AsyncUtils.eventually {
            cache[key] shouldBe value
            consumer.stop()
        }
    }

    @Test
    fun `ManagedKafkaConsumer - konsumere melding pa nytt hvis noe feiler`() {
        val key = "key"
        val value = "value"

        var antallGangerKallt = 0

        produceStringString(ProducerRecord(topic, key, value))

        val consumer = ManagedKafkaConsumer<String, String>(topic, stringConsumerConfig) { _, _ ->
            antallGangerKallt++
            throw IllegalStateException("skal feile noen ganger")
        }
        consumer.run()

        AsyncUtils.eventually {
            antallGangerKallt shouldBe 2
            consumer.stop()
        }
    }
}

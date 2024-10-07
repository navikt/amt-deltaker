package no.nav.amt.deltaker.hendelse

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.hendelse.model.Hendelse
import no.nav.amt.lib.kafka.config.KafkaConfig
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

class FooKafkaConfig : KafkaConfig {
    private val kafkaConfigImpl = KafkaConfigImpl()

    override fun commonConfig() = kafkaConfigImpl.commonConfig()

    override fun <K, V> consumerConfig(
        keyDeserializer: Deserializer<K>,
        valueDeserializer: Deserializer<V>,
        groupId: String,
    ) = kafkaConfigImpl.consumerConfig(keyDeserializer, valueDeserializer, groupId)

    override fun producerConfig() = mapOf(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
        ProducerConfig.ACKS_CONFIG to "1",
        ProducerConfig.RETRIES_CONFIG to Int.MAX_VALUE,
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,
    ) + commonConfig()
}

class HendelseProducer(
    private val kafkaConfig: KafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else FooKafkaConfig(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun produce(hendelse: Hendelse) {
        val key = hendelse.deltaker.id.toString()
        val value = objectMapper.writeValueAsString(hendelse)
        val record = ProducerRecord(Environment.DELTAKER_HENDELSE_TOPIC, key, value)

        KafkaProducer<String, String>(kafkaConfig.producerConfig()).use {
            val metadata = it.send(record).get()
            log.info(
                "Produserte melding til topic ${metadata.topic()}, " +
                    "key=$key, " +
                    "offset=${metadata.offset()}, " +
                    "partition=${metadata.partition()}",
            )
        }
    }
}

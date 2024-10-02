package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.lib.kafka.config.KafkaConfig
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.UUID

class DeltakerV1Producer(
    private val kafkaConfig: KafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun produce(deltakerV1Dto: DeltakerV1Dto) {
        val key = deltakerV1Dto.id.toString()
        val value = objectMapper.writeValueAsString(deltakerV1Dto)
        val record = ProducerRecord(Environment.DELTAKER_V1_TOPIC, key, value)

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

    fun produceTombstone(deltakerId: UUID) {
        val key = deltakerId.toString()
        val value: String? = null
        val record = ProducerRecord(Environment.DELTAKER_V1_TOPIC, key, value)

        KafkaProducer<String, String?>(kafkaConfig.producerConfig()).use {
            val metadata = it.send(record).get()
            log.info(
                "Produserte tombstone-melding til topic ${metadata.topic()}, " +
                    "key=$key, " +
                    "offset=${metadata.offset()}, " +
                    "partition=${metadata.partition()}",
            )
        }
    }
}

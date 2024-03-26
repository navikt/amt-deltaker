package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.kafka.config.KafkaConfig
import no.nav.amt.deltaker.kafka.config.KafkaConfigImpl
import no.nav.amt.deltaker.kafka.config.LocalKafkaConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class DeltakerProducer(
    private val kafkaConfig: KafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl(),
    private val deltakerV2MapperService: DeltakerV2MapperService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun produce(deltaker: Deltaker) {
        if (deltaker.status.type == DeltakerStatus.Type.KLADD) return

        val deltakerV2Dto = deltakerV2MapperService.tilDeltakerV2Dto(deltaker)

        val key = deltaker.id.toString()
        val value = objectMapper.writeValueAsString(deltakerV2Dto)
        val record = ProducerRecord(Environment.DELTAKER_V2_TOPIC, key, value)

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

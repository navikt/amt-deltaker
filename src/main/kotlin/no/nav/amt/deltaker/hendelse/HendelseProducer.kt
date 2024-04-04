package no.nav.amt.deltaker.hendelse

import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.hendelse.model.Hendelse
import no.nav.amt.deltaker.kafka.config.KafkaConfig
import no.nav.amt.deltaker.kafka.config.KafkaConfigImpl
import no.nav.amt.deltaker.kafka.config.LocalKafkaConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class HendelseProducer(
    private val kafkaConfig: KafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl(),
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

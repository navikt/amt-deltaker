package no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.kafka.Consumer
import no.nav.amt.deltaker.kafka.ManagedKafkaConsumer
import no.nav.amt.deltaker.kafka.config.KafkaConfig
import no.nav.amt.deltaker.kafka.config.KafkaConfigImpl
import no.nav.amt.deltaker.kafka.config.LocalKafkaConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import java.util.UUID

class TiltakstypeConsumer(
    private val repository: TiltakstypeRepository,
    kafkaConfig: KafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl(),
) : Consumer<UUID, String?> {
    private val consumer = ManagedKafkaConsumer(
        topic = Environment.TILTAKSTYPE_TOPIC,
        config = kafkaConfig.consumerConfig(
            keyDeserializer = UUIDDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = Environment.KAFKA_CONSUMER_GROUP_ID,
        ),
        consume = ::consume,
    )

    override fun run() = consumer.run()

    override suspend fun consume(key: UUID, value: String?) {
        value?.let { handterTiltakstype(objectMapper.readValue(it)) }
    }

    private fun handterTiltakstype(tiltakstype: TiltakstypeDto) {
        if (!tiltakstype.erStottet() || tiltakstype.status != Tiltakstypestatus.Aktiv) return

        repository.upsert(tiltakstype.toModel())
    }
}

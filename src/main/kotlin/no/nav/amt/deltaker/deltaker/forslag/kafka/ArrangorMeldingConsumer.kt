package no.nav.amt.deltaker.deltaker.forslag.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.kafka.ManagedKafkaConsumer
import no.nav.amt.lib.kafka.config.KafkaConfig
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Melding
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import org.slf4j.LoggerFactory
import java.util.UUID

class ArrangorMeldingConsumer(
    private val forslagService: ForslagService,
    private val deltakerService: DeltakerService,
    private val isDev: Boolean = Environment.isDev(),
    kafkaConfig: KafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl("earliest"),
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = ManagedKafkaConsumer(
        topic = Environment.ARRANGOR_MELDING_TOPIC,
        config = kafkaConfig.consumerConfig(
            keyDeserializer = UUIDDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = Environment.KAFKA_CONSUMER_GROUP_ID,
        ),
        consume = ::consume,
    )

    override suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            log.warn("Mottok tombstone for melding med id: $key")
            forslagService.delete(key)
            return
        }

        val melding = objectMapper.readValue<Melding>(value)
        if (!forslagService.kanLagres(melding.deltakerId)) {
            if (isDev) {
                log.error("Mottatt melding ${melding.id} på deltaker som ikke finnes, deltakerid ${melding.deltakerId}, ignorerer")
            } else {
                throw RuntimeException("Mottatt melding ${melding.id} på deltaker som ikke finnes, deltakerid ${melding.deltakerId}")
            }
        }

        when (melding) {
            is EndringFraArrangor -> deltakerService.upsertEndretDeltaker(melding)
            is Forslag -> forslagService.upsert(melding)
        }
    }

    override fun run() = consumer.run()
}

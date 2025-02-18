package no.nav.amt.deltaker.deltakerliste.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.arenaKodeTilTiltakstype
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.erStottet
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.kafka.ManagedKafkaConsumer
import no.nav.amt.lib.kafka.config.KafkaConfig
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import java.util.UUID

class DeltakerlisteConsumer(
    private val repository: DeltakerlisteRepository,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val arrangorService: ArrangorService,
    private val deltakerService: DeltakerService,
    kafkaConfig: KafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl(),
) : Consumer<UUID, String?> {
    private val consumer = ManagedKafkaConsumer(
        topic = Environment.DELTAKERLISTE_TOPIC,
        config = kafkaConfig.consumerConfig(
            keyDeserializer = UUIDDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = Environment.KAFKA_CONSUMER_GROUP_ID,
        ),
        consume = ::consume,
    )

    override fun run() = consumer.run()

    override suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            repository.delete(key)
        } else {
            handterDeltakerliste(objectMapper.readValue(value))
        }
    }

    private suspend fun handterDeltakerliste(deltakerlisteDto: DeltakerlisteDto) {
        if (!erStottet(deltakerlisteDto.tiltakstype.arenaKode)) return

        val tiltakstype = tiltakstypeRepository.get(arenaKodeTilTiltakstype(deltakerlisteDto.tiltakstype.arenaKode)).getOrThrow()

        val arrangor = arrangorService.hentArrangor(deltakerlisteDto.virksomhetsnummer)
        val deltakerliste = deltakerlisteDto.toModel(arrangor, tiltakstype)
        repository.upsert(deltakerliste)

        if (deltakerliste.erAvlystEllerAvbrutt()) {
            deltakerService.avsluttDeltakelserPaaDeltakerliste(deltakerliste.id)
        }
    }
}

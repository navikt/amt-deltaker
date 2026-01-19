package no.nav.amt.deltaker.deltaker.forslag.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.extensions.toVurdering
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.Melding
import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.util.UUID

class ArrangorMeldingConsumer(
    private val forslagRepository: ForslagRepository,
    private val forslagService: ForslagService,
    private val deltakerService: DeltakerService,
    private val vurderingRepository: VurderingRepository,
    private val deltakerProducerService: DeltakerProducerService,
    private val isDev: Boolean = Environment.isDev(),
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.ARRANGOR_MELDING_TOPIC,
        consumeFunc = ::consume,
    )

    suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            log.warn("Mottok tombstone for melding med id: $key")
            forslagRepository.delete(key)
            return
        }

        val melding = objectMapper.readValue<Melding>(value)
        if (!(forslagRepository.kanLagres(melding.deltakerId) || melding is Vurdering)) {
            if (isDev) {
                log.error("Mottatt melding ${melding.id} på deltaker som ikke finnes, deltakerid ${melding.deltakerId}, ignorerer")
                return
            } else {
                throw RuntimeException("Mottatt melding ${melding.id} på deltaker som ikke finnes, deltakerid ${melding.deltakerId}")
            }
        }

        when (melding) {
            is EndringFraArrangor -> deltakerService.upsertEndretDeltaker(melding)
            is Forslag -> forslagService.upsert(melding)
            is Vurdering -> handleVurdering(melding)
        }
    }

    private suspend fun handleVurdering(vurdering: Vurdering) {
        vurderingRepository.upsert(vurdering.toVurdering())
        val deltaker = deltakerService.get(vurdering.deltakerId).getOrThrow()
        deltakerProducerService.produce(deltaker)
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()
}

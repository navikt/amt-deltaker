package no.nav.amt.deltaker.deltaker.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.apiclients.mulighetsrommet.MulighetsrommetApiClient
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.dto.EnkeltplassDeltakerPayload
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID
import kotlin.getOrThrow

class EnkeltplassDeltakerConsumer(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val unleashToggle: UnleashToggle,
    private val mulighetsrommetApiClient: MulighetsrommetApiClient,
    private val arrangorService: ArrangorService,
    private val tiltakstypeRepository: TiltakstypeRepository,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.ENKELTPLASS_DELTAKER_TOPIC,
        consumeFunc = ::consume,
    )

    override suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            log.info("Konsumerer tombstone med key $key")
            return
        } else {
            log.info("Konsumerer deltaker med key $key")
            processDeltaker(objectMapper.readValue(value))
            log.info("Ferdig med å konsumere deltaker med key $key")
        }
    }

    private suspend fun processDeltaker(deltakerPayload: EnkeltplassDeltakerPayload) {
        val deltakerlisteFromDbResult = deltakerlisteRepository.get(deltakerPayload.gjennomforingId)
        val deltakerliste =
            deltakerlisteFromDbResult.getOrElse {
                mulighetsrommetApiClient
                    .hentGjennomforingV2(deltakerPayload.gjennomforingId) // Fallback hvis deltakerlisten ikke finnes i databasen
                    .let { gjennomforing ->
                        gjennomforing.toModel(
                            arrangor = arrangorService.hentArrangor(gjennomforing.organisasjonsnummer),
                            tiltakstype = tiltakstypeRepository
                                .get(Tiltakskode.valueOf(gjennomforing.tiltakstype.tiltakskode))
                                .getOrThrow(),
                        )
                    }
            }

        if (!unleashToggle.skalLeseArenaDataForTiltakstype(deltakerliste.tiltakstype.tiltakskode)) return

        if (deltakerlisteFromDbResult.isFailure) deltakerlisteRepository.upsert(deltakerliste)

        log.info("Ingester enkeltplass deltaker med id ${deltakerPayload.id}")
        val deltaker = deltakerPayload.toDeltaker(deltakerliste, navBrukerService.get(deltakerPayload.personIdent).getOrThrow())

        upsertImportertDeltaker(deltaker)

        log.info("Ingest for arenadeltaker med id ${deltaker.id} er ferdig")
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    private fun upsertImportertDeltaker(deltaker: Deltaker) {
        val importertData = deltaker.toImportertData()
        deltakerRepository.upsert(deltaker)
        importertFraArenaRepository.upsert(importertData)
    }

    private fun Deltaker.toImportertData() = ImportertFraArena(
        deltakerId = id,
        importertDato = LocalDateTime.now(), // TODO: brukes ikke ved insert i db
        deltakerVedImport = this.toDeltakerVedImport(opprettet.toLocalDate()),
    )
}

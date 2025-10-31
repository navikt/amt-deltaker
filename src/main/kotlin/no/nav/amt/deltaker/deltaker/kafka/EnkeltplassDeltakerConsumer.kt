package no.nav.amt.deltaker.deltaker.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.apiclients.mulighetsrommet.MulighetsrommetApiClient
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
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
    private val deltakerService: DeltakerService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val unleashToggle: UnleashToggle,
    private val mulighetsrommetApiClient: MulighetsrommetApiClient,
    private val arrangorService: ArrangorService,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val deltakerProducerService: DeltakerProducerService,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.ENKELTPLASS_DELTAKER_TOPIC,
        consumeFunc = ::consume,
    )

    override suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            // arena-acl skal aldri tombstone, sletting av enkeltplassdeltakere håndteres med status=feilregistrert
            throw IllegalStateException("Tombstone er ikke støttet. Key: $key")
        } else {
            log.info("Konsumerer enkeltplass deltaker med key $key")
            consumeDeltaker(objectMapper.readValue(value))
            log.info("Ferdig med å konsumere enkeltplass deltaker med key $key")
        }
    }

    suspend fun consumeDeltaker(deltakerPayload: EnkeltplassDeltakerPayload) {
        val deltakerlisteFromDbResult = deltakerlisteRepository.get(deltakerPayload.gjennomforingId)
        val deltakerliste =
            deltakerlisteFromDbResult.getOrElse {
                mulighetsrommetApiClient
                    .hentGjennomforingV2(deltakerPayload.gjennomforingId) // Fallback hvis deltakerlisten ikke finnes i databasen
                    .let { gjennomforing ->
                        gjennomforing.toModel(
                            arrangor = arrangorService.hentArrangor(gjennomforing.arrangor.organisasjonsnummer),
                            tiltakstype = tiltakstypeRepository
                                .get(
                                    Tiltakskode.valueOf(gjennomforing.tiltakstype.tiltakskode),
                                ).getOrThrow(),
                        )
                    }
            }

        if (!unleashToggle.skalLeseArenaDataForTiltakstype(deltakerliste.tiltakstype.tiltakskode)) return

        // Lagrer ny gjennomføring om ikke den finnes i db
        if (deltakerlisteFromDbResult.isFailure) deltakerlisteRepository.upsert(deltakerliste)

        log.info("Ingester enkeltplass deltaker med id ${deltakerPayload.id}")
        val deltakerStatus = deltakerService.get(deltakerPayload.id).getOrNull()?.status
        val deltaker = deltakerPayload.toDeltaker(
            deltakerliste,
            navBrukerService.get(deltakerPayload.personIdent).getOrThrow(),
            deltakerStatus,
        )

        upsertImportertDeltaker(deltaker)
        deltakerProducerService.produce(deltaker)

        log.info("Ingest for arenadeltaker med id ${deltaker.id} er ferdig")
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    private fun upsertImportertDeltaker(deltaker: Deltaker) = deltakerService
        .transactionalDeltakerUpsert(deltaker) { transaction ->
            val importertData = deltaker.toImportertData()
            importertFraArenaRepository.upsert(importertData, transaction)
        }.getOrThrow()

    private fun Deltaker.toImportertData() = ImportertFraArena(
        deltakerId = id,
        importertDato = LocalDateTime.now(), // Bruker current_timestamp i db
        deltakerVedImport = this.toDeltakerVedImport(opprettet.toLocalDate()),
    )
}

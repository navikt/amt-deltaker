package no.nav.amt.deltaker.deltaker.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.extensions.toVurdering
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerV2Dto
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.vurdering.Vurdering
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class DeltakerConsumer(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val deltakerEndringService: DeltakerEndringService,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val vurderingRepository: VurderingRepository,
    private val unleashToggle: UnleashToggle,
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)

    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.DELTAKER_V2_TOPIC,
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

    private suspend fun processDeltaker(deltakerV2: DeltakerV2Dto) {
        return // Denne consumeren skal slettes men beholder den fordi det er noe kode her som skal gjenbrukes i ny consumer
        val deltakerliste = deltakerlisteRepository.get(deltakerV2.deltakerlisteId).getOrThrow()
        if (unleashToggle.erKometMasterForTiltakstype(deltakerliste.tiltakstype.arenaKode)) return

        if (unleashToggle.skalLeseArenaDataForTiltakstype(deltakerliste.tiltakstype.arenaKode)) {
            log.info("Ingester arenadeltaker med id ${deltakerV2.id}")
            val deltaker = deltakerV2.toDeltaker(deltakerliste)
            val historikkImportertFraArena =
                deltakerV2.historikk?.first { it is DeltakerHistorikk.ImportertFraArena } as DeltakerHistorikk.ImportertFraArena
            val vurderinger = deltakerV2.vurderingerFraArrangor?.map { it.toVurdering() }
            upsertImportertDeltaker(deltaker, historikkImportertFraArena.importertFraArena, vurderinger)

            log.info("Ingest for arenadeltaker med id ${deltaker.id} er ferdig")
        }
    }

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    private fun upsertImportertDeltaker(
        deltaker: Deltaker,
        importertData: ImportertFraArena,
        vurderinger: List<Vurdering>?,
    ) {
        deltakerRepository.upsert(deltaker)
        importertFraArenaRepository.upsert(importertData)
        vurderinger?.forEach { vurderingRepository.upsert(it) }
    }

    private suspend fun DeltakerV2Dto.toDeltaker(deltakerliste: Deltakerliste) = Deltaker(
        id = id,
        navBruker = navBrukerService.get(personalia.personident).getOrThrow(),
        deltakerliste = deltakerliste,
        startdato = oppstartsdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = prosentStilling?.toFloat(),
        // Hvis bakgrunnsinfo ikke er oppdatert i ny løsning så skal den settes til null
        // https://trello.com/c/Rotq74xz/1751-vise-bestillingstekst-fra-arena-innhold-og-bakgrunnsinfo-i-en-overgangsfase
        bakgrunnsinformasjon = if (skalLagreBestillingstekst(id)) bestillingTekst else null,
        deltakelsesinnhold = innhold,
        status = status.toDeltakerStatus(id),
        vedtaksinformasjon = null,
        kilde = kilde ?: Kilde.ARENA,
        sistEndret = sistEndret ?: LocalDateTime.now(),
        erManueltDeltMedArrangor = erManueltDeltMedArrangor,
        opprettet = null,
    )

    private fun skalLagreBestillingstekst(deltakerId: UUID): Boolean = deltakerEndringService.getForDeltaker(deltakerId).any {
        it.endring is DeltakerEndring.Endring.EndreBakgrunnsinformasjon
    }
}

package no.nav.amt.deltaker.deltaker.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.dto.EnkeltplassDeltakerPayload
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.utils.objectMapper
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class EnkeltplassDeltakerConsumer(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val unleashToggle: UnleashToggle,
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
        val deltakerliste = deltakerlisteRepository.get(deltakerPayload.gjennomforingId).getOrThrow() // TODO: Fallback med kall mot mulighetsrommet
        if (!unleashToggle.skalLeseArenaDataForTiltakstype(deltakerliste.tiltakstype.tiltakskode)) return

        log.info("Ingester enkeltplass deltaker med id ${deltakerPayload.id}")
        val deltaker = deltakerPayload.toDeltaker(deltakerliste)

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

    private fun Deltaker.toImportertData() = ImportertFraArena( // TODO:Sjekk mot amt-tiltak hva den gjør
        deltakerId = id,
        importertDato = LocalDateTime.now(), // TODO: gjøre i sql?
        deltakerVedImport = this.toDeltakerVedImport(opprettet!!.toLocalDate()),
    )

    private suspend fun EnkeltplassDeltakerPayload.toDeltaker(deltakerliste: Deltakerliste) = Deltaker(
        id = id,
        navBruker = navBrukerService.get(personIdent).getOrThrow(), // TODO: Hva skjer hvis vi ikke har nyeste ident? Kanksje håndtert allerede i arena-acl
        deltakerliste = deltakerliste,
        startdato = startDato,
        sluttdato = sluttDato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = prosentDeltid,
        bakgrunnsinformasjon = null,
        deltakelsesinnhold = null,
        status = DeltakerStatus(
            id = UUID.randomUUID(),
            type = status,
            aarsak = TODO(),
            gyldigFra = TODO(),
            gyldigTil = null,
            opprettet = TODO(), // TODO: sjekk amt-tiltak håndering
        ),
        vedtaksinformasjon = null,
        kilde = Kilde.ARENA,
        sistEndret = LocalDateTime.now(),
        erManueltDeltMedArrangor = false,
        opprettet = registrertDato,
    )
}

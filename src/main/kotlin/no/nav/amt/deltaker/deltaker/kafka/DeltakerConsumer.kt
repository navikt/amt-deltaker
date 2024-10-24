package no.nav.amt.deltaker.deltaker.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Kilde
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.navbruker.NavBrukerService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.kafka.ManagedKafkaConsumer
import no.nav.amt.lib.kafka.config.KafkaConfig
import no.nav.amt.lib.kafka.config.KafkaConfigImpl
import no.nav.amt.lib.kafka.config.LocalKafkaConfig
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerConsumer(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val navBrukerService: NavBrukerService,
    private val deltakerEndringService: DeltakerEndringService,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val deltakerProducerService: DeltakerProducerService,
    private val unleashToggle: UnleashToggle,
    kafkaConfig: KafkaConfig = if (Environment.isLocal()) LocalKafkaConfig() else KafkaConfigImpl("earliest"),
) : Consumer<UUID, String?> {
    private val log = LoggerFactory.getLogger(javaClass)
    private val consumer = ManagedKafkaConsumer(
        topic = Environment.DELTAKER_V2_TOPIC,
        config = kafkaConfig.consumerConfig(
            keyDeserializer = UUIDDeserializer(),
            valueDeserializer = StringDeserializer(),
            groupId = Environment.KAFKA_CONSUMER_GROUP_ID,
        ),
        consume = ::consume,
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
        val deltakerliste = deltakerlisteRepository.get(deltakerV2.deltakerlisteId).getOrThrow()
        val opprettetAvKomet = deltakerV2.kilde == Kilde.KOMET
        val deltakerLestInnTidligere = deltakerV2.historikk != null

        if (opprettetAvKomet) {
            log.info("Hopper over komet deltaker på deltaker-v2. deltakerId: ${deltakerV2.id}")
            return
        }

        if (deltakerLestInnTidligere) {
            log.info("Hopper over deltaker med id ${deltakerV2.id} fordi deltakeren er allerede bearbeidet")
            return
        }

        if (unleashToggle.skalLeseArenaDeltakereForTiltakstype(deltakerliste.tiltakstype.arenaKode)) {
            log.info("Ingester arenadeltaker med id ${deltakerV2.id}")
            val deltaker = deltakerV2.toDeltaker(deltakerliste)

            upsertImportertDeltaker(deltaker, deltakerV2.innsoktDato)
            log.info("Ingest for arenadeltaker med id ${deltaker.id} er ferdig")

            deltakerProducerService.produce(deltaker, publiserTilDeltakerV1 = false)
        }
    }

    override fun run() = consumer.run()

    private fun upsertImportertDeltaker(deltaker: Deltaker, innsoktDato: LocalDate) {
        deltakerRepository.upsert(deltaker)
        val historikkElement = ImportertFraArena(
            deltakerId = deltaker.id,
            importertDato = LocalDateTime.now(),
            deltakerVedImport = deltaker.toDeltakerVedImport(innsoktDato),
        )

        importertFraArenaRepository.upsert(historikkElement)
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
    )

    private fun skalLagreBestillingstekst(deltakerId: UUID): Boolean {
        return deltakerEndringService.getForDeltaker(deltakerId).any { it.endring is DeltakerEndring.Endring.EndreBakgrunnsinformasjon }
    }
}

fun DeltakerV2Dto.DeltakerStatusDto.toDeltakerStatus(deltakerId: UUID) = DeltakerStatus(
    id = id ?: throw IllegalStateException("Deltakerstatus mangler id. deltakerId: $deltakerId"),
    type = type,
    aarsak = aarsak?.let { DeltakerStatus.Aarsak(it.toDeltakerstatusArsak(), aarsaksbeskrivelse) },
    gyldigFra = gyldigFra,
    gyldigTil = null,
    opprettet = opprettetDato,
)

fun DeltakerStatus.Aarsak.Type.toDeltakerstatusArsak(): DeltakerStatus.Aarsak.Type {
    // AVLYST_KONTRAKT er erstattet av SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
    return if (this == DeltakerStatus.Aarsak.Type.AVLYST_KONTRAKT) {
        DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT
    } else {
        this
    }
}

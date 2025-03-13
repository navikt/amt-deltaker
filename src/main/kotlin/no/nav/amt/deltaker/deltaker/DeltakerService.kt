package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.EndringRequest
import no.nav.amt.deltaker.deltaker.api.model.toDeltakerEndringEndring
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringHandler
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringUtfall
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorService
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Kilde
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.job.DeltakerProgresjon
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.tiltakskoordinator.requests.DelMedArrangorRequest
import no.nav.amt.lib.models.tiltakskoordinator.requests.EndringFraTiltakskoordinatorRequest
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerEndringService: DeltakerEndringService,
    private val deltakerProducerService: DeltakerProducerService,
    private val vedtakService: VedtakService,
    private val hendelseService: HendelseService,
    private val endringFraArrangorService: EndringFraArrangorService,
    private val forslagService: ForslagService,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val unleashToggle: UnleashToggle,
    private val endringFraTiltakskoordinatorService: EndringFraTiltakskoordinatorService,
    private val amtTiltakClient: AmtTiltakClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID) = deltakerRepository.get(id)

    fun getDeltakelser(personident: String, deltakerlisteId: UUID) = deltakerRepository.getMany(personident, deltakerlisteId)

    fun getDeltakelser(personident: String) = deltakerRepository.getMany(personident)

    private fun getDeltakereForDeltakerliste(deltakerlisteId: UUID) = deltakerRepository.getDeltakereForDeltakerliste(deltakerlisteId)

    fun getDeltakerIderForTiltakstype(tiltakstype: Tiltakstype.ArenaKode) = deltakerRepository.getDeltakerIderForTiltakstype(tiltakstype)

    fun getDeltakerIder(personId: UUID, deltakerlisteId: UUID) =
        deltakerRepository.getDeltakerIder(personId = personId, deltakerlisteId = deltakerlisteId)

    suspend fun upsertDeltaker(
        deltaker: Deltaker,
        forcedUpdate: Boolean? = false,
        nesteStatus: DeltakerStatus? = null,
    ): Deltaker {
        deltakerRepository.upsert(deltaker.copy(sistEndret = LocalDateTime.now()), nesteStatus = nesteStatus)

        val oppdatertDeltaker = get(deltaker.id).getOrThrow()

        if (oppdatertDeltaker.status.type != DeltakerStatus.Type.KLADD) {
            deltakerProducerService.produce(oppdatertDeltaker, forcedUpdate = forcedUpdate)
        }

        log.info("Oppdatert deltaker med id ${deltaker.id}")
        return oppdatertDeltaker
    }

    fun delete(deltakerId: UUID) {
        importertFraArenaRepository.deleteForDeltaker(deltakerId)
        vedtakService.deleteForDeltaker(deltakerId)
        deltakerEndringService.deleteForDeltaker(deltakerId)
        forslagService.deleteForDeltaker(deltakerId)
        endringFraArrangorService.deleteForDeltaker(deltakerId)
        deltakerRepository.deleteDeltakerOgStatus(deltakerId)
    }

    suspend fun feilregistrerDeltaker(deltakerId: UUID) {
        val deltaker = get(deltakerId).getOrThrow()
        if (deltaker.status.type == DeltakerStatus.Type.KLADD) {
            log.warn("Kan ikke feilregistrere deltaker-kladd, id $deltakerId")
            throw IllegalArgumentException("Kan ikke feilregistrere deltaker-kladd")
        }
        upsertDeltaker(deltaker.copy(status = nyDeltakerStatus(type = DeltakerStatus.Type.FEILREGISTRERT)))
        log.info("Feilregistrert deltaker med id $deltakerId")
    }

    suspend fun upsertEndretDeltaker(deltakerId: UUID, request: EndringRequest): Deltaker {
        val deltaker = get(deltakerId).getOrThrow()
        validerIkkeFeilregistrert(deltaker)

        val endring = request.toDeltakerEndringEndring()
        val deltakerEndringHandler = DeltakerEndringHandler(deltaker, endring, deltakerHistorikkService)

        return when (val utfall = deltakerEndringHandler.sjekkUtfall()) {
            is DeltakerEndringUtfall.VellykketEndring -> {
                deltakerEndringService.upsertEndring(deltaker, endring, utfall, request)
                upsertDeltaker(utfall.deltaker, nesteStatus = utfall.nesteStatus)
            }

            is DeltakerEndringUtfall.FremtidigEndring -> {
                deltakerEndringService.upsertEndring(deltaker, endring, utfall, request)
                upsertDeltaker(utfall.deltaker)
            }

            is DeltakerEndringUtfall.UgyldigEndring -> deltaker
        }
    }

    suspend fun upsertEndretDeltaker(endring: EndringFraArrangor): Deltaker {
        val deltaker = get(endring.deltakerId).getOrThrow()
        validerIkkeFeilregistrert(deltaker)

        return endringFraArrangorService.insertEndring(deltaker, endring).fold(
            onSuccess = { endretDeltaker ->
                return@fold upsertDeltaker(endretDeltaker)
            },
            onFailure = {
                return@fold deltaker
            },
        )
    }

    private fun validerIkkeFeilregistrert(deltaker: Deltaker) = require(deltaker.status.type != DeltakerStatus.Type.FEILREGISTRERT) {
        "Kan ikke oppdatere feilregistrert deltaker, id ${deltaker.id}"
    }

    suspend fun produserDeltakereForPerson(personident: String, publiserTilDeltakerV1: Boolean = true) {
        getDeltakelser(personident).forEach {
            deltakerProducerService.produce(it, publiserTilDeltakerV1 = publiserTilDeltakerV1)
        }
    }

    suspend fun fattVedtak(deltakerId: UUID, vedtakId: UUID): Deltaker {
        val deltaker = get(deltakerId).getOrThrow().let {
            if (it.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
                it.copy(status = nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART))
            } else {
                it
            }
        }

        vedtakService.fattVedtak(vedtakId, deltaker)

        return upsertDeltaker(deltaker)
    }

    fun oppdaterSistBesokt(deltakerId: UUID, sistBesokt: ZonedDateTime) {
        val deltaker = get(deltakerId).getOrThrow()
        hendelseService.hendelseForSistBesokt(deltaker, sistBesokt)
    }

    fun getDeltakereMedStatus(statusType: DeltakerStatus.Type) = deltakerRepository.getDeltakereMedStatus(statusType)

    suspend fun oppdaterDeltakerStatuser() {
        val deltakereSomSkalAvsluttes = deltakereSomSkalHaAvsluttendeStatus()
        avsluttDeltakere(deltakereSomSkalAvsluttes)

        val deltakereSomSkalDelta = deltakereSomSkalHaStatusDeltar()
        DeltakerProgresjon()
            .tilDeltar(deltakereSomSkalDelta)
            .forEach { upsertDeltaker(it) }
    }

    suspend fun avsluttDeltakelserPaaDeltakerliste(deltakerlisteId: UUID) {
        val deltakerePaAvbruttDeltakerliste = getDeltakereForDeltakerliste(deltakerlisteId)
            .filter { it.status.type != DeltakerStatus.Type.KLADD }

        avsluttDeltakere(deltakerePaAvbruttDeltakerliste)
    }

    private suspend fun avsluttDeltakere(deltakereSomSkalAvsluttes: List<Deltaker>) {
        DeltakerProgresjon()
            .tilAvsluttendeStatusOgDatoer(deltakereSomSkalAvsluttes, ::getFremtidigStatus)
            .map { oppdaterVedtakForAvbruttUtkast(it) }
            .forEach { upsertDeltaker(it) }
    }

    private fun getFremtidigStatus(deltaker: Deltaker) = deltakerRepository.getDeltakerStatuser(deltaker.id).firstOrNull { status ->
        status.gyldigTil == null &&
            !status.gyldigFra.toLocalDate().isAfter(LocalDate.now()) &&
            status.type == DeltakerStatus.Type.HAR_SLUTTET
    }

    private fun oppdaterVedtakForAvbruttUtkast(deltaker: Deltaker) = if (deltaker.status.type == DeltakerStatus.Type.AVBRUTT_UTKAST) {
        val vedtak = vedtakService.avbrytVedtakVedAvsluttetDeltakerliste(deltaker)
        deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksinformasjon())
    } else {
        deltaker
    }

    private fun deltakereSomSkalHaAvsluttendeStatus() = deltakerRepository
        .skalHaAvsluttendeStatus()
        .plus(deltakerRepository.deltarPaAvsluttetDeltakerliste())
        .filter { it.kilde == Kilde.KOMET || unleashToggle.erKometMasterForTiltakstype(it.deltakerliste.tiltakstype.arenaKode) }
        .distinct()

    private fun deltakereSomSkalHaStatusDeltar() = deltakerRepository
        .skalHaStatusDeltar()
        .distinct()
        .filter { it.kilde == Kilde.KOMET || unleashToggle.erKometMasterForTiltakstype(it.deltakerliste.tiltakstype.arenaKode) }

    suspend fun avgrensSluttdatoerTil(deltakerliste: Deltakerliste) {
        val deltakere = getDeltakereForDeltakerliste(deltakerliste.id).filter { it.status.type !in DeltakerStatus.avsluttendeStatuser }

        deltakere.forEach {
            if (it.sluttdato != null && deltakerliste.sluttDato != null && it.sluttdato > deltakerliste.sluttDato) {
                upsertDeltaker(
                    deltaker = it.copy(sluttdato = deltakerliste.sluttDato),
                    forcedUpdate = true, // For at oppdateringen skal propageres riktig til amt-deltaker-bff så må vi sette denne.
                )
                log.info("Deltaker ${it.id} fikk ny sluttdato fordi deltakerlisten sin sluttdato var mindre enn deltakers")
            }
        }
    }

    suspend fun upsertEndretDeltakere(request: EndringFraTiltakskoordinatorRequest): List<Deltaker> {
        val deltakere = deltakerRepository.getMany(request.deltakerIder)

        if (deltakere.isEmpty()) return emptyList()

        val tiltakstype = deltakere.distinctBy { it.deltakerliste.tiltakstype.tiltakskode }.map { it.deltakerliste.tiltakstype.tiltakskode }

        require(tiltakstype.size == 1) { "kan ikke endre på deltakere på flere tiltakstyper samtidig" }
        require(tiltakstype.first() in Tiltakstype.kursTiltak) { "kan ikke endre på deltakere på tiltakstypen ${tiltakstype.first()}" }

        return if (unleashToggle.erKometMasterForTiltakstype(tiltakstype.first().toArenaKode())) {
            val endredeDeltakere = endringFraTiltakskoordinatorService.endre(deltakere, request).mapNotNull { it.getOrNull() }
            endredeDeltakere.map { upsertDeltaker(it) }
        } else if (request is DelMedArrangorRequest) {
            val deltakerMap = deltakere.associateBy { it.id }
            val endredeDeltakere = amtTiltakClient
                .delMedArrangor(deltakere.map { it.id })
                .mapNotNull { (id, status) -> deltakerMap[id]?.copy(status = status) }

            endringFraTiltakskoordinatorService.insertDelMedArrangor(endredeDeltakere, request.endretAv)

            endredeDeltakere
        } else {
            throw NotImplementedError(
                "Håndtering av endring fra tiltakskoordinator " +
                    "hvor komet ikke er master og det ikke er av type del-med-arrangør er ikke støttet",
            )
        }
    }
}

fun nyDeltakerStatus(
    type: DeltakerStatus.Type,
    aarsak: DeltakerStatus.Aarsak? = null,
    gyldigFra: LocalDateTime = LocalDateTime.now(),
) = DeltakerStatus(
    id = UUID.randomUUID(),
    type = type,
    aarsak = aarsak,
    gyldigFra = gyldigFra,
    gyldigTil = null,
    opprettet = LocalDateTime.now(),
)

package no.nav.amt.deltaker.deltaker

import kotliquery.TransactionalSession
import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.api.deltaker.toDeltakerEndringEndring
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringHandler
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringUtfall
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorService
import no.nav.amt.deltaker.deltaker.extensions.getVedtakOrThrow
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.job.DeltakerProgresjon
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorService
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.ArenaKode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
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
    private val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID) = deltakerRepository.get(id)

    fun getHistorikk(deltakerId: UUID): List<DeltakerHistorikk> = deltakerHistorikkService.getForDeltaker(deltakerId)

    fun getDeltakelserForPerson(personident: String, deltakerlisteId: UUID) =
        deltakerRepository.getFlereForPerson(personident, deltakerlisteId)

    @Deprecated("Bruk DeltakerRepository#getMany")
    fun getDeltakelser(deltakerIder: List<UUID>) = deltakerRepository.getMany(deltakerIder)

    @Deprecated("Bruk DeltakerRepository#getFlereForPerson")
    fun getDeltakelserForPerson(personident: String) = deltakerRepository.getFlereForPerson(personident)

    private fun getDeltakereForDeltakerliste(deltakerlisteId: UUID) = deltakerRepository.getDeltakereForDeltakerliste(deltakerlisteId)

    fun getDeltakerIderForTiltakstype(tiltakstype: ArenaKode) = deltakerRepository.getDeltakerIderForTiltakstype(tiltakstype)

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
        endringFraTiltakskoordinatorService.deleteForDeltaker(deltakerId)
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

    fun transactionalDeltakerUpsert(deltaker: Deltaker, transactionalUpsert: (t: TransactionalSession) -> Unit = {}) =
        Database.query { session ->
            try {
                session.transaction { transaction ->
                    deltakerRepository.upsert(deltaker, null, transaction)
                    transactionalUpsert(transaction)
                }
                return@query Result.success(deltaker)
            } catch (ex: Exception) {
                return@query Result.failure(ex)
            }
        }

    fun upsertDeltaker(
        deltaker: Deltaker,
        endringsType: EndringFraTiltakskoordinator.Endring,
        endretAv: NavAnsatt,
        endretAvEnhet: NavEnhet,
    ): DeltakerOppdateringResult {
        val endring = EndringFraTiltakskoordinator(
            id = UUID.randomUUID(),
            deltakerId = deltaker.id,
            endring = endringsType,
            endretAv = endretAv.id,
            endretAvEnhet = endretAvEnhet.id,
            endret = LocalDateTime.now(),
        )
        val deltakerResult = endringFraTiltakskoordinatorService.sjekkEndringUtfall(deltaker, endring.endring)

        if (deltakerResult.isFailure) {
            return DeltakerOppdateringResult(
                deltaker,
                deltakerResult.isSuccess,
                deltakerResult.exceptionOrNull(),
            )
        }
        val result = transactionalDeltakerUpsert(deltakerResult.getOrThrow()) { transaction ->
            endringFraTiltakskoordinatorRepository.insert(listOf(endring), transaction)
            if (endringsType is EndringFraTiltakskoordinator.TildelPlass && deltaker.kilde == Kilde.KOMET) {
                vedtakService.navFattVedtak(deltaker, endretAv, endretAvEnhet, transaction)
            }
        }

        return DeltakerOppdateringResult(
            deltakerRepository.get(deltaker.id).getOrThrow(),
            result.isSuccess,
            result.exceptionOrNull(),
        )
    }

    suspend fun oppdaterDeltakere(
        deltakerIder: List<UUID>,
        endringsType: EndringFraTiltakskoordinator.Endring,
        endretAvIdent: String,
    ): List<DeltakerOppdateringResult> {
        val endretAv = navAnsattService.hentEllerOpprettNavAnsatt(endretAvIdent)
        val endretAvNavEnhetId: UUID? = endretAv.navEnhetId

        require(endretAvNavEnhetId != null) { "Tiltakskoordinator ${endretAv.id} mangler en tilknyttet nav-enhet" }

        val endretAvEnhet = navEnhetService.hentEllerOpprettNavEnhet(endretAvNavEnhetId)
        val deltakere = deltakerRepository.getMany(deltakerIder)
        val tiltakstyper = deltakere
            .distinctBy { it.deltakerliste.tiltakstype.tiltakskode }
            .map { it.deltakerliste.tiltakstype.tiltakskode }

        require(tiltakstyper.size == 1) { "kan ikke endre på deltakere på flere tiltakstyper samtidig" }
        require(tiltakstyper.first() in Tiltakstype.kursTiltak) { "kan ikke endre på deltakere på tiltakstypen ${tiltakstyper.first()}" }

        return deltakere
            .map { upsertDeltaker(it, endringsType, endretAv, endretAvEnhet) }
            .map { deltakerOppdateringResult ->
                if (!deltakerOppdateringResult.isSuccess) {
                    log.error(
                        "Kunne ikke oppdatere deltaker fra batch: $deltakerIder med endring ${endringsType::class.simpleName}",
                        deltakerOppdateringResult.exceptionOrNull,
                    )
                } else {
                    val deltaker = deltakerOppdateringResult.deltaker
                    deltakerProducerService.produce(deltaker)
                    hendelseService.produserHendelseFraTiltaksansvarlig(deltaker, endretAv, endretAvEnhet, endringsType)
                }
                return@map deltakerOppdateringResult
            }
    }

    suspend fun giAvslag(
        deltakerId: UUID,
        avslag: EndringFraTiltakskoordinator.Avslag,
        endretAv: String,
    ): Deltaker {
        val deltakerOppdateringResult = oppdaterDeltakere(listOf(deltakerId), avslag, endretAv).first()
        if (deltakerOppdateringResult.isSuccess) {
            return deltakerOppdateringResult.deltaker
        } else {
            throw deltakerOppdateringResult.exceptionOrNull!!
        }
    }

    private fun validerIkkeFeilregistrert(deltaker: Deltaker) = require(deltaker.status.type != DeltakerStatus.Type.FEILREGISTRERT) {
        "Kan ikke oppdatere feilregistrert deltaker, id ${deltaker.id}"
    }

    suspend fun produserDeltakereForPerson(personident: String, publiserTilDeltakerV1: Boolean = true) {
        getDeltakelserForPerson(personident).forEach {
            deltakerProducerService.produce(it, publiserTilDeltakerV1 = publiserTilDeltakerV1)
        }
    }

    suspend fun innbyggerFattVedtak(deltaker: Deltaker): Deltaker {
        val status = if (deltaker.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
            nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
        } else {
            deltaker.status
        }

        val oppdatertDeltaker = deltaker.copy(status = status, sistEndret = LocalDateTime.now())
        vedtakService.innbyggerFattVedtak(oppdatertDeltaker).getVedtakOrThrow()

        return upsertDeltaker(oppdatertDeltaker)
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

    suspend fun avsluttDeltakelserPaaDeltakerliste(deltakerliste: Deltakerliste) {
        val deltakerePaAvbruttDeltakerliste = getDeltakereForDeltakerliste(deltakerliste.id)
            .filter { it.status.type != DeltakerStatus.Type.KLADD }
            .map { it.copy(deltakerliste = deltakerliste) }

        avsluttDeltakere(deltakerePaAvbruttDeltakerliste)
    }

    private suspend fun avsluttDeltakere(deltakereSomSkalAvsluttes: List<Deltaker>) {
        DeltakerProgresjon()
            .tilAvsluttendeStatusOgDatoer(deltakereSomSkalAvsluttes, deltakerRepository.getAvsluttendeDeltakerStatuserForOppdatering())
            .map { oppdaterVedtakForAvbruttUtkast(it) }
            .forEach { upsertDeltaker(it) }
    }

    private fun oppdaterVedtakForAvbruttUtkast(deltaker: Deltaker) = if (deltaker.status.type == DeltakerStatus.Type.AVBRUTT_UTKAST) {
        val vedtak = vedtakService.avbrytVedtakVedAvsluttetDeltakerliste(deltaker).getVedtakOrThrow()

        hendelseService.hendelseFraSystem(deltaker) { HendelseType.AvbrytUtkast(it) }

        deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())
    } else {
        deltaker
    }

    private fun deltakereSomSkalHaAvsluttendeStatus() = deltakerRepository
        .skalHaAvsluttendeStatus()
        .plus(deltakerRepository.deltarPaAvsluttetDeltakerliste())
        .filter { it.kilde == Kilde.KOMET || unleashToggle.erKometMasterForTiltakstype(it.deltakerliste.tiltakstype.tiltakskode) }
        .distinct()

    private fun deltakereSomSkalHaStatusDeltar() = deltakerRepository
        .skalHaStatusDeltar()
        .distinct()
        .filter { it.kilde == Kilde.KOMET || unleashToggle.erKometMasterForTiltakstype(it.deltakerliste.tiltakstype.tiltakskode) }

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
}

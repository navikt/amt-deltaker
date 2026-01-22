package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.DeltakerUtils.sjekkEndringUtfall
import no.nav.amt.deltaker.deltaker.api.deltaker.toDeltakerEndringEndring
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.db.DeltakerStatusRepository
import no.nav.amt.deltaker.deltaker.db.VedtakRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringHandler
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringUtfall
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorRepository
import no.nav.amt.deltaker.deltaker.extensions.getVedtakOrThrow
import no.nav.amt.deltaker.deltaker.extensions.tilVedtaksInformasjon
import no.nav.amt.deltaker.deltaker.forslag.ForslagRepository
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.job.DeltakerProgresjonHandler
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.deltaker.tiltakskoordinator.endring.EndringFraTiltakskoordinatorRepository
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.models.tiltakskoordinator.EndringFraTiltakskoordinator
import no.nav.amt.lib.utils.database.Database
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class DeltakerService(
    private val deltakerRepository: DeltakerRepository,
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val deltakerEndringService: DeltakerEndringService,
    private val deltakerProducerService: DeltakerProducerService,
    private val vedtakRepository: VedtakRepository,
    private val vedtakService: VedtakService,
    private val hendelseService: HendelseService,
    private val endringFraArrangorRepository: EndringFraArrangorRepository,
    private val forslagRepository: ForslagRepository,
    private val importertFraArenaRepository: ImportertFraArenaRepository,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val endringFraTiltakskoordinatorRepository: EndringFraTiltakskoordinatorRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID) = deltakerRepository.get(id)

    suspend fun upsertAndProduceDeltaker(
        deltaker: Deltaker,
        forcedUpdate: Boolean? = false,
        nesteStatus: DeltakerStatus? = null,
        beforeDeltakerUpsert: () -> Unit = {},
        afterDeltakerUpsert: (Deltaker) -> Unit = {},
    ): Deltaker = transactionalDeltakerUpsert(
        deltaker = deltaker.copy(sistEndret = LocalDateTime.now()),
        nesteStatus = nesteStatus,
        beforeDeltakerUpsert = beforeDeltakerUpsert,
        additionalDbOperations = {
            val oppdatertDeltaker = deltakerRepository.get(deltaker.id).getOrThrow()
            if (oppdatertDeltaker.status.type != DeltakerStatus.Type.KLADD) {
                deltakerProducerService.produce(oppdatertDeltaker, forcedUpdate = forcedUpdate)
            }
            log.info("Oppdatert deltaker med id ${deltaker.id}")
            afterDeltakerUpsert(oppdatertDeltaker)
            oppdatertDeltaker
        },
    ).getOrThrow()

    fun delete(deltakerId: UUID) {
        importertFraArenaRepository.deleteForDeltaker(deltakerId)
        vedtakRepository.deleteForDeltaker(deltakerId)
        deltakerEndringRepository.deleteForDeltaker(deltakerId)
        forslagRepository.deleteForDeltaker(deltakerId)
        endringFraArrangorRepository.deleteForDeltaker(deltakerId)
        endringFraTiltakskoordinatorRepository.deleteForDeltaker(deltakerId)
        DeltakerStatusRepository.slettStatus(deltakerId)
        deltakerRepository.slettDeltaker(deltakerId)
    }

    suspend fun feilregistrerDeltaker(deltakerId: UUID) {
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
        if (deltaker.status.type == DeltakerStatus.Type.KLADD) {
            log.warn("Kan ikke feilregistrere deltaker-kladd, id $deltakerId")
            throw IllegalArgumentException("Kan ikke feilregistrere deltaker-kladd")
        }
        upsertAndProduceDeltaker(deltaker.copy(status = nyDeltakerStatus(type = DeltakerStatus.Type.FEILREGISTRERT)))
        log.info("Feilregistrert deltaker med id $deltakerId")
    }

    suspend fun upsertEndretDeltaker(deltakerId: UUID, request: EndringRequest): Deltaker {
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
        validerIkkeFeilregistrert(deltaker)

        val endring = request.toDeltakerEndringEndring()
        val deltakerEndringHandler = DeltakerEndringHandler(deltaker, endring, deltakerHistorikkService)

        return when (val utfall = deltakerEndringHandler.sjekkUtfall()) {
            is DeltakerEndringUtfall.VellykketEndring -> {
                upsertAndProduceDeltaker(
                    utfall.deltaker,
                    nesteStatus = utfall.nesteStatus,
                    beforeDeltakerUpsert = {
                        deltakerEndringService.upsertEndring(deltaker, endring, utfall, request)
                    },
                )
            }

            is DeltakerEndringUtfall.FremtidigEndring -> {
                upsertAndProduceDeltaker(
                    utfall.deltaker,
                    beforeDeltakerUpsert = {
                        deltakerEndringService.upsertEndring(deltaker, endring, utfall, request)
                    },
                )
            }

            is DeltakerEndringUtfall.UgyldigEndring -> {
                deltaker
            }
        }
    }

    suspend fun transactionalDeltakerUpsert(
        deltaker: Deltaker,
        nesteStatus: DeltakerStatus? = null,
        beforeDeltakerUpsert: () -> Unit = {},
        additionalDbOperations: () -> Deltaker = { deltaker },
    ): Result<Deltaker> = runCatching {
        Database.transaction {
            beforeDeltakerUpsert()

            deltakerRepository.upsert(deltaker)
            lagreStatus(deltaker.id, deltaker.status)

            nesteStatus?.let {
                DeltakerStatusRepository.lagreStatus(deltaker.id, it)
            }

            additionalDbOperations()
        }
    }

    // flyttet fra DeltakerRepository
    private fun lagreStatus(deltakerId: UUID, deltakerStatus: DeltakerStatus) {
        DeltakerStatusRepository.lagreStatus(deltakerId, deltakerStatus)

        val erNyStatusAktiv = deltakerStatus.gyldigFra.toLocalDate() <= LocalDate.now()

        if (erNyStatusAktiv) {
            DeltakerStatusRepository.deaktiverTidligereStatuser(deltakerId, deltakerStatus)
        } else {
            // Dette skjer aldri for arenadeltakelser
            DeltakerStatusRepository.slettTidligereFremtidigeStatuser(deltakerId, deltakerStatus)
        }
    }

    private suspend fun localUpsertDeltaker(
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

        val deltakerToUpdate = sjekkEndringUtfall(deltaker, endring.endring).getOrElse { error ->
            return DeltakerOppdateringResult(deltaker, false, error)
        }

        val oppdatertDeltaker = transactionalDeltakerUpsert(
            deltaker = deltakerToUpdate,
            additionalDbOperations = {
                endringFraTiltakskoordinatorRepository.insert(listOf(endring))
                if (endringsType is EndringFraTiltakskoordinator.TildelPlass && deltaker.kilde == Kilde.KOMET) {
                    vedtakService.navFattVedtak(deltaker, endretAv, endretAvEnhet)
                }

                val deltakerFromDb = deltakerRepository.get(deltakerToUpdate.id).getOrThrow()

                deltakerProducerService.produce(deltakerFromDb)
                hendelseService.produserHendelseFraTiltaksansvarlig(
                    deltaker = deltakerFromDb,
                    navAnsatt = endretAv,
                    navEnhet = endretAvEnhet,
                    endringsType = endringsType,
                )

                deltakerFromDb
            },
        ).getOrElse { error ->
            return DeltakerOppdateringResult(deltaker, false, error)
        }

        return DeltakerOppdateringResult(oppdatertDeltaker, true, null)
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
        val tiltakskoder = deltakere
            .map { it.deltakerliste.tiltakstype.tiltakskode }
            .distinct()

        require(tiltakskoder.size == 1) { "kan ikke endre på deltakere på flere tiltakskoder samtidig" }
        require(tiltakskoder.first() in Tiltakstype.kursTiltak) { "kan ikke endre på deltakere på tiltakskoden ${tiltakskoder.first()}" }

        return deltakere.map { deltaker ->
            val oppdateringResult = localUpsertDeltaker(deltaker, endringsType, endretAv, endretAvEnhet)

            if (!oppdateringResult.isSuccess) {
                log.error(
                    "Kunne ikke oppdatere deltaker fra batch: $deltakerIder med endring ${endringsType::class.simpleName}",
                    oppdateringResult.exceptionOrNull,
                )
            }

            oppdateringResult
        }
    }

    suspend fun giAvslag(
        deltakerId: UUID,
        avslag: EndringFraTiltakskoordinator.Avslag,
        endretAv: String,
    ): Deltaker {
        val firstDeltakerOppdateringResult = oppdaterDeltakere(
            deltakerIder = listOf(deltakerId),
            endringsType = avslag,
            endretAvIdent = endretAv,
        ).first()

        return if (firstDeltakerOppdateringResult.isSuccess) {
            firstDeltakerOppdateringResult.deltaker
        } else {
            throw firstDeltakerOppdateringResult.exceptionOrNull!!
        }
    }

    fun produserDeltakereForPerson(personident: String, publiserTilDeltakerV1: Boolean = true): Unit =
        deltakerRepository.getFlereForPerson(personident).forEach { deltaker ->
            deltakerProducerService.produce(
                deltaker = deltaker,
                publiserTilDeltakerV1 = publiserTilDeltakerV1,
            )
        }

    suspend fun innbyggerFattVedtak(deltaker: Deltaker): Deltaker {
        val status = if (deltaker.status.type == DeltakerStatus.Type.UTKAST_TIL_PAMELDING) {
            nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
        } else {
            deltaker.status
        }

        val oppdatertDeltaker = deltaker.copy(status = status, sistEndret = LocalDateTime.now())
        vedtakService.innbyggerFattVedtak(oppdatertDeltaker).getVedtakOrThrow()

        return upsertAndProduceDeltaker(oppdatertDeltaker)
    }

    fun oppdaterSistBesokt(deltakerId: UUID, sistBesokt: ZonedDateTime) {
        val deltaker = deltakerRepository.get(deltakerId).getOrThrow()
        hendelseService.hendelseForSistBesokt(deltaker, sistBesokt)
    }

    fun getDeltakereMedStatus(statusType: DeltakerStatus.Type) = deltakerRepository.getDeltakereMedStatus(statusType)

    suspend fun oppdaterDeltakerStatuser() {
        val deltakereSomSkalAvsluttes = deltakereSomSkalHaAvsluttendeStatus()
        avsluttDeltakere(deltakereSomSkalAvsluttes)

        val deltakereSomSkalDelta = deltakereSomSkalHaStatusDeltar()
        DeltakerProgresjonHandler
            .tilDeltar(deltakereSomSkalDelta)
            .forEach { upsertAndProduceDeltaker(it) }
    }

    suspend fun avsluttDeltakelserPaaDeltakerliste(deltakerliste: Deltakerliste) {
        val deltakerePaAvbruttDeltakerliste = deltakerRepository
            .getDeltakereForDeltakerliste(deltakerliste.id)
            .filter { it.status.type != DeltakerStatus.Type.KLADD }
            .map { it.copy(deltakerliste = deltakerliste) }

        avsluttDeltakere(deltakerePaAvbruttDeltakerliste)
    }

    private suspend fun avsluttDeltakere(deltakereSomSkalAvsluttes: List<Deltaker>) {
        DeltakerProgresjonHandler
            .getAvsluttendeStatusUtfall(deltakereSomSkalAvsluttes)
            .map { oppdaterVedtakForAvbruttUtkast(it) }
            .forEach { upsertAndProduceDeltaker(it) }
    }

    private suspend fun oppdaterVedtakForAvbruttUtkast(deltaker: Deltaker) =
        if (deltaker.status.type == DeltakerStatus.Type.AVBRUTT_UTKAST) {
            Database.transaction {
                val vedtak = vedtakService.avbrytVedtakVedAvsluttetDeltakerliste(deltaker).getVedtakOrThrow()
                hendelseService.hendelseFraSystem(deltaker) { HendelseType.AvbrytUtkast(it) }
                deltaker.copy(vedtaksinformasjon = vedtak.tilVedtaksInformasjon())
            }
        } else {
            deltaker
        }

    private fun deltakereSomSkalHaAvsluttendeStatus() = deltakerRepository
        .skalHaAvsluttendeStatus()
        .plus(deltakerRepository.deltarPaAvsluttetDeltakerliste())
        .distinct()

    private fun deltakereSomSkalHaStatusDeltar() = deltakerRepository
        .skalHaStatusDeltar()
        .distinct()

    suspend fun avgrensSluttdatoerTil(deltakerliste: Deltakerliste) {
        val deltakere = deltakerRepository
            .getDeltakereForDeltakerliste(deltakerliste.id)
            .filter { it.status.type !in DeltakerStatus.avsluttendeStatuser }

        deltakere.forEach {
            if (it.sluttdato != null && deltakerliste.sluttDato != null && it.sluttdato > deltakerliste.sluttDato) {
                upsertAndProduceDeltaker(
                    deltaker = it.copy(sluttdato = deltakerliste.sluttDato),
                    forcedUpdate = true, // For at oppdateringen skal propageres riktig til amt-deltaker-bff så må vi sette denne.
                )
                log.info("Deltaker ${it.id} fikk ny sluttdato fordi deltakerlisten sin sluttdato var mindre enn deltakers")
            }
        }
    }

    companion object {
        fun validerIkkeFeilregistrert(deltaker: Deltaker) = require(deltaker.status.type != DeltakerStatus.Type.FEILREGISTRERT) {
            "Kan ikke oppdatere feilregistrert deltaker, id ${deltaker.id}"
        }
    }
}

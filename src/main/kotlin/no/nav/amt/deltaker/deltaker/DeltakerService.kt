package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.deltaker.api.model.EndringRequest
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringService
import no.nav.amt.deltaker.deltaker.endring.DeltakerEndringUtfall
import no.nav.amt.deltaker.deltaker.endring.fra.arrangor.EndringFraArrangorService
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.importert.fra.arena.ImportertFraArenaRepository
import no.nav.amt.deltaker.deltaker.kafka.DeltakerProducerService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
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
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID) = deltakerRepository.get(id)

    fun getDeltakelser(personident: String, deltakerlisteId: UUID) = deltakerRepository.getMany(personident, deltakerlisteId)

    fun getDeltakelser(personident: String) = deltakerRepository.getMany(personident)

    fun getDeltakereForDeltakerliste(deltakerlisteId: UUID) = deltakerRepository.getDeltakereForDeltakerliste(deltakerlisteId)

    fun getDeltakerIderForTiltakstype(tiltakstype: Tiltakstype.ArenaKode) = deltakerRepository.getDeltakerIderForTiltakstype(tiltakstype)

    fun getDeltakerIder(personId: UUID, deltakerlisteId: UUID) =
        deltakerRepository.getDeltakerIder(personId = personId, deltakerlisteId = deltakerlisteId)

    suspend fun upsertDeltaker(
        deltaker: Deltaker,
        forcedUpdate: Boolean? = false,
        nesteStatus: DeltakerStatus? = null,
    ): Deltaker = upsert(deltaker, erFremtidigEndring = false, forcedUpdate = forcedUpdate, nesteStatus = nesteStatus)

    private suspend fun upsertDeltakerMedFremtidigEndring(deltaker: Deltaker): Deltaker = upsert(deltaker, erFremtidigEndring = true)

    private suspend fun upsert(
        deltaker: Deltaker,
        erFremtidigEndring: Boolean,
        forcedUpdate: Boolean? = false,
        nesteStatus: DeltakerStatus? = null,
    ): Deltaker {
        deltakerRepository.upsert(deltaker.copy(sistEndret = LocalDateTime.now()), nesteStatus = nesteStatus)

        val oppdatertDeltaker = get(deltaker.id).getOrThrow()

        if (oppdatertDeltaker.status.type != DeltakerStatus.Type.KLADD) {
            deltakerProducerService.produce(oppdatertDeltaker, forcedUpdate = forcedUpdate, publiserTilDeltakerV1 = !erFremtidigEndring)
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

        return when (val resultat = deltakerEndringService.upsertEndring(deltaker, request)) {
            is DeltakerEndringUtfall.VellykketEndring -> upsertDeltaker(resultat.deltaker, nesteStatus = resultat.nesteStatus)
            is DeltakerEndringUtfall.FremtidigEndring -> upsertDeltakerMedFremtidigEndring(resultat.deltaker)
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

    suspend fun oppdaterSistBesokt(deltakerId: UUID, sistBesokt: ZonedDateTime) {
        val deltaker = get(deltakerId).getOrThrow()
        hendelseService.hendelseForSistBesokt(deltaker, sistBesokt)
    }

    fun getDeltakereMedStatus(statusType: DeltakerStatus.Type) = deltakerRepository.getDeltakereMedStatus(statusType)
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

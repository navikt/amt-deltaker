package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.api.model.EndringRequest
import no.nav.amt.deltaker.deltaker.api.model.getForslagId
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.nyDeltakerStatus
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringService(
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val hendelseService: HendelseService,
    private val forslagService: ForslagService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getForDeltaker(deltakerId: UUID) = deltakerEndringRepository.getForDeltaker(deltakerId)

    fun deleteForDeltaker(deltakerId: UUID) = deltakerEndringRepository.deleteForDeltaker(deltakerId)

    suspend fun upsertEndring(
        deltaker: Deltaker,
        endring: DeltakerEndring.Endring,
        utfall: DeltakerEndringUtfall,
        request: EndringRequest,
    ): DeltakerEndring? {
        if (utfall.erUgyldig) return null

        val ansatt = navAnsattService.hentEllerOpprettNavAnsatt(request.endretAv)
        val enhet = navEnhetService.hentEllerOpprettNavEnhet(request.endretAvEnhet)
        val godkjentForslag = request.getForslagId()?.let { forslagId ->
            forslagService.godkjennForslag(
                forslagId = forslagId,
                godkjentAvAnsattId = ansatt.id,
                godkjentAvEnhetId = enhet.id,
            )
        }

        val deltakerEndring = DeltakerEndring(
            id = UUID.randomUUID(),
            deltakerId = deltaker.id,
            endring = endring,
            endretAv = ansatt.id,
            endretAvEnhet = enhet.id,
            endret = LocalDateTime.now(),
            forslag = godkjentForslag,
        )

        val behandlet = if (utfall.erVellykket) LocalDateTime.now() else null

        deltakerEndringRepository.upsert(deltakerEndring, behandlet)
        hendelseService.hendelseForDeltakerEndring(deltakerEndring, utfall.getOrThrow(), ansatt, enhet)
        return deltakerEndring
    }

    fun behandleLagretDeltakelsesmengde(endring: DeltakerEndring, deltaker: Deltaker): DeltakerEndringUtfall {
        val deltakelsesmengde = endring.toDeltakelsesmengde()
            ?: throw IllegalStateException("Endring ${endring.id} er ikke en EndreDeltakelsesmengde")

        val gyldigeDeltakelsesmengder = deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder()

        val endringenErIkkeUtfort = deltaker.deltakelsesprosent != deltakelsesmengde.deltakelsesprosent ||
            deltaker.dagerPerUke != deltakelsesmengde.dagerPerUke

        val utfall =
            if (deltakelsesmengde == gyldigeDeltakelsesmengder.gjeldende && endringenErIkkeUtfort) {
                DeltakerEndringUtfall.VellykketEndring(
                    deltaker.copy(
                        deltakelsesprosent = deltakelsesmengde.deltakelsesprosent,
                        dagerPerUke = deltakelsesmengde.dagerPerUke,
                    ),
                )
            } else {
                DeltakerEndringUtfall.UgyldigEndring(IllegalStateException("Endringen er ikke lenger gyldig"))
            }

        log.info("Behandler endring: ${endring.id}, utfall: ${utfall::class.simpleName}, deltaker: ${deltaker.id}")

        deltakerEndringRepository.upsert(endring, LocalDateTime.now())

        return utfall
    }

    fun getUbehandledeDeltakelsesmengder(offset: Int) = deltakerEndringRepository.getUbehandletDeltakelsesmengder(offset)
}

fun Deltaker.getStatusEndretStartOgSluttdato(startdato: LocalDate?, sluttdato: LocalDate?): DeltakerStatus =
    if (status.type == DeltakerStatus.Type.VENTER_PA_OPPSTART && (
            startdato != null && sluttdato != null && sluttdato.isBefore(
                LocalDate.now(),
            )
        )
    ) {
        nyDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET)
    } else if (status.type == DeltakerStatus.Type.VENTER_PA_OPPSTART && (startdato != null && !startdato.isAfter(LocalDate.now()))) {
        nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
    } else if (status.type == DeltakerStatus.Type.DELTAR && (sluttdato != null && sluttdato.isBefore(LocalDate.now()))) {
        nyDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET)
    } else if (status.type == DeltakerStatus.Type.DELTAR && (startdato == null || startdato.isAfter(LocalDate.now()))) {
        nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
    } else if (status.type == DeltakerStatus.Type.HAR_SLUTTET &&
        (sluttdato == null || !sluttdato.isBefore(LocalDate.now())) &&
        (startdato != null && !startdato.isAfter(LocalDate.now()))
    ) {
        nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
    } else if (status.type == DeltakerStatus.Type.HAR_SLUTTET &&
        (sluttdato == null || !sluttdato.isBefore(LocalDate.now())) &&
        (startdato == null || startdato.isAfter(LocalDate.now()))
    ) {
        nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
    } else {
        status
    }

fun DeltakerEndring.Aarsak.toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
    DeltakerStatus.Aarsak.Type.valueOf(type.name),
    beskrivelse,
)

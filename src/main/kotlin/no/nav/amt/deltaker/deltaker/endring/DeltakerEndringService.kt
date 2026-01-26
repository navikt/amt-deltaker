package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.api.deltaker.getForslagId
import no.nav.amt.deltaker.deltaker.db.DeltakerEndringRepository
import no.nav.amt.deltaker.deltaker.forslag.ForslagService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.hendelse.HendelseService
import no.nav.amt.deltaker.navansatt.NavAnsattRepository
import no.nav.amt.deltaker.navenhet.NavEnhetRepository
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class DeltakerEndringService(
    private val deltakerEndringRepository: DeltakerEndringRepository,
    private val navAnsattRepository: NavAnsattRepository,
    private val navEnhetRepository: NavEnhetRepository,
    private val hendelseService: HendelseService,
    private val forslagService: ForslagService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upsertEndring(
        deltakerId: UUID,
        endring: DeltakerEndring.Endring,
        utfall: DeltakerEndringUtfall,
        request: EndringRequest,
    ): DeltakerEndring? {
        if (utfall.erUgyldig) return null

        val ansatt = navAnsattRepository.getOrThrow(request.endretAv)
        val enhet = navEnhetRepository.getOrThrow(request.endretAvEnhet)

        val godkjentForslag = request.getForslagId()?.let { forslagId ->
            forslagService.godkjennForslag(
                forslagId = forslagId,
                godkjentAvAnsattId = ansatt.id,
                godkjentAvEnhetId = enhet.id,
            )
        }

        val deltakerEndring = DeltakerEndring(
            id = UUID.randomUUID(),
            deltakerId = deltakerId,
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
}

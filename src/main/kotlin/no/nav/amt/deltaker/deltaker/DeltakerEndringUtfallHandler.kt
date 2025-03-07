package no.nav.amt.deltaker.deltaker

import no.nav.amt.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltakerendring.DeltakerEndringUtfall
import no.nav.amt.deltaker.deltakerendring.getStatusEndretStartOgSluttdato
import no.nav.amt.deltaker.deltakerendring.toDeltakerStatusAarsak
import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakerEndringUtfallHandler(
    private val deltaker: Deltaker,
    val endring: DeltakerEndring.Endring,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    fun sjekkUtfall(): DeltakerEndringUtfall = when (endring) {
        is DeltakerEndring.Endring.AvsluttDeltakelse -> getAvsluttDeltakelseUtfall(endring)
        is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> getEndreBakgrunnsinformasjonUtfall(endring)
        is DeltakerEndring.Endring.EndreDeltakelsesmengde -> getEndreDeltakelsesmengdeUtfall(endring)
        is DeltakerEndring.Endring.EndreInnhold -> getEndreInnholdUtfall(endring)
        is DeltakerEndring.Endring.EndreSluttdato -> getEndreSluttdatoUtfall(endring)
        is DeltakerEndring.Endring.EndreSluttarsak -> getEndreSluttarsakUtfall(endring)
        is DeltakerEndring.Endring.EndreStartdato -> getEndreStartdatoUtfall(endring)
        is DeltakerEndring.Endring.ForlengDeltakelse -> getForlengDeltakelseUtfall(endring)
        is DeltakerEndring.Endring.IkkeAktuell -> getIkkeAktuellUtfall(endring)
        is DeltakerEndring.Endring.ReaktiverDeltakelse -> getReaktiverDeltakelseUtfall()
        is DeltakerEndring.Endring.FjernOppstartsdato -> getFjernOppstartsdatoUtfall()
    }

    private fun endreDeltaker(erEndret: Boolean, block: () -> DeltakerEndringUtfall.VellykketEndring) = if (erEndret) {
        block()
    } else {
        ugyldigEndring()
    }

    private fun ugyldigEndring() = DeltakerEndringUtfall.UgyldigEndring(IllegalStateException("Ingen gyldig endring"))

    private fun getReaktiverDeltakelseUtfall() = endreDeltaker(deltaker.status.type == DeltakerStatus.Type.IKKE_AKTUELL) {
        DeltakerEndringUtfall.VellykketEndring(
            deltaker.copy(
                status = nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
            ),
        )
    }

    private fun getIkkeAktuellUtfall(endring: DeltakerEndring.Endring.IkkeAktuell) =
        endreDeltaker(deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    status = nyDeltakerStatus(
                        DeltakerStatus.Type.IKKE_AKTUELL,
                        endring.aarsak.toDeltakerStatusAarsak(),
                    ),
                    startdato = null,
                    sluttdato = null,
                ),
            )
        }

    private fun getForlengDeltakelseUtfall(endring: DeltakerEndring.Endring.ForlengDeltakelse) =
        endreDeltaker(deltaker.sluttdato != endring.sluttdato) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = deltaker.getStatusEndretSluttdato(endring.sluttdato),
                ),
            )
        }

    private fun getEndreStartdatoUtfall(endring: DeltakerEndring.Endring.EndreStartdato) =
        endreDeltaker(deltaker.startdato != endring.startdato || deltaker.sluttdato != endring.sluttdato) {
            DeltakerEndringUtfall.VellykketEndring(
                endreDeltakersOppstart(
                    deltaker,
                    endring.startdato,
                    endring.sluttdato,
                    deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder(),
                ),
            )
        }

    private fun getEndreSluttarsakUtfall(endring: DeltakerEndring.Endring.EndreSluttarsak) =
        endreDeltaker(deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(status = nyDeltakerStatus(deltaker.status.type, endring.aarsak.toDeltakerStatusAarsak())),
            )
        }

    private fun getEndreSluttdatoUtfall(endring: DeltakerEndring.Endring.EndreSluttdato) =
        endreDeltaker(endring.sluttdato != deltaker.sluttdato) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = deltaker.getStatusEndretSluttdato(endring.sluttdato),
                ),
            )
        }

    private fun getEndreInnholdUtfall(endring: DeltakerEndring.Endring.EndreInnhold) =
        endreDeltaker(deltaker.deltakelsesinnhold?.innhold != endring.innhold) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(deltakelsesinnhold = Deltakelsesinnhold(endring.ledetekst, endring.innhold)),
            )
        }

    private fun getEndreDeltakelsesmengdeUtfall(endring: DeltakerEndring.Endring.EndreDeltakelsesmengde): DeltakerEndringUtfall {
        val deltakelsesmengder = deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder()
        val nyDeltakelsesmengde = endring.toDeltakelsesmengde(LocalDateTime.now())

        if (!deltakelsesmengder.validerNyDeltakelsesmengde(nyDeltakelsesmengde)) {
            return ugyldigEndring()
        }

        return if (nyDeltakelsesmengde.gyldigFra <= LocalDate.now()) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    deltakelsesprosent = endring.deltakelsesprosent,
                    dagerPerUke = endring.dagerPerUke,
                ),
            )
        } else {
            DeltakerEndringUtfall.FremtidigEndring(deltaker)
        }
    }

    private fun getEndreBakgrunnsinformasjonUtfall(endring: DeltakerEndring.Endring.EndreBakgrunnsinformasjon) =
        endreDeltaker(deltaker.bakgrunnsinformasjon != endring.bakgrunnsinformasjon) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(bakgrunnsinformasjon = endring.bakgrunnsinformasjon),
            )
        }

    private fun getFjernOppstartsdatoUtfall() = endreDeltaker(deltaker.startdato != null) {
        DeltakerEndringUtfall.VellykketEndring(
            deltaker.copy(
                startdato = null,
                sluttdato = null,
            ),
        )
    }

    private fun getAvsluttDeltakelseUtfall(endring: DeltakerEndring.Endring.AvsluttDeltakelse) = endreDeltaker(
        deltaker.status.type != DeltakerStatus.Type.HAR_SLUTTET ||
            endring.sluttdato != deltaker.sluttdato ||
            deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak(),
    ) {
        if (deltaker.status.type == DeltakerStatus.Type.DELTAR || !endring.skalFortsattDelta()) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = endring.getAvsluttendeStatus(),
                ),
            )
        } else {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
                ),
                nesteStatus = endring.getAvsluttendeStatus(),
            )
        }
    }

    private fun DeltakerEndring.Endring.AvsluttDeltakelse.getAvsluttendeStatus(): DeltakerStatus {
        val gyldigFra = if (skalFortsattDelta()) {
            sluttdato.atStartOfDay().plusDays(1)
        } else {
            LocalDateTime.now()
        }
        return nyDeltakerStatus(
            type = DeltakerStatus.Type.HAR_SLUTTET,
            aarsak = aarsak.toDeltakerStatusAarsak(),
            gyldigFra = gyldigFra,
        )
    }

    private fun DeltakerEndring.Endring.AvsluttDeltakelse.skalFortsattDelta(): Boolean = !sluttdato.isBefore(LocalDate.now())

    private fun Deltaker.getStatusEndretSluttdato(sluttdato: LocalDate): DeltakerStatus =
        if (status.type == DeltakerStatus.Type.HAR_SLUTTET &&
            !sluttdato.isBefore(
                LocalDate.now(),
            )
        ) {
            nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
        } else {
            status
        }
}

fun endreDeltakersOppstart(
    deltaker: Deltaker,
    startdato: LocalDate?,
    sluttdato: LocalDate?,
    deltakelsesmengder: Deltakelsesmengder,
): Deltaker {
    val faktiskSluttdato = sluttdato ?: deltaker.sluttdato
    val oppdatertStatus = deltaker.getStatusEndretStartOgSluttdato(
        startdato = startdato,
        sluttdato = faktiskSluttdato,
    )
    val oppdatertDeltakelsmengde = deltakelsesmengder.avgrensPeriodeTilStartdato(startdato)

    return deltaker.copy(
        startdato = if (oppdatertStatus.type == DeltakerStatus.Type.IKKE_AKTUELL) null else startdato,
        sluttdato = if (oppdatertStatus.type == DeltakerStatus.Type.IKKE_AKTUELL) null else faktiskSluttdato,
        status = oppdatertStatus,
        deltakelsesprosent = oppdatertDeltakelsmengde.gjeldende?.deltakelsesprosent,
        dagerPerUke = oppdatertDeltakelsmengde.gjeldende?.dagerPerUke,
    )
}

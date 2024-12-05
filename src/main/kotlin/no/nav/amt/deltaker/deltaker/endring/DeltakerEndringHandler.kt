package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.nyDeltakerStatus
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.Deltakelsesmengder
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import java.time.LocalDate
import java.time.LocalDateTime

class DeltakerEndringHandler(
    private val deltaker: Deltaker,
    val endring: DeltakerEndring.Endring,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    fun handle(): DeltakerEndringUtfall = when (endring) {
        is DeltakerEndring.Endring.AvsluttDeltakelse -> avsluttDeltakelses(endring)
        is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> endreBakgrunnsinformasjon(endring)
        is DeltakerEndring.Endring.EndreDeltakelsesmengde -> endreDeltakelsesmengde(endring)
        is DeltakerEndring.Endring.EndreInnhold -> endreInnhold(endring)
        is DeltakerEndring.Endring.EndreSluttdato -> endreSluttdato(endring)
        is DeltakerEndring.Endring.EndreSluttarsak -> endreSluttarsak(endring)
        is DeltakerEndring.Endring.EndreStartdato -> endreStartdato(endring)
        is DeltakerEndring.Endring.ForlengDeltakelse -> forlengDeltakelse(endring)
        is DeltakerEndring.Endring.IkkeAktuell -> ikkeAktuell(endring)
        is DeltakerEndring.Endring.ReaktiverDeltakelse -> reaktiverDeltakelse()
    }

    private fun endreDeltaker(erEndret: Boolean, block: () -> EndreDeltakerResultat) = if (erEndret) {
        val (deltaker, nesteStatus) = block()
        DeltakerEndringUtfall.VellykketEndring(deltaker, nesteStatus)
    } else {
        ugyldigEndring()
    }

    private fun ugyldigEndring() = DeltakerEndringUtfall.UgyldigEndring(IllegalStateException("Ingen gyldig endring"))

    private fun reaktiverDeltakelse() = endreDeltaker(deltaker.status.type == DeltakerStatus.Type.IKKE_AKTUELL) {
        EndreDeltakerResultat(
            deltaker.copy(
                status = nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART),
                startdato = null,
                sluttdato = null,
            ),
        )
    }

    private fun ikkeAktuell(endring: DeltakerEndring.Endring.IkkeAktuell) =
        endreDeltaker(deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
            EndreDeltakerResultat(
                deltaker.copy(
                    status = nyDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL, endring.aarsak.toDeltakerStatusAarsak()),
                    startdato = null,
                    sluttdato = null,
                ),
            )
        }

    private fun forlengDeltakelse(endring: DeltakerEndring.Endring.ForlengDeltakelse) =
        endreDeltaker(deltaker.sluttdato != endring.sluttdato) {
            EndreDeltakerResultat(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = deltaker.getStatusEndretSluttdato(endring.sluttdato),
                ),
            )
        }

    private fun endreStartdato(endring: DeltakerEndring.Endring.EndreStartdato) =
        endreDeltaker(deltaker.startdato != endring.startdato || deltaker.sluttdato != endring.sluttdato) {
            EndreDeltakerResultat(
                endreDeltakersOppstart(
                    deltaker,
                    endring.startdato,
                    endring.sluttdato,
                    deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder(),
                ),
            )
        }

    private fun endreSluttarsak(endring: DeltakerEndring.Endring.EndreSluttarsak) =
        endreDeltaker(deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
            EndreDeltakerResultat(
                deltaker.copy(status = nyDeltakerStatus(deltaker.status.type, endring.aarsak.toDeltakerStatusAarsak())),
            )
        }

    private fun endreSluttdato(endring: DeltakerEndring.Endring.EndreSluttdato) = endreDeltaker(endring.sluttdato != deltaker.sluttdato) {
        EndreDeltakerResultat(
            deltaker.copy(
                sluttdato = endring.sluttdato,
                status = deltaker.getStatusEndretSluttdato(endring.sluttdato),
            ),
        )
    }

    private fun endreInnhold(endring: DeltakerEndring.Endring.EndreInnhold) =
        endreDeltaker(deltaker.deltakelsesinnhold?.innhold != endring.innhold) {
            EndreDeltakerResultat(
                deltaker.copy(deltakelsesinnhold = Deltakelsesinnhold(endring.ledetekst, endring.innhold)),
            )
        }

    private fun endreDeltakelsesmengde(endring: DeltakerEndring.Endring.EndreDeltakelsesmengde): DeltakerEndringUtfall {
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

    private fun endreBakgrunnsinformasjon(endring: DeltakerEndring.Endring.EndreBakgrunnsinformasjon) =
        endreDeltaker(deltaker.bakgrunnsinformasjon != endring.bakgrunnsinformasjon) {
            EndreDeltakerResultat(
                deltaker.copy(bakgrunnsinformasjon = endring.bakgrunnsinformasjon),
            )
        }

    private fun avsluttDeltakelses(endring: DeltakerEndring.Endring.AvsluttDeltakelse) = endreDeltaker(
        deltaker.status.type != DeltakerStatus.Type.HAR_SLUTTET ||
            endring.sluttdato != deltaker.sluttdato ||
            deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak(),
    ) {
        if (deltaker.status.type == DeltakerStatus.Type.DELTAR) {
            EndreDeltakerResultat(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = nyDeltakerStatus(
                        type = DeltakerStatus.Type.HAR_SLUTTET,
                        aarsak = endring.aarsak.toDeltakerStatusAarsak(),
                        gyldigFra = if (!endring.sluttdato.isBefore(LocalDate.now())) {
                            endring.sluttdato.atStartOfDay().plusDays(1)
                        } else {
                            LocalDateTime.now()
                        },
                    ),
                ),
            )
        } else {
            if (!endring.sluttdato.isBefore(LocalDate.now())) {
                EndreDeltakerResultat(
                    deltaker.copy(
                        sluttdato = endring.sluttdato,
                        status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
                    ),
                    nyDeltakerStatus(
                        type = DeltakerStatus.Type.HAR_SLUTTET,
                        aarsak = endring.aarsak.toDeltakerStatusAarsak(),
                        gyldigFra = endring.sluttdato.atStartOfDay().plusDays(1),
                    ),
                )
            } else {
                EndreDeltakerResultat(
                    deltaker.copy(
                        sluttdato = endring.sluttdato,
                        status = nyDeltakerStatus(
                            type = DeltakerStatus.Type.HAR_SLUTTET,
                            aarsak = endring.aarsak.toDeltakerStatusAarsak(),
                            gyldigFra = LocalDateTime.now(),
                        ),
                    ),
                )
            }
        }
    }

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

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
    fun sjekkUtfall(): DeltakerEndringUtfall = when (endring) {
        is DeltakerEndring.Endring.AvsluttDeltakelse -> avsluttDeltakelse(endring)
        is DeltakerEndring.Endring.AvbrytDeltakelse -> avbrytDeltakelse(endring)
        is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> endreBakgrunnsinformasjon(endring)
        is DeltakerEndring.Endring.EndreDeltakelsesmengde -> endreDeltakelsesmengde(endring)
        is DeltakerEndring.Endring.EndreInnhold -> endreInnhold(endring)
        is DeltakerEndring.Endring.EndreSluttdato -> endreSluttdato(endring)
        is DeltakerEndring.Endring.EndreSluttarsak -> endreSluttarsak(endring)
        is DeltakerEndring.Endring.EndreStartdato -> endreStartdato(endring)
        is DeltakerEndring.Endring.ForlengDeltakelse -> forlengDeltakelse(endring)
        is DeltakerEndring.Endring.IkkeAktuell -> ikkeAktuell(endring)
        is DeltakerEndring.Endring.ReaktiverDeltakelse -> reaktiverDeltakelse()
        is DeltakerEndring.Endring.FjernOppstartsdato -> fjernOppstartsdato()
    }

    private fun endreDeltaker(erEndret: Boolean, block: () -> DeltakerEndringUtfall.VellykketEndring) = if (erEndret) {
        block()
    } else {
        ugyldigEndring()
    }

    private fun ugyldigEndring() = DeltakerEndringUtfall.UgyldigEndring(IllegalStateException("Ingen gyldig endring"))

    private fun reaktiverDeltakelse() = endreDeltaker(deltaker.status.type == DeltakerStatus.Type.IKKE_AKTUELL) {
        val nyStatus = if (deltaker.deltakerliste.erFellesOppstart) {
            nyDeltakerStatus(DeltakerStatus.Type.SOKT_INN)
        } else {
            nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
        }

        DeltakerEndringUtfall.VellykketEndring(
            deltaker.copy(
                status = nyStatus,
                startdato = null,
                sluttdato = null,
            ),
        )
    }

    private fun ikkeAktuell(endring: DeltakerEndring.Endring.IkkeAktuell) =
        endreDeltaker(deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    status = nyDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL, endring.aarsak.toDeltakerStatusAarsak()),
                    startdato = null,
                    sluttdato = null,
                ),
            )
        }

    private fun forlengDeltakelse(endring: DeltakerEndring.Endring.ForlengDeltakelse) =
        endreDeltaker(deltaker.sluttdato != endring.sluttdato) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = deltaker.getStatusEndretSluttdato(endring.sluttdato),
                ),
            )
        }

    private fun endreStartdato(endring: DeltakerEndring.Endring.EndreStartdato) =
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

    private fun endreSluttarsak(endring: DeltakerEndring.Endring.EndreSluttarsak) =
        endreDeltaker(deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(status = nyDeltakerStatus(deltaker.status.type, endring.aarsak.toDeltakerStatusAarsak())),
            )
        }

    private fun endreSluttdato(endring: DeltakerEndring.Endring.EndreSluttdato) = endreDeltaker(endring.sluttdato != deltaker.sluttdato) {
        DeltakerEndringUtfall.VellykketEndring(
            deltaker.copy(
                sluttdato = endring.sluttdato,
                status = deltaker.getStatusEndretSluttdato(endring.sluttdato),
            ),
        )
    }

    private fun endreInnhold(endring: DeltakerEndring.Endring.EndreInnhold) =
        endreDeltaker(deltaker.deltakelsesinnhold?.innhold != endring.innhold) {
            DeltakerEndringUtfall.VellykketEndring(
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
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(bakgrunnsinformasjon = endring.bakgrunnsinformasjon),
            )
        }

    private fun fjernOppstartsdato() = endreDeltaker(deltaker.startdato != null) {
        DeltakerEndringUtfall.VellykketEndring(
            deltaker.copy(
                startdato = null,
                sluttdato = null,
            ),
        )
    }

    private fun avsluttDeltakelse(endring: DeltakerEndring.Endring.AvsluttDeltakelse) = endreDeltaker(
        deltaker.status.type != DeltakerStatus.Type.HAR_SLUTTET ||
            endring.sluttdato != deltaker.sluttdato ||
            deltaker.status.aarsak != endring.aarsak?.toDeltakerStatusAarsak(),
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

    private fun avbrytDeltakelse(endring: DeltakerEndring.Endring.AvbrytDeltakelse) = endreDeltaker(
        deltaker.status.type != DeltakerStatus.Type.HAR_SLUTTET ||
            endring.sluttdato != deltaker.sluttdato ||
            deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak(),
    ) {
        if (deltaker.status.type == DeltakerStatus.Type.DELTAR || !endring.skalFortsattDelta()) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = nyDeltakerStatus(DeltakerStatus.Type.AVBRUTT),
                ),
            )
        } else {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = deltaker.status.copy(gyldigTil = endring.sluttdato.atStartOfDay()),
                ),
                nesteStatus = endring.getAvbruttStatus(),
            )
        }
    }

    private fun DeltakerEndring.Endring.AvsluttDeltakelse.getAvsluttendeStatus(): DeltakerStatus {
        val erFellesInntak = deltaker.deltakerliste.erFellesOppstart
        val gyldigFra = if (skalFortsattDelta()) {
            sluttdato.atStartOfDay().plusDays(1)
        } else {
            LocalDateTime.now()
        }
        return nyDeltakerStatus(
            type = if (erFellesInntak) DeltakerStatus.Type.FULLFORT else DeltakerStatus.Type.HAR_SLUTTET,
            aarsak = aarsak?.toDeltakerStatusAarsak(),
            gyldigFra = gyldigFra,
        )
    }

    private fun DeltakerEndring.Endring.AvbrytDeltakelse.getAvbruttStatus(): DeltakerStatus {
        val gyldigFra = if (skalFortsattDelta()) {
            sluttdato.atStartOfDay().plusDays(1)
        } else {
            LocalDateTime.now()
        }
        return nyDeltakerStatus(
            type = DeltakerStatus.Type.AVBRUTT,
            aarsak = aarsak.toDeltakerStatusAarsak(),
            gyldigFra = gyldigFra,
        )
    }

    private fun DeltakerEndring.Endring.AvsluttDeltakelse.skalFortsattDelta(): Boolean = !sluttdato.isBefore(LocalDate.now())

    private fun DeltakerEndring.Endring.AvbrytDeltakelse.skalFortsattDelta(): Boolean = !sluttdato.isBefore(LocalDate.now())

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

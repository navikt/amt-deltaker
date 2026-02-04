package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Deltaker
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
        is DeltakerEndring.Endring.EndreAvslutning -> endreAvslutning(endring)
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
            // Skal deltaker avsluttes nå eller i fremtiden

            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = endring.getAvsluttendeStatus(),
                ),
            )
        } else {
            // Deltaker er avsluttet allerede, men nav veileder godkjenner et forlag om å avslutte deltaker frem i tid
            // Da settes status til DELTAR igjen med en fremtidig(neste) avsluttende status
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
                ),
                nesteStatus = endring.getAvsluttendeStatus(),
            )
        }
    }

    private fun endreAvslutning(endring: DeltakerEndring.Endring.EndreAvslutning) = endreDeltaker(
        (deltaker.status.type == DeltakerStatus.Type.FULLFORT && endring.harFullfort == false) ||
            (deltaker.status.type == DeltakerStatus.Type.AVBRUTT && endring.harFullfort == true) ||
            deltaker.sluttdato != endring.sluttdato ||
            deltaker.status.aarsak != endring.aarsak?.toDeltakerStatusAarsak(),
    ) {
        if (endring.sluttdato != null && endring.skalFortsattDelta() == true) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato!!,
                    status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
                ),
                nesteStatus = endring.getEndreAvslutningStatus(),
            )
        } else {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    status = endring.getEndreAvslutningStatus(),
                    sluttdato = endring.sluttdato,
                ),
            )
        }
    }

    private fun avbrytDeltakelse(endring: DeltakerEndring.Endring.AvbrytDeltakelse) = endreDeltaker(
        deltaker.status.type != DeltakerStatus.Type.AVBRUTT ||
            endring.sluttdato != deltaker.sluttdato ||
            deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak(),
    ) {
        if (deltaker.status.type == DeltakerStatus.Type.DELTAR || !endring.skalFortsattDelta()) {
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = endring.getAvbruttStatus(),
                ),
            )
        } else {
            // Status er ikke Deltar, men deltakeren skal få deltar status
            DeltakerEndringUtfall.VellykketEndring(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
                ),
                nesteStatus = endring.getAvbruttStatus(),
            )
        }
    }

    private fun DeltakerEndring.Endring.AvsluttDeltakelse.getAvsluttendeStatus(): DeltakerStatus {
        val erFellesOppstart = deltaker.deltakerliste.erFellesOppstart
        val erOpplaeringsTiltak = deltaker.deltakerliste.tiltakstype.tiltakskode
            .erOpplaeringstiltak()
        val gyldigFra = if (skalFortsattDelta()) {
            sluttdato.atStartOfDay().plusDays(1)
        } else {
            LocalDateTime.now()
        }
        return nyDeltakerStatus(
            type = if (erOpplaeringsTiltak || erFellesOppstart) DeltakerStatus.Type.FULLFORT else DeltakerStatus.Type.HAR_SLUTTET,
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

    private fun DeltakerEndring.Endring.EndreAvslutning.getEndreAvslutningStatus(): DeltakerStatus {
        val nyDeltakerStatusType = deltaker.getAvsluttendeStatus(harFullfort)

        val gyldigFra = if (sluttdato != null && skalFortsattDelta() == true) {
            sluttdato!!.atStartOfDay().plusDays(1)
        } else {
            LocalDateTime.now()
        }

        return nyDeltakerStatus(
            type = nyDeltakerStatusType,
            aarsak = aarsak?.toDeltakerStatusAarsak(),
            gyldigFra = gyldigFra,
        )
    }

    private fun DeltakerEndring.Endring.AvsluttDeltakelse.skalFortsattDelta(): Boolean = !sluttdato.isBefore(LocalDate.now())

    private fun DeltakerEndring.Endring.AvbrytDeltakelse.skalFortsattDelta(): Boolean = !sluttdato.isBefore(LocalDate.now())

    private fun DeltakerEndring.Endring.EndreAvslutning.skalFortsattDelta(): Boolean? = sluttdato?.let { !it.isBefore(LocalDate.now()) }

    private fun Deltaker.getStatusEndretSluttdato(sluttdato: LocalDate): DeltakerStatus =
        if (status.type in listOf(DeltakerStatus.Type.HAR_SLUTTET, DeltakerStatus.Type.AVBRUTT, DeltakerStatus.Type.FULLFORT) &&
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

fun Deltaker.getAvsluttendeStatus(harFullfort: Boolean? = false): DeltakerStatus.Type {
    val erFellesOppstart = deltakerliste.erFellesOppstart
    val erOpplaeringsTiltak = deltakerliste.tiltakstype.tiltakskode
        .erOpplaeringstiltak()
    return when {
        erFellesOppstart || erOpplaeringsTiltak -> {
            if (harFullfort == true) DeltakerStatus.Type.FULLFORT else DeltakerStatus.Type.AVBRUTT
        }

        else -> {
            DeltakerStatus.Type.HAR_SLUTTET
        }
    }
}

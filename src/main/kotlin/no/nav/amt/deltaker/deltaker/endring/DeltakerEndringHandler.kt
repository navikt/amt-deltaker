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
    fun getEndretDeltaker(): DeltakerMedFremtidigStatus = when (endring) {
        is DeltakerEndring.Endring.AvsluttDeltakelse -> avsluttDeltakelse(endring)
        is DeltakerEndring.Endring.EndreAvslutning -> endreAvslutning(endring, deltaker.deltakerliste.erFellesOppstart)
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

    private fun reaktiverDeltakelse(): DeltakerMedFremtidigStatus {
        val nyStatus = if (deltaker.deltakerliste.erFellesOppstart) {
            nyDeltakerStatus(DeltakerStatus.Type.SOKT_INN)
        } else {
            nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
        }

        return DeltakerMedFremtidigStatus(
            deltaker.copy(
                status = nyStatus,
                startdato = null,
                sluttdato = null,
            ),
        )
    }

    private fun ikkeAktuell(endring: DeltakerEndring.Endring.IkkeAktuell) = DeltakerMedFremtidigStatus(
        deltaker.copy(
            status = nyDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL, endring.aarsak.toDeltakerStatusAarsak()),
            startdato = null,
            sluttdato = null,
        ),
    )

    private fun forlengDeltakelse(endring: DeltakerEndring.Endring.ForlengDeltakelse) = DeltakerMedFremtidigStatus(
        deltaker.copy(
            sluttdato = endring.sluttdato,
            status = deltaker.getStatusEndretSluttdato(endring.sluttdato),
        ),
    )

    private fun endreStartdato(endring: DeltakerEndring.Endring.EndreStartdato) = DeltakerMedFremtidigStatus(
        endreDeltakersOppstart(
            deltaker,
            endring.startdato,
            endring.sluttdato,
            deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder(),
        ),
    )

    private fun endreSluttarsak(endring: DeltakerEndring.Endring.EndreSluttarsak) =
        DeltakerMedFremtidigStatus(deltaker.copy(status = nyDeltakerStatus(deltaker.status.type, endring.aarsak.toDeltakerStatusAarsak())))

    private fun endreSluttdato(endring: DeltakerEndring.Endring.EndreSluttdato) = DeltakerMedFremtidigStatus(
        deltaker.copy(
            sluttdato = endring.sluttdato,
            status = deltaker.getStatusEndretSluttdato(endring.sluttdato),
        ),
    )

    private fun endreInnhold(endring: DeltakerEndring.Endring.EndreInnhold) =
        DeltakerMedFremtidigStatus(deltaker.copy(deltakelsesinnhold = Deltakelsesinnhold(endring.ledetekst, endring.innhold)))

    private fun endreDeltakelsesmengde(endring: DeltakerEndring.Endring.EndreDeltakelsesmengde): DeltakerMedFremtidigStatus {
        val nyDeltakelsesmengde = endring.toDeltakelsesmengde(LocalDateTime.now())
        return DeltakerMedFremtidigStatus(
            deltaker = if (nyDeltakelsesmengde.gyldigFra <= LocalDate.now()) {
                deltaker.copy(
                    deltakelsesprosent = endring.deltakelsesprosent,
                    dagerPerUke = endring.dagerPerUke,
                )
            } else {
                deltaker
            },
        )
    }

    private fun endreBakgrunnsinformasjon(endring: DeltakerEndring.Endring.EndreBakgrunnsinformasjon) =
        DeltakerMedFremtidigStatus(deltaker.copy(bakgrunnsinformasjon = endring.bakgrunnsinformasjon))

    private fun fjernOppstartsdato() = DeltakerMedFremtidigStatus(
        deltaker.copy(
            startdato = null,
            sluttdato = null,
        ),
    )

    private fun avsluttDeltakelse(endring: DeltakerEndring.Endring.AvsluttDeltakelse): DeltakerMedFremtidigStatus =
        if (deltaker.status.type == DeltakerStatus.Type.DELTAR || !endring.skalFortsattDelta()) {
            // Skal deltaker avsluttes nå eller i fremtiden
            DeltakerMedFremtidigStatus(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = endring.getAvsluttendeStatus(),
                ),
            )
        } else {
            // Deltaker er avsluttet allerede, men nav veileder godkjenner et forlag om å avslutte deltaker frem i tid
            // Da settes status til DELTAR igjen med en fremtidig(neste) avsluttende status
            DeltakerMedFremtidigStatus(
                fremtidigStatus = endring.getAvsluttendeStatus(),
                deltaker = deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
                ),
            )
        }

    private fun endreAvslutning(endring: DeltakerEndring.Endring.EndreAvslutning, erFellesOppstart: Boolean?) =
        if (endring.sluttdato != null && endring.skalFortsattDelta() == true) {
            DeltakerMedFremtidigStatus(
                deltaker.copy(
                    sluttdato = endring.sluttdato!!,
                    status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
                ),
                fremtidigStatus = endring.getEndreAvslutningStatus(erFellesOppstart),
            )
        } else {
            DeltakerMedFremtidigStatus(
                deltaker.copy(
                    status = endring.getEndreAvslutningStatus(erFellesOppstart),
                    sluttdato = endring.sluttdato,
                ),
            )
        }

    private fun avbrytDeltakelse(endring: DeltakerEndring.Endring.AvbrytDeltakelse) =
        if (deltaker.status.type == DeltakerStatus.Type.DELTAR || !endring.skalFortsattDelta()) {
            DeltakerMedFremtidigStatus(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = endring.getAvbruttStatus(),
                ),
            )
        } else {
            // Status er ikke Deltar, men deltakeren skal få deltar status
            DeltakerMedFremtidigStatus(
                deltaker.copy(
                    sluttdato = endring.sluttdato,
                    status = nyDeltakerStatus(DeltakerStatus.Type.DELTAR),
                ),
                fremtidigStatus = endring.getAvbruttStatus(),
            )
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

    private fun DeltakerEndring.Endring.EndreAvslutning.getEndreAvslutningStatus(erFellesOppstart: Boolean?): DeltakerStatus {
        val status = if (erFellesOppstart != true) {
            DeltakerStatus.Type.HAR_SLUTTET
        } else if (harFullfort == true) {
            DeltakerStatus.Type.FULLFORT
        } else {
            DeltakerStatus.Type.AVBRUTT
        }

        val gyldigFra = if (sluttdato != null && skalFortsattDelta() == true) {
            sluttdato!!.atStartOfDay().plusDays(1)
        } else {
            LocalDateTime.now()
        }

        return nyDeltakerStatus(
            type = status,
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

package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.deltaker.utils.toTitleCase
import java.time.LocalDate
import java.util.UUID

class DeltakelserResponseMapper(
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val arrangorService: ArrangorService,
) {
    fun toDeltakelserResponse(deltakelser: List<Deltaker>): DeltakelserResponse {
        val aktive = deltakelser.filter { it.status.type.name in AKTIVE_STATUSER }.map { toAktivDeltakelse(it) }

        val historikk = deltakelser.filter { it.status.type.name in HISTORISKE_STATUSER }.map { toHistoriskDeltakelse(it) }

        return DeltakelserResponse(aktive, historikk)
    }

    private fun toAktivDeltakelse(deltaker: Deltaker): AktivDeltakelse {
        return AktivDeltakelse(
            deltakerId = deltaker.id,
            innsoktDato = getInnsoktDato(deltaker.id),
            sistEndretdato = deltaker.sistEndret.toLocalDate(),
            aktivStatus = AktivDeltakelse.AktivStatusType.valueOf(deltaker.status.type.name),
            tittel = lagTittel(deltaker),
            tiltakstype = deltaker.deltakerliste.tiltakstype.toTiltakstypeRespons(),
        )
    }

    private fun toHistoriskDeltakelse(deltaker: Deltaker): HistoriskDeltakelse {
        return HistoriskDeltakelse(
            deltakerId = deltaker.id,
            innsoktDato = getInnsoktDato(
                deltaker.id,
            ) ?: throw IllegalStateException("Historisk deltakelse med id ${deltaker.id} mangler innsøkt-dato"),
            periode = deltaker.getPeriode(),
            historiskStatus = HistoriskDeltakelse.HistoriskStatus(
                historiskStatusType = HistoriskDeltakelse.HistoriskStatusType.valueOf(deltaker.status.type.name),
                aarsak = deltaker.status.aarsak?.getVisningsnavn(),
            ),
            tittel = lagTittel(deltaker),
            tiltakstype = deltaker.deltakerliste.tiltakstype.toTiltakstypeRespons(),
        )
    }

    private fun getInnsoktDato(deltakerId: UUID): LocalDate? {
        val deltakerhistorikk = deltakerHistorikkService.getForDeltaker(deltakerId)
        return deltakerHistorikkService.getInnsoktDato(deltakerhistorikk)
    }

    private fun Deltaker.getPeriode(): Periode? {
        return if (status.type == DeltakerStatus.Type.IKKE_AKTUELL) {
            null
        } else {
            Periode(
                startdato = startdato,
                sluttdato = sluttdato,
            )
        }
    }

    private fun DeltakerStatus.Aarsak.getVisningsnavn(): String {
        if (beskrivelse != null) {
            return beskrivelse
        }
        return when (type) {
            DeltakerStatus.Aarsak.Type.SYK -> "syk"
            DeltakerStatus.Aarsak.Type.FATT_JOBB -> "fått jobb"
            DeltakerStatus.Aarsak.Type.TRENGER_ANNEN_STOTTE -> "trenger annen støtte"
            DeltakerStatus.Aarsak.Type.FIKK_IKKE_PLASS -> "fikk ikke plass"
            DeltakerStatus.Aarsak.Type.IKKE_MOTT -> "ikke møtt"
            DeltakerStatus.Aarsak.Type.ANNET -> "annet"
            DeltakerStatus.Aarsak.Type.AVLYST_KONTRAKT -> "avlyst kontrakt"
        }
    }

    private fun lagTittel(deltaker: Deltaker): String {
        val arrangorNavn = deltaker.deltakerliste.getArrangorNavn()
        return when (deltaker.deltakerliste.tiltakstype.type) {
            Tiltakstype.Type.DIGIOPPARB -> "Digital oppfølging hos $arrangorNavn"
            Tiltakstype.Type.JOBBK -> "Jobbsøkerkurs hos $arrangorNavn"
            Tiltakstype.Type.GRUPPEAMO -> if (deltaker.deltarPaKurs()) {
                "Kurs: ${deltaker.deltakerliste.navn}"
            } else {
                deltaker.deltakerliste.navn
            }

            Tiltakstype.Type.GRUFAGYRKE -> deltaker.deltakerliste.navn
            else -> "${deltaker.deltakerliste.tiltakstype.navn} hos $arrangorNavn"
        }
    }

    private fun Deltakerliste.getArrangorNavn(): String {
        val arrangor = arrangor.overordnetArrangorId?.let { arrangorService.hentArrangor(it) } ?: arrangor
        return toTitleCase(arrangor.navn)
    }

    private fun Tiltakstype.toTiltakstypeRespons(): DeltakelserResponse.Tiltakstype = DeltakelserResponse.Tiltakstype(
        navn = navn,
        tiltakskode = type,
    )
}

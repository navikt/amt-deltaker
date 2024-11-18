package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.getStatustekst
import no.nav.amt.deltaker.deltaker.model.getVisningsnavn
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import no.nav.amt.deltaker.utils.toTitleCase
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate

class DeltakelserResponseMapper(
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val arrangorService: ArrangorService,
) {
    private val skalViseSistEndretDatoStatuser = listOf(
        DeltakerStatus.Type.KLADD,
        DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
        DeltakerStatus.Type.AVBRUTT_UTKAST,
    )
    private val skalViseInnsoktDatoStatuser = listOf(
        DeltakerStatus.Type.VENTER_PA_OPPSTART,
        DeltakerStatus.Type.DELTAR,
        DeltakerStatus.Type.HAR_SLUTTET,
        DeltakerStatus.Type.IKKE_AKTUELL,
        DeltakerStatus.Type.FEILREGISTRERT,
    )
    private val skalVisePeriodeStatuser = listOf(
        DeltakerStatus.Type.VENTER_PA_OPPSTART,
        DeltakerStatus.Type.DELTAR,
        DeltakerStatus.Type.SOKT_INN,
        DeltakerStatus.Type.VURDERES,
        DeltakerStatus.Type.VENTELISTE,
        DeltakerStatus.Type.HAR_SLUTTET,
        DeltakerStatus.Type.FULLFORT,
        DeltakerStatus.Type.AVBRUTT,
    )
    private val skalViseArsakStatuser = listOf(
        DeltakerStatus.Type.HAR_SLUTTET,
        DeltakerStatus.Type.IKKE_AKTUELL,
        DeltakerStatus.Type.AVBRUTT,
    )

    fun toDeltakelserResponse(deltakelser: List<Deltaker>): DeltakelserResponse {
        val aktive = deltakelser.filter { it.status.type in AKTIVE_STATUSER }
            .sortedByDescending { it.sistEndret }
            .map { toDeltakerKort(it) }

        val historikk = deltakelser.filter { it.status.type in HISTORISKE_STATUSER }
            .sortedByDescending { it.sistEndret }
            .map { toDeltakerKort(it) }

        return DeltakelserResponse(aktive, historikk)
    }

    private fun toDeltakerKort(deltaker: Deltaker): DeltakerKort {
        return DeltakerKort(
            deltakerId = deltaker.id,
            deltakerlisteId = deltaker.deltakerliste.id,
            tittel = lagTittel(deltaker),
            tiltakstype = deltaker.deltakerliste.tiltakstype.toTiltakstypeRespons(),
            status = deltaker.getStatus(),
            innsoktDato = deltaker.getInnsoktDato(),
            sistEndretDato = deltaker.getSistEndretDato(),
            periode = deltaker.getPeriode(),
        )
    }

    private fun Deltaker.getInnsoktDato(): LocalDate? {
        return if (status.type in skalViseInnsoktDatoStatuser) {
            val deltakerhistorikk = deltakerHistorikkService.getForDeltaker(id)
            deltakerHistorikkService.getInnsoktDato(deltakerhistorikk)
        } else {
            null
        }
    }

    private fun Deltaker.getSistEndretDato(): LocalDate? {
        return if (status.type in skalViseSistEndretDatoStatuser) {
            sistEndret.toLocalDate()
        } else {
            null
        }
    }

    private fun Deltaker.getPeriode(): Periode? {
        return if (status.type in skalVisePeriodeStatuser && startdato != null) {
            Periode(
                startdato = startdato,
                sluttdato = sluttdato,
            )
        } else {
            null
        }
    }

    private fun Deltaker.getStatus(): DeltakerKort.Status {
        return DeltakerKort.Status(
            type = status.type,
            visningstekst = status.type.getVisningsnavn(),
            aarsak = getArsak(),
        )
    }

    private fun Deltaker.getArsak(): String? {
        val aarsak = this.status.aarsak
        return if (status.type in skalViseArsakStatuser && aarsak != null) {
            aarsak.getVisningsnavn()
        } else {
            return null
        }
    }

    private fun DeltakerStatus.Type.getVisningsnavn(): String {
        return when (this) {
            DeltakerStatus.Type.PABEGYNT_REGISTRERING -> throw IllegalStateException("Skal ikke vise status ${this.name}")
            else -> this.getStatustekst()
        }
    }

    private fun lagTittel(deltaker: Deltaker): String {
        val arrangorNavn = deltaker.deltakerliste.getArrangorNavn()
        return when (deltaker.deltakerliste.tiltakstype.arenaKode) {
            Tiltakstype.ArenaKode.DIGIOPPARB -> "Digitalt jobbsøkerkurs hos $arrangorNavn"
            Tiltakstype.ArenaKode.JOBBK -> "Jobbsøkerkurs hos $arrangorNavn"
            Tiltakstype.ArenaKode.GRUPPEAMO -> if (deltaker.deltarPaKurs()) {
                "Kurs: ${deltaker.deltakerliste.navn}"
            } else {
                deltaker.deltakerliste.navn
            }

            Tiltakstype.ArenaKode.GRUFAGYRKE -> deltaker.deltakerliste.navn
            else -> "${deltaker.deltakerliste.tiltakstype.navn} hos $arrangorNavn"
        }
    }

    private fun Deltakerliste.getArrangorNavn(): String {
        val arrangor = arrangor.overordnetArrangorId?.let { arrangorService.hentArrangor(it) } ?: arrangor
        return toTitleCase(arrangor.navn)
    }

    private fun Tiltakstype.toTiltakstypeRespons(): DeltakelserResponse.Tiltakstype = DeltakelserResponse.Tiltakstype(
        navn = navn,
        tiltakskode = arenaKode,
    )
}

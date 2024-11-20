package no.nav.amt.deltaker.deltakerliste.kafka

import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import java.time.LocalDate
import java.util.UUID

data class DeltakerlisteDto(
    val id: UUID,
    val tiltakstype: Tiltakstype,
    val navn: String,
    val startDato: LocalDate,
    val sluttDato: LocalDate? = null,
    val status: String,
    val virksomhetsnummer: String,
    val oppstart: Deltakerliste.Oppstartstype?,
    val apentForPamelding: Boolean?,
) {
    data class Tiltakstype(
        val navn: String,
        val arenaKode: String,
    )

    fun toModel(arrangor: Arrangor, tiltakstype: no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype) = Deltakerliste(
        id = this.id,
        tiltakstype = tiltakstype,
        navn = this.navn,
        status = Deltakerliste.Status.fromString(this.status),
        startDato = this.startDato,
        sluttDato = this.sluttDato,
        oppstart = this.oppstart,
        arrangor = arrangor,
        apentForPamelding = apentForPamelding ?: true,
    )
}

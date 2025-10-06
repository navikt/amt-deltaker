package no.nav.amt.deltaker.deltakerliste.kafka

import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
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
    val oppstart: Oppstartstype,
    val apentForPamelding: Boolean?,
) {
    data class Tiltakstype(
        val navn: String,
        val arenaKode: String,
        val tiltakskode: String,
    ) {
        fun erStottet() = this.tiltakskode in Tiltakskode.entries.toTypedArray().map { it.name }
    }

    fun toModel(arrangor: Arrangor, tiltakstype: no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype) = Deltakerliste(
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

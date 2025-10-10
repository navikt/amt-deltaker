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
    val navn: String? = null, // finnes kun for gruppetiltak
    val startDato: LocalDate? = null, // finnes kun for gruppetiltak
    val sluttDato: LocalDate? = null, // finnes kun for gruppetiltak
    val status: String? = null, // finnes kun for gruppetiltak
    val oppstart: Oppstartstype? = null, // finnes kun for gruppetiltak
    val apentForPamelding: Boolean = true, // finnes kun for gruppetiltak
    val virksomhetsnummer: String? = null, // finnes kun for v1
    val arrangor: ArrangorDto? = null, // finnes kun for v2
) {
    data class Tiltakstype(
        val arenaKode: String,
        val tiltakskode: String,
    ) {
        fun erStottet() = this.tiltakskode in Tiltakskode.entries.toTypedArray().map { it.name }
    }

    data class ArrangorDto(
        val organisasjonsnummer: String,
    )

    fun toModel(arrangor: Arrangor, tiltakstype: no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype) = Deltakerliste(
        id = this.id,
        tiltakstype = tiltakstype,
        navn = this.navn ?: tiltakstype.navn,
        status = this.status?.let { Deltakerliste.Status.fromString(this.status) },
        startDato = this.startDato,
        sluttDato = this.sluttDato,
        oppstart = this.oppstart,
        arrangor = arrangor,
        apentForPamelding = apentForPamelding,
    )
}

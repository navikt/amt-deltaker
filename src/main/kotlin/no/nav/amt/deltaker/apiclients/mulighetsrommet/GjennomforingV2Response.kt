package no.nav.amt.deltaker.apiclients.mulighetsrommet

import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate
import java.util.UUID

data class GjennomforingV2Response(
    val id: UUID,
    val tiltakstype: Tiltakstype,
    val navn: String? = null,
    val startDato: LocalDate? = null, // finnes kun for gruppetiltak
    val sluttDato: LocalDate? = null, // finnes kun for gruppetiltak
    val status: String? = null, // finnes kun for gruppetiltak
    val oppstart: Oppstartstype? = null, // finnes kun for gruppetiltak
    val apentForPamelding: Boolean = true, // finnes kun for gruppetiltak
    val arrangor: Arrangor,
) {
    data class Tiltakstype(
        val tiltakskode: String,
    )

    data class Arrangor(
        val organisasjonsnummer: String,
    )

    fun toModel(
        arrangor: no.nav.amt.lib.models.deltaker.Arrangor,
        tiltakstype: no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype,
    ) = Deltakerliste(
        id = this.id,
        tiltakstype = tiltakstype,
        navn = this.navn ?: tiltakstype.navn,
        status = this.status?.let { Deltakerliste.Status.fromString(it) },
        startDato = this.startDato,
        sluttDato = this.sluttDato,
        oppstart = this.oppstart,
        arrangor = arrangor,
        apentForPamelding = apentForPamelding,
    )
}

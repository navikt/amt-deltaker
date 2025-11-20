package no.nav.amt.deltaker.deltakerliste.kafka

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate
import java.util.UUID

data class DeltakerlistePayload(
    val id: UUID,
    val navn: String? = null, // finnes kun for gruppetiltak
    val tiltakskode: String? = null, // skal gjøres non-nullable
    val tiltakstype: Tiltakstype? = null, // skal fjernes
    val startDato: LocalDate? = null, // finnes kun for gruppetiltak
    val sluttDato: LocalDate? = null, // finnes kun for gruppetiltak
    val status: String? = null, // finnes kun for gruppetiltak
    val oppstart: Oppstartstype? = null, // finnes kun for gruppetiltak
    val apentForPamelding: Boolean = true, // finnes kun for gruppetiltak
    val oppmoteSted: String? = null,
    val arrangor: Arrangor,
) {
    data class Tiltakstype(
        val tiltakskode: String,
    )

    data class Arrangor(
        val organisasjonsnummer: String,
    )

    // erstattes av tiltakskode senere
    @get:JsonIgnore
    val effectiveTiltakskode: String
        get() = tiltakskode ?: tiltakstype?.tiltakskode ?: throw IllegalStateException("Tiltakskode er ikke satt")

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
        apentForPamelding = apentForPamelding,
        oppmoteSted = this.oppmoteSted,
        arrangor = arrangor,
    )
}

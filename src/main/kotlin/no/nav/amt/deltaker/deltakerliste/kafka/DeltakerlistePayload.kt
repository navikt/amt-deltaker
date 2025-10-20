package no.nav.amt.deltaker.deltakerliste.kafka

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import java.time.LocalDate
import java.util.UUID

data class DeltakerlistePayload(
    val type: String? = null, // finnes kun for v2, kan fjernes etter overgang til v2
    val id: UUID,
    val tiltakstype: Tiltakstype,
    val navn: String? = null, // finnes kun for gruppetiltak
    val startDato: LocalDate? = null, // finnes kun for gruppetiltak
    val sluttDato: LocalDate? = null, // finnes kun for gruppetiltak
    val status: String? = null, // finnes kun for gruppetiltak
    val oppstart: Oppstartstype? = null, // finnes kun for gruppetiltak
    val apentForPamelding: Boolean = true, // finnes kun for gruppetiltak
    val virksomhetsnummer: String? = null, // finnes kun for v1
    val arrangor: Arrangor? = null, // finnes kun for v2
) {
    data class Tiltakstype(
        val tiltakskode: String,
    )

    data class Arrangor(
        val organisasjonsnummer: String,
    )

    @get:JsonIgnore
    val organisasjonsnummer: String
        get() = setOfNotNull(arrangor?.organisasjonsnummer, virksomhetsnummer)
            .firstOrNull()
            ?: throw IllegalStateException("Virksomhetsnummer mangler")

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

    companion object {
        const val ENKELTPLASS_V2_TYPE = "TiltaksgjennomforingV2.Enkeltplass"
        const val GRUPPE_V2_TYPE = "TiltaksgjennomforingV2.Gruppe"
    }
}

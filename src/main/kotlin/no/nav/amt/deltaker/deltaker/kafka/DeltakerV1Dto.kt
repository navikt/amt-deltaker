package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.deltaker.model.Kilde
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class DeltakerV1Dto(
    val id: UUID,
    val gjennomforingId: UUID,
    val personIdent: String,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val status: DeltakerStatusDto,
    val registrertDato: LocalDateTime,
    val dagerPerUke: Float?,
    val prosentStilling: Float?,
    val endretDato: LocalDateTime,
    val kilde: Kilde?,
    val innhold: DeltakelsesinnholdDto?,
) {
    data class DeltakerStatusDto(
        val type: DeltakerStatus.Type,
        val statusTekst: String,
        val aarsak: DeltakerStatus.Aarsak.Type?,
        val aarsakTekst: String?,
        val opprettetDato: LocalDateTime,
    )

    data class DeltakelsesinnholdDto(
        val ledetekst: String?,
        val innhold: List<InnholdDto>,
    )

    data class InnholdDto(
        val tekst: String,
        val innholdskode: String,
        val beskrivelse: String?,
    )
}

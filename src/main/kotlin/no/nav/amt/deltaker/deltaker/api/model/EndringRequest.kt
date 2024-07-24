package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.Innhold
import java.time.LocalDate
import java.util.UUID

sealed interface EndringRequest {
    val endretAv: String
    val endretAvEnhet: String
}

sealed interface EndringForslagRequest : EndringRequest {
    val forslagId: UUID?
}

data class BakgrunnsinformasjonRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val bakgrunnsinformasjon: String?,
) : EndringRequest

data class InnholdRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val innhold: List<Innhold>,
) : EndringRequest

data class DeltakelsesmengdeRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
    val begrunnelse: String?,
) : EndringForslagRequest

data class StartdatoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val startdato: LocalDate?,
    val sluttdato: LocalDate? = null,
) : EndringRequest

data class SluttdatoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val sluttdato: LocalDate,
    val begrunnelse: String?,
) : EndringForslagRequest

data class SluttarsakRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val aarsak: DeltakerEndring.Aarsak,
) : EndringRequest

data class ForlengDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val sluttdato: LocalDate,
    val begrunnelse: String?,
) : EndringForslagRequest

data class IkkeAktuellRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
) : EndringForslagRequest

data class AvsluttDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val sluttdato: LocalDate,
    val aarsak: DeltakerEndring.Aarsak,
    val begrunnelse: String?,
) : EndringForslagRequest

data class ReaktiverDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val begrunnelse: String,
) : EndringRequest

fun EndringRequest.getForslagId(): UUID? = if (this is EndringForslagRequest) {
    this.forslagId
} else {
    null
}

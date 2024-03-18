package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
import no.nav.amt.deltaker.deltaker.model.Innhold
import java.time.LocalDate

sealed interface EndringRequest {
    val endretAv: String
    val endretAvEnhet: String
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
    val deltakelsesprosent: Int?,
    val dagerPerUke: Int?,
) : EndringRequest

data class StartdatoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val startdato: LocalDate?,
    val sluttdato: LocalDate? = null,
) : EndringRequest

data class SluttdatoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val sluttdato: LocalDate,
) : EndringRequest

data class SluttarsakRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val aarsak: DeltakerEndring.Aarsak,
) : EndringRequest

data class ForlengDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val sluttdato: LocalDate,
) : EndringRequest

data class IkkeAktuellRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val aarsak: DeltakerEndring.Aarsak,
) : EndringRequest

data class AvsluttDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val sluttdato: LocalDate,
    val aarsak: DeltakerEndring.Aarsak,
) : EndringRequest

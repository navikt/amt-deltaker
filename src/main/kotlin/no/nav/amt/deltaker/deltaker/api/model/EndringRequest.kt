package no.nav.amt.deltaker.deltaker.api.model

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
) : EndringRequest

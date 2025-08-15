package no.nav.amt.deltaker.deltaker.api.model.request

data class BakgrunnsinformasjonRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val bakgrunnsinformasjon: String?,
) : EndringRequest

package no.nav.amt.deltaker.deltaker.api.deltaker.request

data class ReaktiverDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val begrunnelse: String,
) : EndringRequest

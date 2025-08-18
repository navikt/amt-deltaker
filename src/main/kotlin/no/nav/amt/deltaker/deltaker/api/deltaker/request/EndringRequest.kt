package no.nav.amt.deltaker.deltaker.api.deltaker.request

sealed interface EndringRequest {
    val endretAv: String
    val endretAvEnhet: String
}

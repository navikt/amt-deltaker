package no.nav.amt.deltaker.deltaker.api.model.request

sealed interface EndringRequest {
    val endretAv: String
    val endretAvEnhet: String
}

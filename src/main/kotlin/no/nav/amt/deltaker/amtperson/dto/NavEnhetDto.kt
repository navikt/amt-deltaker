package no.nav.amt.deltaker.amtperson.dto

import no.nav.amt.deltaker.navenhet.NavEnhet
import java.util.UUID

data class NavEnhetDto(
    val id: UUID,
    val enhetId: String,
    val navn: String,
) {
    fun tilNavEnhet(): NavEnhet = NavEnhet(
        id = id,
        enhetsnummer = enhetId,
        navn = navn,
    )
}

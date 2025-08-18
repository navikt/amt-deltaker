package no.nav.amt.deltaker.deltaker.api.deltaker.request

import java.util.UUID

sealed interface EndringForslagRequest : EndringRequest {
    val forslagId: UUID?
}

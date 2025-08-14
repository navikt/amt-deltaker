package no.nav.amt.deltaker.deltaker.api.model.request

import java.util.UUID

sealed interface EndringForslagRequest : EndringRequest {
    val forslagId: UUID?
}

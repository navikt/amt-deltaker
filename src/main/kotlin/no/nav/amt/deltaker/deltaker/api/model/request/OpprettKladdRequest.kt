package no.nav.amt.deltaker.deltaker.api.model.request

import java.util.UUID

data class OpprettKladdRequest(
    val deltakerlisteId: UUID,
    val personident: String,
)

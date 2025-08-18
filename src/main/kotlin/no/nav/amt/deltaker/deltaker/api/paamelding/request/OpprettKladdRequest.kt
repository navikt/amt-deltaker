package no.nav.amt.deltaker.deltaker.api.paamelding.request

import java.util.UUID

data class OpprettKladdRequest(
    val deltakerlisteId: UUID,
    val personident: String,
)

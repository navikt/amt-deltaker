package no.nav.amt.deltaker.deltaker.api.model

import java.util.UUID

data class OpprettKladdRequest(
    val deltakerlisteId: UUID,
    val personident: String,
    val opprettetAv: String,
    val opprettetAvEnhet: String,
)

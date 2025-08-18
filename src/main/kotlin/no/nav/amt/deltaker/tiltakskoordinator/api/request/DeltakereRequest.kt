package no.nav.amt.deltaker.tiltakskoordinator.api.request

import java.util.UUID

data class DeltakereRequest(
    val deltakere: List<UUID>,
    val endretAv: String,
)

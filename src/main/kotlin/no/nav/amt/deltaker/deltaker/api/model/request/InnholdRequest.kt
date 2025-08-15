package no.nav.amt.deltaker.deltaker.api.model.request

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold

data class InnholdRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    val deltakelsesinnhold: Deltakelsesinnhold,
) : EndringRequest

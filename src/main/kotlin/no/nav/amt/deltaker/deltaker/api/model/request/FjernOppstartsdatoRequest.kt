package no.nav.amt.deltaker.deltaker.api.model.request

import java.util.UUID

data class FjernOppstartsdatoRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val begrunnelse: String?,
) : EndringForslagRequest

package no.nav.amt.deltaker.deltaker.api.model.request

import java.time.LocalDate
import java.util.UUID

data class ForlengDeltakelseRequest(
    override val endretAv: String,
    override val endretAvEnhet: String,
    override val forslagId: UUID?,
    val sluttdato: LocalDate,
    val begrunnelse: String?,
) : EndringForslagRequest

package no.nav.amt.deltaker.apiclients.arrangor

import no.nav.amt.deltaker.arrangor.Arrangor
import java.util.UUID

data class ArrangorResponse(
    val id: UUID,
    val navn: String,
    val organisasjonsnummer: String,
    val overordnetArrangor: Arrangor?,
) {
    fun toModel() = Arrangor(
        id = id,
        navn = navn,
        organisasjonsnummer = organisasjonsnummer,
        overordnetArrangorId = overordnetArrangor?.id,
    )
}

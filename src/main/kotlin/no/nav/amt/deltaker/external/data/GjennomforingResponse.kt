package no.nav.amt.deltaker.external.data

import java.util.UUID

data class GjennomforingResponse(
    val id: UUID,
    val navn: String,
    val type: String, // Arena type
    val tiltakstypeNavn: String,
    val arrangor: ArrangorResponse,
)

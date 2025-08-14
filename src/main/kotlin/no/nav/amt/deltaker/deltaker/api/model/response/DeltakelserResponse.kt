package no.nav.amt.deltaker.deltaker.api.model.response

import no.nav.amt.deltaker.deltaker.api.model.DeltakerKort

data class DeltakelserResponse(
    val aktive: List<DeltakerKort>,
    val historikk: List<DeltakerKort>,
) {
    data class Tiltakstype(
        val navn: String,
        val tiltakskode: no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype.ArenaKode,
    )
}

package no.nav.amt.deltaker.deltakerliste.tiltakstype

import java.util.UUID

data class Tiltakstype(
    val id: UUID,
    val navn: String,
    val type: Type,
    val innhold: DeltakerRegistreringInnhold?,
) {
    enum class Type {
        INDOPPFAG,
        ARBFORB,
        AVKLARAG,
        VASV,
        ARBRRHDAG,
        DIGIOPPARB,
        JOBBK,
        GRUPPEAMO,
        GRUFAGYRKE,
    }
}

data class DeltakerRegistreringInnhold(
    val innholdselementer: List<Innholdselement>,
    val ledetekst: String,
)

data class Innholdselement(
    val tekst: String,
    val innholdskode: String,
)

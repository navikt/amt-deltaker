package no.nav.amt.deltaker.deltaker.model

data class Deltakelsesinnhold(
    val ledetekst: String,
    val innhold: List<Innhold>,
)

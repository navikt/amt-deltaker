package no.nav.amt.deltaker.deltaker.model

data class Deltakelsesinnhold(
    val ledetekst: String? = null,
    val innhold: List<Innhold>,
)

data class Innhold(
    val tekst: String,
    val innholdskode: String,
    val valgt: Boolean,
    val beskrivelse: String?,
)

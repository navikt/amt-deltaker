package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold

data class UtkastRequest(
    val deltakelsesinnhold: Deltakelsesinnhold,
    val bakgrunnsinformasjon: String?,
    val deltakelsesprosent: Float?,
    val dagerPerUke: Float?,
    val endretAv: String,
    val endretAvEnhet: String,
    val godkjentAvNav: Boolean,
)

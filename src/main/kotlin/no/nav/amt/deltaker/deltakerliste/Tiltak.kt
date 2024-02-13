package no.nav.amt.deltaker.deltakerliste

data class Tiltak(
    val navn: String,
    val type: Type,
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

fun arenaKodeTilTiltakstype(type: String?) = when (type) {
    "ARBFORB" -> Tiltak.Type.ARBFORB
    "ARBRRHDAG" -> Tiltak.Type.ARBRRHDAG
    "AVKLARAG" -> Tiltak.Type.AVKLARAG
    "DIGIOPPARB" -> Tiltak.Type.DIGIOPPARB
    "GRUPPEAMO" -> Tiltak.Type.GRUPPEAMO
    "INDOPPFAG" -> Tiltak.Type.INDOPPFAG
    "JOBBK" -> Tiltak.Type.JOBBK
    "VASV" -> Tiltak.Type.VASV
    "GRUFAGYRKE" -> Tiltak.Type.GRUFAGYRKE
    else -> error("Ukjent tiltakstype: $type")
}

fun cleanTiltaksnavn(navn: String) = when (navn) {
    "Arbeidsforberedende trening (AFT)" -> "Arbeidsforberedende trening"
    "Arbeidsrettet rehabilitering (dag)" -> "Arbeidsrettet rehabilitering"
    "Digitalt oppfølgingstiltak for arbeidsledige (jobbklubb)" -> "Digitalt oppfølgingstiltak"
    "Gruppe AMO" -> "Arbeidsmarkedsopplæring"
    else -> navn
}

fun erTiltakmedDeltakelsesmendge(type: Tiltak.Type) = when (type) {
    Tiltak.Type.ARBFORB -> true
    Tiltak.Type.VASV -> true
    else -> false
}

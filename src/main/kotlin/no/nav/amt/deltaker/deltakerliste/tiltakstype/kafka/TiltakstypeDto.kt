package no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka

import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype

fun erStottet(arenaKode: String) = arenaKode in setOf(
    "INDOPPFAG",
    "ARBFORB",
    "AVKLARAG",
    "VASV",
    "ARBRRHDAG",
    "DIGIOPPARB",
    "JOBBK",
    "GRUPPEAMO",
    "GRUFAGYRKE",
)

fun arenaKodeTilTiltakstype(type: String?) = when (type) {
    "ARBFORB" -> Tiltakstype.ArenaKode.ARBFORB
    "ARBRRHDAG" -> Tiltakstype.ArenaKode.ARBRRHDAG
    "AVKLARAG" -> Tiltakstype.ArenaKode.AVKLARAG
    "DIGIOPPARB" -> Tiltakstype.ArenaKode.DIGIOPPARB
    "GRUPPEAMO" -> Tiltakstype.ArenaKode.GRUPPEAMO
    "INDOPPFAG" -> Tiltakstype.ArenaKode.INDOPPFAG
    "JOBBK" -> Tiltakstype.ArenaKode.JOBBK
    "VASV" -> Tiltakstype.ArenaKode.VASV
    "GRUFAGYRKE" -> Tiltakstype.ArenaKode.GRUFAGYRKE
    else -> error("Ukjent tiltakstype: $type")
}

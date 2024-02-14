package no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka

import no.nav.amt.deltaker.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import java.util.UUID

data class TiltakstypeDto(
    val id: UUID,
    val navn: String,
    val arenaKode: String,
    val status: Tiltakstypestatus,
    val deltakerRegistreringInnhold: DeltakerRegistreringInnhold?,
) {
    fun toModel(): Tiltakstype {
        return Tiltakstype(
            id = id,
            navn = navn,
            type = arenaKodeTilTiltakstype(arenaKode),
            innhold = deltakerRegistreringInnhold,
        )
    }

    fun erStottet() = this.arenaKode in setOf(
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
}

fun arenaKodeTilTiltakstype(type: String?) = when (type) {
    "ARBFORB" -> Tiltakstype.Type.ARBFORB
    "ARBRRHDAG" -> Tiltakstype.Type.ARBRRHDAG
    "AVKLARAG" -> Tiltakstype.Type.AVKLARAG
    "DIGIOPPARB" -> Tiltakstype.Type.DIGIOPPARB
    "GRUPPEAMO" -> Tiltakstype.Type.GRUPPEAMO
    "INDOPPFAG" -> Tiltakstype.Type.INDOPPFAG
    "JOBBK" -> Tiltakstype.Type.JOBBK
    "VASV" -> Tiltakstype.Type.VASV
    "GRUFAGYRKE" -> Tiltakstype.Type.GRUFAGYRKE
    else -> error("Ukjent tiltakstype: $type")
}

enum class Tiltakstypestatus {
    Aktiv,
    Planlagt,
    Avsluttet,
}

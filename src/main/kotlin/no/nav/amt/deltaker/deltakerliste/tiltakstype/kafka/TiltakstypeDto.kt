package no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka

import no.nav.amt.deltaker.deltaker.model.Innsatsgruppe
import no.nav.amt.deltaker.deltakerliste.tiltakstype.DeltakerRegistreringInnhold
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
import java.util.UUID

data class TiltakstypeDto(
    val id: UUID,
    val navn: String,
    val tiltakskode: Tiltakstype.Tiltakskode,
    val arenaKode: String?,
    val innsatsgrupper: Set<Innsatsgruppe>,
    val deltakerRegistreringInnhold: DeltakerRegistreringInnhold?,
) {
    fun toModel(arenaKode: String): Tiltakstype {
        return Tiltakstype(
            id = id,
            navn = navn,
            tiltakskode = tiltakskode,
            arenaKode = Tiltakstype.ArenaKode.valueOf(arenaKode),
            innsatsgrupper = innsatsgrupper,
            innhold = deltakerRegistreringInnhold,
        )
    }
}

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

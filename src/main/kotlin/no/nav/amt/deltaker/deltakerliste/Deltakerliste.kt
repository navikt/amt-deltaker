package no.nav.amt.deltaker.deltakerliste

import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.util.UUID

data class Deltakerliste(
    val id: UUID,
    val tiltakstype: Tiltakstype,
    val navn: String,
    val status: Status,
    val startDato: LocalDate,
    val sluttDato: LocalDate? = null,
    val oppstart: Oppstartstype?,
    val apentForPamelding: Boolean,
    val arrangor: Arrangor,
) {
    enum class Oppstartstype {
        LOPENDE,
        FELLES,
    }

    enum class Status {
        GJENNOMFORES,
        AVBRUTT,
        AVLYST,
        AVSLUTTET,
        ;

        companion object {
            fun fromString(status: String) = when (status) {
                "GJENNOMFORES" -> GJENNOMFORES
                "AVBRUTT" -> AVBRUTT
                "AVLYST" -> AVLYST
                "AVSLUTTET" -> AVSLUTTET
                else -> error("Ukjent deltakerlistestatus: $status")
            }
        }
    }

    fun erAvlystEllerAvbrutt(): Boolean = status == Status.AVLYST || status == Status.AVBRUTT

    fun erAvsluttet(): Boolean = erAvlystEllerAvbrutt() || status == Status.AVSLUTTET

    fun erKurs(): Boolean = if (oppstart != null) {
        oppstart == Oppstartstype.FELLES
    } else {
        kursTiltak.contains(tiltakstype.arenaKode)
    }

    private val kursTiltak = setOf(
        Tiltakstype.ArenaKode.JOBBK,
        Tiltakstype.ArenaKode.GRUPPEAMO,
        Tiltakstype.ArenaKode.GRUFAGYRKE,
    )
}

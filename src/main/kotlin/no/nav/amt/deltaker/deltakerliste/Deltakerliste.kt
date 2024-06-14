package no.nav.amt.deltaker.deltakerliste

import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype
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
        PLANLAGT,
        ;

        companion object {
            fun fromString(status: String) = when (status) {
                "GJENNOMFORES" -> GJENNOMFORES
                "AVBRUTT" -> AVBRUTT
                "AVLYST" -> AVLYST
                "AVSLUTTET" -> AVSLUTTET
                "PLANLAGT", "APENT_FOR_INNSOK" -> PLANLAGT
                else -> error("Ukjent deltakerlistestatus: $status")
            }
        }
    }

    fun erAvlystEllerAvbrutt(): Boolean {
        return status == Status.AVLYST || status == Status.AVBRUTT
    }

    fun erAvsluttet(): Boolean {
        return erAvlystEllerAvbrutt() || status == Status.AVSLUTTET
    }

    fun erKurs(): Boolean {
        if (oppstart != null) {
            return oppstart == Oppstartstype.FELLES
        } else {
            return kursTiltak.contains(tiltakstype.arenaKode)
        }
    }

    private val kursTiltak = setOf(
        Tiltakstype.ArenaKode.JOBBK,
        Tiltakstype.ArenaKode.GRUPPEAMO,
        Tiltakstype.ArenaKode.GRUFAGYRKE,
    )
}

data class Innhold(
    val visningstekst: String,
    val type: String,
    val valgt: Boolean,
    val beskrivelse: String?,
)

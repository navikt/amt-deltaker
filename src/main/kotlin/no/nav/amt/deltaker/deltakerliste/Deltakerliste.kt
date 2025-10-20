package no.nav.amt.deltaker.deltakerliste

import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.util.UUID

data class Deltakerliste(
    val id: UUID,
    val tiltakstype: Tiltakstype,
    val navn: String,
    val status: Status?,
    val startDato: LocalDate?,
    val sluttDato: LocalDate? = null,
    val oppstart: Oppstartstype?,
    val apentForPamelding: Boolean,
    val arrangor: Arrangor,
) {
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

    val erFellesOppstart get() = oppstart == Oppstartstype.FELLES
}

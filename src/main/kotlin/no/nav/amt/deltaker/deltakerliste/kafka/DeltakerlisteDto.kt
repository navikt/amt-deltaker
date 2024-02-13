package no.nav.amt.deltaker.deltakerliste.kafka

import no.nav.amt.deltaker.arrangor.Arrangor
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.Tiltak
import no.nav.amt.deltaker.deltakerliste.arenaKodeTilTiltakstype
import java.time.LocalDate
import java.util.UUID

data class DeltakerlisteDto(
    val id: UUID,
    val tiltakstype: Tiltakstype,
    val navn: String,
    val startDato: LocalDate,
    val sluttDato: LocalDate? = null,
    val status: String,
    val virksomhetsnummer: String,
    val oppstart: Deltakerliste.Oppstartstype?,
) {
    data class Tiltakstype(
        val navn: String,
        val arenaKode: String,
    ) {
        fun toModel() = Tiltak(
            this.navn,
            arenaKodeTilTiltakstype(this.arenaKode),
        )

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

    fun toModel(arrangor: Arrangor) = Deltakerliste(
        id = this.id,
        tiltak = this.tiltakstype.toModel(),
        navn = this.navn,
        status = Deltakerliste.Status.fromString(this.status),
        startDato = this.startDato,
        sluttDato = this.sluttDato,
        oppstart = this.oppstart,
        arrangor = arrangor,
    )
}

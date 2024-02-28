package no.nav.amt.deltaker.deltaker.model

import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.navbruker.model.NavBruker
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Deltaker(
    val id: UUID,
    val navBruker: NavBruker,
    val deltakerliste: Deltakerliste,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val innhold: List<Innhold>,
    val status: DeltakerStatus,
    val vedtaksinformasjon: Vedtaksinformasjon?,
    val sistEndretAv: UUID,
    val sistEndretAvEnhet: UUID,
    val sistEndret: LocalDateTime,
    val opprettet: LocalDateTime,
) {
    fun harSluttet(): Boolean {
        return status.type in AVSLUTTENDE_STATUSER
    }

    fun deltarPaKurs(): Boolean {
        return deltakerliste.erKurs()
    }

    data class Vedtaksinformasjon(
        val fattet: LocalDateTime?,
        val fattetAvNav: FattetAvNav?,
        val opprettet: LocalDateTime,
        val opprettetAv: UUID,
        val opprettetAvEnhet: UUID,
        val sistEndret: LocalDateTime,
        val sistEndretAv: UUID,
        val sistEndretAvEnhet: UUID,
    )
}

data class Innhold(
    val tekst: String,
    val innholdskode: String,
    val valgt: Boolean,
    val beskrivelse: String?,
)

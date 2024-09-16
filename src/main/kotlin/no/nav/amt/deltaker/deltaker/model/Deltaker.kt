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
    val deltakelsesinnhold: Deltakelsesinnhold?, // arenadeltakere vil ikke ha denne
    val status: DeltakerStatus,
    val vedtaksinformasjon: Vedtaksinformasjon?,
    val sistEndret: LocalDateTime,
    val kilde: Kilde,
) {
    fun harSluttet(): Boolean {
        return status.type in AVSLUTTENDE_STATUSER
    }

    fun deltarPaKurs(): Boolean {
        return deltakerliste.erKurs()
    }

    fun toDeltakerVedVedtak(): DeltakerVedVedtak = DeltakerVedVedtak(
        id = id,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = deltakelsesinnhold,
        status = status,
    )

    data class Vedtaksinformasjon(
        val fattet: LocalDateTime?,
        val fattetAvNav: Boolean,
        val opprettet: LocalDateTime,
        val opprettetAv: UUID,
        val opprettetAvEnhet: UUID,
        val sistEndret: LocalDateTime,
        val sistEndretAv: UUID,
        val sistEndretAvEnhet: UUID,
    )
}

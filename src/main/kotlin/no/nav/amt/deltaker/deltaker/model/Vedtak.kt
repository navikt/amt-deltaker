package no.nav.amt.deltaker.deltaker.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Vedtak(
    val id: UUID,
    val deltakerId: UUID,
    val fattet: LocalDateTime?,
    val gyldigTil: LocalDateTime?,
    val deltakerVedVedtak: DeltakerVedVedtak,
    val fattetAvNav: Boolean,
    val opprettet: LocalDateTime,
    val opprettetAv: UUID,
    val opprettetAvEnhet: UUID,
    val sistEndret: LocalDateTime,
    val sistEndretAv: UUID,
    val sistEndretAvEnhet: UUID,
) {
    fun tilVedtaksinformasjon(): Deltaker.Vedtaksinformasjon = Deltaker.Vedtaksinformasjon(
        fattet = fattet,
        fattetAvNav = fattetAvNav,
        opprettet = opprettet,
        opprettetAv = opprettetAv,
        opprettetAvEnhet = opprettetAvEnhet,
        sistEndret = sistEndret,
        sistEndretAv = sistEndretAv,
        sistEndretAvEnhet = sistEndretAvEnhet,
    )
}

data class DeltakerVedVedtak(
    val id: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val deltakelsesinnhold: Deltakelsesinnhold?,
    val status: DeltakerStatus,
)

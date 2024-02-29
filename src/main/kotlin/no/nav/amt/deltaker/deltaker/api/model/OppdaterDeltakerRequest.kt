package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.deltaker.deltaker.model.DeltakerEndring.Endring
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring.Endringstype
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.DeltakerVedVedtak
import no.nav.amt.deltaker.deltaker.model.Innhold
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class OppdaterDeltakerRequest(
    val id: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val innhold: List<Innhold>,
    val status: DeltakerStatus,
    val vedtaksinformasjon: Vedtaksinformasjon?,
    val sistEndretAv: String,
    val sistEndretAvEnhet: String,
    val sistEndret: LocalDateTime,
    val deltakerEndring: DeltakerEndring?,
) {
    data class Vedtaksinformasjon(
        val id: UUID,
        val fattet: LocalDateTime?,
        val gyldigTil: LocalDateTime?,
        val deltakerVedVedtak: DeltakerVedVedtak,
        val fattetAvNav: Boolean,
        val opprettet: LocalDateTime,
        val opprettetAv: String,
        val opprettetAvEnhet: String,
        val sistEndret: LocalDateTime,
        val sistEndretAv: String,
        val sistEndretAvEnhet: String,
    )

    data class DeltakerEndring(
        val id: UUID,
        val deltakerId: UUID,
        val endringstype: Endringstype,
        val endring: Endring,
        val endretAv: String,
        val endretAvEnhet: String,
        val endret: LocalDateTime,
    )
}

package no.nav.amt.deltaker.internal.konvertervedtak

import no.nav.amt.deltaker.deltaker.model.Deltakelsesinnhold
import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import no.nav.amt.deltaker.deltaker.model.DeltakerVedVedtak
import no.nav.amt.deltaker.deltaker.model.Innhold
import no.nav.amt.deltaker.deltaker.model.Vedtak
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class VedtakOld(
    val id: UUID,
    val deltakerId: UUID,
    val fattet: LocalDateTime?,
    val gyldigTil: LocalDateTime?,
    val deltakerVedVedtak: DeltakerVedVedtakOld,
    val fattetAvNav: Boolean,
    val opprettet: LocalDateTime,
    val opprettetAv: UUID,
    val opprettetAvEnhet: UUID,
    val sistEndret: LocalDateTime,
    val sistEndretAv: UUID,
    val sistEndretAvEnhet: UUID,
    val ledetekst: String?,
) {
    fun toVedtak(): Vedtak = Vedtak(
        id = id,
        deltakerId = deltakerId,
        fattet = fattet,
        gyldigTil = gyldigTil,
        deltakerVedVedtak = deltakerVedVedtak.toDeltakerVedVedtak(ledetekst),
        fattetAvNav = fattetAvNav,
        opprettet = opprettet,
        opprettetAv = opprettetAv,
        opprettetAvEnhet = opprettetAvEnhet,
        sistEndret = sistEndret,
        sistEndretAv = sistEndretAv,
        sistEndretAvEnhet = sistEndretAvEnhet,
    )
}

data class DeltakerVedVedtakOld(
    val id: UUID,
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
    val dagerPerUke: Float?,
    val deltakelsesprosent: Float?,
    val bakgrunnsinformasjon: String?,
    val innhold: List<Innhold>,
    val status: DeltakerStatus,
) {
    fun toDeltakerVedVedtak(ledetekst: String?): DeltakerVedVedtak = DeltakerVedVedtak(
        id = id,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        bakgrunnsinformasjon = bakgrunnsinformasjon,
        deltakelsesinnhold = ledetekst?.let {
            Deltakelsesinnhold(
                ledetekst = it,
                innhold = innhold,
            )
        },
        status = status,
    )
}

fun Vedtak.toVedtakOld(): VedtakOld = VedtakOld(
    id = id,
    deltakerId = deltakerId,
    fattet = fattet,
    gyldigTil = gyldigTil,
    deltakerVedVedtak = deltakerVedVedtak.toDeltakerVedVedtakOld(),
    fattetAvNav = fattetAvNav,
    opprettet = opprettet,
    opprettetAv = opprettetAv,
    opprettetAvEnhet = opprettetAvEnhet,
    sistEndret = sistEndret,
    sistEndretAv = sistEndretAv,
    sistEndretAvEnhet = sistEndretAvEnhet,
    ledetekst = deltakerVedVedtak.deltakelsesinnhold?.ledetekst,
)

fun DeltakerVedVedtak.toDeltakerVedVedtakOld(): DeltakerVedVedtakOld = DeltakerVedVedtakOld(
    id = id,
    startdato = startdato,
    sluttdato = sluttdato,
    dagerPerUke = dagerPerUke,
    deltakelsesprosent = deltakelsesprosent,
    bakgrunnsinformasjon = bakgrunnsinformasjon,
    innhold = deltakelsesinnhold?.innhold ?: emptyList(),
    status = status,
)

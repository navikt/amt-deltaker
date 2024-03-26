package no.nav.amt.deltaker.deltaker.api.model

import java.time.LocalDate
import java.util.UUID

data class DeltakelserResponse(
    val aktive: List<AktivDeltakelse>,
    val historikk: List<HistoriskDeltakelse>,
) {
    data class Tiltakstype(
        val navn: String,
        val tiltakskode: no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype.Type,
    )
}

data class AktivDeltakelse(
    val deltakerId: UUID,
    val innsoktDato: LocalDate?,
    val sistEndretdato: LocalDate,
    val aktivStatus: AktivStatusType,
    val tittel: String,
    val tiltakstype: DeltakelserResponse.Tiltakstype,
) {
    enum class AktivStatusType {
        KLADD,
        UTKAST_TIL_PAMELDING,
        VENTER_PA_OPPSTART,
        DELTAR,
        SOKT_INN,
        VURDERES,
        VENTELISTE,
        PABEGYNT_REGISTRERING,
    }
}

data class HistoriskDeltakelse(
    val deltakerId: UUID,
    val innsoktDato: LocalDate,
    val periode: Periode?,
    val historiskStatus: HistoriskStatus,
    val tittel: String,
    val tiltakstype: DeltakelserResponse.Tiltakstype,
) {
    data class HistoriskStatus(
        val historiskStatusType: HistoriskStatusType,
        val aarsak: String?,
    )

    enum class HistoriskStatusType {
        AVBRUTT_UTKAST,
        HAR_SLUTTET,
        IKKE_AKTUELL,
        FEILREGISTRERT,
        AVBRUTT,
        FULLFORT,
    }
}

data class Periode(
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
)

val AKTIVE_STATUSER = AktivDeltakelse.AktivStatusType.entries.map { it.name }
val HISTORISKE_STATUSER = HistoriskDeltakelse.HistoriskStatusType.entries.map { it.name }

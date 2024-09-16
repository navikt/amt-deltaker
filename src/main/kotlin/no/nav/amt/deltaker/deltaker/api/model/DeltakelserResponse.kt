package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class DeltakelserResponse(
    val aktive: List<DeltakerKort>,
    val historikk: List<DeltakerKort>,
) {
    data class Tiltakstype(
        val navn: String,
        val tiltakskode: no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype.ArenaKode,
    )
}

data class DeltakerKort(
    val deltakerId: UUID,
    val deltakerlisteId: UUID,
    val tittel: String,
    val tiltakstype: DeltakelserResponse.Tiltakstype,
    val status: Status,
    val innsoktDato: LocalDate?,
    val sistEndretDato: LocalDate?,
    val periode: Periode?,
) {
    data class Status(
        val type: DeltakerStatus.Type,
        val visningstekst: String,
        val aarsak: String?,
    )
}

data class Periode(
    val startdato: LocalDate?,
    val sluttdato: LocalDate?,
)

val AKTIVE_STATUSER = listOf(
    DeltakerStatus.Type.KLADD,
    DeltakerStatus.Type.UTKAST_TIL_PAMELDING,
    DeltakerStatus.Type.VENTER_PA_OPPSTART,
    DeltakerStatus.Type.DELTAR,
    DeltakerStatus.Type.SOKT_INN,
    DeltakerStatus.Type.VURDERES,
    DeltakerStatus.Type.VENTELISTE,
)
val HISTORISKE_STATUSER = listOf(
    DeltakerStatus.Type.AVBRUTT_UTKAST,
    DeltakerStatus.Type.HAR_SLUTTET,
    DeltakerStatus.Type.IKKE_AKTUELL,
    DeltakerStatus.Type.AVBRUTT,
    DeltakerStatus.Type.FULLFORT,
    DeltakerStatus.Type.FEILREGISTRERT,
)

package no.nav.amt.deltaker.deltaker.api.model

import no.nav.amt.deltaker.deltaker.model.DeltakerStatus
import java.time.LocalDate
import java.util.UUID

data class DeltakelserResponse(
    val aktive: List<DeltakerKort>,
    val historikk: List<DeltakerKort>,
) {
    data class Tiltakstype(
        val navn: String,
        val tiltakskode: no.nav.amt.deltaker.deltakerliste.tiltakstype.Tiltakstype.Type,
    )
}

data class DeltakerKort(
    val deltakerId: UUID,
    val tittel: String,
    val tiltakstype: DeltakelserResponse.Tiltakstype,
    val status: Status,
    val innsoktDato: LocalDate?,
    val sistEndretdato: LocalDate?,
    val periode: Periode?,
) {
    data class Status(
        val status: DeltakerStatus.Type,
        val statustekst: String,
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
)

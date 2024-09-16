package no.nav.amt.deltaker.deltaker.model

import no.nav.amt.lib.models.deltaker.DeltakerStatus

val AVSLUTTENDE_STATUSER = listOf(
    DeltakerStatus.Type.HAR_SLUTTET,
    DeltakerStatus.Type.IKKE_AKTUELL,
    DeltakerStatus.Type.FEILREGISTRERT,
    DeltakerStatus.Type.AVBRUTT,
    DeltakerStatus.Type.FULLFORT,
    DeltakerStatus.Type.AVBRUTT_UTKAST,
)

val VENTER_PAA_PLASS_STATUSER = listOf(
    DeltakerStatus.Type.SOKT_INN,
    DeltakerStatus.Type.VURDERES,
    DeltakerStatus.Type.VENTELISTE,
    DeltakerStatus.Type.PABEGYNT_REGISTRERING,
)

val HAR_IKKE_STARTET_STATUSER = listOf(DeltakerStatus.Type.VENTER_PA_OPPSTART).plus(VENTER_PAA_PLASS_STATUSER)

fun DeltakerStatus.harIkkeStartet(): Boolean {
    return type in HAR_IKKE_STARTET_STATUSER
}

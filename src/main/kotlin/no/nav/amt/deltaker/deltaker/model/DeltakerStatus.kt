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

fun DeltakerStatus.Aarsak.getVisningsnavn(): String {
    val beskrivelse = this.beskrivelse
    if (beskrivelse != null) {
        return beskrivelse
    }
    return when (type) {
        DeltakerStatus.Aarsak.Type.SYK -> "Syk"
        DeltakerStatus.Aarsak.Type.FATT_JOBB -> "Fått jobb"
        DeltakerStatus.Aarsak.Type.TRENGER_ANNEN_STOTTE -> "Trenger annen støtte"
        DeltakerStatus.Aarsak.Type.FIKK_IKKE_PLASS -> "Fikk ikke plass"
        DeltakerStatus.Aarsak.Type.IKKE_MOTT -> "Møter ikke opp"
        DeltakerStatus.Aarsak.Type.ANNET -> "Annet"
        DeltakerStatus.Aarsak.Type.AVLYST_KONTRAKT -> "Avlyst kontrakt"
        DeltakerStatus.Aarsak.Type.UTDANNING -> "Utdanning"
        DeltakerStatus.Aarsak.Type.SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT -> "Samarbeidet med arrangøren er avbrutt"
    }
}

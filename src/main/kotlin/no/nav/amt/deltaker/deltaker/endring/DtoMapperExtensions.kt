package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate

fun Deltaker.getStatusEndretStartOgSluttdato(startdato: LocalDate?, sluttdato: LocalDate?): DeltakerStatus {
    val today = LocalDate.now()

    val startdatoPassert = startdato?.let { it <= today } ?: false
    val startdatoFremtid = startdato?.let { it > today } ?: true

    val sluttdatoPassert = sluttdato?.let { it < today } ?: false
    val sluttdatoFremtid = sluttdato?.let { it >= today } ?: true

    return when (status.type) {
        DeltakerStatus.Type.VENTER_PA_OPPSTART -> when {
            sluttdatoPassert && startdato == null -> nyDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL)
            sluttdatoPassert -> nyDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET)
            startdatoPassert -> nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
            else -> status
        }

        DeltakerStatus.Type.DELTAR -> when {
            sluttdatoPassert -> nyDeltakerStatus(DeltakerStatus.Type.HAR_SLUTTET)
            startdatoFremtid -> nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
            else -> status
        }

        DeltakerStatus.Type.HAR_SLUTTET,
        DeltakerStatus.Type.AVBRUTT,
        DeltakerStatus.Type.FULLFORT,
        -> when {
            sluttdatoFremtid && startdatoPassert -> nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
            sluttdatoFremtid -> nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
            else -> status
        }

        else -> status
    }
}

fun DeltakerEndring.Aarsak.toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
    DeltakerStatus.Aarsak.Type.valueOf(type.name),
    beskrivelse,
)

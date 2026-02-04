package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.DeltakerUtils.nyDeltakerStatus
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDate

fun Deltaker.getStatusEndretStartOgSluttdato(startdato: LocalDate?, sluttdato: LocalDate?): DeltakerStatus =
    if (status.type == DeltakerStatus.Type.VENTER_PA_OPPSTART && (sluttdato != null && sluttdato.isBefore(LocalDate.now()))) {
        if (startdato == null) {
            nyDeltakerStatus(DeltakerStatus.Type.IKKE_AKTUELL)
        } else {
            nyDeltakerStatus(getAvsluttendeStatus(harFullfort = true))
        }
    } else if (status.type == DeltakerStatus.Type.VENTER_PA_OPPSTART && (startdato != null && !startdato.isAfter(LocalDate.now()))) {
        nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
    } else if (status.type == DeltakerStatus.Type.DELTAR && (sluttdato != null && sluttdato.isBefore(LocalDate.now()))) {
        nyDeltakerStatus(getAvsluttendeStatus(harFullfort = true))
    } else if (status.type == DeltakerStatus.Type.DELTAR && (startdato == null || startdato.isAfter(LocalDate.now()))) {
        nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
    } else if (status.type == DeltakerStatus.Type.HAR_SLUTTET &&
        (sluttdato == null || !sluttdato.isBefore(LocalDate.now())) &&
        (startdato != null && !startdato.isAfter(LocalDate.now()))
    ) {
        nyDeltakerStatus(DeltakerStatus.Type.DELTAR)
    } else if (status.type == DeltakerStatus.Type.HAR_SLUTTET &&
        (sluttdato == null || !sluttdato.isBefore(LocalDate.now())) &&
        (startdato == null || startdato.isAfter(LocalDate.now()))
    ) {
        nyDeltakerStatus(DeltakerStatus.Type.VENTER_PA_OPPSTART)
    } else {
        status
    }

fun DeltakerEndring.Aarsak.toDeltakerStatusAarsak() = DeltakerStatus.Aarsak(
    DeltakerStatus.Aarsak.Type.valueOf(type.name),
    beskrivelse,
)

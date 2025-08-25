package no.nav.amt.deltaker.deltaker

import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDateTime
import java.util.UUID

object DeltakerUtils {
    fun nyDeltakerStatus(
        type: DeltakerStatus.Type,
        aarsak: DeltakerStatus.Aarsak? = null,
        gyldigFra: LocalDateTime = LocalDateTime.now(),
    ) = DeltakerStatus(
        id = UUID.randomUUID(),
        type = type,
        aarsak = aarsak,
        gyldigFra = gyldigFra,
        gyldigTil = null,
        opprettet = LocalDateTime.now(),
    )
}

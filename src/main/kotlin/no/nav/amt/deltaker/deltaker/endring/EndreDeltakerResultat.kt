package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus

data class EndreDeltakerResultat(
    val deltaker: Deltaker,
    val nesteStatus: DeltakerStatus? = null,
)

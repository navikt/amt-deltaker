package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus

class DeltakerMedFremtidigStatus(
    val deltaker: Deltaker,
    val fremtidigStatus: DeltakerStatus? = null,
)

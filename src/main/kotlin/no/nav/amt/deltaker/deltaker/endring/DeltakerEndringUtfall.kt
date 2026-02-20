package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus

sealed interface DeltakerEndringUtfall {
    class VellykketEndring(
        val deltaker: Deltaker,
        val nesteStatus: DeltakerStatus? = null,
    ) : DeltakerEndringUtfall

    class FremtidigEndring(
        val deltaker: Deltaker,
    ) : DeltakerEndringUtfall

    class UgyldigEndring(
        val error: Throwable,
    ) : DeltakerEndringUtfall

    val erVellykket: Boolean get() = this is VellykketEndring

    val erUgyldig: Boolean get() = this is UgyldigEndring

    fun getOrThrow(): Deltaker = when (this) {
        is VellykketEndring -> deltaker
        is FremtidigEndring -> deltaker
        is UgyldigEndring -> throw error
    }
}

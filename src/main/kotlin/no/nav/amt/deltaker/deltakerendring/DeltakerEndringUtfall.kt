package no.nav.amt.deltaker.deltakerendring

import no.nav.amt.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerStatus

sealed class DeltakerEndringUtfall {
    class VellykketEndring(
        val deltaker: Deltaker,
        val nesteStatus: DeltakerStatus? = null,
    ) : DeltakerEndringUtfall()

    class FremtidigEndring(
        val deltaker: Deltaker,
    ) : DeltakerEndringUtfall()

    class UgyldigEndring(
        val error: Throwable,
    ) : DeltakerEndringUtfall()

    val erVellykket: Boolean get() = this is VellykketEndring

    val erUgyldig: Boolean get() = this is UgyldigEndring

    fun getOrNull(): Deltaker? = when (this) {
        is VellykketEndring -> this.deltaker
        is FremtidigEndring -> this.deltaker
        is UgyldigEndring -> null
    }

    fun getOrThrow(): Deltaker = when (this) {
        is VellykketEndring -> this.deltaker
        is FremtidigEndring -> this.deltaker
        is UgyldigEndring -> throw this.error
    }
}

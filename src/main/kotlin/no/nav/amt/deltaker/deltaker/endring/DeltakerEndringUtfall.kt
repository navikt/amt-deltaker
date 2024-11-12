package no.nav.amt.deltaker.deltaker.endring

import no.nav.amt.deltaker.deltaker.model.Deltaker

sealed class DeltakerEndringUtfall {
    class VellykketEndring(
        val deltaker: Deltaker,
    ) : DeltakerEndringUtfall()

    class FremtidigEndring(
        val deltaker: Deltaker,
    ) : DeltakerEndringUtfall()

    class UgyldigEndring(
        val error: Throwable,
    ) : DeltakerEndringUtfall()

    val erVellykket: Boolean get() = this is VellykketEndring

    val erFremtidig: Boolean get() = this is FremtidigEndring

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

    inline fun onVellykketEllerFremtidigEndring(action: (deltaker: Deltaker) -> Unit): DeltakerEndringUtfall {
        when (this) {
            is VellykketEndring -> action(this.deltaker)
            is FremtidigEndring -> action(this.deltaker)
            is UgyldigEndring -> {}
        }
        return this
    }
}
